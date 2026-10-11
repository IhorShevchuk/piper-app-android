package dev.ihorshevchuk.piper.voicedownload

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Sample URL derivation for listen-before-install:
 * the hosted sample.mp3 sits next to the model file, and the fallback
 * is the rhasspy piper-samples speaker_0.mp3 for the same voice.
 */
class VoiceSampleUrlTest {

    private fun entry(
        key: String = "uk_UA-lada-x_low",
        name: String = "lada",
        code: String = "uk_UA",
        quality: String = "x_low",
        onnxUrl: String = "https://huggingface.co/IhorShevchuk/" +
            "piper1-voices-fp16-quantized/resolve/main/uk/uk_UA/lada/x_low/" +
            "uk_UA-lada-x_low.onnx"
    ) = VoiceCatalogEntry(
        key = key,
        displayName = name,
        languageCode = code,
        languageEnglish = "Ukrainian",
        languageNative = "Українська",
        countryEnglish = "Ukraine",
        quality = quality,
        numSpeakers = 1,
        onnxUrl = onnxUrl,
        configUrl = null,
        onnxSizeBytes = 1,
        onnxMd5 = null,
        configSizeBytes = 1,
        configMd5 = null
    )

    @Test
    fun hostedSampleSitsNextToTheModelFile() {
        assertEquals(
            "https://huggingface.co/IhorShevchuk/piper1-voices-fp16-quantized/" +
                "resolve/main/uk/uk_UA/lada/x_low/sample.mp3",
            entry().sampleUrl
        )
    }

    @Test
    fun fallbackIsTheCommunitySpeakerSample() {
        assertEquals(
            "https://rhasspy.github.io/piper-samples/samples/" +
                "uk/uk_UA/lada/x_low/speaker_0.mp3",
            entry().fallbackSampleUrl
        )
    }

    @Test
    fun fallbackUsesCanonicalCodeForRegionalKeys() {
        // Catalog code "en-gb-x-rp" canonicalizes to en_GB;
        // the community path uses the canonical code.
        val rp = entry(
            key = "en_GB-alan-medium",
            name = "alan",
            code = "en-gb-x-rp",
            quality = "medium"
        )
        assertEquals(
            "https://rhasspy.github.io/piper-samples/samples/" +
                "en/en_GB/alan/medium/speaker_0.mp3",
            rp.fallbackSampleUrl
        )
    }
}
