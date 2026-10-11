package dev.ihorshevchuk.piper.tts

/**
 * Entry-point contract for the voice catalog / download-manager UI, which
 * lives in the `:voice-download` module.
 *
 * `:tts-service` cannot depend on `:voice-download` (that module depends on
 * this one), so the settings activity launches the voice list through this
 * intent action instead of a class reference. `:voice-download` declares the
 * matching intent-filter in its manifest; keep the two strings in sync.
 *
 * The voice-detail action is the mirror image: `:voice-download` cannot
 * depend on `:app` (the app depends on it), so an installed row's tap
 * reaches the detail screen in `:app` through [ACTION_VOICE_DETAIL];
 * `:app` declares the matching intent-filter on its detail activity.
 */
object VoiceListContract {
    const val ACTION_MANAGE_VOICES = "dev.ihorshevchuk.piper.tts.action.MANAGE_VOICES"

    const val ACTION_VOICE_DETAIL = "dev.ihorshevchuk.piper.app.action.VOICE_DETAIL"
    const val EXTRA_VOICE_NAME = "dev.ihorshevchuk.piper.app.extra.VOICE_NAME"
}
