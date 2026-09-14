package dev.ihorshevchuk.piper.tts

import android.content.Context
import android.util.Log
import dev.ihorshevchuk.piper.engine.PiperCreateOptions
import dev.ihorshevchuk.piper.engine.PiperEngine

/**
 * Process-wide voice engine cache.
 *
 * Loading a voice means parsing the .onnx model and building the ONNX
 * Runtime graph, which costs seconds on slow devices even with the
 * multi-threaded inference fix. The cache keeps the most recently used
 * engines alive across screens, so:
 *
 * - [PiperApp] warms the active voice at app start, off the main thread;
 * - voice detail and reader screens share warmed engines instead of each
 *   paying the model load on open;
 * - revisiting a recently used voice never reloads it.
 *
 * The cache owns its engines: callers must NOT close an engine they got
 * from here. Eviction (LRU) closes the dropped engine quietly. All public
 * methods are thread-safe; [getOrCreate] blocks while the model loads and
 * must never be called on the main thread.
 *
 * An engine handed out by [acquire] is pinned until [release]: loading a
 * third voice never closes an engine that is mid-synthesis on another
 * thread. If every cached engine is acquired, the cache temporarily grows
 * past [maxVoices] instead of closing one out from under its user.
 *
 * Generic over the engine type so the eviction/warm logic is unit-testable
 * on plain JVM without the native piper_jni library (see EngineCacheTest).
 */
class EngineCache<E : AutoCloseable>(
    private val maxVoices: Int = 2,
    private val load: (VoiceInfo) -> E,
    private val onTrimMemory: (E, Int) -> Unit = { _, _ -> },
    private val log: (String) -> Unit = {}
) {
    private val lock = Any()

    /**
     * Access-ordered: the eldest entry is the least recently used.
     * Guarded by [lock].
     */
    private val engines = LinkedHashMap<String, E>(maxVoices, 0.75f, true)

    /**
     * Voices with a load currently running on some thread. The loading
     * thread is the only one that inserts the voice into [engines], so a
     * second caller for the same voice waits instead of loading (and
     * leaking) a duplicate. Guarded by [lock].
     */
    private val loading = HashSet<String>()

    /**
     * Outstanding [acquire] counts per voice. Guarded by [lock].
     */
    private val useCounts = HashMap<String, Int>()

    /**
     * Starts loading [voice] on a daemon thread if it is not cached yet.
     * Returns immediately; failures are logged, never thrown.
     */
    fun warm(voice: VoiceInfo) {
        val alreadyCached = synchronized(lock) { engines.containsKey(voice.name) }
        if (alreadyCached) return
        Thread({
            try {
                getOrCreate(voice)
            } catch (e: Exception) {
                log("warm failed for ${voice.name}: ${e.message}")
            }
        }, "piper-engine-warm").apply { isDaemon = true; start() }
    }

    /**
     * Returns the cached engine for [voice], loading it on first use.
     * A second caller for a voice that is already loading waits for the
     * in-flight load instead of starting (and leaking) a duplicate.
     * The load itself runs outside the cache lock, so a slow model load
     * for one voice does not block access to the others.
     * Must not be called on the main thread: a cold load takes seconds.
     */
    fun getOrCreate(voice: VoiceInfo): E {
        synchronized(lock) {
            engines[voice.name]?.let { return it }
            while (voice.name in loading) {
                (lock as java.lang.Object).wait()
                engines[voice.name]?.let { return it }
            }
            loading.add(voice.name)
        }
        val startedNs = System.nanoTime()
        try {
            val engine = load(voice)
            val loadMs = (System.nanoTime() - startedNs) / 1_000_000
            log("engine load ${voice.name}: ${loadMs}ms " +
                "(model ${(voice.modelFile.length() / 1024 / 1024)}MB)")
            synchronized(lock) {
                loading.remove(voice.name)
                engines[voice.name] = engine
                evictLocked()
                (lock as java.lang.Object).notifyAll()
            }
            return engine
        } catch (e: Exception) {
            synchronized(lock) {
                // Never cache a failure: the next call retries the load,
                // and waiters wake up to retry instead of hanging.
                loading.remove(voice.name)
                (lock as java.lang.Object).notifyAll()
            }
            log("load failed for ${voice.name}: ${e.message}")
            throw e
        }
    }

    /**
     * Like [getOrCreate], but pins the engine until [release]: eviction
     * skips acquired engines, so a third voice loading on another thread
     * cannot close this engine mid-synthesis. Every [acquire] must be
     * paired with [release], ideally in a finally block; an [acquire]
     * whose [load] throws never pins anything.
     * Must not be called on the main thread: a cold load takes seconds.
     */
    fun acquire(voice: VoiceInfo): E {
        while (true) {
            val engine = getOrCreate(voice)
            synchronized(lock) {
                if (engines[voice.name] === engine) {
                    useCounts[voice.name] = (useCounts[voice.name] ?: 0) + 1
                    return engine
                }
                // Lost a race with eviction between the get and the pin:
                // loop around and re-resolve (reloads only if evicted).
            }
        }
    }

    /**
     * Unpins an engine previously returned by [acquire]. Calls without a
     * matching [acquire] are ignored. Never blocks.
     */
    fun release(voiceName: String) {
        synchronized(lock) {
            val remaining = (useCounts[voiceName] ?: 0) - 1
            if (remaining <= 0) useCounts.remove(voiceName)
            else useCounts[voiceName] = remaining
        }
    }

    /** Forwards a memory-trim signal to every cached engine. Never blocks. */
    fun trimMemory(level: Int) = synchronized(lock) {
        engines.values.forEach {
            try {
                onTrimMemory(it, level)
            } catch (_: Exception) {
            }
        }
    }

    /** Closes every cached engine. Used by tests and, rarely, by hand. */
    fun clear() = synchronized(lock) {
        engines.entries.forEach { (name, engine) -> closeQuietly(name, engine) }
        engines.clear()
        useCounts.clear()
    }

    /**
     * Evicts least-recently-used engines down to [maxVoices], closing them.
     * Acquired engines are never evicted; if all of them are in use the
     * cache temporarily grows instead. Callers hold [lock].
     */
    private fun evictLocked() {
        while (engines.size > maxVoices) {
            val victim = engines.entries.firstOrNull { (useCounts[it.key] ?: 0) == 0 }
            if (victim == null) {
                log("cache over capacity ($maxVoices): all engines in use, keeping them")
                return
            }
            engines.remove(victim.key)
            closeQuietly(victim.key, victim.value)
        }
    }

    private fun closeQuietly(name: String, engine: E) {
        try {
            engine.close()
        } catch (e: Exception) {
            log("close failed for $name: ${e.message}")
        }
    }
}

/**
 * Production wiring of [EngineCache] with [PiperEngine].
 *
 * Holds the application context only, never an activity. The load path is
 * instrumented: espeak-ng data staging and the ONNX model load are timed
 * separately in logcat, so a slow first-voice load can be attributed
 * instead of guessed at.
 */
object EngineCacheHolder {
    @Volatile
    private var instance: EngineCache<PiperEngine>? = null

    fun get(context: Context): EngineCache<PiperEngine> =
        instance ?: synchronized(this) {
            instance ?: build(context.applicationContext).also { instance = it }
        }

    /** Returns the cache if it was already created, without creating it. */
    fun peek(): EngineCache<PiperEngine>? = instance

    private fun build(appContext: Context): EngineCache<PiperEngine> {
        val filesDir = appContext.filesDir
        return EngineCache(
            load = { voice ->
                val stageStartNs = System.nanoTime()
                val espeakDir = EspeakDataInstaller.ensure(appContext)
                val stageMs = (System.nanoTime() - stageStartNs) / 1_000_000
                val modelStartNs = System.nanoTime()
                PiperEngine(
                    PiperCreateOptions(
                        modelPath = voice.modelFile.absolutePath,
                        // null configPath -> native uses modelPath + ".json", like iOS.
                        configPath = voice.configFile?.absolutePath,
                        espeakDataPath = espeakDir?.takeIf { it.isDirectory }?.absolutePath
                    ),
                    filesDir
                ).also {
                    val modelMs = (System.nanoTime() - modelStartNs) / 1_000_000
                    Log.i(TAG, "load ${voice.name}: " +
                        "espeak-stage=${stageMs}ms model=${modelMs}ms")
                }
            },
            onTrimMemory = { engine, level -> engine.onTrimMemory(level) },
            log = { message -> Log.i(TAG, message) }
        )
    }

    private const val TAG = "PiperEngineCache"
}
