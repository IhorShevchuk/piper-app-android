package dev.ihorshevchuk.piper.tts

import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageNormalizerTest {

    @Test
    fun twoLetterPassesThrough() {
        assertEquals("en", LanguageNormalizer.normalizeLanguage("en"))
        assertEquals("pt", LanguageNormalizer.normalizeLanguage("pt"))
        assertEquals("uk", LanguageNormalizer.normalizeLanguage("uk"))
    }

    @Test
    fun threeLetterIsoMapsToTwoLetter() {
        assertEquals("en", LanguageNormalizer.normalizeLanguage("eng"))
        assertEquals("pt", LanguageNormalizer.normalizeLanguage("por"))
        assertEquals("uk", LanguageNormalizer.normalizeLanguage("ukr"))
        assertEquals("de", LanguageNormalizer.normalizeLanguage("deu"))
    }

    @Test
    fun bibliographicThreeLetterCodesMap() {
        // Android may send ISO 639-2/B; Locale reports 639-2/T.
        assertEquals("zh", LanguageNormalizer.normalizeLanguage("chi"))
        assertEquals("fr", LanguageNormalizer.normalizeLanguage("fre"))
        assertEquals("de", LanguageNormalizer.normalizeLanguage("ger"))
    }

    @Test
    fun dashedAndUnderscoredTagsNormalize() {
        assertEquals("en", LanguageNormalizer.normalizeLanguage("en-US"))
        assertEquals("en", LanguageNormalizer.normalizeLanguage("en_US"))
        assertEquals("pt", LanguageNormalizer.normalizeLanguage("pt-BR"))
    }

    @Test
    fun caseInsensitive() {
        assertEquals("en", LanguageNormalizer.normalizeLanguage("EN"))
        assertEquals("en", LanguageNormalizer.normalizeLanguage("ENG"))
    }

    @Test
    fun unknownPassesThroughLowercased() {
        assertEquals("xx", LanguageNormalizer.normalizeLanguage("xx"))
    }

    @Test
    fun countryTwoLetterPassesThroughUppercased() {
        assertEquals("US", LanguageNormalizer.normalizeCountry("US"))
        assertEquals("GB", LanguageNormalizer.normalizeCountry("gb"))
    }

    @Test
    fun countryThreeLetterMapsToTwoLetter() {
        assertEquals("GB", LanguageNormalizer.normalizeCountry("GBR"))
        assertEquals("US", LanguageNormalizer.normalizeCountry("USA"))
        assertEquals("UA", LanguageNormalizer.normalizeCountry("UKR"))
    }

    @Test
    fun emptyCountryStaysEmpty() {
        assertEquals("", LanguageNormalizer.normalizeCountry(""))
    }
}
