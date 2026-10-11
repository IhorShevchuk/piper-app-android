package dev.ihorshevchuk.piper.voicedownload

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [DownloadSessions]: a cancelled download and its
 * replacement cannot corrupt each other.
 */
class DownloadSessionsTest {

    @Test
    fun `begin registers the session as current`() {
        val sessions = DownloadSessions()
        val token = sessions.begin("en_US-lessac-medium")

        assertTrue(sessions.isDownloading("en_US-lessac-medium"))
        assertTrue(sessions.isCurrent("en_US-lessac-medium", token))
        assertEquals(token, sessions.current("en_US-lessac-medium"))
        assertFalse(sessions.isCancelled(token))
    }

    @Test
    fun `replacement supersedes the cancelled session`() {
        val sessions = DownloadSessions()
        val first = sessions.begin("en_US-lessac-medium")
        sessions.cancel("en_US-lessac-medium")
        val second = sessions.begin("en_US-lessac-medium")

        assertTrue(sessions.isCancelled(first))
        assertFalse(sessions.isCancelled(second))
        assertFalse(sessions.isCurrent("en_US-lessac-medium", first))
        assertTrue(sessions.isCurrent("en_US-lessac-medium", second))
    }

    @Test
    fun `stale teardown cannot end the replacement session`() {
        val sessions = DownloadSessions()
        val first = sessions.begin("en_US-lessac-medium")
        sessions.cancel("en_US-lessac-medium")
        val second = sessions.begin("en_US-lessac-medium")

        // The cancelled session's finally-block runs late:
        assertFalse(sessions.end("en_US-lessac-medium", first))

        // The replacement is untouched.
        assertTrue(sessions.isDownloading("en_US-lessac-medium"))
        assertTrue(sessions.isCurrent("en_US-lessac-medium", second))

        // And its own teardown works exactly once.
        assertTrue(sessions.end("en_US-lessac-medium", second))
        assertFalse(sessions.isDownloading("en_US-lessac-medium"))
    }

    @Test
    fun `cancel with nothing downloading returns null`() {
        val sessions = DownloadSessions()
        assertNull(sessions.cancel("en_US-lessac-medium"))
        assertFalse(sessions.isDownloading("en_US-lessac-medium"))
    }

    @Test
    fun `cancel marks only that session and keeps it registered until ended`() {
        val sessions = DownloadSessions()
        val token = sessions.begin("en_US-lessac-medium")

        assertEquals(token, sessions.cancel("en_US-lessac-medium"))
        assertTrue(sessions.isCancelled(token))
        // Still registered: the owner ends it from its teardown.
        assertTrue(sessions.isDownloading("en_US-lessac-medium"))
        assertTrue(sessions.end("en_US-lessac-medium", token))
        // Cancel state is cleaned up with the session.
        assertFalse(sessions.isCancelled(token))
    }

    @Test
    fun `sessions for different keys are independent`() {
        val sessions = DownloadSessions()
        val a = sessions.begin("en_US-lessac-medium")
        val b = sessions.begin("uk_UA-ukrainian-medium")

        sessions.cancel("en_US-lessac-medium")
        assertTrue(sessions.isCancelled(a))
        assertFalse(sessions.isCancelled(b))
        assertTrue(sessions.isCurrent("uk_UA-ukrainian-medium", b))
    }
}
