package dev.ihorshevchuk.piper.app

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.ihorshevchuk.piper.engine.PiperEngine

/**
 * About screen, mirroring the iOS AboutAppView: versions, links, license.
 */
class AboutActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = dp(16)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        val title = TextView(this).apply {
            text = getString(R.string.about_title)
            setTypeface(typeface, Typeface.BOLD)
            textSize = 20f
            setPadding(0, 0, 0, dp(8))
        }
        val versions = TextView(this).apply {
            @Suppress("DEPRECATION")
            val appVersion =
                packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
            text = getString(R.string.about_app_version, appVersion) + "\n" +
                getString(R.string.about_engine_version, PiperEngine.version())
            setPadding(0, 0, 0, dp(8))
        }
        val body = TextView(this).apply {
            text = getString(R.string.about_body)
            setPadding(0, 0, 0, dp(8))
        }
        val license = TextView(this).apply {
            text = getString(R.string.about_license)
            setPadding(0, 0, 0, dp(8))
        }

        root.addView(title)
        root.addView(versions)
        root.addView(body)
        root.addView(license)
        for ((label, url) in LINKS) {
            root.addView(Button(this).apply {
                text = getString(R.string.open_in_browser, label)
                contentDescription = "$label: $url"
                setOnClickListener { openUrl(url) }
            })
        }

        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun openUrl(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        private val LINKS = listOf(
            "piper-app-android" to "https://github.com/IhorShevchuk/piper-app-android",
            "piper-android" to "https://github.com/IhorShevchuk/piper-android",
            "piper1-gpl" to "https://github.com/OHF-Voice/piper1-gpl",
        )
    }
}
