package dev.ihorshevchuk.piper.voicedownload

/**
 * Canonical language codes (`canonical` / `resolve`).
 *
 * Model configs do not always use the catalog's language code: community
 * configs ship codes like "en-gb-x-rp" where the catalog says "en_GB".
 * Reducing a code to family + region lets installed voices group under
 * the same key the catalog uses.
 *
 * Pure JVM - unit-testable, no Android dependencies.
 */
object LanguageCodes {

    /**
     * "en-gb-x-rp" -> "en_GB"; "en_US" -> "en_US"; "uk" -> "uk".
     * Extension parts after the region (e.g. the `x-rp` above) are dropped.
     */
    fun canonical(code: String): String {
        val components = code.split('_', '-').filter { it.isNotEmpty() }
        val family = components.firstOrNull()?.lowercase() ?: return code
        if (components.size < 2) return family
        return family + "_" + components[1].uppercase()
    }

    /** Language family of a (raw or canonical) code: "en_GB" -> "en". */
    fun family(code: String): String =
        canonical(code).substringBefore('_').lowercase()

    /** Japanese is hidden from catalog browsing/search. */
    fun isJapanese(code: String): Boolean = family(code) == "ja"

    /**
     * Maps a language group to the catalog key its voices live under:
     * the canonical code when the catalog has it, else the catalog's
     * only language with the same family (a config that ships just
     * "uk" still finds "uk_UA"). Falls back to the canonical code.
     */
    fun resolve(code: String, catalogCodes: Collection<String>): String {
        val canonical = canonical(code)
        if (canonical in catalogCodes) return canonical
        val fam = family(canonical)
        val matches = catalogCodes.filter { family(it) == fam }
        return if (matches.size == 1) matches[0] else canonical
    }
}
