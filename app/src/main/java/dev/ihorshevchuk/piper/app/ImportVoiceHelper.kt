package dev.ihorshevchuk.piper.app

/**
 * Pure logic for the "import voice from file" flow.
 *
 * A voice is a `<name>.onnx` model file, optionally accompanied by
 * `<name>.onnx.json` (the config). This matches the layout
 * [dev.ihorshevchuk.piper.voicedownload.VoiceDownloadManager] produces,
 * so imported voices are indistinguishable from downloaded ones to the
 * TTS service.
 */
object ImportVoiceHelper {

    /**
     * True for a model file name. Note `"x.onnx.json".endsWith(".onnx")`
     * is false, so the config name is naturally excluded.
     */
    fun isModelFileName(name: String): Boolean =
        name.endsWith(".onnx") && name.length > ".onnx".length

    /** True for a config file name (`<name>.onnx.json`). */
    fun isConfigFileName(name: String): Boolean =
        name.endsWith(".onnx.json") && name.length > ".onnx.json".length

    /** The config file name that belongs to [modelFileName]. */
    fun configFileNameFor(modelFileName: String): String = "$modelFileName.json"

    /** The voice key (and TTS voice name) derived from a model file name. */
    fun voiceKeyFor(modelFileName: String): String =
        modelFileName.removeSuffix(".onnx")
}
