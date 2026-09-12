package dev.ihorshevchuk.piper.tts

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class PcmConverterTest {

    @Test
    fun emptyInEmptyOut() {
        assertArrayEquals(ByteArray(0), PcmConverter.floatToPcm16Bytes(FloatArray(0)))
    }

    @Test
    fun silenceIsZeroBytes() {
        assertArrayEquals(byteArrayOf(0, 0), PcmConverter.floatToPcm16Bytes(floatArrayOf(0f)))
    }

    @Test
    fun fullScalePositiveIsLittleEndian() {
        // 32767 = 0x7FFF -> [0xFF, 0x7F]
        assertArrayEquals(
            byteArrayOf(0xFF.toByte(), 0x7F.toByte()),
            PcmConverter.floatToPcm16Bytes(floatArrayOf(1f))
        )
    }

    @Test
    fun fullScaleNegativeIsLittleEndian() {
        // -32767 = 0x8001 -> [0x01, 0x80]
        assertArrayEquals(
            byteArrayOf(0x01.toByte(), 0x80.toByte()),
            PcmConverter.floatToPcm16Bytes(floatArrayOf(-1f))
        )
    }

    @Test
    fun outOfRangeSamplesAreClamped() {
        assertArrayEquals(
            PcmConverter.floatToPcm16Bytes(floatArrayOf(1f)),
            PcmConverter.floatToPcm16Bytes(floatArrayOf(2.5f))
        )
        assertArrayEquals(
            PcmConverter.floatToPcm16Bytes(floatArrayOf(-1f)),
            PcmConverter.floatToPcm16Bytes(floatArrayOf(-3f))
        )
    }

    @Test
    fun halfScaleSample() {
        // 0.5 * 32767 = 16383.5 -> 16383 = 0x3FFF -> [0xFF, 0x3F]
        assertArrayEquals(
            byteArrayOf(0xFF.toByte(), 0x3F.toByte()),
            PcmConverter.floatToPcm16Bytes(floatArrayOf(0.5f))
        )
    }
}
