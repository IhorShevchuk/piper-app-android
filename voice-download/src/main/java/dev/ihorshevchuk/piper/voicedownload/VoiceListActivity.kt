package dev.ihorshevchuk.piper.voicedownload

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.ihorshevchuk.piper.engine.PiperCreateOptions
import dev.ihorshevchuk.piper.engine.PiperEngine
import dev.ihorshevchuk.piper.player.PiperPlayer
import dev.ihorshevchuk.piper.tts.EspeakDataInstaller
import dev.ihorshevchuk.piper.tts.FileVoiceStore
import dev.ihorshevchuk.piper.tts.VoiceListContract
import dev.ihorshevchuk.piper.tts.VoicePrefs
import java.io.File
import java.util.Locale

/**
 * Voice list screen, in two modes:
 *
 * - Language mode (launched with [EXTRA_LANGUAGE_CODE] from the unified
 *   main screen): the voices of ONE canonical language, mirroring the
 *   iOS LanguageVoicesView - the "not fully tested" warning ABOVE the
 *   voices and the "large files" warning BELOW (PR #77), "No voices
 *   available" when the language has none. The same screen serves the
 *   "Installed languages" rows and the "All Languages" rows.
 * - Browse mode (no extra; launched from the TTS engine settings via
 *   [VoiceListContract.ACTION_MANAGE_VOICES], as before): installed /
 *   available / all filter + search over the whole catalog.
 *
 * Rows mirror the iOS voice rows: download with a circular progress
 * ring that IS the cancel control (tap it to cancel, PR #71), delete,
 * set-active, preview (installed voices synthesize the sample locally;
 * a not-installed row is action-only - its tap does nothing, while an
 * installed row's tap opens the voice detail in `:app` through
 * [VoiceListContract.ACTION_VOICE_DETAIL]), and a license line when
 * the catalog provides one (PR #62). Row sizes follow the Voice format
 * setting, read when the list binds.
 *
 * Accessibility: the status line is a polite live region; every button
 * names its voice in its content description; download progress exposes
 * "Downloading <voice>, <n> percent" on focus; terminal states (done,
 * failed, deleted, active changed, preview errors) are announced.
 *
 * Plain Views, no Compose - matching the rest of the app.
 */
class VoiceListActivity : Activity() {

    private lateinit var root: LinearLayout
    private lateinit var statusView: TextView
    private lateinit var emptyView: TextView
    private lateinit var warningTopView: TextView
    private lateinit var warningBottomView: TextView
    private lateinit var recycler: RecyclerView
    private lateinit var adapter: VoiceListAdapter

    private lateinit var manager: VoiceDownloadManager
    private lateinit var prefs: VoicePrefs
    private lateinit var voicesDir: File
    private val player = PiperPlayer()

    /** Canonical language code in language mode; null in browse mode. */
    private var languageCode: String? = null
    private var allEntries: List<VoiceCatalogEntry> = emptyList()
    private var installedKeys: Set<String> = emptySet()
    private var filterMode: VoiceFilterMode = VoiceFilterMode.ALL
    private var query: String = ""
    private val rowState = mutableMapOf<String, RowOverlay>()

    @Volatile
    private var previewEngine: PiperEngine? = null
    private var previewThread: Thread? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        languageCode = intent.getStringExtra(EXTRA_LANGUAGE_CODE)
            ?.let { LanguageCodes.canonical(it) }

        voicesDir = File(filesDir, FileVoiceStore.VOICES_DIR_NAME).apply { mkdirs() }
        prefs = VoicePrefs(this)
        manager = VoiceDownloadManager(this)

        buildLayout()
        setContentView(root)
        loadCatalog(forceRefresh = false)
    }

    override fun onResume() {
        super.onResume()
        // Returning from Settings (or a detail screen) can change the
        // Voice format and the installed set: rebind so sizes and row
        // states follow.
        if (::adapter.isInitialized && allEntries.isNotEmpty()) {
            installedKeys = manager.installedKeys()
            applyFilter()
        }
    }

    override fun onDestroy() {
        stopPreview()
        super.onDestroy()
    }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    private fun buildLayout() {
        val pad = dp(16)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        val title = TextView(this).apply {
            text = languageCode?.let { languageTitle(it) }
                ?: getString(R.string.voice_list_title)
            textSize = 20f
        }
        this.title = title.text
        statusView = TextView(this).apply {
            textSize = 14f
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        warningTopView = warningView(R.string.warning_not_tested_voices)
        warningBottomView = warningView(R.string.warning_big_voice_files)

        adapter = VoiceListAdapter(
            onDownload = ::startDownload,
            onCancel = ::cancelDownload,
            onRetry = ::startDownload,
            onDelete = ::confirmDelete,
            onPreview = ::preview,
            onSetActive = ::setActive,
            onRowClick = ::openRow
        )
        recycler = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@VoiceListActivity)
            adapter = this@VoiceListActivity.adapter
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        emptyView = TextView(this).apply {
            text = getString(
                if (languageCode != null) R.string.no_voices_available
                else R.string.voice_list_empty
            )
            textSize = 16f
            visibility = View.GONE
        }

        root.addView(title)
        root.addView(statusView)
        if (languageCode == null) {
            root.addView(filterRow())
            root.addView(searchField())
        }
        root.addView(warningTopView)
        root.addView(recycler)
        root.addView(warningBottomView)
        root.addView(emptyView)
    }

    private fun warningView(textRes: Int): TextView = TextView(this).apply {
        text = getString(textRes)
        textSize = 14f
        setPadding(0, dp(8), 0, dp(8))
        visibility = View.GONE
    }

    private fun filterRow(): RadioGroup {
        val filters = RadioGroup(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val allId = View.generateViewId()
        val installedId = View.generateViewId()
        val availableId = View.generateViewId()
        filters.addView(filterButton(allId, R.string.voice_filter_all))
        filters.addView(filterButton(installedId, R.string.voice_filter_installed))
        filters.addView(filterButton(availableId, R.string.voice_filter_available))
        filters.check(allId)
        filters.setOnCheckedChangeListener { _, checkedId ->
            filterMode = when (checkedId) {
                installedId -> VoiceFilterMode.INSTALLED
                availableId -> VoiceFilterMode.AVAILABLE
                else -> VoiceFilterMode.ALL
            }
            applyFilter()
        }
        return filters
    }

    private fun searchField(): EditText = EditText(this).apply {
        hint = getString(R.string.voice_search_hint)
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString() ?: ""
                applyFilter()
            }
        })
    }

    private fun filterButton(id: Int, textRes: Int): RadioButton =
        RadioButton(this).apply {
            this.id = id
            text = getString(textRes)
        }

    private fun languageTitle(code: String): String {
        val family = LanguageCodes.family(code)
        val region = code.substringAfter('_', "")
        val locale = if (region.isNotEmpty()) Locale(family, region) else Locale(family)
        return locale.getDisplayName(Locale.getDefault()).ifEmpty { code }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    // ------------------------------------------------------------------
    // Catalog + filtering
    // ------------------------------------------------------------------

    private fun loadCatalog(forceRefresh: Boolean) {
        statusView.text = getString(R.string.voice_list_loading_catalog)
        Thread {
            try {
                val entries = manager.fetchCatalog(forceRefresh)
                runOnUiThread {
                    allEntries = entries
                    installedKeys = manager.installedKeys()
                    applyFilter()
                }
            } catch (e: Exception) {
                Log.w(TAG, "catalog load failed", e)
                runOnUiThread {
                    val msg = getString(
                        R.string.voice_list_catalog_error, e.message ?: "?"
                    )
                    statusView.text = msg
                    root.announceForAccessibility(msg)
                }
            }
        }.apply { isDaemon = true; start() }
    }

    private fun applyFilter() {
        val code = languageCode
        val filtered = if (code != null) {
            VoiceListFilter.voicesForLanguage(allEntries, code)
        } else {
            VoiceListFilter.filter(allEntries, installedKeys, filterMode, query)
        }
        val active = prefs.activeVoiceName
        // Sizes follow the Voice format setting (read at bind time, so a
        // change in Settings shows as soon as the user returns here).
        val format = VoiceFormatStore(this).current
        adapter.submitList(filtered.map { entry ->
            val overlay = rowState[entry.key]
            VoiceRow(
                entry = entry,
                isInstalled = entry.key in installedKeys,
                isActive = entry.key == active,
                displaySizeBytes = entry.effectiveTotalSizeBytes(format),
                downloadState = overlay?.state ?: RowDownloadState.IDLE,
                progressPercent = overlay?.percent ?: 0,
                errorMessage = overlay?.error
            )
        })
        val empty = filtered.isEmpty() && allEntries.isNotEmpty()
        emptyView.visibility = if (empty) View.VISIBLE else View.GONE
        recycler.visibility = if (empty) View.GONE else View.VISIBLE
        // Language mode: warnings frame the voices (iOS #77); with no
        // voices the screen shows just the empty text, like iOS.
        val warnings = if (code != null && filtered.isNotEmpty()) View.VISIBLE else View.GONE
        warningTopView.visibility = warnings
        warningBottomView.visibility = warnings
        statusView.text = if (code != null) {
            ""
        } else {
            getString(
                R.string.voice_list_status,
                allEntries.size,
                allEntries.map { it.languageEnglish }.distinct().size,
                installedKeys.size
            )
        }
    }

    /** Installed rows open the voice detail; other rows are action-only. */
    private fun openRow(entry: VoiceCatalogEntry) {
        if (entry.key !in installedKeys) return
        try {
            startActivity(
                Intent(VoiceListContract.ACTION_VOICE_DETAIL)
                    .putExtra(VoiceListContract.EXTRA_VOICE_NAME, entry.key)
            )
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "voice detail screen not available", e)
        }
    }

    // ------------------------------------------------------------------
    // Downloads
    // ------------------------------------------------------------------

    private val downloadListener = object : VoiceDownloadManager.Listener {
        private val lastPercent = mutableMapOf<String, Int>()

        override fun onProgress(entry: VoiceCatalogEntry, bytesDownloaded: Long, totalBytes: Long) {
            val pct = DownloadProgress.percent(bytesDownloaded, totalBytes)
            if (lastPercent[entry.key] == pct) return
            lastPercent[entry.key] = pct
            rowState[entry.key] = RowOverlay(RowDownloadState.DOWNLOADING, pct, null)
            adapter.updateRow(entry.key) {
                it.copy(downloadState = RowDownloadState.DOWNLOADING, progressPercent = pct)
            }
        }

        override fun onComplete(entry: VoiceCatalogEntry) {
            lastPercent.remove(entry.key)
            rowState.remove(entry.key)
            installedKeys = manager.installedKeys()
            if (prefs.activeVoiceName == null) {
                prefs.activeVoiceName = entry.key
            }
            applyFilter()
            root.announceForAccessibility(
                getString(R.string.voice_download_done_announcement, entry.displayName)
            )
        }

        override fun onError(entry: VoiceCatalogEntry, message: String) {
            lastPercent.remove(entry.key)
            rowState[entry.key] = RowOverlay(RowDownloadState.FAILED, 0, message)
            applyFilter()
            root.announceForAccessibility(
                getString(
                    R.string.voice_download_error_announcement,
                    entry.displayName, message
                )
            )
        }
    }

    private fun startDownload(entry: VoiceCatalogEntry) {
        rowState[entry.key] = RowOverlay(RowDownloadState.DOWNLOADING, 0, null)
        applyFilter()
        if (!manager.download(entry, downloadListener)) {
            // Already installed or already downloading; re-sync the row.
            rowState.remove(entry.key)
            applyFilter()
        }
    }

    private fun cancelDownload(entry: VoiceCatalogEntry) {
        manager.cancel(entry.key)
        rowState.remove(entry.key)
        applyFilter()
    }

    private fun confirmDelete(entry: VoiceCatalogEntry) {
        AlertDialog.Builder(this)
            .setTitle(R.string.voice_delete_title)
            .setMessage(getString(R.string.voice_delete_message, entry.displayName))
            .setPositiveButton(R.string.voice_delete_confirm) { _, _ -> deleteVoice(entry) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun deleteVoice(entry: VoiceCatalogEntry) {
        Thread {
            val ok = manager.delete(entry.key)
            runOnUiThread {
                if (!ok) return@runOnUiThread
                installedKeys = manager.installedKeys()
                rowState.remove(entry.key)
                applyFilter()
                root.announceForAccessibility(
                    getString(R.string.voice_deleted_announcement, entry.displayName)
                )
            }
        }.apply { isDaemon = true; start() }
    }

    private fun setActive(entry: VoiceCatalogEntry) {
        prefs.activeVoiceName = entry.key
        applyFilter()
        root.announceForAccessibility(
            getString(R.string.voice_set_active_announcement, entry.displayName)
        )
    }

    // ------------------------------------------------------------------
    // Preview through :piper-player
    // ------------------------------------------------------------------

    private fun preview(entry: VoiceCatalogEntry) {
        stopPreview()
        val model = File(voicesDir, entry.modelFileName)
        if (!model.isFile) {
            val msg = getString(R.string.voice_preview_error_announcement, "not installed")
            root.announceForAccessibility(msg)
            return
        }
        statusView.text = getString(R.string.voice_preview_loading)
        val thread = Thread {
            var engine: PiperEngine? = null
            try {
                // First preview stages espeak-ng-data (~25 MB); later ones reuse it.
                val espeakDir = EspeakDataInstaller.ensure(this@VoiceListActivity)
                val config = File(voicesDir, entry.configFileName).takeIf { it.isFile }
                engine = PiperEngine(
                    PiperCreateOptions(
                        modelPath = model.absolutePath,
                        configPath = config?.absolutePath,
                        espeakDataPath = espeakDir?.takeIf { it.isDirectory }?.absolutePath
                    ),
                    filesDir
                )
                previewEngine = engine
                val text = PreviewSamples.sampleTextFor(resources, entry.languageCode)
                runOnUiThread {
                    statusView.text =
                        getString(R.string.voice_preview_playing, entry.displayName)
                }
                // Async: the player owns its worker thread; the engine must
                // stay open until stopPreview() closes it.
                player.play(engine, text, 1.0f)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                closeQuietly(engine)
            } catch (e: Exception) {
                Log.w(TAG, "preview failed: ${entry.key}", e)
                closeQuietly(engine)
                runOnUiThread {
                    val msg = getString(
                        R.string.voice_preview_error_announcement, e.message ?: "?"
                    )
                    root.announceForAccessibility(msg)
                }
            }
        }
        previewThread = thread
        thread.isDaemon = true
        thread.start()
    }

    private fun stopPreview() {
        previewThread?.interrupt()
        previewThread = null
        try {
            player.stop()
        } catch (_: Exception) {
        }
        closeQuietly(previewEngine)
        previewEngine = null
    }

    private fun closeQuietly(engine: PiperEngine?) {
        try {
            engine?.close()
        } catch (_: Exception) {
        }
    }

    private data class RowOverlay(
        val state: RowDownloadState,
        val percent: Int,
        val error: String?
    )

    companion object {
        /** Canonical language code for language mode (see class doc). */
        const val EXTRA_LANGUAGE_CODE = "language_code"
        private const val TAG = "VoiceListActivity"
    }
}
