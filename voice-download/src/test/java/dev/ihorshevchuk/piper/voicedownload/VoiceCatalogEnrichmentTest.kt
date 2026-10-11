package dev.ihorshevchuk.piper.voicedownload

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Catalog enrichment tests: per-voice licenses + training data (iOS
 * PR #62) and the INT8 `variants` field (iOS PR #76), including the
 * effective download URL/size/MD5 under both voice formats.
 *
 * Fixture shape verified against the live
 * IhorShevchuk/piper1-voices-fp16-quantized voices.json.
 */
class VoiceCatalogEnrichmentTest {

    private val source = VoiceCatalogSource(
        catalogUrl = "https://example.invalid/voices.json",
        filesBaseUrl = "https://example.invalid/resolve/main/"
    )

    private val enriched = """
        {
          "en_GB-alan-low": {
            "key": "en_GB-alan-low",
            "name": "Alan (low)",
            "language": {
              "code": "en_GB",
              "family": "en",
              "name_native": "English",
              "name_english": "English",
              "country_english": "United Kingdom"
            },
            "quality": "low",
            "num_speakers": 1,
            "license": "CC-BY-SA-4.0",
            "model_license": "CC-BY-SA-4.0",
            "training_data": {
              "url": "https://example.invalid/dataset",
              "license": "CC-BY-SA-4.0",
              "license_raw": "Creative Commons Attribution Share Alike 4.0"
            },
            "files": {
              "en/en_GB/alan/low/en_GB-alan-low.onnx": {
                "size_bytes": 30000000,
                "md5_digest": "aaaa"
              },
              "en/en_GB/alan/low/en_GB-alan-low.onnx.json": {
                "size_bytes": 2000,
                "md5_digest": "bbbb"
              }
            },
            "variants": {
              "int8": {
                "model": "en/en_GB/alan/low/en_GB-alan-low.int8.onnx",
                "size_bytes": 18000000
              }
            }
          },
          "en_US-plain-medium": {
            "key": "en_US-plain-medium",
            "name": "Plain (medium)",
            "language": {"code": "en_US", "name_english": "English"},
            "quality": "medium",
            "num_speakers": 1,
            "files": {
              "en/en_US/plain/medium/en_US-plain-medium.onnx": {
                "size_bytes": 1000,
                "md5_digest": "cccc"
              },
              "en/en_US/plain/medium/en_US-plain-medium.onnx.json": {
                "size_bytes": 100,
                "md5_digest": "dddd"
              }
            }
          }
        }
    """.trimIndent()

    @Test
    fun `parses license model license and training data`() {
        val alan = VoiceCatalogParser.parse(enriched, source)
            .first { it.key == "en_GB-alan-low" }

        assertEquals("CC-BY-SA-4.0", alan.license)
        assertEquals("CC-BY-SA-4.0", alan.modelLicense)
        assertEquals("https://example.invalid/dataset", alan.trainingData?.url)
        assertEquals("CC-BY-SA-4.0", alan.trainingData?.license)
        assertEquals(
            "Creative Commons Attribution Share Alike 4.0",
            alan.trainingData?.licenseRaw
        )
    }

    @Test
    fun `parses the int8 variant`() {
        val alan = VoiceCatalogParser.parse(enriched, source)
            .first { it.key == "en_GB-alan-low" }

        assertEquals(
            "en/en_GB/alan/low/en_GB-alan-low.int8.onnx",
            alan.int8Variant?.modelPath
        )
        assertEquals(
            "https://example.invalid/resolve/main/en/en_GB/alan/low/en_GB-alan-low.int8.onnx",
            alan.int8Variant?.modelUrl
        )
        assertEquals(18000000L, alan.int8Variant?.sizeBytes)
    }

    @Test
    fun `legacy entry without enrichment parses with nulls`() {
        val plain = VoiceCatalogParser.parse(enriched, source)
            .first { it.key == "en_US-plain-medium" }

        assertNull(plain.license)
        assertNull(plain.modelLicense)
        assertNull(plain.trainingData)
        assertNull(plain.int8Variant)
        assertEquals(1100L, plain.totalSizeBytes)
    }

    @Test
    fun `standard format keeps exact fp16 behaviour`() {
        val alan = VoiceCatalogParser.parse(enriched, source)
            .first { it.key == "en_GB-alan-low" }

        assertEquals(alan.onnxUrl, alan.effectiveOnnxUrl(VoiceFormat.STANDARD))
        assertEquals(30000000L, alan.effectiveOnnxSizeBytes(VoiceFormat.STANDARD))
        assertEquals("aaaa", alan.effectiveOnnxMd5(VoiceFormat.STANDARD))
        assertEquals(30002000L, alan.effectiveTotalSizeBytes(VoiceFormat.STANDARD))
    }

    @Test
    fun `compact format selects the int8 variant and skips md5`() {
        val alan = VoiceCatalogParser.parse(enriched, source)
            .first { it.key == "en_GB-alan-low" }

        assertTrue(alan.isCompactSelected(VoiceFormat.COMPACT))
        assertEquals(
            "https://example.invalid/resolve/main/en/en_GB/alan/low/en_GB-alan-low.int8.onnx",
            alan.effectiveOnnxUrl(VoiceFormat.COMPACT)
        )
        assertEquals(18000000L, alan.effectiveOnnxSizeBytes(VoiceFormat.COMPACT))
        // Config size is shared: total = variant model + config.
        assertEquals(18002000L, alan.effectiveTotalSizeBytes(VoiceFormat.COMPACT))
        // Variants carry no digest in the catalog.
        assertNull(alan.effectiveOnnxMd5(VoiceFormat.COMPACT))
    }

    @Test
    fun `compact format without a variant falls back to fp16`() {
        val plain = VoiceCatalogParser.parse(enriched, source)
            .first { it.key == "en_US-plain-medium" }

        assertEquals(plain.onnxUrl, plain.effectiveOnnxUrl(VoiceFormat.COMPACT))
        assertEquals(1000L, plain.effectiveOnnxSizeBytes(VoiceFormat.COMPACT))
        assertEquals("cccc", plain.effectiveOnnxMd5(VoiceFormat.COMPACT))
        assertEquals(1100L, plain.effectiveTotalSizeBytes(VoiceFormat.COMPACT))
    }

    @Test
    fun `partial training data object does not break parsing`() {
        val json = """
            {"aa_BB-v-low": {
              "key": "aa_BB-v-low", "name": "V",
              "language": {"code": "aa_BB", "name_english": "Aalang"},
              "quality": "low", "num_speakers": 1,
              "training_data": {"license": "MIT"},
              "files": {"p/aa_BB-v-low.onnx": {"size_bytes": 10}}
            }}
        """.trimIndent()

        val entries = VoiceCatalogParser.parse(json, source)
        assertEquals(1, entries.size)
        assertNull(entries[0].trainingData?.url)
        assertEquals("MIT", entries[0].trainingData?.license)
        assertNull(entries[0].trainingData?.licenseRaw)
    }
}
