package dev.ihorshevchuk.piper.voicedownload

import java.io.File
import java.io.IOException

/**
 * Voice catalog repository: fetches voices.json, caches it on disk, and
 * parses it via [VoiceCatalogParser].
 *
 * Offline behavior: a fresh cache (younger than [maxAgeMs]) is used without
 * touching the network; when the network fails but a stale cache exists,
 * the stale cache is served so the voice list still works offline. Only
 * when there is no cache at all does a network failure surface.
 */
class VoiceCatalog(
    private val source: VoiceCatalogSource = VoiceCatalogSource.DEFAULT,
    private val cacheFile: File,
    private val fetcher: UrlFetcher = HttpUrlFetcher(),
    private val maxAgeMs: Long = 24 * 60 * 60 * 1000L,
    private val clock: () -> Long = System::currentTimeMillis
) {

    @Throws(IOException::class)
    fun getCatalog(forceRefresh: Boolean = false): List<VoiceCatalogEntry> {
        val cached = readCache()
        if (!forceRefresh && cached != null &&
            CatalogCachePolicy.isFresh(cacheFile.lastModified(), clock(), maxAgeMs)
        ) {
            return cached
        }
        return try {
            val json = fetcher.fetchText(source.catalogUrl)
            writeCache(json)
            VoiceCatalogParser.parse(json, source)
        } catch (e: IOException) {
            // Offline with a stale cache: degrade gracefully.
            cached ?: throw e
        } catch (e: Exception) {
            cached ?: throw IOException("failed to parse voice catalog", e)
        }
    }

    private fun readCache(): List<VoiceCatalogEntry>? {
        if (!cacheFile.isFile) return null
        return try {
            VoiceCatalogParser.parse(cacheFile.readText(), source)
        } catch (_: Exception) {
            null
        }
    }

    private fun writeCache(json: String) {
        try {
            cacheFile.parentFile?.mkdirs()
            cacheFile.writeText(json)
        } catch (_: Exception) {
            // Cache is best-effort; the parsed result is still returned.
        }
    }
}
