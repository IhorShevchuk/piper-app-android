package dev.ihorshevchuk.piper.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Help screen, mirroring the iOS HelpView: the five help lines, with the
 * VoiceOver-specific ones adapted to the Android TTS settings flow, plus a
 * button that opens the system TTS settings directly.
 */
class HelpActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.help_title)

        val pad = dp(16)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        val lines = listOf(
            R.string.help_line_1,
            R.string.help_line_2,
            R.string.help_line_3,
            R.string.help_line_4,
            R.string.help_line_5,
        )
        for ((index, lineRes) in lines.withIndex()) {
            root.addView(TextView(this).apply {
                text = "•  " + getString(lineRes)
                textSize = 16f
                setPadding(0, 0, 0, dp(12))
            })
            if (index == 2) {
                root.addView(Button(this).apply {
                    text = getString(R.string.open_tts_settings)
                    setOnClickListener { openTtsSettings() }
                    setPadding(0, 0, 0, dp(12))
                })
            }
        }

        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun openTtsSettings() {
        try {
            startActivity(Intent("com.android.settings.TTS_SETTINGS"))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.tts_settings_unavailable,
                Toast.LENGTH_LONG).show()
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
