package dev.ihorshevchuk.piper.voicedownload

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Failing-first tests for [DownloadProgress] and [CatalogCachePolicy]. */
class DownloadProgressTest {

    @Test
    fun `percent is zero when total is unknown`() {
        assertEquals(0, DownloadProgress.percent(1234, 0))
        assertEquals(0, DownloadProgress.percent(1234, -5))
    }

    @Test
    fun `percent scales linearly and clamps`() {
        assertEquals(0, DownloadProgress.percent(0, 100))
        assertEquals(50, DownloadProgress.percent(50, 100))
        assertEquals(100, DownloadProgress.percent(100, 100))
        // Over-read (retried range overlap) never exceeds 100.
        assertEquals(100, DownloadProgress.percent(150, 100))
    }

    @Test
    fun `percent avoids overflow on large voices`() {
        assertEquals(50, DownloadProgress.percent(Long.MAX_VALUE / 2, Long.MAX_VALUE))
    }
}

class CatalogCachePolicyTest {

    @Test
    fun `fresh within max age`() {
        assertTrue(CatalogCachePolicy.isFresh(lastModifiedMs = 1000, nowMs = 2000, maxAgeMs = 5000))
    }

    @Test
    fun `stale after max age`() {
        assertFalse(CatalogCachePolicy.isFresh(lastModifiedMs = 1000, nowMs = 7000, maxAgeMs = 5000))
    }

    @Test
    fun `missing cache is never fresh`() {
        assertFalse(CatalogCachePolicy.isFresh(lastModifiedMs = 0, nowMs = 1000, maxAgeMs = 5000))
        assertFalse(CatalogCachePolicy.isFresh(lastModifiedMs = -1, nowMs = 1000, maxAgeMs = 5000))
    }

    @Test
    fun `clock skew does not crash`() {
        // lastModified in the future: treat as fresh rather than erroring.
        assertTrue(CatalogCachePolicy.isFresh(lastModifiedMs = 9000, nowMs = 1000, maxAgeMs = 5000))
    }
}
