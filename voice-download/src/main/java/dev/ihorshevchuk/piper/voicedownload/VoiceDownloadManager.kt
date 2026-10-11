package dev.ihorshevchuk.piper.voicedownload

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import dev.ihorshevchuk.piper.tts.FileVoiceStore
import dev.ihorshevchuk.piper.tts.VoicePrefs
import java.io.File
import java.io.InterruptedIOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * Downloads Piper voices into the directory the TTS service scans
 * ([FileVoiceStore]), and deletes them again.
 *
 * Threading: one background serial executor for all downloads; [Listener]
 * callbacks are posted to the main thread.
 *
 * Download state is session-scoped (iOS PR #72): every attempt gets a
 * token from [DownloadSessions], and progress / completion / error
 * delivery plus cleanup are token-gated, so a cancelled download and
 * its immediate replacement cannot corrupt each other. A cancelled
 * session ends silently - cancelling is not an error (iOS logs it at
 * debug level and the row returns to the download state).
 *
 * Coexistence with [dev.ihorshevchuk.piper.tts.PiperTtsService] (same
 * process, same filesDir):
 * - Files download to `<name>.onnx.part` and are atomically renamed only
 *   after size/MD5 verification, so the service's voice scan never sees a
 *   partial model.
 * - Re-downloading a voice the service currently has loaded is safe: the
 *   native engine reads the file at load time, so the running engine keeps
 *   the old data until the service next loads that voice.
 * - Deleting the active voice clears the active-voice preference; the
 *   service falls back to another installed voice on its next request.
 *   A voice that is downloading cannot be deleted until it finishes or is
 *   cancelled.
 */
class VoiceDownloadManager(
    context: Context,
    private val voicesDir: File = File(context.filesDir, FileVoiceStore.VOICES_DIR_NAME)
        .apply { mkdirs() },
    private val prefs: VoicePrefs = VoicePrefs(context.applicationContext),
    private val catalog: VoiceCatalog = VoiceCatalog(
        cacheFile = File(context.cacheDir, CATALOG_CACHE_FILE)
    ),
    private val downloader: FileDownloader = HttpFileDownloader(),
    private val formatStore: VoiceFormatStore = VoiceFormatStore(context.applicationContext)
) {

    interface Listener {
        fun onProgress(entry: VoiceCatalogEntry, bytesDownloaded: Long, totalBytes: Long)
        fun onComplete(entry: VoiceCatalogEntry)
        fun onError(entry: VoiceCatalogEntry, message: String)
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "voice-download").apply { isDaemon = true }
    }

    /** Session registry: the per-key state, token-gated (see class doc). */
    internal val sessions = DownloadSessions()
    private val futures = ConcurrentHashMap<UUID, Future<*>>()

    /** Catalog entries; serves the disk cache offline. */
    @Throws(java.io.IOException::class)
    fun fetchCatalog(forceRefresh: Boolean = false): List<VoiceCatalogEntry> =
        catalog.getCatalog(forceRefresh)

    /** Keys of fully downloaded voices (`<key>.onnx` present). */
    fun installedKeys(): Set<String> =
        try {
            FileVoiceStore(voicesDir).listVoices().map { it.name }.toSet()
        } catch (e: Exception) {
            Log.w(TAG, "installedKeys failed", e)
            emptySet()
        }

    fun isInstalled(entry: VoiceCatalogEntry): Boolean =
        File(voicesDir, entry.modelFileName).isFile

    fun isDownloading(key: String): Boolean = sessions.isDownloading(key)

    /**
     * Starts downloading [entry]'s model + config in the current format
     * ([VoiceFormatStore.current], read once at start; installed voices
     * keep their format). The model is always stored
     * under the fixed name `<key>.onnx`, whichever build was fetched
     * (same as iOS). Returns false (and calls nothing) when the voice
     * is already installed or already downloading.
     */
    fun download(entry: VoiceCatalogEntry, listener: Listener?): Boolean {
        if (isInstalled(entry)) {
            Log.i(TAG, "already installed: ${entry.key}")
            return false
        }
        if (isDownloading(entry.key)) {
            Log.i(TAG, "already downloading: ${entry.key}")
            return false
        }
        val token = sessions.begin(entry.key)
        val format = formatStore.current
        val future = executor.submit {
            try {
                runDownload(entry, token, format, listener)
                if (sessions.isCurrent(entry.key, token) &&
                    !sessions.isCancelled(token)
                ) {
                    postComplete(entry, listener)
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                onDownloadFailed(entry, token, listener, "cancelled")
            } catch (e: InterruptedIOException) {
                onDownloadFailed(entry, token, listener, "cancelled")
            } catch (e: Exception) {
                Log.w(TAG, "download failed: ${entry.key}", e)
                onDownloadFailed(entry, token, listener, e.message ?: "download failed")
                // The .part file is intentionally left behind for resume.
            } finally {
                // Token-gated teardown: a stale session cannot remove a
                // replacement session's registration.
                sessions.end(entry.key, token)
                futures.remove(token)
            }
        }
        futures[token] = future
        return true
    }

    /**
     * Reports a failed download - unless this session was cancelled or
     * superseded, in which case it ends silently (its replacement owns
     * the row state now).
     */
    private fun onDownloadFailed(
        entry: VoiceCatalogEntry,
        token: UUID,
        listener: Listener?,
        message: String
    ) {
        if (!sessions.isCurrent(entry.key, token)) return
        if (sessions.isCancelled(token)) return
        postError(entry, listener, message)
    }

    /** Cancels the current session for [key]; the partial file is kept for resume. */
    fun cancel(key: String) {
        val token = sessions.cancel(key) ?: return
        futures[token]?.cancel(true)
    }

    /**
     * Deletes an installed voice. Returns false when the voice is
     * downloading (cancel first) or not installed. When the deleted voice
     * was the active one, the active-voice preference is cleared so the TTS
     * service falls back to another installed voice.
     */
    fun delete(key: String): Boolean {
        if (isDownloading(key)) return false
        val model = File(voicesDir, "$key.onnx")
        val config = File(voicesDir, "$key.onnx.json")
        if (!model.isFile && !config.isFile) return false
        model.delete()
        config.delete()
        if (prefs.activeVoiceName == key) {
            prefs.clear()
            Log.i(TAG, "cleared active voice after deleting $key")
        }
        Log.i(TAG, "deleted voice $key")
        return true
    }

    private fun runDownload(
        entry: VoiceCatalogEntry,
        token: UUID,
        format: VoiceFormat,
        listener: Listener?
    ) {
        // A cancel that landed between the config and model files (or
        // before the task even started) stops the session here: the
        // cancelled set is checked before each file, like iOS.
        if (sessions.isCancelled(token)) throw InterruptedIOException("cancelled")

        val total = entry.effectiveTotalSizeBytes(format)
        var base = 0L
        val modelDest = File(voicesDir, entry.modelFileName)
        val configDest = File(voicesDir, entry.configFileName)

        postProgress(entry, token, listener, 0, total)
        downloader.download(
            url = entry.effectiveOnnxUrl(format),
            dest = modelDest,
            // Variants carry no MD5: size-only verification for INT8.
            expectedSize = entry.effectiveOnnxSizeBytes(format),
            expectedMd5 = entry.effectiveOnnxMd5(format),
            onProgress = { d, _ ->
                postProgress(entry, token, listener, d, total)
                base = d
            }
        )
        base = entry.effectiveOnnxSizeBytes(format)
        if (entry.configUrl != null) {
            if (sessions.isCancelled(token)) throw InterruptedIOException("cancelled")
            downloader.download(
                url = entry.configUrl,
                dest = configDest,
                expectedSize = entry.configSizeBytes,
                expectedMd5 = entry.configMd5,
                onProgress = { d, _ -> postProgress(entry, token, listener, base + d, total) }
            )
        }
        // Final flush so the UI lands on 100%.
        postProgress(entry, token, listener, total, total)
        Log.i(TAG, "downloaded ${entry.key}")
    }

    /** Progress is delivered only while [token] is the live, uncancelled session. */
    private fun postProgress(
        entry: VoiceCatalogEntry,
        token: UUID,
        listener: Listener?,
        downloaded: Long,
        total: Long
    ) {
        if (listener == null) return
        if (!sessions.isCurrent(entry.key, token) || sessions.isCancelled(token)) return
        mainHandler.post { listener.onProgress(entry, downloaded, total) }
    }

    private fun postComplete(entry: VoiceCatalogEntry, listener: Listener?) {
        if (listener == null) return
        mainHandler.post { listener.onComplete(entry) }
    }

    private fun postError(entry: VoiceCatalogEntry, listener: Listener?, message: String) {
        if (listener == null) return
        mainHandler.post { listener.onError(entry, message) }
    }

    companion object {
        private const val TAG = "VoiceDownloadManager"
        private const val CATALOG_CACHE_FILE = "voices_catalog.json"
    }
}
