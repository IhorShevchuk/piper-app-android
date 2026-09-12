package dev.ihorshevchuk.piper.voicedownload

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.security.MessageDigest

/**
 * Failing-first tests for [HttpFileDownloader] against a real local HTTP
 * server: full download, resume via Range, and integrity failures.
 * No Android APIs involved; the server is a hand-rolled ServerSocket loop
 * (the JDK's com.sun.net.httpserver is not on the unit-test classpath).
 */
class HttpFileDownloaderTest {

    private lateinit var server: TinyHttpServer
    private lateinit var dir: File
    private var baseUrl = ""

    private val payload = ByteArray(256 * 1024) { i -> (i % 251).toByte() }
    private val payloadMd5: String = MessageDigest.getInstance("MD5")
        .digest(payload).joinToString("") { "%02x".format(it) }

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("dl-test").toFile()
        server = TinyHttpServer(payload)
        baseUrl = "http://127.0.0.1:${server.port}"
    }

    @After
    fun tearDown() {
        server.stop()
        dir.deleteRecursively()
    }

    @Test
    fun `full download writes dest verifies size and md5 and removes part file`() {
        val dest = File(dir, "voice.onnx")
        val progress = mutableListOf<Pair<Long, Long>>()

        HttpFileDownloader().download(
            url = "$baseUrl/file",
            dest = dest,
            expectedSize = payload.size.toLong(),
            expectedMd5 = payloadMd5,
            onProgress = { d, t -> progress.add(d to t) }
        )

        assertArrayEquals(payload, dest.readBytes())
        assertFalse(File(dir, "voice.onnx.part").exists())
        assertTrue(progress.isNotEmpty())
        assertEquals(payload.size.toLong(), progress.last().first)
        assertEquals(payload.size.toLong(), progress.last().second)
    }

    @Test
    fun `resume appends to existing part file via Range`() {
        val dest = File(dir, "voice.onnx")
        val part = File(dir, "voice.onnx.part")
        val half = payload.size / 2
        part.writeBytes(payload.copyOfRange(0, half))

        HttpFileDownloader().download(
            url = "$baseUrl/file",
            dest = dest,
            expectedSize = payload.size.toLong(),
            expectedMd5 = payloadMd5
        )

        assertArrayEquals(payload, dest.readBytes())
    }

    @Test(expected = java.io.IOException::class)
    fun `size mismatch throws and leaves no dest`() {
        val dest = File(dir, "voice.onnx")
        try {
            HttpFileDownloader().download(
                url = "$baseUrl/file",
                dest = dest,
                expectedSize = payload.size.toLong() + 1,
                expectedMd5 = null
            )
        } finally {
            assertFalse("dest must not exist after size mismatch", dest.exists())
        }
    }

    @Test(expected = java.io.IOException::class)
    fun `md5 mismatch throws and leaves no dest`() {
        val dest = File(dir, "voice.onnx")
        try {
            HttpFileDownloader().download(
                url = "$baseUrl/file",
                dest = dest,
                expectedSize = payload.size.toLong(),
                expectedMd5 = "deadbeefdeadbeefdeadbeefdeadbeef"
            )
        } finally {
            assertFalse("dest must not exist after md5 mismatch", dest.exists())
        }
    }

    @Test(expected = java.io.IOException::class)
    fun `http error throws`() {
        HttpFileDownloader().download(url = "$baseUrl/missing", dest = File(dir, "x.onnx"))
    }

    @Test
    fun `interrupted thread aborts download`() {
        val dest = File(dir, "voice.onnx")
        Thread.currentThread().interrupt()
        try {
            HttpFileDownloader().download(url = "$baseUrl/file", dest = dest)
            throw AssertionError("expected interruption to abort the download")
        } catch (e: java.io.InterruptedIOException) {
            // expected
        } finally {
            Thread.interrupted() // clear flag
        }
    }

    /**
     * Minimal HTTP/1.1 server: GET /file (honors a single Range), anything
     * else 404s. Connection: close, no keep-alive.
     */
    private class TinyHttpServer(private val payload: ByteArray) {
        private val socket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val port: Int get() = socket.localPort

        @Volatile
        private var running = true

        init {
            Thread({
                while (running) {
                    try {
                        handle(socket.accept())
                    } catch (_: Exception) {
                        // Socket closed on stop().
                    }
                }
            }, "tiny-http").apply { isDaemon = true; start() }
        }

        private fun handle(s: Socket) {
            s.use { sock ->
                val reader = sock.getInputStream().bufferedReader()
                val requestLine = reader.readLine() ?: return
                val headers = mutableMapOf<String, String>()
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                    val i = line.indexOf(':')
                    if (i > 0) {
                        headers[line.substring(0, i).trim().lowercase()] =
                            line.substring(i + 1).trim()
                    }
                }
                val out = sock.getOutputStream()
                val path = requestLine.substringAfter(' ').substringBefore(' ')
                if (path != "/file") {
                    out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                    out.flush()
                    return
                }
                val rangeHeader = headers["range"]
                var start = 0L
                var status = "HTTP/1.1 200 OK"
                if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                    val from = rangeHeader.removePrefix("bytes=")
                        .substringBefore("-").toLongOrNull() ?: 0L
                    if (from in 1 until payload.size) {
                        start = from
                        status = "HTTP/1.1 206 Partial Content"
                    }
                }
                val body = payload.copyOfRange(start.toInt(), payload.size)
                val head = buildString {
                    append(status).append("\r\n")
                    append("Content-Type: application/octet-stream\r\n")
                    append("Accept-Ranges: bytes\r\n")
                    append("Content-Length: ${body.size}\r\n")
                    if (start > 0) {
                        append("Content-Range: bytes $start-${payload.size - 1}/${payload.size}\r\n")
                    }
                    append("Connection: close\r\n\r\n")
                }
                out.write(head.toByteArray())
                out.write(body)
                out.flush()
            }
        }

        fun stop() {
            running = false
            socket.close()
        }
    }
}
