package dev.ihorshevchuk.piper.voicedownload

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal HTTP GET abstraction, injectable so [VoiceCatalog] stays
 * unit-testable without a network.
 */
interface UrlFetcher {
    @Throws(IOException::class)
    fun fetchText(url: String): String
}

/** [UrlFetcher] over [HttpURLConnection] with sane timeouts. */
class HttpUrlFetcher(
    private val userAgent: String = "PiperAndroid/1.0",
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 30_000
) : UrlFetcher {

    @Throws(IOException::class)
    override fun fetchText(url: String): String {
        val conn = open(url)
        try {
            checkResponse(conn, url)
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    internal fun open(url: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = connectTimeoutMs
        conn.readTimeout = readTimeoutMs
        conn.setRequestProperty("User-Agent", userAgent)
        return conn
    }

    internal fun checkResponse(conn: HttpURLConnection, url: String) {
        if (conn.responseCode != HttpURLConnection.HTTP_OK) {
            throw IOException("HTTP ${conn.responseCode} fetching $url")
        }
    }
}
