package dev.ihorshevchuk.piper.voicedownload

/** Voice-list filter tabs. */
enum class VoiceFilterMode { ALL, INSTALLED, AVAILABLE }

/**
 * Pure filter + search over catalog entries. Case-insensitive; matches the
 * display name, both language names, country, language code and key.
 */
object VoiceListFilter {

    fun filter(
        entries: List<VoiceCatalogEntry>,
        installedKeys: Set<String>,
        mode: VoiceFilterMode,
        query: String
    ): List<VoiceCatalogEntry> {
        val q = query.trim().lowercase()
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

    private fun matches(entry: VoiceCatalogEntry, q: String): Boolean =
        entry.displayName.lowercase().contains(q) ||
            entry.languageEnglish.lowercase().contains(q) ||
            entry.languageNative.lowercase().contains(q) ||
            entry.countryEnglish.lowercase().contains(q) ||
            entry.languageCode.lowercase().contains(q) ||
            entry.key.lowercase().contains(q)
}
