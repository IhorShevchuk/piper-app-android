package dev.ihorshevchuk.piper.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * How-to-use screen, mirroring the iOS HelpView.
 */
class HelpActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = dp(16)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        val title = TextView(this).apply {
            text = getString(R.string.help_title)
            setTypeface(typeface, Typeface.BOLD)
            textSize = 20f
            setPadding(0, 0, 0, dp(8))
        }
        val body = TextView(this).apply {
            text = getString(R.string.help_body)
        }
        val ttsButton = Button(this).apply {
            text = getString(R.string.open_tts_settings)
            setOnClickListener { openTtsSettings() }
        }

        root.addView(title)
        root.addView(body)
        root.addView(ttsButton)

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
