package dev.ihorshevchuk.piper.app

import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import dev.ihorshevchuk.piper.tts.FileVoiceStore
import java.io.File

/**
 * Import a voice from files, mirroring the iOS ImportVoiceView: pick the
 * `.onnx` model and its `.onnx.json` configuration independently, then
 * install. The JSON is validated by parsing it, like iOS `ModelInfo.create`.
 */
class ImportVoiceActivity : ComponentActivity() {

    private lateinit var voicesDir: File
    private var modelUri: Uri? = null
    private var jsonUri: Uri? = null
    private var modelName: String? = null
    private var jsonName: String? = null

    private lateinit var modelButton: Button
    private lateinit var jsonButton: Button
    private lateinit var installButton: Button

    private val pickModel =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri ?: return@registerForActivityResult
            val name = displayNameOf(uri)
            if (name == null || !ImportVoiceHelper.isModelFileName(name)) {
                showError(getString(R.string.import_invalid_model, name ?: "?"))
                return@registerForActivityResult
            }
            modelUri = uri
            modelName = name
            refresh()
        }

    private val pickJson =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri ?: return@registerForActivityResult
            val name = displayNameOf(uri)
            if (name == null || !ImportVoiceHelper.isConfigFileName(name)) {
                showError(getString(R.string.import_invalid_config))
                return@registerForActivityResult
            }
            // Validate the config the way iOS validates ModelInfo.
            val valid = try {
                contentResolver.openInputStream(uri)?.use { input ->
                    VoiceConfigParser.parseJson(input.bufferedReader().readText())
                } != null
            } catch (e: Exception) {
                Log.w(TAG, "validating imported config failed", e)
                false
            }
            if (!valid) {
                showError(getString(R.string.import_invalid_config))
                return@registerForActivityResult
            }
            jsonUri = uri
            jsonName = name
            refresh()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.update_model_in_app)
        voicesDir = File(filesDir, FileVoiceStore.VOICES_DIR_NAME).apply { mkdirs() }

        val pad = dp(16)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        modelButton = Button(this).apply {
            setOnClickListener { pickModel.launch(arrayOf("*/*")) }
        }
        jsonButton = Button(this).apply {
            setOnClickListener { pickJson.launch(arrayOf("*/*")) }
        }
        installButton = Button(this).apply {
            text = getString(R.string.update_model_in_app)
            setOnClickListener { install() }
        }
        val hint = TextView(this).apply {
            text = getString(R.string.help_line_5)
            setPadding(0, dp(8), 0, 0)
        }

        root.addView(modelButton)
        root.addView(jsonButton)
        root.addView(installButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(16) })
        root.addView(hint)

        setContentView(ScrollView(this).apply { addView(root) })
        refresh()
    }

    private fun refresh() {
        modelButton.text = getString(
            R.string.import_pick_model_label,
            modelName ?: getString(R.string.onnx_file)
        )
        jsonButton.text = getString(
            R.string.import_pick_json_label,
            jsonName ?: getString(R.string.select_json)
        )
        installButton.isEnabled = modelUri != null && jsonUri != null
    }

    private fun install() {
        val model = modelName ?: return
        val json = jsonName ?: return
        val modelUri = modelUri ?: return
        val jsonUri = jsonUri ?: return
        if (ImportVoiceHelper.configFileNameFor(model) != json) {
            showError(getString(R.string.import_mismatch))
            return
        }
        installButton.isEnabled = false
        Thread {
            try {
                copyUriTo(modelUri, File(voicesDir, model))
                copyUriTo(jsonUri, File(voicesDir, json))
                runOnUiThread {
                    Toast.makeText(this, getString(R.string.import_done,
                        ImportVoiceHelper.voiceKeyFor(model)), Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                Log.w(TAG, "importing voice failed", e)
                runOnUiThread {
                    installButton.isEnabled = true
                    showError(getString(R.string.import_error_copy, e.message ?: "?"))
                }
            }
        }.apply { isDaemon = true; start() }
    }

    private fun showError(message: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.error)
            .setMessage(message)
            .setPositiveButton(R.string.ok, null)
            .show()
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
        private const val TAG = "PiperImport"
    }
}
