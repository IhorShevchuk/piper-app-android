package dev.ihorshevchuk.piper.voicedownload

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import dev.ihorshevchuk.piper.tts.FileVoiceStore
import dev.ihorshevchuk.piper.tts.VoicePrefs
import java.io.File
import java.io.InterruptedIOException
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
    private val downloader: FileDownloader = HttpFileDownloader()
) {

    interface Listener {
        fun onProgress(entry: VoiceCatalogEntry, bytesDownloaded: Long, totalBytes: Long)
        fun onComplete(entry: VoiceCatalogEntry)
        fun onError(entry: VoiceCatalogEntry, message: String)
    }

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "voice-download").apply { isDaemon = true }
    }
    private val inFlight = ConcurrentHashMap<String, Future<*>>()

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

    fun isDownloading(key: String): Boolean = inFlight.containsKey(key)

    /**
     * Starts downloading [entry]'s model + config. Returns false (and calls
     * nothing) when the voice is already installed or already downloading.
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
        val future = executor.submit {
            try {
                runDownload(entry, listener)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                postError(entry, listener, "cancelled")
            } catch (e: InterruptedIOException) {
                postError(entry, listener, "cancelled")
            } catch (e: Exception) {
                Log.w(TAG, "download failed: ${entry.key}", e)
                postError(entry, listener, e.message ?: "download failed")
                // The .part file is intentionally left behind for resume.
            } finally {
                inFlight.remove(entry.key)
            }
        }
        inFlight[entry.key] = future
        return true
    }

    /** Cancels an in-flight download; the partial file is kept for resume. */
    fun cancel(key: String) {
        inFlight[key]?.cancel(true)
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

    private fun runDownload(entry: VoiceCatalogEntry, listener: Listener?) {
        val total = entry.totalSizeBytes
        var base = 0L
        val modelDest = File(voicesDir, entry.modelFileName)
        val configDest = File(voicesDir, entry.configFileName)

        postProgress(entry, listener, 0, total)
        downloader.download(
            url = entry.onnxUrl,
            dest = modelDest,
            expectedSize = entry.onnxSizeBytes,
            expectedMd5 = entry.onnxMd5,
            onProgress = { d, _ ->
                postProgress(entry, listener, d, total)
                base = d
            }
        )
        base = entry.onnxSizeBytes
        if (entry.configUrl != null) {
            downloader.download(
                url = entry.configUrl,
                dest = configDest,
                expectedSize = entry.configSizeBytes,
                expectedMd5 = entry.configMd5,
                onProgress = { d, _ -> postProgress(entry, listener, base + d, total) }
            )
        }
        // Final flush so the UI lands on 100%.
        postProgress(entry, listener, total, total)
        postComplete(entry, listener)
        Log.i(TAG, "downloaded ${entry.key}")
    }

    private fun postProgress(
        entry: VoiceCatalogEntry,
        listener: Listener?,
        downloaded: Long,
        total: Long
    ) {
        if (listener == null) return
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
