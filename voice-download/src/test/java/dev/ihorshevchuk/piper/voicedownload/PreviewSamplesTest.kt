package dev.ihorshevchuk.piper.voicedownload

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * The preview line must come from the same Samples.json the iOS app
 * uses, looked up by full locale code exactly like iOS does.
 */
class PreviewSamplesTest {

    @Test
    fun `exact locale code wins`() {
        val samples = mapOf("en_US" to "Hello.", "en_GB" to "Howdy.")
        assertEquals(
            "Hello.",
            PreviewSamples.sampleTextFor(samples, Locale("en", "US"), "default")
        )
    }

    @Test
    fun `missing code falls back to default`() {
        assertEquals(
            "default",
            PreviewSamples.sampleTextFor(emptyMap(), Locale("en", "US"), "default")
        )
    }

    @Test
    fun `no language-family fallback, mirroring iOS`() {
        val samples = mapOf("en_US" to "Hello.")
        assertEquals(
            "default",
            PreviewSamples.sampleTextFor(samples, Locale("en", "AU"), "default")
        )
    }

    @Test
    fun `parses samples json`() {
        val parsed = PreviewSamples.parseSamplesJson(
            """{"en_US":"Hello.","uk_UA":"Привіт."}"""
        )
        assertEquals(mapOf("en_US" to "Hello.", "uk_UA" to "Привіт."), parsed)
    }

    @Test
    fun `malformed json yields empty map`() {
        assertEquals(emptyMap<String, String>(), PreviewSamples.parseSamplesJson("not json"))
    }

    @Test
    fun `default source serves the quantized catalog`() {
        assertTrue(
            VoiceCatalogSource.DEFAULT.catalogUrl
                .contains("IhorShevchuk/piper1-voices-fp16-quantized")
        )
        assertTrue(
            VoiceCatalogSource.DEFAULT.filesBaseUrl
                .contains("IhorShevchuk/piper1-voices-fp16-quantized")
        )
    }
}
