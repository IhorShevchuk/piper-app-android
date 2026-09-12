package dev.ihorshevchuk.piper.tts

import android.media.AudioFormat
import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import android.speech.tts.Voice
import android.util.Log
import dev.ihorshevchuk.piper.engine.PiperCreateOptions
import dev.ihorshevchuk.piper.engine.PiperEngine
import dev.ihorshevchuk.piper.player.SpeedCurve
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Android system TTS engine backed by our native piper1-gpl core.
 *
 * Manifest requirements (declared in this module's AndroidManifest.xml, merged
 * into the app): BIND_TEXT_TO_SPEECH_SERVICE permission, TTS_SERVICE
 * intent-filter, and the android.speech.tts meta-data pointing at tts_engine.xml.
 *
 * Threading: the framework calls onSynthesizeText on a binder thread.
 * PiperEngine serializes native calls on its own engine thread, and all
 * engine access here is additionally guarded by [engineLock] so voice
 * switches never race synthesis. onStop never takes the lock - it only
 * flips [stopRequested] and pokes the engine's own cancel flag, so a stop
 * request is honored between sentences and between audio chunks.
 */
class PiperTtsService : TextToSpeechService() {

    private val engineLock = Any()

    @Volatile
    private var engine: PiperEngine? = null
    private var loadedVoiceName: String? = null

    @Volatile
    private var currentLocale: Locale = Locale.getDefault()
    private val stopRequested = AtomicBoolean(false)

    @Volatile
    private var espeakDataDir: File? = null

    private lateinit var voiceStore: VoiceStore
    private lateinit var voicePrefs: VoicePrefs
    private var preloadThread: Thread? = null

    override fun onCreate() {
        super.onCreate()
        // Cheap on the main thread: no I/O beyond SharedPreferences.
        voiceStore = FileVoiceStore(File(filesDir, "voices").apply { mkdirs() })
        voicePrefs = VoicePrefs(this)

        // Heavy work - espeak-ng data staging (~25 MB first run) and voice
        // model load - stays off the main thread. onSynthesizeText loads
        // synchronously under engineLock if the preload hasn't finished.
        preloadThread = thread(name = "piper-tts-preload", isDaemon = true) {
            try {
                espeakDataDir = EspeakDataInstaller.ensure(this@PiperTtsService)
                val preferred = voicePrefs.activeVoiceName
                val voice = preferred?.let { voiceStore.findVoice(it) }
                    ?: voiceStore.listVoices().firstOrNull()
                if (voice != null) {
                    synchronized(engineLock) { engineFor(voice) }
                    Log.i(TAG, "preloaded voice ${voice.name}")
                } else {
                    Log.i(TAG, "no voices installed yet; service will report per-request")
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (e: Exception) {
                Log.w(TAG, "voice preload failed", e)
            }
        }
        Log.i(TAG, "PiperTtsService created")
    }

    // ------------------------------------------------------------------
    // Synthesis
    // ------------------------------------------------------------------

    override fun onSynthesizeText(request: SynthesisRequest, callback: SynthesisCallback) {
        stopRequested.set(false)
        val text = request.charSequenceText?.toString().orEmpty()
        if (text.isBlank()) {
            callback.done()
            return
        }
        try {
            val installed = voiceStore.listVoices()
            val voice = VoiceResolver.resolve(
                installed = installed,
                requestVoiceName = request.voiceName?.takeIf { it.isNotEmpty() },
                language = LanguageNormalizer.normalizeLanguage(request.language ?: ""),
                country = LanguageNormalizer.normalizeCountry(request.country ?: ""),
                preferredVoiceName = voicePrefs.activeVoiceName
            )
            if (voice == null) {
                Log.w(TAG, "onSynthesizeText: no voices installed")
                callback.error(TextToSpeech.ERROR_NOT_INSTALLED_YET)
                return
            }

            // iOS-parity speed: the 17-point curve + 2.2x sibilant clamp,
            // never the naive 1/rate.
            val lengthScale = SpeedCurve.lengthScaleForAndroidSpeechRate(request.speechRate)

            synchronized(engineLock) {
                if (stopRequested.get()) {
                    callback.done()
                    return
                }
                val eng = engineFor(voice)
                if (eng == null) {
                    callback.error(TextToSpeech.ERROR_SYNTHESIS)
                    return
                }
                val sampleRate = eng.currentSampleRate.get().takeIf { it > 0 } ?: 22050
                if (callback.start(sampleRate, AudioFormat.ENCODING_PCM_16BIT, 1)
                    != TextToSpeech.SUCCESS
                ) {
                    Log.w(TAG, "synthesis callback refused start")
                    return
                }
                val options = eng.defaultSynthesizeOptions().copy(lengthScale = lengthScale)
                val chunkLimit = callback.maxBufferSize.takeIf { it > 0 } ?: 8192
                var streaming = true
                // PiperEngine.synthesize splits into sentences internally
                // (piper-utils SentenceSplitter, ported from piper-objc) and
                // skips a failed sentence instead of aborting the utterance.
                eng.synthesize(text, options, onSamples = { samples ->
                    if (!streaming || stopRequested.get() || !callback.hasStarted()) return@synthesize
                    val pcm = PcmConverter.floatToPcm16Bytes(samples)
                    var offset = 0
                    while (offset < pcm.size && !stopRequested.get() && callback.hasStarted()) {
                        val n = minOf(chunkLimit, pcm.size - offset)
                        if (callback.audioAvailable(pcm, offset, n) != TextToSpeech.SUCCESS) {
                            streaming = false
                            return@synthesize
                        }
                        offset += n
                    }
                })
                callback.done()
            }
        } catch (e: Exception) {
            Log.e(TAG, "onSynthesizeText failed", e)
            try {
                if (callback.hasStarted()) callback.done()
                else callback.error(TextToSpeech.ERROR_SYNTHESIS)
            } catch (_: Exception) {
            }
        }
    }

    override fun onStop() {
        // Prompt: no locks. The synthesis loop checks this between chunks
        // and sentences; the engine's own cancel flag stops it between
        // sentences even mid-utterance.
        stopRequested.set(true)
        engine?.cancel()
    }

    override fun onDestroy() {
        stopRequested.set(true)
        preloadThread?.interrupt()
        synchronized(engineLock) {
            try {
                engine?.close()
            } catch (_: Exception) {
            }
            engine = null
            loadedVoiceName = null
        }
        super.onDestroy()
        Log.i(TAG, "PiperTtsService destroyed")
    }

    /**
     * Returns the engine with [voice] loaded, loading it on first use or
     * when the requested voice differs. Must be called holding [engineLock].
     */
    private fun engineFor(voice: VoiceInfo): PiperEngine? {
        engine?.let { if (loadedVoiceName == voice.name) return it }
        return try {
            try {
                engine?.close()
            } catch (_: Exception) {
            }
            val options = PiperCreateOptions(
                modelPath = voice.modelFile.absolutePath,
                // null configPath -> native uses modelPath + ".json", like iOS.
                configPath = voice.configFile?.absolutePath,
                espeakDataPath = espeakDataDir?.takeIf { it.isDirectory }?.absolutePath
            )
            PiperEngine(options, filesDir).also {
                engine = it
                loadedVoiceName = voice.name
                currentLocale = voice.locale
                Log.i(TAG, "loaded voice ${voice.name} (native ${PiperEngine.version()})")
            }
        } catch (e: Exception) {
            Log.e(TAG, "failed to load voice ${voice.name}", e)
            engine = null
            loadedVoiceName = null
            null
        }
    }

    // ------------------------------------------------------------------
    // Languages and voices
    // ------------------------------------------------------------------

    override fun onIsLanguageAvailable(lang: String?, country: String?, variant: String?): Int {
        if (lang.isNullOrBlank()) return TextToSpeech.LANG_NOT_SUPPORTED
        val normLang = LanguageNormalizer.normalizeLanguage(lang)
        val normCountry = LanguageNormalizer.normalizeCountry(country ?: "")

        val voices = try {
            voiceStore.listVoices()
        } catch (e: Exception) {
            Log.w(TAG, "onIsLanguageAvailable: voice listing failed", e)
            emptyList()
        }
        // No voices installed yet: still claim availability so the engine
        // stays selectable in system TTS settings. Synthesis will report
        // NOT_INSTALLED_YET per request until the download manager lands.
        if (voices.isEmpty()) return TextToSpeech.LANG_AVAILABLE

        val exact = voices.any {
            it.locale.language == normLang &&
                (normCountry.isEmpty() || it.locale.country.equals(normCountry, ignoreCase = true))
        }
        if (exact) return TextToSpeech.LANG_COUNTRY_AVAILABLE
        val langOnly = voices.any { it.locale.language == normLang }
        return if (langOnly) TextToSpeech.LANG_AVAILABLE
        else TextToSpeech.LANG_NOT_SUPPORTED
    }

    override fun onGetLanguage(): Array<String> {
        val l = currentLocale
        return arrayOf(l.language, l.country, l.variant)
    }

    override fun onLoadLanguage(lang: String?, country: String?, variant: String?): Int {
        val availability = onIsLanguageAvailable(lang, country, variant)
        if (availability == TextToSpeech.LANG_NOT_SUPPORTED) {
            return TextToSpeech.LANG_NOT_SUPPORTED
        }
        // Record the request, but do NOT load the model here: the framework
        // calls this on the binder thread during service probing, and a
        // model load would block it. The voice is loaded on the synthesis
        // path (or the background preload) under engineLock.
        currentLocale = Locale(
            LanguageNormalizer.normalizeLanguage(lang ?: "en"),
            LanguageNormalizer.normalizeCountry(country ?: ""),
            variant ?: ""
        )
        return availability
    }

    override fun onLoadVoice(voiceName: String): Int {
        val voice = voiceStore.findVoice(voiceName) ?: return TextToSpeech.ERROR
        return try {
            synchronized(engineLock) { engineFor(voice) }
                ?: return TextToSpeech.ERROR
            voicePrefs.activeVoiceName = voice.name
            TextToSpeech.SUCCESS
        } catch (e: Exception) {
            Log.e(TAG, "onLoadVoice failed for $voiceName", e)
            TextToSpeech.ERROR
        }
    }

    override fun onGetVoices(): List<Voice> {
        return try {
            voiceStore.listVoices().map { v ->
                Voice(
                    v.name,
                    v.locale,
                    qualityForVoiceName(v.name),
                    Voice.LATENCY_NORMAL,
                    false,
                    setOf(FEATURE_PIPER_VOICE)
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "onGetVoices failed", e)
            emptyList()
        }
    }

    override fun onGetDefaultVoiceNameFor(lang: String, country: String, variant: String): String {
        val normLang = LanguageNormalizer.normalizeLanguage(lang)
        val normCountry = LanguageNormalizer.normalizeCountry(country)
        return try {
            val voices = voiceStore.listVoices()
            voices.find {
                it.locale.language == normLang && normCountry.isNotEmpty() &&
                    it.locale.country.equals(normCountry, ignoreCase = true)
            }?.name
                ?: voices.find { it.locale.language == normLang }?.name
                ?: ""
        } catch (e: Exception) {
            Log.w(TAG, "onGetDefaultVoiceNameFor failed", e)
            ""
        }
    }

    override fun onGetFeaturesForLanguage(lang: String, country: String, variant: String): Set<String> {
        return emptySet()
    }

    companion object {
        private const val TAG = "PiperTtsService"
        private const val FEATURE_PIPER_VOICE = "piperVoice"

        internal fun qualityForVoiceName(name: String): Int {
            val lower = name.lowercase()
            return when {
                lower.contains("-x_low") -> Voice.QUALITY_VERY_LOW
                lower.contains("-low") -> Voice.QUALITY_LOW
                lower.contains("-high") -> Voice.QUALITY_VERY_HIGH
                else -> Voice.QUALITY_NORMAL
            }
        }
    }
}
