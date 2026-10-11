package dev.ihorshevchuk.piper.voicedownload

import java.text.Normalizer

/** Voice-list filter tabs (legacy browse mode of the voice list). */
enum class VoiceFilterMode { ALL, INSTALLED, AVAILABLE }

/**
 * Pure filter + search over catalog entries.
 *
 * Matching is case- and diacritic-insensitive, over the display name, both
 * language names, country, language code/family and key.
 *
 * Japanese voices are excluded from browsing and search (the `ja`
 * family is hidden there); they are NOT removed from the catalog, so an
 * installed Japanese voice still resolves to its language group.
 */
object VoiceListFilter {

    fun filter(
        entries: List<VoiceCatalogEntry>,
        installedKeys: Set<String>,
        mode: VoiceFilterMode,
        query: String
    ): List<VoiceCatalogEntry> {
        val q = fold(query.trim())
        return entries.filter { entry ->
            val installed = entry.key in installedKeys
            val modeOk = when (mode) {
                VoiceFilterMode.ALL -> true
                VoiceFilterMode.INSTALLED -> installed
                VoiceFilterMode.AVAILABLE -> !installed
            }
            modeOk && (q.isEmpty() || matches(entry, q))
        }
    }

    /**
     * Flat search across all languages for the unified main screen:
     * Japanese excluded, results
     * sorted by language (English name) then voice name.
     */
    fun search(entries: List<VoiceCatalogEntry>, query: String): List<VoiceCatalogEntry> {
        val q = fold(query.trim())
        if (q.isEmpty()) return emptyList()
        return entries
            .filter { entry -> !LanguageCodes.isJapanese(entry.languageCode) }
            .filter { entry -> matches(entry, q) }
            .sortedWith(compareBy({ it.languageEnglish }, { it.displayName }))
    }

    /**
     * Catalog languages visible for browsing: canonical codes, Japanese
     * excluded, sorted by English language name.
     */
    fun browsableLanguageCodes(entries: List<VoiceCatalogEntry>): List<String> {
        val byCode = LinkedHashMap<String, VoiceCatalogEntry>()
        for (entry in entries) {
            val code = entry.canonicalLanguageCode
            if (LanguageCodes.isJapanese(code)) continue
            byCode.putIfAbsent(code, entry)
        }
        return byCode.entries
            .sortedWith(compareBy({ it.value.languageEnglish }, { it.key }))
            .map { it.key }
    }

    /** Voices of one canonical language, in catalog order (name-sorted, as parsed). */
    fun voicesForLanguage(
        entries: List<VoiceCatalogEntry>,
        canonicalCode: String
    ): List<VoiceCatalogEntry> =
        entries.filter { it.canonicalLanguageCode == canonicalCode }

    private fun matches(entry: VoiceCatalogEntry, foldedQuery: String): Boolean {
        val candidates = listOf(
            entry.displayName,
            entry.languageEnglish,
            entry.languageNative,
            entry.countryEnglish,
            entry.languageCode,
            entry.canonicalLanguageCode,
            LanguageCodes.family(entry.languageCode),
            entry.key
        )
        return candidates.any { fold(it).contains(foldedQuery) }
    }

    /** Lowercases and strips diacritics: "Żary" and "zary" fold together. */
    fun fold(text: String): String {
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
        return normalized.replace(Regex("\\p{Mn}+"), "").lowercase()
    }
}
