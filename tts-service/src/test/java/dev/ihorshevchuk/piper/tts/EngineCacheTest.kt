package dev.ihorshevchuk.piper.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tests for [EngineCache], the process-wide voice engine cache.
 *
 * The cache is generic over the engine type so these tests run on plain
 * JVM without the native piper_jni library: production wires it with
 * PiperEngine, tests with [FakeEngine].
 */
class EngineCacheTest {

    private class FakeEngine : AutoCloseable {
        var closed = false
        val trimLevels = mutableListOf<Int>()
        override fun close() {
            closed = true
        }
    }

    private fun voice(name: String) = VoiceInfo(
        name = name,
        locale = Locale.US,
        modelFile = File("$name.onnx"),
        configFile = null
    )

    private fun cache(
        maxVoices: Int = 2,
        created: AtomicInteger = AtomicInteger(0),
        failOn: Set<String> = emptySet(),
        log: (String) -> Unit = {},
        onTrimMemory: (FakeEngine, Int) -> Unit = { e, level -> e.trimLevels += level }
    ): Pair<EngineCache<FakeEngine>, MutableMap<String, FakeEngine>> {
        val live = mutableMapOf<String, FakeEngine>()
        val c = EngineCache(
            maxVoices = maxVoices,
            load = { v ->
                if (v.name in failOn) throw IllegalStateException("load failed: ${v.name}")
                created.incrementAndGet()
                FakeEngine().also { live[v.name] = it }
            },
            onTrimMemory = onTrimMemory,
            log = log
        )
        return c to live
    }

    @Test
    fun `getOrCreate returns the same instance for the same voice`() {
        val (cache, _) = cache()
        val v = voice("en_US-lessac-medium")
        assertSame(cache.getOrCreate(v), cache.getOrCreate(v))
    }

    @Test
    fun `getOrCreate creates once per voice`() {
        val created = AtomicInteger(0)
        val (cache, _) = cache(created = created)
        cache.getOrCreate(voice("a"))
        cache.getOrCreate(voice("b"))
        cache.getOrCreate(voice("a"))
        assertEquals(2, created.get())
    }

    @Test
    fun `evicts least-recently-used voice beyond maxVoices and closes it`() {
        val (cache, live) = cache(maxVoices = 2)
        val a = voice("a")
        val b = voice("b")
        val c = voice("c")
        cache.getOrCreate(a)
        cache.getOrCreate(b)
        cache.getOrCreate(c) // evicts a
        assertTrue("evicted engine was not closed", live.getValue("a").closed)
        // b and c stay live
        assertSame(live.getValue("b"), cache.getOrCreate(b))
        assertSame(live.getValue("c"), cache.getOrCreate(c))
    }

    @Test
    fun `recency update protects a re-acquired voice from eviction`() {
        val (cache, live) = cache(maxVoices = 2)
        val a = voice("a")
        val b = voice("b")
        cache.getOrCreate(a)
        cache.getOrCreate(b)
        cache.getOrCreate(a) // a is now most recent; b is the victim
        cache.getOrCreate(voice("c")) // evicts b
        assertTrue("b should have been evicted", live.getValue("b").closed)
        assertSame(live.getValue("a"), cache.getOrCreate(a))
    }

    @Test
    fun `evicted voice reloads on next access`() {
        val created = AtomicInteger(0)
        val (cache, _) = cache(maxVoices = 1, created = created)
        val a = voice("a")
        val first = cache.getOrCreate(a)
        cache.getOrCreate(voice("b")) // evicts a
        val second = cache.getOrCreate(a) // reloads
        assertNotSame(first, second)
        assertEquals(3, created.get())
    }

    @Test
    fun `load failure does not poison the cache`() {
        val (cache, _) = cache(failOn = setOf("broken"))
        try {
            cache.getOrCreate(voice("broken"))
            fail("expected load failure")
        } catch (_: IllegalStateException) {
        }
        // A later retry goes through load again instead of caching the failure.
        val created = AtomicInteger(0)
        val (retrying, _) = cache(created = created, failOn = emptySet())
        retrying.getOrCreate(voice("broken"))
        assertEquals(1, created.get())
    }

    @Test
    fun `warm does not block the caller`() {
        val enteredLoad = CountDownLatch(1)
        val releaseLoad = CountDownLatch(1)
        val warmReturned = CountDownLatch(1)
        val slow = EngineCache<FakeEngine>(
            load = {
                enteredLoad.countDown()
                assertTrue(releaseLoad.await(5, TimeUnit.SECONDS))
                FakeEngine()
            }
        )
        val warmer = Thread {
            slow.warm(voice("a"))
            warmReturned.countDown()
        }.apply { isDaemon = true; start() }
        assertTrue("background load never started", enteredLoad.await(5, TimeUnit.SECONDS))
        // warm() must have returned while the background load is still gated.
        assertTrue("warm() blocked until the load finished",
            warmReturned.await(2, TimeUnit.SECONDS))
        releaseLoad.countDown()
        warmer.join(5000)
        // After the background load finishes, the engine is cached.
        assertSame(slow.getOrCreate(voice("a")), slow.getOrCreate(voice("a")))
    }

    @Test
    fun `warm is a no-op for an already cached voice`() {
        val created = AtomicInteger(0)
        val (cache, _) = cache(created = created)
        val v = voice("a")
        cache.getOrCreate(v)
        val done = CountDownLatch(1)
        Thread {
            cache.warm(v)
            done.countDown()
        }.apply { isDaemon = true; start() }
        assertTrue(done.await(5, TimeUnit.SECONDS))
        Thread.sleep(300) // give a redundant load time to (not) happen
        assertEquals(1, created.get())
    }

    @Test
    fun `trimMemory forwards to cached engines`() {
        val (cache, live) = cache()
        cache.getOrCreate(voice("a"))
        cache.getOrCreate(voice("b"))
        cache.trimMemory(80)
        assertEquals(listOf(80), live.getValue("a").trimLevels)
        assertEquals(listOf(80), live.getValue("b").trimLevels)
    }

    @Test
    fun `clear closes every cached engine`() {
        val (cache, live) = cache()
        cache.getOrCreate(voice("a"))
        cache.getOrCreate(voice("b"))
        cache.clear()
        assertTrue(live.getValue("a").closed)
        assertTrue(live.getValue("b").closed)
        // After clear, voices reload on demand.
        val created = AtomicInteger(0)
        val (c2, _) = cache(created = created)
        c2.getOrCreate(voice("a"))
        assertEquals(1, created.get())
    }

    @Test
    fun `log receives a load timing entry`() {
        val messages = mutableListOf<String>()
        val (cache, _) = cache(log = { messages += it })
        cache.getOrCreate(voice("en_US-lessac-medium"))
        assertTrue("expected a timing log, got: $messages",
            messages.any { it.contains("en_US-lessac-medium") && it.contains("ms") })
    }

    @Test
    fun `concurrent getOrCreate for the same voice loads only once`() {
        val enteredLoad = CountDownLatch(1)
        val releaseLoad = CountDownLatch(1)
        val created = AtomicInteger(0)
        val slow = EngineCache<FakeEngine>(
            load = {
                created.incrementAndGet()
                enteredLoad.countDown()
                assertTrue(releaseLoad.await(5, TimeUnit.SECONDS))
                FakeEngine()
            }
        )
        val v = voice("a")
        val results = java.util.Collections.synchronizedList(mutableListOf<FakeEngine>())
        val t1 = Thread { results += slow.getOrCreate(v) }
        val t2 = Thread { results += slow.getOrCreate(v) }
        t1.start()
        t2.start()
        assertTrue("first load never started", enteredLoad.await(5, TimeUnit.SECONDS))
        Thread.sleep(300) // let the second thread arrive at the in-flight wait
        releaseLoad.countDown()
        t1.join(5000)
        t2.join(5000)
        assertEquals("voice loaded more than once", 1, created.get())
        assertEquals(2, results.size)
        assertSame(results[0], results[1])
    }

    @Test
    fun `failed load wakes waiters instead of hanging them`() {
        val attempts = AtomicInteger(0)
        val enteredFirst = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val flaky = EngineCache<FakeEngine>(
            load = {
                if (attempts.incrementAndGet() == 1) {
                    enteredFirst.countDown()
                    assertTrue(releaseFirst.await(5, TimeUnit.SECONDS))
                    throw IllegalStateException("boom")
                }
                FakeEngine()
            }
        )
        val v = voice("a")
        var second: FakeEngine? = null
        val t1 = Thread {
            try {
                flaky.getOrCreate(v)
            } catch (_: IllegalStateException) {
            }
        }
        val t2 = Thread { second = flaky.getOrCreate(v) }
        t1.start()
        assertTrue("first load never started", enteredFirst.await(5, TimeUnit.SECONDS))
        t2.start()
        Thread.sleep(300) // let the second thread block on the in-flight load
        releaseFirst.countDown()
        t1.join(5000)
        t2.join(5000)
        assertTrue("waiter did not recover after the failed load", second != null)
        assertEquals(2, attempts.get())
    }

    @Test
    fun `acquired engine is not evicted while in use`() {
        val (cache, live) = cache(maxVoices = 2)
        val a = cache.acquire(voice("a"))
        cache.getOrCreate(voice("b"))
        cache.getOrCreate(voice("c")) // would evict a, but a is acquired: evicts b
        assertTrue("acquired engine was evicted", !live.getValue("a").closed)
        assertTrue("wrong victim evicted", live.getValue("b").closed)
        assertSame(a, cache.getOrCreate(voice("a")))
        cache.release("a")
        cache.getOrCreate(voice("c")) // touch c so a becomes least-recently-used
        cache.getOrCreate(voice("d")) // now a is evictable again
        assertTrue("released engine was not evicted", live.getValue("a").closed)
    }

    @Test
    fun `release without acquire is a no-op`() {
        val (cache, live) = cache(maxVoices = 1)
        cache.getOrCreate(voice("a"))
        cache.release("a") // never acquired
        cache.release("missing") // never existed
        cache.getOrCreate(voice("b")) // evicts a normally
        assertTrue("eviction broke after stray release", live.getValue("a").closed)
    }
}
