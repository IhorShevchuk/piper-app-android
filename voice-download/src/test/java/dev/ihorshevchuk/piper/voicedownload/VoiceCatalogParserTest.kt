package dev.ihorshevchuk.piper.voicedownload

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Failing-first tests for [VoiceCatalogParser].
 *
 * Fixture mirrors the real HuggingFace voices.json schema (verified
 * 2026-09-12 against rhasspy/piper-voices).
 */
class VoiceCatalogParserTest {

    private val fixture = """
        {
          "en_US-lessac-medium": {
            "key": "en_US-lessac-medium",
            "name": "Lessac (medium)",
            "language": {
              "code": "en_US",
              "family": "en",
              "region": "US",
              "name_native": "English",
              "name_english": "English",
              "country_english": "United States"
            },
            "quality": "medium",
            "num_speakers": 1,
            "files": {
              "en/en_US/lessac/medium/en_US-lessac-medium.onnx": {
                "size_bytes": 63201294,
                "md5_digest": "2fc642b535197b6305c7c8f92dc8b24f"
              },
              "en/en_US/lessac/medium/en_US-lessac-medium.onnx.json": {
                "size_bytes": 4885,
                "md5_digest": "c1f2b7bddefe113f3255ff9ef234cfd3"
              },
              "en/en_US/lessac/medium/MODEL_CARD": {
                "size_bytes": 351,
                "md5_digest": "42f2dd4a98149e12fc70b301d9579dfd"
              }
            }
          },
          "uk_UA-ukrainian-medium": {
            "key": "uk_UA-ukrainian-medium",
            "name": "Ukrainian (medium)",
            "language": {
              "code": "uk_UA",
              "family": "uk",
              "region": "UA",
              "name_native": "Українська",
              "name_english": "Ukrainian",
              "country_english": "Ukraine"
            },
            "quality": "medium",
            "num_speakers": 1,
            "files": {
              "uk/uk_UA/ukrainian/medium/uk_UA-ukrainian-medium.onnx": {
                "size_bytes": 60332210,
                "md5_digest": "aaaabbbbccccddddeeeeffff00001111"
              },
              "uk/uk_UA/ukrainian/medium/uk_UA-ukrainian-medium.onnx.json": {
                "size_bytes": 3210,
                "md5_digest": "11112222333344445555666677778888"
              }
            }
          },
          "xx_YY-broken-no-files": {
            "key": "xx_YY-broken-no-files",
            "name": "Broken",
            "language": {"code": "xx_YY", "name_english": "Brokenish"},
            "quality": "low",
            "num_speakers": 1
          },
          "zz_ZZ-only-card": {
            "key": "zz_ZZ-only-card",
            "name": "Card only",
            "language": {"code": "zz_ZZ", "name_english": "Cardish"},
            "quality": "low",
            "num_speakers": 1,
            "files": {
              "zz/zz_ZZ/card/low/MODEL_CARD": {"size_bytes": 10, "md5_digest": "abc"}
            }
          }
        }
    """.trimIndent()

    private val source = VoiceCatalogSource(
        catalogUrl = "https://example.invalid/voices.json",
        filesBaseUrl = "https://example.invalid/resolve/main/"
    )

    @Test
    fun `parses full entries and skips ones without onnx files`() {
        val entries = VoiceCatalogParser.parse(fixture, source)

        // Two broken entries (no files / no .onnx) are skipped.
        assertEquals(2, entries.size)
    }

    @Test
    fun `maps all fields including md5 checksums`() {
        val entries = VoiceCatalogParser.parse(fixture, source)
        val lessac = entries.first { it.key == "en_US-lessac-medium" }

        assertEquals("Lessac (medium)", lessac.displayName)
        assertEquals("en_US", lessac.languageCode)
        assertEquals("English", lessac.languageEnglish)
        assertEquals("English", lessac.languageNative)
        assertEquals("United States", lessac.countryEnglish)
        assertEquals("medium", lessac.quality)
        assertEquals(1, lessac.numSpeakers)
        assertEquals(63201294L, lessac.onnxSizeBytes)
        assertEquals("2fc642b535197b6305c7c8f92dc8b24f", lessac.onnxMd5)
        assertEquals(4885L, lessac.configSizeBytes)
        assertEquals("c1f2b7bddefe113f3255ff9ef234cfd3", lessac.configMd5)
        assertEquals(
            "https://example.invalid/resolve/main/en/en_US/lessac/medium/en_US-lessac-medium.onnx",
            lessac.onnxUrl
        )
        assertEquals(
            "https://example.invalid/resolve/main/en/en_US/lessac/medium/en_US-lessac-medium.onnx.json",
            lessac.configUrl
        )
        assertEquals("en_US-lessac-medium.onnx", lessac.modelFileName)
        assertEquals("en_US-lessac-medium.onnx.json", lessac.configFileName)
        assertEquals(63201294L + 4885L, lessac.totalSizeBytes)
    }

    @Test
    fun `sorts by language then display name`() {
        val entries = VoiceCatalogParser.parse(fixture, source)

        assertEquals("en_US-lessac-medium", entries[0].key)
        assertEquals("uk_UA-ukrainian-medium", entries[1].key)
    }

    @Test
    fun `entry missing config json is still parsed with null config`() {
        val json = """
            {"aa_BB-voice-x_low": {
              "key": "aa_BB-voice-x_low",
              "name": "Voice",
              "language": {"code": "aa_BB", "name_english": "Aalang"},
              "quality": "x_low",
              "num_speakers": 2,
              "files": {"p/aa_BB-voice-x_low.onnx": {"size_bytes": 100}}
            }}
        """.trimIndent()

        val entries = VoiceCatalogParser.parse(json, source)

        assertEquals(1, entries.size)
        assertNull(entries[0].configUrl)
        assertEquals(2, entries[0].numSpeakers)
    }

    @Test
    fun `empty and malformed json yield empty list not crash`() {
        assertTrue(VoiceCatalogParser.parse("{}", source).isEmpty())
        assertTrue(VoiceCatalogParser.parse("not json at all", source).isEmpty())
    }
}
