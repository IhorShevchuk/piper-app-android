package dev.ihorshevchuk.piper.voicedownload

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Session-scoped download registry: download state is scoped to a
 * session so cancel + restart cannot corrupt it.
 *
 * The bug it prevents: when all per-download state is keyed by voice key
 * alone, a cancelled download and its immediate replacement share that
 * state - the stale session's late teardown can delete the NEW download's
 * registry entry mid-flight, and a leftover cancelled flag can cancel the
 * new download at its first file.
 *
 * Here every download attempt gets a fresh [UUID] token. Progress /
 * completion / error delivery and cleanup are gated on the token still
 * being the current session for the key; cancelling marks only that
 * session. Pure JVM apart from the concurrent map - unit-testable.
 */
class DownloadSessions {

    private val currentByKey = ConcurrentHashMap<String, UUID>()
    private val cancelled = ConcurrentHashMap.newKeySet<UUID>()

    /** Starts a session for [key]; returns its token. Supersedes any old one. */
    fun begin(key: String): UUID {
        val token = UUID.randomUUID()
        currentByKey[key] = token
        return token
    }

    /** The current session token for [key], or null when idle. */
    fun current(key: String): UUID? = currentByKey[key]

    fun isDownloading(key: String): Boolean = currentByKey.containsKey(key)

    /** True when [token] is still the live session for [key]. */
    fun isCurrent(key: String, token: UUID): Boolean = currentByKey[key] == token

    /**
     * Marks the current session for [key] cancelled and returns its token
     * (null when nothing was downloading). The session stays registered
     * until its owner ends it, so its late callbacks are recognized as a
     * cancelled session's, not a replacement's.
     */
    fun cancel(key: String): UUID? {
        val token = currentByKey[key] ?: return null
        cancelled.add(token)
        return token
    }

    /** Marks [token] cancelled directly (used when the token is known). */
    fun markCancelled(token: UUID) {
        cancelled.add(token)
    }

    fun isCancelled(token: UUID): Boolean = token in cancelled

    /**
     * Ends [token]'s session for [key]. Token-gated: a stale session's
     * teardown cannot remove a replacement session's registration.
     * Returns true when this call actually ended the current session.
     */
    fun end(key: String, token: UUID): Boolean {
        val removed = currentByKey.remove(key, token)
        if (removed) cancelled.remove(token)
        return removed
    }
}
