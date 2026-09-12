package dev.ihorshevchuk.piper.app

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import android.widget.Toolbar
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import dev.ihorshevchuk.piper.tts.FileVoiceStore
import dev.ihorshevchuk.piper.tts.VoiceInfo
import dev.ihorshevchuk.piper.voicedownload.VoiceListActivity
import java.io.File

/**
 * App home screen, mirroring the iOS MainView: the installed-voices list,
 * download/import actions, and Help / About in the toolbar.
 *
 * Plain Views on purpose - no Compose dependency for the app shell.
 */
class MainActivity : ComponentActivity() {

    private lateinit var voicesDir: File
    private lateinit var listView: ListView
    private lateinit var emptyView: TextView

    /** Model file name chosen in the first import step, awaiting its config. */
    private var pendingModelName: String? = null

    private val pickModel =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri ?: return@registerForActivityResult
            importModel(uri)
        }

    private val pickConfig =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri ?: return@registerForActivityResult
            importConfig(uri)
        }

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
            visibility = View.GONE
        }
        listView = ListView(this).apply {
            emptyView = this@MainActivity.emptyView
            setOnItemClickListener { _, _, position, _ ->
                val voice = installedVoices().getOrNull(position) ?: return@setOnItemClickListener
                startActivity(Intent(this@MainActivity, VoiceDetailActivity::class.java)
                    .putExtra(VoiceDetailActivity.EXTRA_VOICE_NAME, voice.name))
            }
        }
        content.addView(emptyView)
        content.addView(listView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val downloadButton = Button(this).apply {
            text = getString(R.string.download_voices)
            setOnClickListener {
                startActivity(Intent(this@MainActivity, VoiceListActivity::class.java))
            }
        }
        val importButton = Button(this).apply {
            text = getString(R.string.import_voice_from_file)
            setOnClickListener { pickModel.launch(arrayOf("*/*")) }
        }
        content.addView(downloadButton)
        content.addView(importButton)

        root.addView(content, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    private fun installedVoices(): List<VoiceInfo> =
        try {
            FileVoiceStore(voicesDir).listVoices()
        } catch (e: Exception) {
            Log.w(TAG, "listing installed voices failed", e)
            emptyList()
        }

    private fun refreshList() {
        val voices = installedVoices()
        listView.adapter = ArrayAdapter(
            this, android.R.layout.simple_list_item_1, voices.map { it.name })
        emptyView.visibility = if (voices.isEmpty()) View.VISIBLE else View.GONE
    }

    // ------------------------------------------------------------------
    // Import voice from file (iOS "update_model_in_app")
    // ------------------------------------------------------------------

    private fun importModel(uri: Uri) {
        val name = displayNameOf(uri)
        if (name == null || !ImportVoiceHelper.isModelFileName(name)) {
            Toast.makeText(this, R.string.import_error_not_model, Toast.LENGTH_LONG).show()
            return
        }
        pendingModelName = name
        Thread {
            try {
                copyUriTo(uri, File(voicesDir, name))
                runOnUiThread { promptForConfig(name) }
            } catch (e: Exception) {
                Log.w(TAG, "importing model failed", e)
                pendingModelName = null
                runOnUiThread {
                    Toast.makeText(this, getString(R.string.import_error_copy, e.message),
                        Toast.LENGTH_LONG).show()
                }
            }
        }.apply { isDaemon = true; start() }
    }

    private fun promptForConfig(modelName: String) {
        val configName = ImportVoiceHelper.configFileNameFor(modelName)
        AlertDialog.Builder(this)
            .setTitle(R.string.import_choose_config_title)
            .setMessage(getString(R.string.import_choose_config_message, modelName, configName))
            .setPositiveButton(R.string.choose_file) { _, _ ->
                pickConfig.launch(arrayOf("*/*"))
            }
            .setNegativeButton(R.string.skip) { _, _ ->
                pendingModelName = null
                refreshList()
                Toast.makeText(this, getString(R.string.import_done,
                    ImportVoiceHelper.voiceKeyFor(modelName)), Toast.LENGTH_SHORT).show()
            }
            .setCancelable(false)
            .show()
    }

    private fun importConfig(uri: Uri) {
        val modelName = pendingModelName
        pendingModelName = null
        if (modelName == null) return
        val expectedName = ImportVoiceHelper.configFileNameFor(modelName)
        val name = displayNameOf(uri)
        if (name == null || !ImportVoiceHelper.isConfigFileName(name) || name != expectedName) {
            Toast.makeText(this, R.string.import_error_not_config, Toast.LENGTH_LONG).show()
            refreshList()
            return
        }
        Thread {
            try {
                copyUriTo(uri, File(voicesDir, expectedName))
                runOnUiThread {
                    refreshList()
                    Toast.makeText(this, getString(R.string.import_done,
                        ImportVoiceHelper.voiceKeyFor(modelName)), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.w(TAG, "importing config failed", e)
                runOnUiThread {
                    Toast.makeText(this, getString(R.string.import_error_copy, e.message),
                        Toast.LENGTH_LONG).show()
                    refreshList()
                }
            }
        }.apply { isDaemon = true; start() }
    }

    private fun displayNameOf(uri: Uri): String? =
        try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME),
                null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "resolving display name failed", e)
            null
        }

    private fun copyUriTo(uri: Uri, dest: File) {
        contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) } }
            ?: throw IllegalStateException("cannot open $uri")
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val TAG = "PiperMain"
    }
}
