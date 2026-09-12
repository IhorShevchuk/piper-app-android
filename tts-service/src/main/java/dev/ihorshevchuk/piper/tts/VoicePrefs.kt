package dev.ihorshevchuk.piper.tts

import android.content.Context

/**
 * Persists the user's active (default) voice selection, shared by the
 * system TTS service and the app UI.
 */
class VoicePrefs(context: Context) {

    private val prefs =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Name of the active voice (e.g. "en_US-lessac-medium"), or null. */
    var activeVoiceName: String?
        get() = prefs.getString(KEY_ACTIVE_VOICE, null)
        set(value) {
            prefs.edit().putString(KEY_ACTIVE_VOICE, value).apply()
        }

    fun clear() {
        prefs.edit().remove(KEY_ACTIVE_VOICE).apply()
    }

    companion object {
        private const val PREFS_NAME = "piper_tts_prefs"
        private const val KEY_ACTIVE_VOICE = "active_voice_name"
    }
}
