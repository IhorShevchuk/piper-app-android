package dev.ihorshevchuk.piper.app.reader

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.util.Log
import dev.ihorshevchuk.piper.engine.PiperEngine
import dev.ihorshevchuk.piper.utils.MarkerRange
import dev.ihorshevchuk.piper.utils.SpeechMarker
import dev.ihorshevchuk.piper.utils.SpeedCurve
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Streaming reader playback with word highlighting.
 *
 * Drives [PiperEngine.synthesize] directly (rather than PiperPlayer) so the
 * word [SpeechMarker]s are available: each sentence's markers carry cumulative
 * float32 byte offsets into the utterance, and [WordTracker] maps the
 * AudioTrack playback head to the word being spoken.
 *
 * Highlight callbacks fire on the main thread. Audio setup mirrors
 * PiperPlayer (16-bit mono stream, speech usage).
 */
class ReaderPlayer {

    interface Listener {
        /** Called on the main thread when the highlighted word changes (null clears). */
        fun onWord(range: MarkerRange?)
        /** Called on the main thread when playback reaches the end. */
        fun onFinished()
        /** Called on the main thread when synthesis/playback fails. */
        fun onError(e: Exception)
    }

    private val stopped = AtomicBoolean(false)
    private val worker = AtomicReference<java.util.concurrent.ExecutorService?>()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var tracker = WordTracker(emptyList())
    private val markers = mutableListOf<SpeechMarker>()

    @Volatile
    private var track: AudioTrack? = null
    @Volatile
    private var synthDone = false
    @Volatile
    private var totalFramesWritten = 0L

    /**
     * Starts reading [text] aloud.
     *
     * @param speedPercent Android-style speech rate (100 = normal), clamped to
     * 25..220 through [SpeedCurve.lengthScaleForAndroidSpeechRate] so the
     * PT-BR sibilant ceiling (2.2x) always holds.
     */
    fun play(
        engine: PiperEngine,
        text: String,
        speedPercent: Int,
        speakerId: Int,
        listener: Listener
    ) {
        stop()
        stopped.set(false)
        markers.clear()
        tracker = WordTracker(emptyList())
        synthDone = false
        totalFramesWritten = 0L

        val exec = Executors.newSingleThreadExecutor { r -> Thread(r, "piper-reader") }
        worker.set(exec)
        exec.execute {
            var highlightTask: Runnable? = null
            try {
                val options = engine.defaultSynthesizeOptions().copy(
                    lengthScale = SpeedCurve.lengthScaleForAndroidSpeechRate(speedPercent),
                    speakerId = speakerId
                )
                highlightTask = startHighlightLoop(listener)
                engine.synthesize(
                    text,
                    options,
                    onSamples = { samples ->
                        if (stopped.get()) return@synthesize
                        writeSamples(engine, samples)
                    },
                    onMarkers = { batch ->
                        synchronized(markers) {
                            markers.addAll(batch)
                            tracker = WordTracker(markers.toList())
                        }
                    }
                )
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (e: Exception) {
                if (!stopped.get()) {
                    Log.e(TAG, "reader playback failed", e)
                    mainHandler.post { listener.onError(e) }
                }
            } finally {
                synthDone = true
                // The highlight loop keeps polling until the buffered audio
                // drains, then finishes itself. On stop() it is removed.
                if (stopped.get()) {
                    highlightTask?.let { mainHandler.removeCallbacks(it) }
                    releaseTrack()
                }
            }
        }
    }

    fun stop() {
        stopped.set(true)
        worker.getAndSet(null)?.shutdownNow()
        mainHandler.post {
            releaseTrack()
        }
    }

    private fun writeSamples(engine: PiperEngine, samples: FloatArray) {
        val sr = engine.currentSampleRate.get()
        var t = track
        if (t == null && sr > 0) {
            t = createTrack(sr)
            track = t
            t.play()
        }
        if (t != null) {
            val pcm = ShortArray(samples.size) { i ->
                (samples[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
            }
            t.write(pcm, 0, pcm.size)
            totalFramesWritten += pcm.size
        }
    }

    /**
     * Polls the playback head on the main thread and reports word changes.
     * Finishes (stops the track, calls onFinished) once synthesis is done and
     * the buffered audio has drained.
     */
    private fun startHighlightLoop(listener: Listener): Runnable {
        var lastRange: MarkerRange? = null
        var announced = false
        val task = object : Runnable {
            override fun run() {
                if (stopped.get()) return
                val t = track
                try {
                    val headFrames = t?.playbackHeadPosition?.toLong() ?: 0L
                    val playedBytes = headFrames * BYTES_PER_FLOAT32_SAMPLE
                    val range = tracker.wordRangeAt(playedBytes)
                    if (range != lastRange) {
                        lastRange = range
                        listener.onWord(range)
                    }
                    val drained = synthDone && headFrames >= totalFramesWritten && totalFramesWritten > 0
                    val nothingToPlay = synthDone && totalFramesWritten == 0L
                    if ((drained || nothingToPlay) && !announced) {
                        announced = true
                        releaseTrack()
                        listener.onFinished()
                        return
                    }
                } catch (_: IllegalStateException) {
                    // Track released mid-poll; stop() owns teardown.
                    return
                }
                mainHandler.postDelayed(this, HIGHLIGHT_POLL_MS)
            }
        }
        mainHandler.post(task)
        return task
    }

    private fun releaseTrack() {
        try {
            track?.stop()
        } catch (_: Exception) {
        }
        try {
            track?.release()
        } catch (_: Exception) {
        }
        track = null
    }

    private fun createTrack(sampleRate: Int): AudioTrack {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val format = AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        return AudioTrack(
            attributes,
            format,
            minBuffer * 4,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )
    }

    companion object {
        private const val TAG = "ReaderPlayer"
        private const val BYTES_PER_FLOAT32_SAMPLE = 4L
        private const val HIGHLIGHT_POLL_MS = 100L
    }
}
