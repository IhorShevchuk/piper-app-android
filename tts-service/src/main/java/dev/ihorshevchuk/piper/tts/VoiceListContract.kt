package dev.ihorshevchuk.piper.tts

/**
 * Entry-point contract for the voice catalog / download-manager UI, which
 * lives in the `:voice-download` module.
 *
 * `:tts-service` cannot depend on `:voice-download` (that module depends on
 * this one), so the settings activity launches the voice list through this
 * intent action instead of a class reference. `:voice-download` declares the
 * matching intent-filter in its manifest; keep the two strings in sync.
 */
object VoiceListContract {
    const val ACTION_MANAGE_VOICES = "dev.ihorshevchuk.piper.tts.action.MANAGE_VOICES"
}
