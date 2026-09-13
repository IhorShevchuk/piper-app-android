package dev.ihorshevchuk.piper.app

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toolbar
import androidx.activity.ComponentActivity
import dev.ihorshevchuk.piper.tts.FileVoiceStore
import dev.ihorshevchuk.piper.tts.VoiceInfo
import dev.ihorshevchuk.piper.voicedownload.VoiceListActivity
import java.io.File

/**
 * App home screen, mirroring the iOS MainView: the installed-voices list,
 * Download Voice Model / Import Model from Files actions, and Help / About
 * in the toolbar.
 *
 * Plain Views on purpose - no Compose dependency for the app shell.
 */
class MainActivity : ComponentActivity() {

    private lateinit var voicesDir: File
    private lateinit var listView: ListView
    private lateinit var emptyView: TextView
    private var voices: List<VoiceInfo> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        voicesDir = File(filesDir, FileVoiceStore.VOICES_DIR_NAME).apply { mkdirs() }

        val pad = dp(16)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val toolbar = Toolbar(this).apply {
            title = getString(R.string.app_name)
            inflateMenu(R.menu.main_menu)
            setOnMenuItemClickListener { item: MenuItem ->
                when (item.itemId) {
                    R.id.menu_help -> {
                        startActivity(Intent(this@MainActivity, HelpActivity::class.java))
                        true
                    }
                    R.id.menu_about -> {
                        startActivity(Intent(this@MainActivity, AboutActivity::class.java))
                        true
                    }
                    else -> false
                }
            }
        }
        root.addView(toolbar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT))

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        val header = TextView(this).apply {
            text = getString(R.string.installed_voices)
            setTypeface(typeface, Typeface.BOLD)
            textSize = 18f
        }
        content.addView(header)

        emptyView = TextView(this).apply {
            text = getString(R.string.no_voices_message)
            setPadding(0, dp(8), 0, dp(8))
        }
        listView = ListView(this).apply {
            setOnItemClickListener { _, _, position, _ ->
                val voice = voices.getOrNull(position) ?: return@setOnItemClickListener
                startActivity(Intent(this@MainActivity, VoiceDetailActivity::class.java)
                    .putExtra(VoiceDetailActivity.EXTRA_VOICE_NAME, voice.name))
            }
        }
        // The empty view only takes over when the adapter is empty.
        listView.emptyView = emptyView
        content.addView(emptyView)
        content.addView(listView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val downloadButton = Button(this).apply {
            text = getString(R.string.download_voice_model)
            setOnClickListener {
                startActivity(Intent(this@MainActivity, VoiceListActivity::class.java))
            }
        }
        val importButton = Button(this).apply {
            text = getString(R.string.update_model_in_app)
            setOnClickListener {
                startActivity(Intent(this@MainActivity, ImportVoiceActivity::class.java))
            }
        }
        content.addView(downloadButton)
        content.addView(importButton)
        val readButton = Button(this).apply {
            text = getString(R.string.read_text_button)
            setOnClickListener {
                startActivity(Intent(this@MainActivity,
                    dev.ihorshevchuk.piper.app.reader.ReaderActivity::class.java))
            }
        }
        content.addView(readButton)

        root.addView(content, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    private fun refreshList() {
        voices = try {
            FileVoiceStore(voicesDir).listVoices()
        } catch (e: Exception) {
            Log.w(TAG, "listing installed voices failed", e)
            emptyList()
        }
        val titles = voices.map { voice ->
            VoiceTitles.title(voice, voice.configFile?.let(VoiceConfigParser::parse))
        }
        listView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, titles)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val TAG = "PiperMain"
    }
}
