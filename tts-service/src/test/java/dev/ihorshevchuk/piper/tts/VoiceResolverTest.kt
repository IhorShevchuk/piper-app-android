package dev.ihorshevchuk.piper.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.util.Locale

class VoiceResolverTest {

    private fun voice(name: String, lang: String, country: String) =
        VoiceInfo(name, Locale(lang, country), File("/voices/$name.onnx"), null)

    private val enUs = voice("en_US-lessac-medium", "en", "US")
    private val enGb = voice("en_GB-alba-medium", "en", "GB")
    private val ukUa = voice("uk_UA-mysky-medium", "uk", "UA")

    @Test
    fun noVoicesResolvesNull() {
        assertNull(VoiceResolver.resolve(emptyList(), null, "en", "US", null))
    }

    @Test
    fun exactVoiceNameWins() {
        val got = VoiceResolver.resolve(
            listOf(enUs, enGb, ukUa), "en_GB-alba-medium", "uk", "UA", "uk_UA-mysky-medium"
        )
        assertEquals(enGb, got)
    }

    @Test
    fun exactLocaleMatchBeatsLanguageOnly() {
        val got = VoiceResolver.resolve(listOf(enUs, enGb), null, "en", "GB", null)
        assertEquals(enGb, got)
    }

    @Test
    fun languageOnlyMatchWhenCountryMissing() {
        val got = VoiceResolver.resolve(listOf(enUs, ukUa), null, "en", "AU", null)
        assertEquals(enUs, got)
    }

    @Test
    fun preferredVoiceWinsForItsLanguage() {
        val ryan = voice("en_US-ryan-medium", "en", "US")
        val got = VoiceResolver.resolve(
            listOf(enUs, ryan), null, "en", "US", "en_US-ryan-medium"
        )
        assertEquals(ryan, got)
    }

    @Test
    fun preferredVoiceNotForcedOntoOtherLanguage() {
        val got = VoiceResolver.resolve(
            listOf(enUs, ukUa), null, "uk", "UA", "en_US-lessac-medium"
        )
        assertEquals(ukUa, got)
    }

    @Test
    fun fallsBackToPreferredWhenNoLanguageRequested() {
        val got = VoiceResolver.resolve(listOf(enUs, ukUa), null, "", "", "uk_UA-mysky-medium")
        assertEquals(ukUa, got)
    }

    @Test
    fun fallsBackToFirstInstalled() {
        val got = VoiceResolver.resolve(listOf(enUs, ukUa), null, "", "", null)
        assertEquals(enUs, got)
    }

    @Test
    fun unknownRequestedNameFallsBackToLocale() {
        val got = VoiceResolver.resolve(listOf(enUs, ukUa), "de_DE-thorsten-medium", "uk", "UA", null)
        assertEquals(ukUa, got)
    }
}
