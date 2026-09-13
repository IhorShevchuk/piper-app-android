package dev.ihorshevchuk.piper.tts

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * The TTS framework must be told the voice's real sample rate in
 * callback.start(): claiming 22050 for a 16000 Hz voice plays the audio
 * back at the wrong speed. The rate comes from the voice config JSON
 * (audio.sample_rate), read once when the voice is loaded.
 */
class VoiceSampleRateTest {

    @Test
    fun `parses audio sample_rate`() {
        val config = File.createTempFile("voice", ".json").apply {
            writeText("""{"audio": {"sample_rate": 16000}}""")
        }
        try {
            assertEquals(16000, VoiceSampleRate.fromConfig(config))
        } finally {
            config.delete()
        }
    }

    @Test
    fun `falls back to 22050 when config is missing`() {
        assertEquals(22050, VoiceSampleRate.fromConfig(null))
        val gone = File.createTempFile("voice", ".json").apply { delete() }
        assertEquals(22050, VoiceSampleRate.fromConfig(gone))
    }

    @Test
    fun `falls back to 22050 on malformed config`() {
        val config = File.createTempFile("voice", ".json").apply {
            writeText("""{"audio": {"sample_rate": "fast"}}""")
        }
        try {
            assertEquals(22050, VoiceSampleRate.fromConfig(config))
        } finally {
            config.delete()
        }
    }

    @Test
    fun `falls back to 22050 when audio section is absent`() {
        val config = File.createTempFile("voice", ".json").apply {
            writeText("""{"language": {"code": "en_US"}}""")
        }
        try {
            assertEquals(22050, VoiceSampleRate.fromConfig(config))
        } finally {
            config.delete()
        }
    }

    @Test
    fun `falls back to 22050 on non-positive sample rate`() {
        val config = File.createTempFile("voice", ".json").apply {
            writeText("""{"audio": {"sample_rate": -1}}""")
        }
        try {
            assertEquals(22050, VoiceSampleRate.fromConfig(config))
        } finally {
            config.delete()
        }
    }
}
