package dev.ihorshevchuk.piper.app

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
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
 * Installed-voice detail, mirroring the iOS VoiceView: sample playback,
 * set-as-active, and delete with confirmation.
 */
class VoiceDetailActivity : Activity() {

    private lateinit var voice: VoiceInfo
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

    private lateinit var activeBadge: TextView
    private lateinit var sampleInput: EditText
    private lateinit var playButton: Button
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
        prefs = VoicePrefs(this)
        manager = VoiceDownloadManager(this)

        val pad = dp(16)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        val nameView = TextView(this).apply {
            text = voice.name
            setTypeface(typeface, Typeface.BOLD)
            textSize = 20f
        }
        val localeView = TextView(this).apply {
            text = voice.locale.displayName
            setPadding(0, 0, 0, dp(4))
        }
        activeBadge = TextView(this).apply {
            text = getString(R.string.active_voice_badge)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, dp(8))
        }

        sampleInput = EditText(this).apply {
            hint = getString(R.string.sample_text_hint)
            minLines = 3
            setText(PreviewSamples.sampleTextFor(resources, voice.locale.language))
        }
        playButton = Button(this).apply {
            text = getString(R.string.play_sample)
            setOnClickListener { togglePreview() }
        }
        statusView = TextView(this).apply {
            setPadding(0, dp(4), 0, dp(4))
        }
        setActiveButton = Button(this).apply {
            text = getString(R.string.set_as_active_voice)
            setOnClickListener { setActive() }
        }
        val deleteButton = Button(this).apply {
            text = getString(R.string.delete_voice)
            setTextColor(Color.parseColor("#B00020"))
            setOnClickListener { confirmDelete() }
        }

        root.addView(nameView)
        root.addView(localeView)
        root.addView(activeBadge)
        root.addView(sampleInput,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(playButton)
        root.addView(statusView)
        root.addView(setActiveButton)
        root.addView(deleteButton)

        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
        refreshActiveState()
    }

    override fun onDestroy() {
        stopPreview()
        super.onDestroy()
    }

    private fun refreshActiveState() {
        val isActive = prefs.activeVoiceName == voice.name
        activeBadge.visibility = if (isActive) View.VISIBLE else View.GONE
        setActiveButton.visibility = if (isActive) View.GONE else View.VISIBLE
    }

    private fun setActive() {
        prefs.activeVoiceName = voice.name
        refreshActiveState()
        Toast.makeText(this, getString(R.string.voice_set_active, voice.name),
            Toast.LENGTH_SHORT).show()
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_voice_confirm_title)
            .setMessage(getString(R.string.delete_voice_confirm_message, voice.name))
            .setPositiveButton(R.string.delete) { _, _ ->
                if (manager.delete(voice.name)) {
                    Toast.makeText(this, R.string.voice_deleted, Toast.LENGTH_SHORT).show()
                }
                finish()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ------------------------------------------------------------------
    // Sample preview (same engine path as the catalog's preview)
    // ------------------------------------------------------------------

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
        val text = sampleInput.text.toString()
            .ifBlank { PreviewSamples.sampleTextFor(resources, voice.locale.language) }
        val thread = Thread {
            var eng: PiperEngine? = null
            try {
                // First preview stages espeak-ng-data; later ones reuse it.
                val espeakDir = EspeakDataInstaller.ensure(this@VoiceDetailActivity)
                eng = PiperEngine(
                    PiperCreateOptions(
                        modelPath = voice.modelFile.absolutePath,
                        configPath = voice.configFile?.absolutePath,
                        espeakDataPath = espeakDir?.takeIf { it.isDirectory }?.absolutePath
                    ),
                    filesDir
                )
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
                player.play(eng, text, 1.0f)
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

    private fun closeQuietly(eng: PiperEngine?) {
        try {
            eng?.close()
        } catch (_: Exception) {
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_VOICE_NAME = "voice_name"
        private const val TAG = "PiperVoiceDetail"
    }
}
