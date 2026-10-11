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

    // ---- Unified-list parity (iOS #66/#68): flat search, ja filter ----

    private val withJapanese = all + entry(
        "ja_JP-hi_fi_captain-medium", "Hi Fi Captain (medium)",
        languageEnglish = "Japanese", languageNative = "日本語",
        countryEnglish = "Japan", languageCode = "ja_JP"
    )

    @Test
    fun `search is flat across languages and shows language via the entry`() {
        val out = VoiceListFilter.search(withJapanese, "medium")
        // All non-Japanese voices match "(medium)" in the name; the
        // Japanese one is excluded even though it matches too.
        assertEquals(3, out.size)
        assertEquals(
            setOf("English", "Ukrainian"),
            out.map { it.languageEnglish }.toSet()
        )
    }

    @Test
    fun `search excludes japanese but catalog resolution still finds it`() {
        assertEquals(0, VoiceListFilter.search(withJapanese, "japanese").size)
        assertEquals(0, VoiceListFilter.search(withJapanese, "hi fi").size)
        // The entry itself is not deleted: its language group resolves.
        val codes = withJapanese.map { it.canonicalLanguageCode }.toSet()
        assertEquals("ja_JP", LanguageCodes.resolve("ja_JP", codes))
        assertEquals(
            listOf("ja_JP-hi_fi_captain-medium"),
            VoiceListFilter.voicesForLanguage(withJapanese, "ja_JP").map { it.key }
        )
    }

    @Test
    fun `browsable languages exclude japanese and use canonical codes`() {
        val codes = VoiceListFilter.browsableLanguageCodes(withJapanese)
        assertEquals(listOf("en_GB", "en_US", "uk_UA"), codes.sorted())
        assertEquals(3, codes.size)
    }

    @Test
    fun `search is diacritic insensitive in both directions`() {
        val diacritic = listOf(entry(
            "pl_PL-zary-medium", "Żary (medium)",
            languageEnglish = "Polish", languageNative = "Polski",
            countryEnglish = "Poland", languageCode = "pl_PL"
        ))
        assertEquals(1, VoiceListFilter.search(diacritic, "zary").size)
        assertEquals(1, VoiceListFilter.search(diacritic, "ŻARY").size)
        assertEquals(1, VoiceListFilter.search(diacritic, "polski").size)
    }

    @Test
    fun `search sorts by language then name`() {
        val out = VoiceListFilter.search(all, "united")
        assertEquals(
            listOf("en_GB-alan-medium", "en_US-lessac-medium"),
            out.map { it.key }
        )
    }

    @Test
    fun `voices for language returns that language only in catalog order`() {
        val out = VoiceListFilter.voicesForLanguage(all, "en_GB")
        assertEquals(listOf("en_GB-alan-medium"), out.map { it.key })
    }
}
