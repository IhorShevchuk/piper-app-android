package dev.ihorshevchuk.piper.app

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toolbar
import androidx.activity.ComponentActivity
import dev.ihorshevchuk.piper.tts.FileVoiceStore
import dev.ihorshevchuk.piper.tts.VoiceInfo
import dev.ihorshevchuk.piper.voicedownload.LanguageCodes
import dev.ihorshevchuk.piper.voicedownload.VoiceCatalogEntry
import dev.ihorshevchuk.piper.voicedownload.VoiceDownloadManager
import dev.ihorshevchuk.piper.voicedownload.VoiceListActivity
import dev.ihorshevchuk.piper.voicedownload.VoiceListFilter
import java.io.File
import java.util.Locale

/**
 * App home screen: the unified voice list.
 *
 * - A Search field at the top: flat voice results across all languages
 *   (each row shows its language); tapping an installed result opens its
 *   detail, not-installed results open the language's voices screen
 *   where the download action lives.
 * - "Installed languages": one row per language with installed voices -
 *   the visual label is the language name plus just the count, the
 *   accessibility label is "<language>, Installed voices: N".
 *   Tapping opens that language's voices screen, resolved through
 *   [LanguageCodes.resolve] so a community config code like
 *   "en-gb-x-rp" still lands on the catalog's "en_GB".
 * - "All Languages": one row per catalog language (Japanese excluded),
 *   tapping opens the same per-language voices screen.
 * - Toolbar carries only Help and Settings (Import Model from Files
 *   lives in Settings).
 *
 * Android-only: the Reader entry point stays, as a button under the
 * sections. Plain Views on purpose - no Compose for the app shell.
 */
class MainActivity : ComponentActivity() {

    private lateinit var voicesDir: File
    private lateinit var content: LinearLayout
    private lateinit var searchField: EditText
    private lateinit var manager: VoiceDownloadManager

    private var installedVoices: List<VoiceInfo> = emptyList()
    private var catalogEntries: List<VoiceCatalogEntry> = emptyList()
    private var catalogLoaded = false
    private var catalogFailed = false
    private var query: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        voicesDir = File(filesDir, FileVoiceStore.VOICES_DIR_NAME).apply { mkdirs() }
        manager = VoiceDownloadManager(this)

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
                    R.id.menu_settings -> {
                        startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                        true
                    }
                    else -> false
                }
            }
        }
        root.addView(toolbar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT))

        searchField = EditText(this).apply {
            hint = getString(R.string.search_voices)
            setSingleLine(true)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    query = s?.toString() ?: ""
                    render()
                }
            })
        }
        val searchWrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), 0)
            addView(searchField)
        }
        root.addView(searchWrap)

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(16))
        }
        root.addView(ScrollView(this).apply { addView(content) },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)
        render()
    }

    override fun onResume() {
        super.onResume()
        refreshInstalled()
        loadCatalog()
    }

    // ------------------------------------------------------------------
    // Data
    // ------------------------------------------------------------------

    private fun refreshInstalled() {
        installedVoices = try {
            FileVoiceStore(voicesDir).listVoices()
        } catch (e: Exception) {
            Log.w(TAG, "listing installed voices failed", e)
            emptyList()
        }
    }

    private fun loadCatalog() {
        Thread {
            try {
                val entries = manager.fetchCatalog()
                runOnUiThread {
                    catalogEntries = entries
                    catalogLoaded = true
                    catalogFailed = false
                    render()
                }
            } catch (e: Exception) {
                Log.w(TAG, "catalog load failed", e)
                runOnUiThread {
                    catalogFailed = true
                    render()
                }
            }
        }.apply { isDaemon = true; start() }
    }

    /** Installed voices grouped by canonical config language code. */
    private fun installedGroups(): List<Pair<String, List<VoiceInfo>>> {
        val byLanguage = LinkedHashMap<String, MutableList<VoiceInfo>>()
        for (voice in installedVoices) {
            val config = voice.configFile?.let(VoiceConfigParser::parse)
            val rawCode = config?.languageCode
                ?: voice.name.substringBefore('-')
            byLanguage.getOrPut(LanguageCodes.canonical(rawCode)) { mutableListOf() }
                .add(voice)
        }
        return byLanguage.entries
            .map { it.key to it.value.toList() }
            .sortedBy { languageDisplayName(it.first) }
    }

    // ------------------------------------------------------------------
    // Rendering (the whole content column is rebuilt; lists are small)
    // ------------------------------------------------------------------

    private fun render() {
        if (!::content.isInitialized) return
        content.removeAllViews()
        if (query.trim().isNotEmpty()) {
            renderSearchResults()
        } else {
            renderInstalledSection()
            renderCatalogSection()
        }
        // Android-only Reader entry point, kept under the sections.
        content.addView(Button(this).apply {
            text = getString(R.string.read_text_button)
            setOnClickListener {
                startActivity(Intent(this@MainActivity,
                    dev.ihorshevchuk.piper.app.reader.ReaderActivity::class.java))
            }
            setPadding(0, dp(16), 0, 0)
        })
    }

    private fun renderInstalledSection() {
        if (installedVoices.isEmpty()) {
            content.addView(TextView(this).apply {
                text = getString(R.string.no_voices_message)
                textSize = 16f
                setPadding(0, dp(8), 0, dp(8))
            })
            return
        }
        content.addView(sectionHeader(getString(R.string.installed_languages)))
        val catalogCodes = catalogEntries.map { it.canonicalLanguageCode }.toSet()
        for ((code, voices) in installedGroups()) {
            val name = languageDisplayName(code)
            val count = voices.size
            content.addView(rowView(
                visualText = "$name   $count",
                // Sighted users see just the number; accessibility keeps
                // the full "Installed voices: N".
                accessibilityText = "$name, " +
                    getString(R.string.installed_count_format, count),
                onClick = {
                    startActivity(
                        Intent(this@MainActivity, VoiceListActivity::class.java)
                            .putExtra(
                                VoiceListActivity.EXTRA_LANGUAGE_CODE,
                                LanguageCodes.resolve(code, catalogCodes)
                            )
                    )
                }
            ))
        }
    }

    private fun renderCatalogSection() {
        when {
            catalogFailed && !catalogLoaded -> {
                content.addView(TextView(this).apply {
                    text = getString(R.string.catalog_load_failed)
                    textSize = 16f
                    setPadding(0, dp(12), 0, 0)
                })
                content.addView(Button(this).apply {
                    text = getString(R.string.retry)
                    setOnClickListener { catalogFailed = false; loadCatalog() }
                })
            }
            !catalogLoaded -> {
                content.addView(TextView(this).apply {
                    text = getString(R.string.loading_voices)
                    textSize = 16f
                    setPadding(0, dp(12), 0, 0)
                })
            }
            else -> {
                content.addView(sectionHeader(getString(R.string.all_languages)))
                // One representative entry per language for its display name.
                val representatives = HashMap<String, VoiceCatalogEntry>()
                for (entry in catalogEntries) {
                    representatives.putIfAbsent(entry.canonicalLanguageCode, entry)
                }
                for (code in VoiceListFilter.browsableLanguageCodes(catalogEntries)) {
                    val name = languageDisplayName(code, representatives[code])
                    content.addView(rowView(
                        visualText = name,
                        accessibilityText = name,
                        onClick = {
                            startActivity(
                                Intent(this@MainActivity, VoiceListActivity::class.java)
                                    .putExtra(VoiceListActivity.EXTRA_LANGUAGE_CODE, code)
                            )
                        }
                    ))
                }
            }
        }
    }

    private fun renderSearchResults() {
        if (!catalogLoaded) {
            content.addView(TextView(this).apply {
                text = getString(R.string.loading_voices)
                textSize = 16f
                setPadding(0, dp(8), 0, 0)
            })
            return
        }
        val results = VoiceListFilter.search(catalogEntries, query)
        if (results.isEmpty()) {
            content.addView(TextView(this).apply {
                text = getString(dev.ihorshevchuk.piper.voicedownload.R.string.no_voices_available)
                textSize = 16f
                setPadding(0, dp(8), 0, 0)
            })
            return
        }
        val installedNames = installedVoices.map { it.name }.toSet()
        for (entry in results) {
            val language = entry.languageEnglish.ifEmpty { entry.canonicalLanguageCode }
            val label = "${entry.displayName} — $language"
            val installed = entry.key in installedNames
            content.addView(rowView(
                visualText = label,
                accessibilityText = label,
                onClick = {
                    if (installed) {
                        startActivity(
                            Intent(this@MainActivity, VoiceDetailActivity::class.java)
                                .putExtra(VoiceDetailActivity.EXTRA_VOICE_NAME, entry.key)
                        )
                    } else {
                        // The row itself does not navigate; send the user
                        // to the language screen where the download
                        // action lives.
                        startActivity(
                            Intent(this@MainActivity, VoiceListActivity::class.java)
                                .putExtra(
                                    VoiceListActivity.EXTRA_LANGUAGE_CODE,
                                    entry.canonicalLanguageCode
                                )
                        )
                    }
                }
            ))
        }
    }

    // ------------------------------------------------------------------
    // Small view helpers
    // ------------------------------------------------------------------

    private fun sectionHeader(text: String): TextView = TextView(this).apply {
        this.text = text
        setTypeface(typeface, Typeface.BOLD)
        textSize = 18f
        setPadding(0, dp(12), 0, dp(4))
    }

    private fun rowView(visualText: String, accessibilityText: String, onClick: () -> Unit): View =
        TextView(this).apply {
            text = visualText
            textSize = 17f
            contentDescription = accessibilityText
            isClickable = true
            isFocusable = true
            setPadding(0, dp(10), 0, dp(10))
            setOnClickListener { onClick() }
        }

    /**
     * Display name for a canonical code: the catalog's English name +
     * country when a representative entry is known ("English (United
     * States)"), else the platform locale name for the code.
     */
    private fun languageDisplayName(
        code: String,
        representative: VoiceCatalogEntry? = null
    ): String {
        if (representative != null && representative.languageEnglish.isNotEmpty()) {
            val country = representative.countryEnglish
            return if (country.isNotEmpty()) {
                "${representative.languageEnglish} ($country)"
            } else {
                representative.languageEnglish
            }
        }
        val family = LanguageCodes.family(code)
        val region = code.substringAfter('_', "")
        val locale = if (region.isNotEmpty()) Locale(family, region) else Locale(family)
        return locale.getDisplayName(Locale.getDefault()).ifEmpty { code }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val TAG = "PiperMain"
    }
}
