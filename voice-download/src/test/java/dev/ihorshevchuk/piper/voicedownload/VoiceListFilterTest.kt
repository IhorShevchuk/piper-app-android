package dev.ihorshevchuk.piper.voicedownload

import org.junit.Assert.assertEquals
import org.junit.Test

/** Failing-first tests for [VoiceListFilter]. */
class VoiceListFilterTest {

    private fun entry(
        key: String,
        displayName: String = key,
        languageEnglish: String = "English",
        languageNative: String = "English",
        countryEnglish: String = "United States",
        languageCode: String = "en_US",
        quality: String = "medium"
    ) = VoiceCatalogEntry(
        key = key,
        displayName = displayName,
        languageCode = languageCode,
        languageEnglish = languageEnglish,
        languageNative = languageNative,
        countryEnglish = countryEnglish,
        quality = quality,
        numSpeakers = 1,
        onnxUrl = "https://example.invalid/$key.onnx",
        configUrl = null,
        onnxSizeBytes = 10,
        onnxMd5 = null,
        configSizeBytes = 0,
        configMd5 = null
    )

    private val all = listOf(
        entry("en_US-lessac-medium", "Lessac (medium)"),
        entry(
            "uk_UA-ukrainian-medium", "Ukrainian (medium)",
            languageEnglish = "Ukrainian", languageNative = "Українська",
            countryEnglish = "Ukraine", languageCode = "uk_UA"
        ),
        entry("en_GB-alan-medium", "Alan (medium)", countryEnglish = "United Kingdom", languageCode = "en_GB")
    )
    private val installed = setOf("en_US-lessac-medium")

    @Test
    fun `ALL returns everything`() {
        val out = VoiceListFilter.filter(all, installed, VoiceFilterMode.ALL, "")
        assertEquals(3, out.size)
    }

    @Test
    fun `INSTALLED returns only installed keys`() {
        val out = VoiceListFilter.filter(all, installed, VoiceFilterMode.INSTALLED, "")
        assertEquals(listOf("en_US-lessac-medium"), out.map { it.key })
    }

    @Test
    fun `AVAILABLE returns only not-installed keys`() {
        val out = VoiceListFilter.filter(all, installed, VoiceFilterMode.AVAILABLE, "")
        assertEquals(
            listOf("en_GB-alan-medium", "uk_UA-ukrainian-medium"),
            out.map { it.key }.sorted()
        )
    }

    @Test
    fun `query matches name language country and key case-insensitively`() {
        assertEquals(
            listOf("uk_UA-ukrainian-medium"),
            VoiceListFilter.filter(all, installed, VoiceFilterMode.ALL, "україн").map { it.key }
        )
        assertEquals(
            2,
            VoiceListFilter.filter(all, installed, VoiceFilterMode.ALL, "united").size
        )
        assertEquals(
            listOf("en_GB-alan-medium"),
            VoiceListFilter.filter(all, installed, VoiceFilterMode.ALL, "ALAN").map { it.key }
        )
        assertEquals(
            listOf("en_US-lessac-medium"),
            VoiceListFilter.filter(all, installed, VoiceFilterMode.ALL, "lessac-medium").map { it.key }
        )
    }

    @Test
    fun `filter and query combine`() {
        val out = VoiceListFilter.filter(all, installed, VoiceFilterMode.AVAILABLE, "english")
        assertEquals(listOf("en_GB-alan-medium"), out.map { it.key })
    }

    @Test
    fun `blank query behaves like no query`() {
        val out = VoiceListFilter.filter(all, installed, VoiceFilterMode.ALL, "   ")
        assertEquals(3, out.size)
    }
}
