package dev.ihorshevchuk.piper.tts

/**
 * Converts the engine's float32 mono PCM (-1..1) to 16-bit little-endian
 * PCM bytes for [android.speech.tts.SynthesisCallback.audioAvailable].
 *
 * Pure JVM - unit-testable.
 */
object PcmConverter {

    fun floatToPcm16Bytes(samples: FloatArray): ByteArray {
        val out = ByteArray(samples.size * 2)
        for (i in samples.indices) {
            val v = (samples[i].coerceIn(-1f, 1f) * 32767f).toInt()
                .coerceIn(-32768, 32767)
            out[i * 2] = (v and 0xFF).toByte()
            out[i * 2 + 1] = ((v ushr 8) and 0xFF).toByte()
        }
        return out
    }
}
