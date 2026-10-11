package dev.ihorshevchuk.piper.voicedownload

/**
 * Where a voice catalog lives: the voices.json URL and the base URL that
 * file paths inside it resolve against.
 *
 * DEFAULT points at Ihor's fp16-quantized catalog,
 * not the upstream full-size one.
 */
data class VoiceCatalogSource(
    val catalogUrl: String,
    val filesBaseUrl: String
) {
    companion object {
        val DEFAULT = VoiceCatalogSource(
            catalogUrl = "https://huggingface.co/IhorShevchuk/piper1-voices-fp16-quantized/raw/main/voices.json",
            filesBaseUrl = "https://huggingface.co/IhorShevchuk/piper1-voices-fp16-quantized/resolve/main/"
        )
    }
}
