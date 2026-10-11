package dev.ihorshevchuk.piper.voicedownload

import android.content.Context

/**
 * The voice model format used for new downloads, chosen in Settings and
 * persisted in SharedPreferences.
 *
 * Downloads read the current format when they start; installed voices
 * keep the format they were downloaded with.
 */
enum class VoiceFormat(val rawValue: String) {
    STANDARD("standard"),
    COMPACT("compact");

    companion object {
        fun fromRawValue(raw: String?): VoiceFormat =
            entries.firstOrNull { it.rawValue == raw } ?: STANDARD
    }
}

/** SharedPreferences-backed store for the [VoiceFormat] setting. */
class VoiceFormatStore(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var current: VoiceFormat
        get() = VoiceFormat.fromRawValue(prefs.getString(KEY_MODEL_FORMAT, null))
        set(value) {
            prefs.edit().putString(KEY_MODEL_FORMAT, value.rawValue).apply()
        }

    companion object {
        private const val PREFS_NAME = "piper_voice_format"
        /** SharedPreferences key for the format. */
        const val KEY_MODEL_FORMAT = "model_format"
    }
}
