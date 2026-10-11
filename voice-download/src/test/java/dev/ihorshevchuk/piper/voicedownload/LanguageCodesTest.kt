package dev.ihorshevchuk.piper.voicedownload

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests for [LanguageCodes]. */
class LanguageCodesTest {

    @Test
    fun `canonical reduces community codes to family and region`() {
        assertEquals("en_GB", LanguageCodes.canonical("en-gb-x-rp"))
        assertEquals("en_GB", LanguageCodes.canonical("en_GB"))
        assertEquals("en_US", LanguageCodes.canonical("en-us"))
        assertEquals("uk_UA", LanguageCodes.canonical("UK_ua"))
    }

    @Test
    fun `canonical keeps bare family codes bare`() {
        assertEquals("uk", LanguageCodes.canonical("uk"))
        assertEquals("en", LanguageCodes.canonical("EN"))
        assertEquals("", LanguageCodes.canonical(""))
    }

    @Test
    fun `family extracts the language family`() {
        assertEquals("en", LanguageCodes.family("en-gb-x-rp"))
        assertEquals("ja", LanguageCodes.family("ja_JP"))
    }

    @Test
    fun `japanese detection is by family`() {
        assertTrue(LanguageCodes.isJapanese("ja_JP"))
        assertTrue(LanguageCodes.isJapanese("ja"))
        assertFalse(LanguageCodes.isJapanese("en_US"))
    }

    @Test
    fun `resolve prefers the canonical catalog code`() {
        val catalog = setOf("en_US", "en_GB", "uk_UA")
        assertEquals("en_GB", LanguageCodes.resolve("en-gb-x-rp", catalog))
        assertEquals("uk_UA", LanguageCodes.resolve("uk_UA", catalog))
    }

    @Test
    fun `resolve falls back to the unique same-family catalog language`() {
        val catalog = setOf("en_US", "uk_UA")
        // A config that ships just "uk" finds "uk_UA".
        assertEquals("uk_UA", LanguageCodes.resolve("uk", catalog))
    }

    @Test
    fun `resolve keeps canonical when family is ambiguous or unknown`() {
        val catalog = setOf("en_US", "en_GB")
        assertEquals("en", LanguageCodes.resolve("en", catalog))
        assertEquals("fr_FR", LanguageCodes.resolve("fr_FR", catalog))
    }

    @Test
    fun `installed japanese code still resolves to its catalog language`() {
        // Browsing hides ja, but resolution must keep working (the
        // catalog entry is kept for exactly this).
        val catalog = setOf("ja_JP", "en_US")
        assertEquals("ja_JP", LanguageCodes.resolve("ja_JP", catalog))
        assertEquals("ja_JP", LanguageCodes.resolve("ja", catalog))
    }
}
