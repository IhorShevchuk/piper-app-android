package dev.ihorshevchuk.piper.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * About screen, mirroring the iOS AboutAppView: Credits & Legal, engine
 * status, app version, and a feedback link.
 *
 * The iOS "Audio Unit Status / Connect" row becomes the Android TTS engine
 * status: whether Piper is the system's selected TTS engine, with a button
 * to the system TTS settings when it is not.
 */
class AboutActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.about_app)

        val pad = dp(16)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        root.addView(TextView(this).apply {
            text = getString(R.string.credits_and_legal)
            setTypeface(typeface, Typeface.BOLD)
            textSize = 18f
            setPadding(0, 0, 0, dp(8))
        })
        for ((titleRes, descRes, url) in CREDITS) {
            addCredit(root,
                getString(titleRes), getString(descRes), url)
        }

        root.addView(TextView(this).apply {
            val active = isPiperDefaultTts()
            text = getString(R.string.tts_engine_status) + ": " +
                getString(if (active) R.string.tts_status_active
                else R.string.tts_status_inactive)
            setPadding(0, dp(16), 0, 0)
        })
        if (!isPiperDefaultTts()) {
            root.addView(Button(this).apply {
                text = getString(R.string.open_tts_settings)
                setOnClickListener { openTtsSettings() }
            })
        }

        root.addView(TextView(this).apply {
            @Suppress("DEPRECATION")
            val appVersion =
                packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
            text = getString(R.string.app_version) + ": " + appVersion
            setPadding(0, dp(16), 0, 0)
        })

        root.addView(Button(this).apply {
            text = getString(R.string.share_feedback)
            setOnClickListener { openUrl(FEEDBACK_URL) }
            setPadding(0, dp(8), 0, 0)
        })

        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun addCredit(root: LinearLayout, title: String, description: String, url: String) {
        root.addView(TextView(this).apply {
            text = title
            setTypeface(typeface, Typeface.BOLD)
            textSize = 16f
            setPadding(0, dp(8), 0, 0)
        })
        root.addView(TextView(this).apply {
            text = description
            setPadding(0, 0, 0, dp(2))
        })
        root.addView(Button(this).apply {
            text = getString(R.string.open_in_browser, title)
            contentDescription = "$title: $url"
            setOnClickListener { openUrl(url) }
        })
    }

    private fun isPiperDefaultTts(): Boolean =
        try {
            Settings.Secure.getString(contentResolver, "tts_default_synth")
                ?.contains(packageName) == true
        } catch (e: Exception) {
            Log.w(TAG, "reading default TTS engine failed", e)
            false
        }

    private fun openTtsSettings() {
        try {
            startActivity(Intent("com.android.settings.TTS_SETTINGS"))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.tts_settings_unavailable,
                Toast.LENGTH_LONG).show()
        }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, getString(R.string.open_in_browser, url),
                Toast.LENGTH_LONG).show()
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val TAG = "PiperAbout"
        private const val FEEDBACK_URL =
            "mailto:piper-feedback@ihor-shevchuk.dev?subject=Piper%20feedback"

        private val CREDITS = listOf(
            Triple(R.string.license_piper_title,
                R.string.license_piper_description,
                "https://github.com/OHF-Voice/piper1-gpl"),
            Triple(R.string.license_espeak_title,
                R.string.license_espeak_description,
                "https://github.com/espeak-ng/espeak-ng-spm"),
            Triple(R.string.license_app_title,
                R.string.license_app_description,
                "https://github.com/IhorShevchuk/piper-app-android"),
        )
    }
}
