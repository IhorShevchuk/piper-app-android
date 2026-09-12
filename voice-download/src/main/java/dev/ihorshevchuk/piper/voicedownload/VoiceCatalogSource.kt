package dev.ihorshevchuk.piper.voicedownload

/**
 * Where a voice catalog lives: the voices.json URL and the base URL that
 * file paths inside it resolve against.
 *
 * DEFAULT points at the upstream Piper catalog. To serve the fp16-quantized
 * voices instead, pass a source pointing at that repo's voices.json - no
 * other code changes needed, as long as the JSON keeps the same schema.
 */
data class VoiceCatalogSource(
    val catalogUrl: String,
    val filesBaseUrl: String
) {
    companion object {
        val DEFAULT = VoiceCatalogSource(
            catalogUrl = "https://huggingface.co/rhasspy/piper-voices/raw/main/voices.json",
            filesBaseUrl = "https://huggingface.co/rhasspy/piper-voices/resolve/main/"
        )
    }
}
