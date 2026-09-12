package dev.ihorshevchuk.piper.tts

import android.content.Context
import android.util.Log
import java.io.File

/**
 * First-run staging of the espeak-ng data directory the native engine needs.
 *
 * Copies `assets/espeak-ng-data/` to `<files>/espeak-ng-data/` once (a
 * marker file records the completed copy; subsequent starts are a cheap
 * existence check). Stage the data into `app/src/main/assets/espeak-ng-data/`
 * by compiling the vendored espeak-ng, or reuse the iOS app's data directory
 * - see the piper-android README "espeak-ng-data packaging".
 *
 * Returns the staged directory, an already-present install, or null when
 * neither assets nor an install exist (dev builds); the engine then falls
 * back to native auto-discovery.
 */
object EspeakDataInstaller {

    const val DIR_NAME = "espeak-ng-data"
    private const val MARKER_NAME = ".staged"
    private const val TAG = "EspeakDataInstaller"

    fun ensure(context: Context): File? {
        val dest = File(context.filesDir, DIR_NAME)
        if (isStaged(dest)) return dest
        return try {
            if (!copyAssets(context, DIR_NAME, dest)) {
                // No assets to copy - keep whatever install may already exist.
                return if (dest.isDirectory) dest else null
            }
            File(dest, MARKER_NAME).writeText("1")
            Log.i(TAG, "staged espeak-ng-data to ${dest.absolutePath}")
            dest
        } catch (e: Exception) {
            Log.w(TAG, "espeak-ng-data staging failed", e)
            if (dest.isDirectory) dest else null
        }
    }

    private fun isStaged(dest: File): Boolean =
        dest.isDirectory && File(dest, MARKER_NAME).isFile

    /**
     * Recursively copies an assets directory. Returns false when the assets
     * directory does not exist (nothing to stage).
     */
    private fun copyAssets(context: Context, assetDir: String, destDir: File): Boolean {
        val entries = context.assets.list(assetDir) ?: return false
        if (entries.isEmpty()) {
            // Either a leaf file or a missing directory. A top-level call
            // with no entries means the data is not bundled.
            return false
        }
        destDir.mkdirs()
        for (entry in entries) {
            val src = "$assetDir/$entry"
            val childEntries = context.assets.list(src)
            if (childEntries != null && childEntries.isNotEmpty()) {
                if (!copyAssets(context, src, File(destDir, entry))) return false
            } else {
                context.assets.open(src).use { input ->
                    File(destDir, entry).outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
        return true
    }
}
