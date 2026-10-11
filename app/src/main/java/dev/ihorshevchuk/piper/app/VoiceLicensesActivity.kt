package dev.ihorshevchuk.piper.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.ihorshevchuk.piper.voicedownload.VoiceCatalogEntry
import dev.ihorshevchuk.piper.voicedownload.VoiceDownloadManager

/**
 * Voice Licenses screen, mirroring the iOS VoiceLicensesView (PR #62):
 * every catalog voice with its headline license, the model license and
 * the training-data license + source link when the catalog provides
 * them. Loads the catalog only when opened; a load failure shows the
 * same empty state as an empty catalog (iOS fails soft here too).
 */
class VoiceLicensesActivity : Activity() {

    private lateinit var root: LinearLayout
    private lateinit var statusView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.voice_licenses)

        val pad = dp(16)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        statusView = TextView(this).apply {
            text = getString(R.string.loading_voices)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        root.addView(statusView)
        setContentView(ScrollView(this).apply { addView(root) })

        loadLicenses()
    }

    private fun loadLicenses() {
        val manager = VoiceDownloadManager(this)
        Thread {
            val entries = try {
                manager.fetchCatalog()
            } catch (e: Exception) {
                Log.w(TAG, "catalog load for licenses failed", e)
                emptyList()
            }
            // iOS sorts the licenses list by name, then quality, then key.
            val sorted = entries.sortedWith(
                compareBy({ it.displayName }, { it.quality }, { it.key })
            )
            runOnUiThread { showLicenses(sorted) }
        }.apply { isDaemon = true; start() }
    }

    private fun showLicenses(entries: List<VoiceCatalogEntry>) {
        root.removeView(statusView)
        if (entries.isEmpty()) {
            root.addView(TextView(this).apply {
                text = getString(R.string.catalog_load_failed)
                textSize = 16f
            })
            return
        }
        for (entry in entries) {
            root.addView(TextView(this).apply {
                text = entry.displayName
                textSize = 16f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, dp(12), 0, 0)
            })
            root.addView(TextView(this).apply {
                text = getString(
                    R.string.voice_license
                ) + ": " + (entry.license ?: getString(R.string.license_unknown))
            })
            entry.modelLicense?.let { modelLicense ->
                root.addView(TextView(this).apply {
                    text = getString(R.string.model_license) + ": " + modelLicense
                    textSize = 14f
                })
            }
            entry.trainingData?.let { training ->
                root.addView(TextView(this).apply {
                    text = getString(R.string.training_data) + ": " +
                        (training.license ?: getString(R.string.license_unknown))
                    textSize = 14f
                })
                training.url?.takeIf { it.isNotEmpty() }?.let { url ->
                    root.addView(TextView(this).apply {
                        text = url
                        textSize = 12f
                        setPadding(0, dp(2), 0, 0)
                        setOnClickListener {
                            try {
                                startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                )
                            } catch (_: Exception) {
                                // No browser: the URL stays readable as text.
                            }
                        }
                    })
                }
            }
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val TAG = "PiperVoiceLicenses"
    }
}
