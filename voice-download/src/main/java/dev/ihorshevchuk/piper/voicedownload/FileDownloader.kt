package dev.ihorshevchuk.piper.voicedownload

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.net.HttpURLConnection
import java.security.MessageDigest

/**
 * Downloads one file, with resume, size and MD5 verification.
 *
 * Downloads go to `<dest>.part` and are atomically renamed onto [dest] only
 * after verification, so readers of the destination directory (like the TTS
 * service's [dev.ihorshevchuk.piper.tts.FileVoiceStore], which scans for
 * `*.onnx`) never see a partial file.
 */
interface FileDownloader {
    @Throws(IOException::class)
    fun download(
        url: String,
        dest: File,
        expectedSize: Long = 0,
        expectedMd5: String? = null,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit = { _, _ -> }
    )
}

/** [FileDownloader] over [HttpURLConnection]. */
class HttpFileDownloader(
    private val userAgent: String = "PiperAndroid/1.0",
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 60_000
) : FileDownloader {

    @Throws(IOException::class)
    override fun download(
        url: String,
        dest: File,
        expectedSize: Long,
        expectedMd5: String?,
        onProgress: (Long, Long) -> Unit
    ) {
        if (Thread.currentThread().isInterrupted) {
            throw InterruptedIOException("download interrupted before start: $url")
        }
        dest.parentFile?.mkdirs()
        val part = File(dest.parentFile, dest.name + PART_SUFFIX)
        val resumedFrom = if (part.isFile && part.length() > 0) part.length() else 0L

        val conn = open(url, resumedFrom)
        try {
            val response = conn.responseCode
            val append: Boolean
            val total: Long
            when {
                response == HttpURLConnection.HTTP_PARTIAL && resumedFrom > 0 -> {
                    append = true
                    total = if (expectedSize > 0) expectedSize
                    else resumedFrom + conn.contentLengthLong.coerceAtLeast(0)
                }
                response == HttpURLConnection.HTTP_OK -> {
                    // Server ignored our Range (or none sent): restart cleanly.
                    if (resumedFrom > 0) part.delete()
                    append = false
                    total = if (expectedSize > 0) expectedSize
                    else conn.contentLengthLong.takeIf { it > 0 } ?: 0L
                }
                else -> throw IOException("HTTP $response downloading $url")
            }

            var downloaded = if (append) resumedFrom else 0L
            conn.inputStream.use { input ->
                FileOutputStream(part, append).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        if (Thread.currentThread().isInterrupted) {
                            throw InterruptedIOException("download interrupted: $url")
                        }
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        downloaded += n
                        onProgress(downloaded, total)
                    }
                }
            }

            verify(part, expectedSize, expectedMd5, url)

            if (!part.renameTo(dest)) {
                throw IOException("failed to move ${part.name} to ${dest.name}")
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun open(url: String, resumedFrom: Long): HttpURLConnection {
        val conn = java.net.URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = connectTimeoutMs
        conn.readTimeout = readTimeoutMs
        conn.setRequestProperty("User-Agent", userAgent)
        if (resumedFrom > 0) {
            conn.setRequestProperty("Range", "bytes=$resumedFrom-")
        }
        return conn
    }

    private fun verify(part: File, expectedSize: Long, expectedMd5: String?, url: String) {
        if (expectedSize > 0 && part.length() != expectedSize) {
            part.delete()
            throw IOException(
                "size mismatch for $url: expected $expectedSize, got ${part.length()}"
            )
        }
        if (!expectedMd5.isNullOrEmpty()) {
            val actual = md5(part)
            if (!actual.equals(expectedMd5, ignoreCase = true)) {
                part.delete()
                throw IOException("md5 mismatch for $url")
            }
        }
    }

    private fun md5(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        internal const val PART_SUFFIX = ".part"
    }
}
