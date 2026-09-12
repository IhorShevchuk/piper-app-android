package dev.ihorshevchuk.piper.voicedownload

/** Progress math shared by the downloader and the list UI. */
object DownloadProgress {

    /**
     * Whole-percent progress, 0..100. Returns 0 when the total is unknown
     * and clamps over-reads (e.g. after a resumed range overlap).
     */
    fun percent(bytesDownloaded: Long, totalBytes: Long): Int {
        if (totalBytes <= 0 || bytesDownloaded <= 0) return 0
        // Divide before multiplying to stay in Long range for large voices.
        val pct = bytesDownloaded / (totalBytes / 100.0)
        return pct.coerceIn(0.0, 100.0).toInt()
    }
}

/** Decides whether a cached catalog file is still fresh. Pure clock math. */
object CatalogCachePolicy {

    fun isFresh(lastModifiedMs: Long, nowMs: Long, maxAgeMs: Long): Boolean {
        if (lastModifiedMs <= 0) return false
        val age = nowMs - lastModifiedMs
        // Future timestamps (clock skew): treat as fresh, not an error.
        if (age < 0) return true
        return age < maxAgeMs
    }
}
