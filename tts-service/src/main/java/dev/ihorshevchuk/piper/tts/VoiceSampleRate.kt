package dev.ihorshevchuk.piper.tts

import org.json.JSONObject
import java.io.File

/**
 * Reads a voice's native sample rate from its `.onnx.json` config
 * (`audio.sample_rate`).
 *
 * The TTS framework's `callback.start()` must be told the voice's real
 * sample rate: claiming 22050 for a 16000 Hz voice plays the audio back
 * at the wrong speed. Read once when the voice is loaded, not per chunk.
 *
 * Falls back to [DEFAULT] (the Piper default) when the config is missing
 * or malformed. Pure JVM - unit-testable. Logging is the caller's job:
 * android.util.Log throws in JVM unit tests.
 */
object VoiceSampleRate {

    const val DEFAULT = 22050

    fun fromConfig(configFile: File?): Int {
        if (configFile == null || !configFile.isFile) return DEFAULT
        return try {
            JSONObject(configFile.readText())
                .optJSONObject("audio")
                ?.optInt("sample_rate", -1)
                ?.takeIf { it > 0 }
                ?: DEFAULT
        } catch (_: Exception) {
            DEFAULT
        }
    }
}
