package dev.ihorshevchuk.piper.tts

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File

/**
 * Minimal settings entry point for the system TTS engine
 * (the gear icon in Android's TTS settings, via tts_engine.xml).
 *
 * Shows engine status: installed voice count and the active voice.
 * The full voice-list / download-manager UI lands here later.
 *
 * Plain Views on purpose - no Compose dependency. All user-facing strings
 * come from resources.
 */
class TtsSettingsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            gravity = Gravity.START
        }

        val title = TextView(this).apply {
            text = getString(R.string.tts_settings_title)
            textSize = 20f
        }
        val status = TextView(this).apply {
            text = voiceStatusText()
            textSize = 14f
        }
        val hint = TextView(this).apply {
            text = getString(R.string.tts_settings_voice_manager_hint)
            textSize = 14f
        }
        // Voice catalog / download manager lives in :voice-download; reached
        // through the intent action so this module keeps no dependency on it.
        val manageVoices = Button(this).apply {
            text = getString(R.string.tts_settings_manage_voices)
            contentDescription = getString(R.string.tts_settings_manage_voices)
            setOnClickListener {
                startActivity(Intent(VoiceListContract.ACTION_MANAGE_VOICES))
            }
        }

        root.addView(title)
        root.addView(status)
        root.addView(manageVoices)
        root.addView(hint)
        setContentView(root)
    }

    private fun voiceStatusText(): String {
        val store = FileVoiceStore(File(filesDir, "voices"))
        val voices = try {
            store.listVoices()
        } catch (_: Exception) {
            emptyList()
        }
        val active = VoicePrefs(this).activeVoiceName
        return getString(
            R.string.tts_settings_status,
            voices.size,
            active ?: getString(R.string.tts_settings_no_active_voice)
        )
    }
}
