package dev.ihorshevchuk.piper.tts

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import java.io.File

/**
 * Handles android.speech.tts.engine.CHECK_TTS_DATA.
 *
 * Android sends this to verify the engine has voice data; without it the
 * engine may not appear in system TTS settings on some versions.
 */
class CheckVoiceData : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Default result first: a missing result (or a crash below) makes
        // Android hide the engine from TTS settings, so PASS is the safe
        // default. Real data overwrites it below.
        val result = Intent()
        result.putStringArrayListExtra(
            TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES, ArrayList()
        )
        result.putStringArrayListExtra(
            TextToSpeech.Engine.EXTRA_UNAVAILABLE_VOICES, ArrayList()
        )
        setResult(TextToSpeech.Engine.CHECK_VOICE_DATA_PASS, result)

        val voicesDir = File(filesDir, "voices")
        val voices = try {
            FileVoiceStore(voicesDir).listVoices()
        } catch (_: Exception) {
            emptyList()
        }
        // Locale strings in Locale.toString() format, e.g. "en_US".
        val available = ArrayList(voices.map { v ->
            val c = v.locale.country
            if (c.isNotEmpty()) "${v.locale.language}_$c" else v.locale.language
        })
        result.putStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES, available)
        setResult(TextToSpeech.Engine.CHECK_VOICE_DATA_PASS, result)
        finish()
    }
}

/**
 * Handles android.speech.tts.engine.INSTALL_TTS_DATA.
 *
 * Redirects to the engine settings; the voice download manager UI
 * (which will fill the voices directory) is a later step.
 */
class InstallVoiceData : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, TtsSettingsActivity::class.java))
        finish()
    }
}

/**
 * Handles android.speech.tts.engine.GET_SAMPLE_TEXT.
 *
 * Returns the sample text behind the "Listen to an example" button in
 * system TTS settings.
 */
class GetSampleText : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val result = Intent().apply {
            putExtra(
                TextToSpeech.Engine.EXTRA_SAMPLE_TEXT,
                getString(R.string.tts_sample_text)
            )
        }
        setResult(RESULT_OK, result)
        finish()
    }
}
