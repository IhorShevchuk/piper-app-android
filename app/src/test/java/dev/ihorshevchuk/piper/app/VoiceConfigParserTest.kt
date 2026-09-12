package dev.ihorshevchuk.piper.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceConfigParserTest {

    private val fullConfig = """
        {
          "dataset": "lessac",
          "audio": {"sample_rate": 22050, "quality": "medium"},
          "espeak": {"voice": "en-us"},
          "language": {
            "code": "en_US",
            "family": "en",
            "region": "US",
            "name_native": "English",
            "name_english": "English",
            "country_english": "United States"
          },
          "piper_version": "1.0.0",
          "speaker_id_map": {"lessac": 0},
          "num_speakers": 1
        }
    """.trimIndent()

    @Test
    fun `parses full config`() {
        val config = VoiceConfigParser.parseJson(fullConfig)
        assertNotNull(config)
        config!!
        assertEquals("lessac", config.name)
        assertEquals("en_US", config.languageCode)
        assertEquals("English", config.languageName)
        assertEquals("United States", config.countryName)
        assertEquals("1.0.0", config.piperVersion)
        assertEquals("medium", config.quality)
        assertEquals(22050, config.sampleRate)
        assertEquals(mapOf("lessac" to 0), config.speakers)
        assertEquals(1, config.numSpeakers)
        // Single speaker with a map: the picker still offers it, like iOS.
        assertEquals(mapOf("lessac" to 0), config.pickerSpeakers())
    }

    @Test
    fun `multi-speaker without map generates picker entries`() {
        val config = VoiceConfigParser.parseJson("""
            {
              "dataset": "vctk",
              "audio": {"sample_rate": 22050, "quality": "medium"},
              "language": {"code": "en_GB", "family": "en", "region": "GB"},
              "piper_version": "1.0.0",
              "num_speakers": 3
            }
        """.trimIndent())
        assertNotNull(config)
        config!!
        assertTrue(config.speakers.isEmpty())
        assertEquals(
            mapOf("Speaker 0" to 0, "Speaker 1" to 1, "Speaker 2" to 2),
            config.pickerSpeakers()
        )
    }

    @Test
    fun `missing dataset falls back to Unknown`() {
        val config = VoiceConfigParser.parseJson("""
            {
              "audio": {"sample_rate": 16000, "quality": "low"},
              "language": {"code": "uk_UA", "family": "uk", "region": "UA"},
              "piper_version": "1.0.0"
            }
        """.trimIndent())
        assertNotNull(config)
        assertEquals("Unknown", config!!.name)
    }

    @Test
    fun `malformed json returns null`() {
        assertNull(VoiceConfigParser.parseJson("{ not json"))
    }

    @Test
    fun `missing language section returns null`() {
        assertNull(VoiceConfigParser.parseJson("""{"dataset": "x"}"""))
    }

    @Test
    fun `missing audio section returns null`() {
        assertNull(VoiceConfigParser.parseJson(
            """{"dataset": "x", "language": {"code": "en_US"}}"""
        ))
    }

    @Test
    fun `missing file returns null`() {
        assertNull(VoiceConfigParser.parse(java.io.File("/nonexistent/voice.onnx.json")))
    }
}
