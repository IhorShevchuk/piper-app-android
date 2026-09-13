package dev.ihorshevchuk.piper.tts

import android.content.Context
import android.util.Log
import java.io.File

/**
 * First-run staging of the espeak-ng data directory the native engine needs.
 *
 * Copies `assets/espeak-ng-data/` to `<files>/espeak-ng-data/`. The bundled
 * data is versioned (via `assets/espeak-ng-data/version`, which espeak-ng's
 * build always produces): when the bundled version differs from the staged
 * one, the directory is re-staged into place, so data upgrades are picked
 * up instead of rotting behind a stage-once marker. Stage the data into
 * `app/src/main/assets/espeak-ng-data/` by compiling the vendored espeak-ng,
 * or reuse the iOS app's data directory - see the piper-kotlin README
 * "espeak-ng-data packaging".
 *
 * Returns the staged directory, an already-present install, or null when
 * neither assets nor an install exist (dev builds); the engine then falls
 * back to native auto-discovery.
 */
object EspeakDataInstaller {

    const val DIR_NAME = "espeak-ng-data"
    private const val MARKER_NAME = ".staged"
    private const val VERSION_FILE = "version"

    /**
     * Version recorded when the bundled data carries no version file.
     * Matches the legacy stage-once marker content, so pre-versioning
     * installs are not needlessly re-staged when the data is unversioned.
     */
    private const val UNVERSIONED = "1"
    private const val TAG = "EspeakDataInstaller"

    fun ensure(context: Context): File? {
        val dest = File(context.filesDir, DIR_NAME)
        return try {
            val assetVersion = assetVersion(context)
            if (assetVersion == null) {
                // Data not bundled (dev builds): keep any existing install.
                return if (dest.isDirectory) dest else null
            }
            if (shouldRestage(dest.isDirectory, stagedVersion(dest), assetVersion)) {
                // Stage into a temp dir first so a failed copy never leaves
                // a half-written data directory behind.
                val tmp = File(context.filesDir, "$DIR_NAME.tmp")
                tmp.deleteRecursively()
                try {
                    if (!copyAssets(context, DIR_NAME, tmp)) {
                        return if (dest.isDirectory) dest else null
                    }
                    File(tmp, MARKER_NAME).writeText(assetVersion)
                    dest.deleteRecursively()
                    if (!tmp.renameTo(dest)) {
                        Log.w(TAG, "could not move staged data into place")
                        return if (dest.isDirectory) dest else null
                    }
                    Log.i(TAG, "staged espeak-ng-data version $assetVersion")
                } finally {
                    tmp.deleteRecursively()
                }
            }
            dest
        } catch (e: Exception) {
            Log.w(TAG, "espeak-ng-data staging failed", e)
            if (dest.isDirectory) dest else null
        }
    }

    /**
     * Pure staging decision, unit-testable: re-stage when there is nothing
     * on disk or when the bundled version differs from the staged one.
     */
    internal fun shouldRestage(
        destIsDirectory: Boolean,
        stagedVersion: String?,
        assetVersion: String
    ): Boolean = !destIsDirectory || stagedVersion != assetVersion

    private fun stagedVersion(dest: File): String? =
        File(dest, MARKER_NAME)
            .takeIf { it.isFile }
            ?.readText()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    /**
     * Version of the bundled data, or null when the data is not bundled.
     * Falls back to [UNVERSIONED] when the data is bundled without a
     * version file.
     */
    private fun assetVersion(context: Context): String? {
        if ((context.assets.list(DIR_NAME)?.size ?: 0) == 0) return null
        return try {
            context.assets.open("$DIR_NAME/$VERSION_FILE")
                .bufferedReader().use { it.readText() }
                .trim().takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        } ?: UNVERSIONED
    }

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
