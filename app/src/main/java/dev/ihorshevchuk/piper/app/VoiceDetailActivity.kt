package dev.ihorshevchuk.piper.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import dev.ihorshevchuk.piper.engine.PiperCreateOptions
import dev.ihorshevchuk.piper.engine.PiperEngine
import dev.ihorshevchuk.piper.player.PiperPlayer
import dev.ihorshevchuk.piper.tts.EspeakDataInstaller
import dev.ihorshevchuk.piper.tts.FileVoiceStore
import dev.ihorshevchuk.piper.tts.VoiceInfo
import dev.ihorshevchuk.piper.tts.VoicePrefs
import dev.ihorshevchuk.piper.voicedownload.PreviewSamples
import dev.ihorshevchuk.piper.voicedownload.VoiceDownloadManager
import java.io.File

/**
 * Installed-voice detail, mirroring the iOS VoiceView: voice information,
 * sample playback, WAV export, speaker picker for multi-speaker voices,
 * and removal with confirmation.
 *
 * Android addition: "Set as Active Voice", which drives the system TTS
 * service (iOS picks the voice from the VoiceOver rotor instead).
 */
class VoiceDetailActivity : Activity() {

    private lateinit var voice: VoiceInfo
    private var config: VoiceConfig? = null
    private lateinit var prefs: VoicePrefs
    private lateinit var manager: VoiceDownloadManager

    private val player = PiperPlayer()

    @Volatile
    private var engine: PiperEngine? = null
    private var previewThread: Thread? = null
    private var isPlaying = false

    /**
     * Bumped on every play/stop; the loader thread only keeps its engine
     * when its generation is still current, so a stop racing the load
     * cannot leak the engine.
     */
    @Volatile
    private var previewGen = 0

    private var selectedSpeakerId = 0

    private lateinit var activeBadge: TextView
    private lateinit var sampleInput: EditText
    private lateinit var playButton: Button
    private lateinit var exportButton: Button
    private lateinit var setActiveButton: Button
    private lateinit var statusView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val name = intent.getStringExtra(EXTRA_VOICE_NAME)
        val voicesDir = File(filesDir, FileVoiceStore.VOICES_DIR_NAME)
        val found = name?.let { FileVoiceStore(voicesDir).findVoice(it) }
        if (found == null) {
            finish()
            return
        }
        voice = found
        // The config is optional: without it the info section is skipped,
        // exactly like iOS when ModelInfo is nil.
        config = voice.configFile?.let(VoiceConfigParser::parse)
        prefs = VoicePrefs(this)
        manager = VoiceDownloadManager(this)
        title = config?.name
            ?.replaceFirstChar { it.uppercaseChar() }
            ?.takeIf { it != "Unknown" }
            ?: voice.name

        val pad = dp(16)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        addInfoSection(root)
        addSampleSection(root)
        addActiveSection(root)
        addRemoveSection(root)

        setContentView(ScrollView(this).apply { addView(root) })
        refreshActiveState()
    }

    override fun onDestroy() {
        stopPreview()
        super.onDestroy()
    }

    // ------------------------------------------------------------------
    // Voice information (iOS ModelInfoView, detailed)
    // ------------------------------------------------------------------

    private fun addInfoSection(root: LinearLayout) {
        val info = config ?: return
        root.addView(TextView(this).apply {
            text = getString(R.string.voice_information)
            setTypeface(typeface, Typeface.BOLD)
            textSize = 18f
            setPadding(0, 0, 0, dp(4))
        })
        infoRow(root, R.string.info_model,
            info.name.replaceFirstChar { it.uppercaseChar() })
        if (info.countryName.isNotEmpty()) {
            infoRow(root, R.string.info_country, info.countryName)
        }
        if (info.languageName.isNotEmpty()) {
            infoRow(root, R.string.info_language, info.languageName)
        }
        infoRow(root, R.string.info_version, info.piperVersion)
        info.quality?.let { infoRow(root, R.string.info_quality, it) }
        info.sampleRate?.let { infoRow(root, R.string.info_sample_rate, it.toString()) }
    }

    private fun infoRow(root: LinearLayout, labelRes: Int, value: String) {
        root.addView(TextView(this).apply {
            text = getString(labelRes) + ": " + value
            setPadding(0, dp(2), 0, dp(2))
        })
    }

    // ------------------------------------------------------------------
    // Sample playback + export (iOS playSampleView)
    // ------------------------------------------------------------------

    private fun addSampleSection(root: LinearLayout) {
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12), 0, 0)
        }
        playButton = Button(this).apply {
            text = getString(R.string.play_sample)
            setOnClickListener { togglePreview() }
        }
        exportButton = Button(this).apply {
            text = getString(R.string.export_file)
            setOnClickListener { exportSample() }
        }
        buttons.addView(playButton, LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        buttons.addView(exportButton, LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(buttons)

        val speakers = config?.pickerSpeakers() ?: emptyMap()
        if (speakers.isNotEmpty()) {
            val names = speakers.keys.toList()
            root.addView(TextView(this).apply {
                text = getString(R.string.speaker)
                setPadding(0, dp(8), 0, 0)
            })
            val spinner = Spinner(this).apply {
                adapter = ArrayAdapter(this@VoiceDetailActivity,
                    android.R.layout.simple_spinner_item, names).apply {
                    setDropDownViewResource(
                        android.R.layout.simple_spinner_dropdown_item)
                }
                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(
                        parent: AdapterView<*>?, view: View?,
                        position: Int, id: Long
                    ) {
                        selectedSpeakerId = speakers[names[position]] ?: 0
                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) {
                        selectedSpeakerId = 0
                    }
                }
            }
            root.addView(spinner)
        }

        sampleInput = EditText(this).apply {
            hint = getString(R.string.sample_text_hint)
            minLines = 3
            setText(PreviewSamples.sampleTextFor(resources, voice.locale))
            setPadding(0, dp(8), 0, 0)
        }
        root.addView(sampleInput,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT))
        statusView = TextView(this).apply {
            setPadding(0, dp(4), 0, 0)
        }
        root.addView(statusView)
    }

    private fun sampleText(): String =
        sampleInput.text.toString()
            .ifBlank { PreviewSamples.sampleTextFor(resources, voice.locale) }

    private fun createEngine(): PiperEngine {
        // First preview stages espeak-ng-data; later ones reuse it.
        val espeakDir = EspeakDataInstaller.ensure(this)
        return PiperEngine(
            PiperCreateOptions(
                modelPath = voice.modelFile.absolutePath,
                configPath = voice.configFile?.absolutePath,
                espeakDataPath = espeakDir?.takeIf { it.isDirectory }?.absolutePath
            ),
            filesDir
        )
    }

    private fun togglePreview() {
        if (isPlaying) {
            stopPreview()
            return
        }
        stopPreview()
        isPlaying = true
        val gen = ++previewGen
        statusView.text = getString(R.string.preview_loading)
        playButton.text = getString(R.string.stop)
        val text = sampleText()
        val speakerId = selectedSpeakerId
        val thread = Thread {
            var eng: PiperEngine? = null
            try {
                eng = createEngine()
                if (gen == previewGen) {
                    engine = eng
                } else {
                    // Stopped while loading: drop the engine immediately.
                    closeQuietly(eng)
                    return@Thread
                }
                runOnUiThread { statusView.text = getString(R.string.preview_playing) }
                // Async: the player owns its worker thread; the engine must
                // stay open until stopPreview() closes it.
                player.play(eng, text, 1.0f, speakerId)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                closeQuietly(eng)
            } catch (e: Exception) {
                Log.w(TAG, "preview failed: ${voice.name}", e)
                closeQuietly(eng)
                runOnUiThread {
                    isPlaying = false
                    statusView.text = getString(R.string.preview_error, e.message ?: "?")
                    resetPlayButton()
                }
            }
        }
        previewThread = thread
        thread.isDaemon = true
        thread.start()
    }

    private fun stopPreview() {
        previewGen++ // invalidate any in-flight loader
        previewThread?.interrupt()
        previewThread = null
        try {
            player.stop()
        } catch (_: Exception) {
        }
        closeQuietly(engine)
        engine = null
        if (isPlaying) {
            isPlaying = false
            runOnUiThread { resetPlayButton() }
        }
    }

    private fun resetPlayButton() {
        playButton.text = getString(R.string.play_sample)
        statusView.text = ""
    }

    private fun exportSample() {
        stopPreview()
        exportButton.isEnabled = false
        statusView.text = getString(R.string.preview_loading)
        val text = sampleText()
        val speakerId = selectedSpeakerId
        Thread {
            var eng: PiperEngine? = null
            try {
                eng = createEngine()
                val out = File(File(cacheDir, "exports").apply { mkdirs() },
                    "${voice.name}-sample.wav")
                val options = eng.defaultSynthesizeOptions().copy(speakerId = speakerId)
                eng.synthesizeToFile(text, out.absolutePath, options)
                runOnUiThread { shareExport(out) }
            } catch (e: Exception) {
                Log.w(TAG, "export failed: ${voice.name}", e)
                runOnUiThread {
                    statusView.text = getString(R.string.export_error, e.message ?: "?")
                }
            } finally {
                closeQuietly(eng)
                runOnUiThread {
                    exportButton.isEnabled = true
                    if (statusView.text == getString(R.string.preview_loading)) {
                        statusView.text = ""
                    }
                }
            }
        }.apply { isDaemon = true; start() }
    }

    private fun shareExport(file: File) {
        val uri = FileProvider.getUriForFile(
            this, "${packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/wav"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        statusView.text = ""
        startActivity(Intent.createChooser(intent, getString(R.string.export_share_title)))
    }

    private fun closeQuietly(eng: PiperEngine?) {
        try {
            eng?.close()
        } catch (_: Exception) {
        }
    }

    // ------------------------------------------------------------------
    // Active voice (Android TTS service; iOS uses the VoiceOver rotor)
    // ------------------------------------------------------------------

    private fun addActiveSection(root: LinearLayout) {
        activeBadge = TextView(this).apply {
            text = getString(R.string.active_voice_badge)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(12), 0, 0)
        }
        setActiveButton = Button(this).apply {
            text = getString(R.string.set_as_active_voice)
            setOnClickListener {
                prefs.activeVoiceName = voice.name
                refreshActiveState()
                Toast.makeText(this@VoiceDetailActivity,
                    getString(R.string.voice_set_active, voice.name),
                    Toast.LENGTH_SHORT).show()
            }
        }
        root.addView(activeBadge)
        root.addView(setActiveButton)
    }

    private fun refreshActiveState() {
        val isActive = prefs.activeVoiceName == voice.name
        activeBadge.visibility = if (isActive) View.VISIBLE else View.GONE
        setActiveButton.visibility = if (isActive) View.GONE else View.VISIBLE
    }

    // ------------------------------------------------------------------
    // Remove (iOS uninstall)
    // ------------------------------------------------------------------

    private fun addRemoveSection(root: LinearLayout) {
        root.addView(Button(this).apply {
            text = getString(R.string.uninstall_voice)
            setTextColor(Color.parseColor("#B00020"))
            setOnClickListener { confirmRemove() }
            setPadding(0, dp(8), 0, 0)
        })
    }

    private fun confirmRemove() {
        AlertDialog.Builder(this)
            .setTitle(R.string.uninstall_voice)
            .setMessage(getString(R.string.uninstall_confirm_message, voice.name))
            .setPositiveButton(R.string.uninstall_button) { _, _ ->
                stopPreview()
                if (manager.delete(voice.name)) {
                    Toast.makeText(this, R.string.voice_removed, Toast.LENGTH_SHORT).show()
                }
                finish()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_VOICE_NAME = "voice_name"
        private const val TAG = "PiperVoiceDetail"
    }
}
