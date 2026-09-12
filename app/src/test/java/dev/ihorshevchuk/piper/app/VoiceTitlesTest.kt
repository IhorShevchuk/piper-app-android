package dev.ihorshevchuk.piper.app

import dev.ihorshevchuk.piper.tts.VoiceInfo
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.util.Locale

class VoiceTitlesTest {

    private fun voice(name: String, locale: Locale) =
        VoiceInfo(name, locale, File("/tmp/$name.onnx"), null)

    @Test
    fun `title uses dataset and language like ios`() {
        val config = VoiceConfig(
            name = "lessac", languageCode = "en_US", languageName = "English",
            countryName = "United States", piperVersion = "1.0.0",
            quality = "medium", sampleRate = 22050,
            speakers = emptyMap(), numSpeakers = 1
        )
        assertEquals(
            "Lessac English",
            VoiceTitles.title(voice("en_US-lessac-medium", Locale("en", "US")), config)
        )
    }

    @Test
    fun `title falls back to voice key without config`() {
        assertEquals(
            "en_US-lessac-medium",
            VoiceTitles.title(voice("en_US-lessac-medium", Locale("en", "US")), null)
        )
    }
}
