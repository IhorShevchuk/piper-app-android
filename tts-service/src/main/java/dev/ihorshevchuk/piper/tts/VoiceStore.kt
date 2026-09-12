package dev.ihorshevchuk.piper.tts

import java.io.File
import java.util.Locale

/**
 * A Piper voice available to the TTS service: `<name>.onnx` plus its
 * optional `<name>.onnx.json` config (null configPath means the native
 * layer uses modelPath + ".json", matching iOS).
 */
data class VoiceInfo(
    val name: String,
    val locale: Locale,
    val modelFile: File,
    val configFile: File?
)

/**
 * Source of installed voices.
 *
 * The download manager + voice catalog UI (a later step) fill the backing
 * directory; the service only reads through this interface, so the catalog
 * can later be swapped for a richer implementation without touching the
 * service.
 */
interface VoiceStore {
    /** All installed voices, sorted by name. Empty when none are installed. */
    fun listVoices(): List<VoiceInfo>

    /** Voice with exactly [name], or null. */
    fun findVoice(name: String): VoiceInfo?
}

/**
 * [VoiceStore] backed by a plain directory of `<name>.onnx` files
 * (e.g. `<files>/voices/`), each optionally accompanied by
 * `<name>.onnx.json`.
 *
 * Takes a [java.io.File] instead of a Context so it stays JVM-testable.
 */
class FileVoiceStore(private val voicesDir: File) : VoiceStore {

    override fun listVoices(): List<VoiceInfo> {
        if (!voicesDir.isDirectory) return emptyList()
        return voicesDir.listFiles { f -> f.isFile && f.name.endsWith(".onnx") }
            ?.mapNotNull { modelFile ->
                val name = modelFile.name.removeSuffix(".onnx")
                val locale = parseLocale(name) ?: return@mapNotNull null
                val config = File(voicesDir, "$name.onnx.json").takeIf { it.isFile }
                VoiceInfo(name, locale, modelFile, config)
            }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    override fun findVoice(name: String): VoiceInfo? =
        listVoices().find { it.name == name }

    companion object {
        /**
         * Name of the voices directory under the app's filesDir, shared by
         * the TTS service, the settings UI and the voice download manager.
         * Additive constant - existing call sites keep their literals.
         */
        const val VOICES_DIR_NAME = "voices"

        /**
         * Parses the locale prefix of a Piper voice name:
         * "en_US-lessac-medium" -> Locale("en", "US").
         * Null when the name has no recognizable `ll_CC-` prefix.
         */
        fun parseLocale(voiceName: String): Locale? {
            val tag = voiceName.substringBefore('-')
            val parts = tag.split('_')
            if (parts.size < 2) return null
            val lang = LanguageNormalizer.normalizeLanguage(parts[0])
            if (lang.length != 2) return null
            val country = LanguageNormalizer.normalizeCountry(parts[1])
            return Locale(lang, country)
        }
    }
}
