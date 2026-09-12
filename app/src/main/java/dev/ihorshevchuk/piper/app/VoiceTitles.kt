package dev.ihorshevchuk.piper.app

import dev.ihorshevchuk.piper.tts.VoiceInfo

/**
 * Display titles for installed voices.
 *
 * Mirrors the iOS `modelTitle` (`"<dataset> <language>"`, e.g. "Lessac
 * English"); falls back to the raw voice key when there is no config.
 */
object VoiceTitles {

    fun title(voice: VoiceInfo, config: VoiceConfig?): String {
        val dataset = config?.name?.takeIf { it.isNotEmpty() && it != "Unknown" }
            ?: return voice.name
        val language = voice.locale.displayLanguage.ifEmpty { voice.locale.language }
        val capitalized = dataset.replaceFirstChar { it.uppercaseChar() }
        return if (language.isNotEmpty()) "$capitalized $language" else capitalized
    }
}
