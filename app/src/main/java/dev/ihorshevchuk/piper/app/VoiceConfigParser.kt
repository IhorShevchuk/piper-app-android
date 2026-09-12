package dev.ihorshevchuk.piper.app

import org.json.JSONObject
import java.io.File
import java.util.Locale

/**
 * Parses a Piper voice `.onnx.json` configuration file.
 *
 * Android twin of the iOS `ModelInfo` decoder: dataset name, language,
 * piper version, audio quality / sample rate, and the speaker map used by
 * the voice detail screen (mirrors iOS `VoiceView`'s speaker picker).
 */
data class VoiceConfig(
    /** Dataset name, e.g. "lessac". Falls back to "Unknown" like iOS. */
    val name: String,
    /** BCP-47-ish code from the config, e.g. "en_US". */
    val languageCode: String,
    /** Display language derived from the family, e.g. "English". */
    val languageName: String,
    /** Display country derived from the region, e.g. "United States". */
    val countryName: String,
    val piperVersion: String,
    /** e.g. "medium"; null when the config omits it. */
    val quality: String?,
    /** e.g. 22050; null when the config omits it. */
    val sampleRate: Int?,
    /**
     * Speaker display name to id, in config order. Empty for single-speaker
     * voices without a `speaker_id_map`.
     */
    val speakers: Map<String, Int>,
    val numSpeakers: Int,
) {
    /**
     * Speakers to offer in the picker: the config's `speaker_id_map` when
     * present, otherwise generated entries when `num_speakers` > 1.
     */
    fun pickerSpeakers(): Map<String, Int> {
        if (speakers.isNotEmpty()) return speakers
        if (numSpeakers > 1) {
            return (0 until numSpeakers).associate { "Speaker $it" to it }
        }
        return emptyMap()
    }
}

object VoiceConfigParser {

    /** Returns null when the file is missing or not valid JSON. */
    fun parse(configFile: File): VoiceConfig? {
        if (!configFile.isFile) return null
        return try {
            parseJson(configFile.readText())
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Returns null for malformed JSON or configs missing the sections the
     * voice detail screen needs (mirrors the strictness of iOS `ModelInfo`).
     */
    fun parseJson(json: String): VoiceConfig? {
        val root = try {
            JSONObject(json)
        } catch (_: Exception) {
            return null
        }
        val language = root.optJSONObject("language") ?: return null
        val audio = root.optJSONObject("audio") ?: return null
        val code = language.optString("code", "")
        if (code.isEmpty()) return null

        val family = language.optString("family", "")
            .ifEmpty { code.substringBefore('_').substringBefore('-') }
        val region = language.optString("region", "")
            .ifEmpty { code.substringAfter('_', "").substringBefore('-') }
        val speakerMap = LinkedHashMap<String, Int>()
        root.optJSONObject("speaker_id_map")?.let { map ->
            val keys = map.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                speakerMap[key] = map.optInt(key, 0)
            }
        }
        return VoiceConfig(
            name = root.optString("dataset", "").ifEmpty { "Unknown" },
            languageCode = code,
            languageName = Locale(family).displayLanguage.ifEmpty { family },
            countryName = Locale("", region).displayCountry.ifEmpty { region },
            piperVersion = root.optString("piper_version", "?"),
            quality = audio.optString("quality", null)?.ifEmpty { null },
            sampleRate = audio.optInt("sample_rate", -1).takeIf { it > 0 },
            speakers = speakerMap,
            numSpeakers = root.optInt("num_speakers", 1).coerceAtLeast(1),
        )
    }
}
