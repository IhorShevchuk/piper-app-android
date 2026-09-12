package dev.ihorshevchuk.piper.tts

/**
 * Picks the voice for a synthesis request from the installed set.
 *
 * Priority:
 * 1. Exact voice name from the request (most specific - the client asked).
 * 2. The user's persisted preferred voice, when it matches the requested
 *    language.
 * 3. Installed voice matching the requested language + country.
 * 4. Installed voice matching the requested language.
 * 5. The preferred voice (no language requested, or requested language not
 *    installed).
 * 6. The first installed voice.
 *
 * A system TTS engine that stays silent when it could speak is worse than
 * one speaking a fallback voice, so resolution only returns null when
 * nothing is installed at all. Pure JVM - unit-testable.
 */
object VoiceResolver {

    fun resolve(
        installed: List<VoiceInfo>,
        requestVoiceName: String?,
        language: String,
        country: String,
        preferredVoiceName: String?
    ): VoiceInfo? {
        if (installed.isEmpty()) return null

        if (!requestVoiceName.isNullOrEmpty()) {
            installed.find { it.name == requestVoiceName }?.let { return it }
        }

        val preferred = preferredVoiceName?.let { name ->
            installed.find { it.name == name }
        }
        if (preferred != null && language.isNotEmpty() &&
            preferred.locale.language == language
        ) {
            return preferred
        }

        if (language.isNotEmpty()) {
            installed.find {
                it.locale.language == language &&
                    (country.isEmpty() || it.locale.country.equals(country, ignoreCase = true))
            }?.let { return it }
            installed.find { it.locale.language == language }?.let { return it }
        }

        return preferred ?: installed.first()
    }
}
