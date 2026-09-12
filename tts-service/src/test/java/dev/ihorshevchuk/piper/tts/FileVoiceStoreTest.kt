package dev.ihorshevchuk.piper.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Locale

class FileVoiceStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun voiceFile(dir: File, name: String): File {
        val model = File(dir, "$name.onnx")
        model.writeText("fake-onnx")
        return model
    }

    @Test
    fun emptyDirListsNoVoices() {
        val store = FileVoiceStore(temp.root)
        assertTrue(store.listVoices().isEmpty())
    }

    @Test
    fun missingDirListsNoVoices() {
        val store = FileVoiceStore(File(temp.root, "nope"))
        assertTrue(store.listVoices().isEmpty())
    }

    @Test
    fun listsOnnxVoicesWithLocales() {
        voiceFile(temp.root, "en_US-lessac-medium")
        voiceFile(temp.root, "uk_UA-mysky-medium")
        // Not a voice model - ignored.
        File(temp.root, "notes.txt").writeText("hi")

        val voices = FileVoiceStore(temp.root).listVoices()
        assertEquals(2, voices.size)
        val en = voices.find { it.name == "en_US-lessac-medium" }!!
        assertEquals(Locale("en", "US"), en.locale)
        assertEquals("en_US-lessac-medium.onnx", en.modelFile.name)
        val uk = voices.find { it.name == "uk_UA-mysky-medium" }!!
        assertEquals(Locale("uk", "UA"), uk.locale)
    }

    @Test
    fun configFilePickedUpWhenPresent() {
        voiceFile(temp.root, "en_US-lessac-medium")
        val config = File(temp.root, "en_US-lessac-medium.onnx.json")
        config.writeText("{}")

        val voice = FileVoiceStore(temp.root).findVoice("en_US-lessac-medium")!!
        assertEquals(config.absolutePath, voice.configFile?.absolutePath)
    }

    @Test
    fun configFileNullWhenAbsent() {
        voiceFile(temp.root, "en_US-lessac-medium")
        val voice = FileVoiceStore(temp.root).findVoice("en_US-lessac-medium")!!
        assertNull(voice.configFile)
    }

    @Test
    fun findVoiceByName() {
        voiceFile(temp.root, "en_US-lessac-medium")
        val store = FileVoiceStore(temp.root)
        assertEquals("en_US-lessac-medium", store.findVoice("en_US-lessac-medium")?.name)
        assertNull(store.findVoice("de_DE-thorsten-medium"))
    }

    @Test
    fun parseLocaleHandlesCommonNameShapes() {
        assertEquals(Locale("en", "US"), FileVoiceStore.parseLocale("en_US-lessac-medium"))
        assertEquals(Locale("en", "GB"), FileVoiceStore.parseLocale("en_GB-alba-medium"))
        assertEquals(Locale("pt", "BR"), FileVoiceStore.parseLocale("pt_BR-cadu-medium"))
        assertNull(FileVoiceStore.parseLocale("lessac-medium"))
        assertNull(FileVoiceStore.parseLocale("x"))
    }
}
