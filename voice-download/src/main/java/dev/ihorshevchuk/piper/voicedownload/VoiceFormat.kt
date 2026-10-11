package dev.ihorshevchuk.piper.voicedownload

import android.content.Context

/**
 * The voice model format used for new downloads. Android twin of the iOS
 * `ModelFormat` (`PiperApp/Sources/Utils/ModelFormat.swift`): same
 * preference key, same raw values, same default.
 *
 * INT8 (Compact) is skipped on Android for now: ONNX Runtime 1.22 crashes
 * creating sessions for quantized models on 32-bit ARM (SIGBUS in graph
 * optimization; see piper-kotlin patch 0003 for the engine-side
 * workaround and its perf cost), so [VoiceFormatStore.current] always
 * reports [VoiceFormat.STANDARD] and Settings offers no format choice.
 * The preference is still persisted so re-enabling Compact later picks
 * the user's choice back up.
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
        // INT8 is skipped on Android for now (see VoiceFormat doc): always
        // Standard, whatever an earlier build persisted.
        get() = VoiceFormat.STANDARD
        set(value) {
            prefs.edit().putString(KEY_MODEL_FORMAT, value.rawValue).apply()
        }

    companion object {
        private const val PREFS_NAME = "piper_voice_format"
        /** Same key the iOS app uses in UserDefaults. */
        const val KEY_MODEL_FORMAT = "model_format"
    }
}
