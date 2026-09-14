package dev.ihorshevchuk.piper.app

import android.app.Application
import android.util.Log
import dev.ihorshevchuk.piper.tts.EngineCacheHolder
import dev.ihorshevchuk.piper.tts.FileVoiceStore
import dev.ihorshevchuk.piper.tts.VoicePrefs
import java.io.File

/**
 * Warms the active voice's engine at app start so the first Play/Read tap
 * anywhere in the UI usually hits an already-loaded model instead of
 * paying the ONNX load on the tap path.
 */
class PiperApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Thread({
            try {
                val store = FileVoiceStore(File(filesDir, FileVoiceStore.VOICES_DIR_NAME))
                val prefs = VoicePrefs(this)
                val voice = prefs.activeVoiceName?.let(store::findVoice)
                    ?: store.listVoices().firstOrNull()
                if (voice != null) {
                    EngineCacheHolder.get(this).warm(voice)
                }
            } catch (e: Exception) {
                Log.w(TAG, "voice warm failed", e)
            }
        }, "piper-app-warm").apply { isDaemon = true; start() }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Forward, don't drop: PiperEngine.onTrimMemory never blocks and
        // queues the release/recreate work on the engine thread, so warmed
        // engines survive trims and stay instant.
        EngineCacheHolder.peek()?.trimMemory(level)
    }

    companion object {
        private const val TAG = "PiperApp"
    }
}
