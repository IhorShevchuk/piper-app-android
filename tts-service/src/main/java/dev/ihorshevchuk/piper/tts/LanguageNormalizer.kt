package dev.ihorshevchuk.piper.tts

import java.util.Locale

/**
 * Normalizes language/country codes the Android TTS framework hands us.
 *
 * The framework may pass ISO 639-2/T 3-letter codes ("eng"), ISO 639-2/B
 * bibliographic codes ("chi"), or BCP-47-ish tags ("en-US", "en_US"), while
 * Piper voice names use 2-letter ISO 639-1 ("en"). Same story for countries:
 * 3-letter ISO 3166-1 ("GBR") vs the 2-letter codes in voice names ("GB").
 *
 * Pure JVM - unit-testable, no Android dependencies.
 */
object LanguageNormalizer {

    private val iso3ToIso1: Map<String, String> by lazy {
        val map = mutableMapOf<String, String>()
        for (loc in Locale.getAvailableLocales()) {
            try {
                val iso3 = loc.isO3Language
                val iso1 = loc.language
                if (iso3.length == 3 && iso1.length == 2) {
                    map[iso3.lowercase()] = iso1
                }
            } catch (_: Exception) {
                // Some locales throw MissingResourceException for getISO3Language.
            }
        }
        // ISO 639-2/B bibliographic codes Android may send; Locale reports /T.
        map.putAll(
            mapOf(
                "alb" to "sq", "arm" to "hy", "baq" to "eu", "bur" to "my",
                "chi" to "zh", "cze" to "cs", "dut" to "nl", "fre" to "fr",
                "geo" to "ka", "ger" to "de", "gre" to "el", "ice" to "is",
                "mac" to "mk", "mao" to "mi", "may" to "ms", "per" to "fa",
                "rum" to "ro", "slo" to "sk", "tib" to "bo", "wel" to "cy"
            )
        )
        map
    }

    private val iso3CountryToIso2: Map<String, String> by lazy {
        val map = mutableMapOf<String, String>()
        for (loc in Locale.getAvailableLocales()) {
            try {
                val iso3 = loc.isO3Country
                val iso2 = loc.country
                if (iso3.length == 3 && iso2.length == 2) {
                    map[iso3.uppercase()] = iso2
                }
            } catch (_: Exception) {
                // Some locales throw MissingResourceException for getISO3Country.
            }
        }
        map
    }

    /**
     * Normalizes a language tag to 2-letter ISO 639-1 ("eng"/"en-US" -> "en").
     * Unknown codes pass through lowercased.
     */
    fun normalizeLanguage(lang: String): String {
        val lower = lang.lowercase().replace('-', '_')
        if (lower.length == 2) return lower
        if (lower.length == 3) return iso3ToIso1[lower] ?: lower
        val first = lower.substringBefore('_')
        if (first.length == 2) return first
        if (first.length == 3) return iso3ToIso1[first] ?: first
        return lower
    }

    /**
     * Normalizes a country code to 2-letter uppercase ISO 3166-1
     * ("GBR"/"gb" -> "GB"). Empty stays empty.
     */
    fun normalizeCountry(country: String): String {
        if (country.isEmpty()) return ""
        val upper = country.uppercase()
        if (upper.length == 2) return upper
        if (upper.length == 3) return iso3CountryToIso2[upper] ?: upper
        return upper
    }
}
