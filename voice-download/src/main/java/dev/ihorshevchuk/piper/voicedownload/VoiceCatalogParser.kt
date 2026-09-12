package dev.ihorshevchuk.piper.voicedownload

import org.json.JSONObject

/**
 * Parses a HuggingFace piper-voices `voices.json` document into
 * [VoiceCatalogEntry]s.
 *
 * Uses org.json (built into Android) rather than a serialization library so
 * the code survives R8/minification untouched. Malformed documents and
 * entries without an `.onnx` file yield an empty list / skipped entry,
 * never an exception.
 */
object VoiceCatalogParser {

    fun parse(json: String, source: VoiceCatalogSource): List<VoiceCatalogEntry> {
        val entries = mutableListOf<VoiceCatalogEntry>()
        val root = try {
            JSONObject(json)
        } catch (_: Exception) {
            return emptyList()
        }
        val keys = root.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            try {
                parseEntry(key, root.getJSONObject(key), source)?.let { entries.add(it) }
            } catch (_: Exception) {
                // Skip the broken entry, keep the rest of the catalog.
            }
        }
        return entries.sortedWith(
            compareBy({ it.languageEnglish }, { it.displayName }, { it.quality })
        )
    }

    private fun parseEntry(
        key: String,
        obj: JSONObject,
        source: VoiceCatalogSource
    ): VoiceCatalogEntry? {
        val lang = obj.optJSONObject("language")
        val files = obj.optJSONObject("files") ?: return null

        var onnxPath: String? = null
        var onnxSize = 0L
        var onnxMd5: String? = null
        var configPath: String? = null
        var configSize = 0L
        var configMd5: String? = null

        val fileKeys = files.keys()
        while (fileKeys.hasNext()) {
            val path = fileKeys.next()
            val meta = files.optJSONObject(path) ?: continue
            val size = meta.optLong("size_bytes", 0)
            val md5 = meta.optString("md5_digest").takeIf { it.isNotEmpty() }
            when {
                path.endsWith(".onnx.json") -> {
                    configPath = path; configSize = size; configMd5 = md5
                }
                path.endsWith(".onnx") -> {
                    onnxPath = path; onnxSize = size; onnxMd5 = md5
                }
            }
        }
        // No model file (e.g. a MODEL_CARD-only entry): not downloadable.
        val model = onnxPath ?: return null

        return VoiceCatalogEntry(
            key = key,
            displayName = obj.optString("name", key).ifEmpty { key },
            languageCode = lang?.optString("code", "") ?: "",
            languageEnglish = lang?.optString("name_english", "") ?: "",
            languageNative = lang?.optString("name_native", "") ?: "",
            countryEnglish = lang?.optString("country_english", "") ?: "",
            quality = obj.optString("quality", "medium").ifEmpty { "medium" },
            numSpeakers = obj.optInt("num_speakers", 1).coerceAtLeast(1),
            onnxUrl = source.filesBaseUrl + model,
            configUrl = configPath?.let { source.filesBaseUrl + it },
            onnxSizeBytes = onnxSize,
            onnxMd5 = onnxMd5,
            configSizeBytes = configSize,
            configMd5 = configMd5
        )
    }
}
