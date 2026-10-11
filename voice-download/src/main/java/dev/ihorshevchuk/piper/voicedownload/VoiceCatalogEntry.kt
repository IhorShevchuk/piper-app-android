package dev.ihorshevchuk.piper.voicedownload

/**
 * Training-data provenance of a catalog voice, from its MODEL_CARD.
 * Android twin of the iOS `TrainingData` struct. Every field is nullable,
 * so a partially-filled `training_data` object can never break parsing.
 */
data class TrainingData(
    val url: String?,
    val license: String?,
    val licenseRaw: String?
)

/**
 * An alternative model build from the catalog's top-level `variants`
 * field (today only "int8"). [modelUrl] is absolute; [sizeBytes] is the
 * variant model size. Variants carry no MD5 digest in the catalog.
 */
data class VoiceVariant(
    val modelPath: String,
    val modelUrl: String,
    val sizeBytes: Long
)

/**
 * One downloadable voice from the catalog.
 *
 * [key] is the catalog key and the installed file stem: `<key>.onnx` /
 * `<key>.onnx.json` in the voices directory, matching [FileVoiceStore]'s
 * naming. URLs are absolute; sizes and MD5 digests come from the catalog
 * (null when the catalog omits them).
 *
 * The license / training-data / variant fields are catalog enrichment
 * (iOS PR #62 / #76): optional, and absent on entries written before
 * they were added - parsing and behaviour are unchanged for those.
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
    val configMd5: String?,
    /** Headline license (most restrictive of model vs training data). */
    val license: String? = null,
    /** License of the model itself, when the catalog separates it. */
    val modelLicense: String? = null,
    val trainingData: TrainingData? = null,
    /** The INT8 ("Compact") build, when the catalog offers one. */
    val int8Variant: VoiceVariant? = null
) {
    /** File name under the voices dir, e.g. "en_US-lessac-medium.onnx". */
    val modelFileName: String get() = "$key.onnx"

    /** Config file name under the voices dir, e.g. "en_US-lessac-medium.onnx.json". */
    val configFileName: String get() = "$key.onnx.json"

    /** Combined FP16 download size (0 when the catalog reports no sizes). */
    val totalSizeBytes: Long get() = onnxSizeBytes + configSizeBytes

    /** Canonical catalog language code, e.g. "en_GB" for "en-gb-x-rp". */
    val canonicalLanguageCode: String get() = LanguageCodes.canonical(languageCode)

    /** True when [format] selects the INT8 build for this voice (iOS `compactSelected`). */
    fun isCompactSelected(format: VoiceFormat): Boolean =
        format == VoiceFormat.COMPACT && int8Variant != null

    /** Model URL a download under [format] fetches. */
    fun effectiveOnnxUrl(format: VoiceFormat): String =
        if (isCompactSelected(format)) int8Variant!!.modelUrl else onnxUrl

    /** Model size a download under [format] fetches. */
    fun effectiveOnnxSizeBytes(format: VoiceFormat): Long =
        if (isCompactSelected(format)) int8Variant!!.sizeBytes else onnxSizeBytes

    /**
     * MD5 to verify the model download against, or null to skip the MD5
     * check: the catalog serves no digest for variants (size check only
     * for INT8, same as iOS).
     */
    fun effectiveOnnxMd5(format: VoiceFormat): String? =
        if (isCompactSelected(format)) null else onnxMd5

    /** Combined download size under [format] (config is shared by both). */
    fun effectiveTotalSizeBytes(format: VoiceFormat): Long =
        effectiveOnnxSizeBytes(format) + configSizeBytes
}
