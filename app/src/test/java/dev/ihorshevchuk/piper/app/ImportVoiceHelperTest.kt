package dev.ihorshevchuk.piper.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportVoiceHelperTest {

    @Test
    fun `model file name is recognized`() {
        assertTrue(ImportVoiceHelper.isModelFileName("en_US-lessac-medium.onnx"))
    }

    @Test
    fun `config file name is not a model`() {
        assertFalse(ImportVoiceHelper.isModelFileName("en_US-lessac-medium.onnx.json"))
    }

    @Test
    fun `bare extension is not a model`() {
        assertFalse(ImportVoiceHelper.isModelFileName(".onnx"))
    }

    @Test
    fun `other extensions are not models`() {
        assertFalse(ImportVoiceHelper.isModelFileName("voice.wav"))
        assertFalse(ImportVoiceHelper.isModelFileName("voice.onnx.txt"))
    }

    @Test
    fun `config file name is recognized`() {
        assertTrue(ImportVoiceHelper.isConfigFileName("en_US-lessac-medium.onnx.json"))
    }

    @Test
    fun `model file name is not a config`() {
        assertFalse(ImportVoiceHelper.isConfigFileName("en_US-lessac-medium.onnx"))
    }

    @Test
    fun `config file name is derived from model file name`() {
        assertEquals(
            "en_US-lessac-medium.onnx.json",
            ImportVoiceHelper.configFileNameFor("en_US-lessac-medium.onnx")
        )
    }

    @Test
    fun `voice key strips the model extension`() {
        assertEquals(
            "en_US-lessac-medium",
            ImportVoiceHelper.voiceKeyFor("en_US-lessac-medium.onnx")
        )
    }
}
