package dev.ihorshevchuk.piper.voicedownload

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/** Per-row download state for a voice the catalog knows about. */
enum class RowDownloadState { IDLE, DOWNLOADING, FAILED }

/** Streamed-sample state for a not-installed row (iOS play/stop button). */
enum class RowSampleState { IDLE, LOADING, PLAYING }

/** One row in the voice list: catalog entry plus local state. */
data class VoiceRow(
    val entry: VoiceCatalogEntry,
    val isInstalled: Boolean,
    val isActive: Boolean,
    /** Size shown on the row: the entry's size under the current Voice format. */
    val displaySizeBytes: Long = entry.totalSizeBytes,
    val downloadState: RowDownloadState = RowDownloadState.IDLE,
    val progressPercent: Int = 0,
    val errorMessage: String? = null,
    val sampleState: RowSampleState = RowSampleState.IDLE
)

/**
 * RecyclerView adapter for the voice catalog. All rows are built
 * programmatically (no view binding / Compose, matching the rest of the
 * app); every interactive element carries a content description naming the
 * voice, and terminal download states are announced by the activity.
 */
class VoiceListAdapter(
    private val onDownload: (VoiceCatalogEntry) -> Unit,
    private val onCancel: (VoiceCatalogEntry) -> Unit,
    private val onRetry: (VoiceCatalogEntry) -> Unit,
    private val onDelete: (VoiceCatalogEntry) -> Unit,
    private val onPreview: (VoiceCatalogEntry) -> Unit,
    private val onSetActive: (VoiceCatalogEntry) -> Unit,
    private val onSampleClick: (VoiceCatalogEntry) -> Unit = {},
    private val onRowClick: (VoiceCatalogEntry) -> Unit = {}
) : RecyclerView.Adapter<VoiceListAdapter.ViewHolder>() {

    private var rows: List<VoiceRow> = emptyList()

    fun submitList(newRows: List<VoiceRow>) {
        rows = newRows
        notifyDataSetChanged()
    }

    /** Rebinds a single row (e.g. progress ticks) without rebuilding the list. */
    fun updateRow(key: String, update: (VoiceRow) -> VoiceRow) {
        val index = rows.indexOfFirst { it.entry.key == key }
        if (index < 0) return
        rows = rows.toMutableList().also { it[index] = update(it[index]) }
        notifyItemChanged(index)
    }

    override fun getItemCount(): Int = rows.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(
            parent,
            onDownload, onCancel, onRetry, onDelete, onPreview, onSetActive,
            onSampleClick, onRowClick
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(rows[position])
    }

    class ViewHolder(
        parent: ViewGroup,
        private val onDownload: (VoiceCatalogEntry) -> Unit,
        private val onCancel: (VoiceCatalogEntry) -> Unit,
        private val onRetry: (VoiceCatalogEntry) -> Unit,
        private val onDelete: (VoiceCatalogEntry) -> Unit,
        private val onPreview: (VoiceCatalogEntry) -> Unit,
        private val onSetActive: (VoiceCatalogEntry) -> Unit,
        private val onSampleClick: (VoiceCatalogEntry) -> Unit,
        private val onRowClick: (VoiceCatalogEntry) -> Unit
    ) : RecyclerView.ViewHolder(RowView(parent)) {

        private val row = itemView as RowView

        fun bind(rowData: VoiceRow) {
            row.bind(
                rowData, onDownload, onCancel, onRetry, onDelete,
                onPreview, onSetActive, onSampleClick, onRowClick
            )
        }
    }

    /**
     * The row itself: title + subtitle + a rebuilt action row.
     * Kept as a dedicated View so bind logic stays out of the adapter.
     */
    private class RowView(parent: ViewGroup) : LinearLayout(parent.context) {

        private val title: TextView
        private val activeBadge: TextView
        private val subtitle: TextView
        private val licenseLine: TextView
        private val actions: LinearLayout

        init {
            orientation = VERTICAL
            val pad = dp(12)
            setPadding(pad, dp(8), pad, dp(8))

            val titleRow = LinearLayout(context).apply {
                orientation = HORIZONTAL
            }
            title = TextView(context).apply {
                textSize = 16f
                layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            }
            activeBadge = TextView(context).apply {
                textSize = 12f
                visibility = View.GONE
            }
            titleRow.addView(title)
            titleRow.addView(activeBadge)

            subtitle = TextView(context).apply { textSize = 14f }
            licenseLine = TextView(context).apply {
                textSize = 12f
                visibility = View.GONE
            }
            actions = LinearLayout(context).apply {
                orientation = HORIZONTAL
            }

            addView(titleRow)
            addView(subtitle)
            addView(licenseLine)
            addView(actions)
        }

        fun bind(
            rowData: VoiceRow,
            onDownload: (VoiceCatalogEntry) -> Unit,
            onCancel: (VoiceCatalogEntry) -> Unit,
            onRetry: (VoiceCatalogEntry) -> Unit,
            onDelete: (VoiceCatalogEntry) -> Unit,
            onPreview: (VoiceCatalogEntry) -> Unit,
            onSetActive: (VoiceCatalogEntry) -> Unit,
            onSampleClick: (VoiceCatalogEntry) -> Unit,
            onRowClick: (VoiceCatalogEntry) -> Unit
        ) {
            val e = rowData.entry
            title.text = e.displayName
            subtitle.text = context.getString(
                R.string.voice_row_subtitle,
                e.languageEnglish.ifEmpty { e.languageCode },
                e.countryEnglish.ifEmpty { "–" },
                e.quality,
                formatSize(rowData.displaySizeBytes)
            )
            // License line under the size, when the catalog provides a
            // headline license (iOS VoiceItemView, PR #62).
            val license = e.license
            if (!license.isNullOrEmpty()) {
                licenseLine.text = context.getString(R.string.voice_license_line, license)
                licenseLine.visibility = View.VISIBLE
            } else {
                licenseLine.visibility = View.GONE
            }
            // Whole-row tap: the activity decides (installed -> detail,
            // not installed -> nothing, as on iOS). Action buttons keep
            // their own clicks.
            isClickable = true
            isFocusable = true
            setOnClickListener { onRowClick(e) }
            activeBadge.visibility = if (rowData.isActive) View.VISIBLE else View.GONE
            if (rowData.isActive) {
                activeBadge.text = context.getString(R.string.voice_badge_active)
            }

            actions.removeAllViews()
            when {
                rowData.isInstalled -> {
                    actions.addView(actionButton(
                        R.string.voice_action_preview,
                        context.getString(R.string.voice_cd_preview, e.displayName)
                    ) { onPreview(e) })
                    if (!rowData.isActive) {
                        actions.addView(actionButton(
                            R.string.voice_action_set_active,
                            context.getString(R.string.voice_cd_set_active, e.displayName)
                        ) { onSetActive(e) })
                    }
                    actions.addView(actionButton(
                        R.string.voice_action_delete,
                        context.getString(R.string.voice_cd_delete, e.displayName)
                    ) { onDelete(e) })
                }
                rowData.downloadState == RowDownloadState.DOWNLOADING -> {
                    // 1:1 with iOS: the circular progress indicator, not a
                    // horizontal bar - and the ring IS the cancel control
                    // (PR #71): tapping it cancels, its accessibility
                    // label is "Cancel download" and the percent is the
                    // accessibility value. A separate Cancel button stays
                    // next to it for discoverability.
                    val dial = CircularProgressView(context).apply {
                        progress = rowData.progressPercent / 100f
                        contentDescription = context.getString(
                            R.string.cancel_download
                        ) + ": " + context.getString(
                            R.string.voice_cd_downloading,
                            e.displayName, rowData.progressPercent
                        )
                        isClickable = true
                        isFocusable = true
                        setOnClickListener { onCancel(e) }
                        layoutParams = LayoutParams(dp(40), dp(40)).apply {
                            gravity = android.view.Gravity.CENTER_VERTICAL
                        }
                    }
                    val pct = TextView(context).apply {
                        text = context.getString(
                            R.string.voice_download_percent, rowData.progressPercent
                        )
                        textSize = 14f
                        setPadding(dp(8), 0, dp(8), 0)
                    }
                    actions.addView(dial)
                    actions.addView(pct)
                    actions.addView(actionButton(
                        R.string.voice_action_cancel,
                        context.getString(R.string.voice_cd_cancel, e.displayName)
                    ) { onCancel(e) })
                }
                rowData.downloadState == RowDownloadState.FAILED -> {
                    actions.addView(actionButton(
                        R.string.voice_action_retry,
                        context.getString(R.string.voice_cd_retry, e.displayName)
                    ) { onRetry(e) })
                    val err = TextView(context).apply {
                        text = rowData.errorMessage
                        textSize = 12f
                        setPadding(dp(8), 0, 0, 0)
                    }
                    actions.addView(err)
                }
                else -> {
                    actions.addView(actionButton(
                        R.string.voice_action_download,
                        context.getString(R.string.voice_cd_download, e.displayName)
                    ) { onDownload(e) })
                    // Listen before installing (iOS VoiceItemView play
                    // button): streams the hosted sample; while it loads
                    // or plays, the button becomes Stop.
                    if (rowData.sampleState == RowSampleState.IDLE) {
                        actions.addView(actionButton(
                            R.string.voice_action_play_sample,
                            context.getString(R.string.voice_cd_play_sample, e.displayName)
                        ) { onSampleClick(e) })
                    } else {
                        actions.addView(actionButton(
                            R.string.voice_action_stop_sample,
                            context.getString(R.string.voice_cd_stop_sample, e.displayName)
                        ) { onSampleClick(e) })
                    }
                }
            }
        }

        private fun actionButton(
            textRes: Int,
            contentDescription: String,
            onClick: () -> Unit
        ): Button = Button(context).apply {
            text = context.getString(textRes)
            this.contentDescription = contentDescription
            setOnClickListener { onClick() }
            val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            lp.marginEnd = dp(8)
            layoutParams = lp
        }

        private fun formatSize(bytes: Long): String =
            if (bytes > 0) context.getString(
                R.string.voice_size_mb, bytes / (1024.0 * 1024.0)
            )
            else context.getString(R.string.voice_size_unknown)

        private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    }
}
