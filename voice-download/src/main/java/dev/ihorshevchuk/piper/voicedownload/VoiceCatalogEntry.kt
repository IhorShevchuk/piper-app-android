package dev.ihorshevchuk.piper.voicedownload

/**
 * One downloadable voice from the catalog.
 *
 * [key] is the catalog key and the installed file stem: `<key>.onnx` /
 * `<key>.onnx.json` in the voices directory, matching [FileVoiceStore]'s
 * naming. URLs are absolute; sizes and MD5 digests come from the catalog
 * (null when the catalog omits them).
 */
data class VoiceCatalogEntry(
    val key: String,
    val displayName: String,
    val languageCode: String,
    val languageEnglish: String,
    val languageNative: String,
    val countryEnglish: String,
    val quality: String,
    val numSpeakers: Int,
    val onnxUrl: String,
    val configUrl: String?,
    val onnxSizeBytes: Long,
    val onnxMd5: String?,
    val configSizeBytes: Long,
    val configMd5: String?
) {
    /** File name under the voices dir, e.g. "en_US-lessac-medium.onnx". */
    val modelFileName: String get() = "$key.onnx"

    /** Config file name under the voices dir, e.g. "en_US-lessac-medium.onnx.json". */
    val configFileName: String get() = "$key.onnx.json"

    /** Combined download size (0 when the catalog reports no sizes). */
    val totalSizeBytes: Long get() = onnxSizeBytes + configSizeBytes
}
