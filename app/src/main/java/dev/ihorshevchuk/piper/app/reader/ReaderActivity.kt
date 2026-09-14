package dev.ihorshevchuk.piper.app.reader

import android.graphics.Typeface
import android.os.Bundle
import android.text.Spannable
import android.text.style.BackgroundColorSpan
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.ComponentActivity
import dev.ihorshevchuk.piper.app.R
import dev.ihorshevchuk.piper.tts.EngineCacheHolder
import dev.ihorshevchuk.piper.tts.FileVoiceStore
import dev.ihorshevchuk.piper.tts.VoiceInfo
import dev.ihorshevchuk.piper.tts.VoicePrefs
import dev.ihorshevchuk.piper.utils.MarkerRange
import java.io.File

/**
 * Read-aloud screen: type or paste text, pick a voice and speed, and listen
 * with the spoken word highlighted as it plays.
 *
 * Speed is an Android-style percent (25-220, 100 = normal) mapped through
 * SpeedCurve.lengthScaleForAndroidSpeechRate, so the PT-BR sibilant ceiling
 * (2.2x) always holds. Word ranges come from the engine's word SpeechMarkers
 * via [WordTracker]; TalkBack announces start/finish but not every word.
 *
 * Plain Views, matching the rest of the app shell.
 */
class ReaderActivity : ComponentActivity() {

    private var voices: List<VoiceInfo> = emptyList()
    private var selectedVoice: VoiceInfo? = null
    private var speedPercent = SPEED_DEFAULT

    private lateinit var voiceSpinner: Spinner
    private lateinit var speedSeekBar: SeekBar
    private lateinit var speedLabel: TextView
    private lateinit var textInput: EditText
    private lateinit var playButton: Button
    private lateinit var statusView: TextView

    private val player = ReaderPlayer()
    /**
     * Engines live in the process-wide [EngineCacheHolder] (warmed at app
     * start for the active voice); this screen never creates or closes one
     * directly. Switching voices just warms the newly selected voice.
     */
    private var isPlaying = false
    private var currentSpan: BackgroundColorSpan? = null
    /**
     * Name of the voice whose engine the reader currently pins via
     * [EngineCache.acquire], or null when nothing is pinned. Released in
     * [stopReading] (and therefore in [onDestroy]).
     */
    private var readerVoiceName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.reader_title)

        val voicesDir = File(filesDir, FileVoiceStore.VOICES_DIR_NAME).apply { mkdirs() }
        voices = FileVoiceStore(voicesDir).listVoices()
        val prefs = VoicePrefs(this)
        selectedVoice = prefs.activeVoiceName?.let { name -> voices.find { it.name == name } }
            ?: voices.firstOrNull()

        val pad = dp(16)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        // Voice picker.
        root.addView(TextView(this).apply {
            text = getString(R.string.reader_voice)
            setTypeface(typeface, Typeface.BOLD)
        })
        voiceSpinner = Spinner(this).apply {
            contentDescription = getString(R.string.reader_voice)
        }
        root.addView(voiceSpinner)
        refreshVoiceSpinner()

        // Speed control.
        speedLabel = TextView(this).apply {
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(12), 0, 0)
        }
        root.addView(speedLabel)
        speedSeekBar = SeekBar(this).apply {
            max = SPEED_MAX - SPEED_MIN
            progress = SPEED_DEFAULT - SPEED_MIN
            contentDescription = getString(R.string.reader_speed)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    speedPercent = progress + SPEED_MIN
                    updateSpeedLabel()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        root.addView(speedSeekBar)
        updateSpeedLabel()

        // Text input.
        root.addView(TextView(this).apply {
            text = getString(R.string.reader_text)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(12), 0, dp(4))
        })
        textInput = EditText(this).apply {
            hint = getString(R.string.reader_text_hint)
            minLines = 6
            gravity = android.view.Gravity.TOP
        }
        root.addView(textInput, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        statusView = TextView(this).apply {
            setPadding(0, dp(8), 0, 0)
        }
        root.addView(statusView)

        playButton = Button(this).apply {
            setOnClickListener { toggleReading() }
        }
        root.addView(playButton)
        refreshPlayButton()

        setContentView(ScrollView(this).apply { addView(root) })

        // Warm the engine off the tap path: opening the reader pays the
        // model load in the background, so the first Read starts instantly.
        // (PiperApp already warms the active voice at app start; this
        // covers a non-active preselected voice.)
        selectedVoice?.let { EngineCacheHolder.get(this).warm(it) }
    }

    override fun onDestroy() {
        stopReading()
        // The engine stays in the process-wide cache for the next visit.
        super.onDestroy()
    }

    private fun refreshVoiceSpinner() {
        val names = voices.map { it.name }
        voiceSpinner.adapter = ArrayAdapter(this,
            android.R.layout.simple_spinner_item, names).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        val selected = selectedVoice?.let { names.indexOf(it.name) } ?: -1
        if (selected >= 0) voiceSpinner.setSelection(selected)
        voiceSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val newlySelected = voices.getOrNull(position)
                if (newlySelected != null && newlySelected.name != selectedVoice?.name) {
                    // Warm the newly picked voice off the tap path so the
                    // first Read with it doesn't pay the model load.
                    EngineCacheHolder.get(this@ReaderActivity).warm(newlySelected)
                }
                selectedVoice = newlySelected
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        val enabled = voices.isNotEmpty()
        voiceSpinner.isEnabled = enabled
        if (!enabled) statusView.text = getString(R.string.no_voices_message)
    }

    private fun updateSpeedLabel() {
        speedLabel.text = getString(R.string.reader_speed_value, speedPercent)
        speedSeekBar.contentDescription =
            getString(R.string.reader_speed_announcement, speedPercent)
    }

    private fun refreshPlayButton() {
        playButton.text = getString(if (isPlaying) R.string.stop else R.string.reader_play)
        playButton.contentDescription = playButton.text
    }

    private fun toggleReading() {
        if (isPlaying) {
            stopReading()
            statusView.text = ""
            return
        }
        val voice = selectedVoice
        val text = textInput.text.toString()
        if (voice == null || text.isBlank()) {
            statusView.text = getString(
                if (voice == null) R.string.no_voices_message else R.string.reader_empty_text)
            return
        }
        isPlaying = true
        refreshPlayButton()
        textInput.isEnabled = false
        clearHighlight()
        statusView.text = getString(R.string.preview_loading)
        textInput.announceForAccessibility(getString(R.string.reader_started))

        Thread {
            try {
                // Shared cache: the first Read pays the model load (or the
                // warm already did); repeat taps and revisits do not. Must
                // stay off the main thread on a cold load. The engine is
                // pinned for the whole reading so eviction cannot close it
                // mid-synthesis; the pin is released in stopReading().
                val eng = EngineCacheHolder.get(this@ReaderActivity)
                    .acquire(voice)
                readerVoiceName = voice.name
                runOnUiThread { statusView.text = "" }
                player.play(eng, text, speedPercent, 0, object : ReaderPlayer.Listener {
                    override fun onWord(range: MarkerRange?) = highlight(range)
                    override fun onFinished() {
                        runOnUiThread {
                            val wasPlaying = isPlaying
                            stopReading()
                            statusView.text = ""
                            if (wasPlaying) {
                                textInput.announceForAccessibility(
                                    getString(R.string.reader_finished))
                            }
                        }
                    }
                    override fun onError(e: Exception) {
                        runOnUiThread {
                            stopReading()
                            statusView.text = getString(R.string.reader_error, e.message ?: "?")
                        }
                    }
                })
            } catch (e: Exception) {
                Log.w(TAG, "reader failed", e)
                // The shared engine is left alone: a synthesis failure is
                // usually input-specific, and the cache only holds
                // successfully created engines. Unpin if we pinned.
                releaseReaderEngine()
                runOnUiThread {
                    isPlaying = false
                    refreshPlayButton()
                    textInput.isEnabled = true
                    statusView.text = getString(R.string.reader_error, e.message ?: "?")
                }
            }
        }.apply { isDaemon = true; start() }
    }

    private fun stopReading() {
        if (!isPlaying) return
        isPlaying = false
        try {
            player.stop()
        } catch (_: Exception) {
        }
        // Unpin the shared engine; the cache keeps it warm and evicts it
        // only when idle.
        releaseReaderEngine()
        runOnUiThread {
            clearHighlight()
            textInput.isEnabled = true
            refreshPlayButton()
        }
    }

    /** Releases the reader's [EngineCache.acquire] pin, once. */
    private fun releaseReaderEngine() {
        val name = readerVoiceName ?: return
        readerVoiceName = null
        try {
            EngineCacheHolder.peek()?.release(name)
        } catch (_: Exception) {
        }
    }

    /** Highlights [range] in the text; null clears the highlight. Main thread. */
    private fun highlight(range: MarkerRange?) {
        val editable = textInput.text
        currentSpan?.let { editable.removeSpan(it) }
        currentSpan = null
        if (range != null) {
            val end = (range.location + range.length).coerceAtMost(editable.length)
            if (range.location in 0 until end) {
                BackgroundColorSpan(HIGHLIGHT_COLOR).let { span ->
                    editable.setSpan(span, range.location, end,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    currentSpan = span
                }
            }
        }
    }

    private fun clearHighlight() {
        currentSpan?.let { textInput.text.removeSpan(it) }
        currentSpan = null
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val TAG = "ReaderActivity"
        private const val SPEED_MIN = 25
        private const val SPEED_DEFAULT = 100
        private const val SPEED_MAX = 220
        /** iOS accent #007AFF at ~20% over the text. */
        private const val HIGHLIGHT_COLOR = 0x33007AFF
    }
}
