package dev.ihorshevchuk.piper.voicedownload

import android.content.res.Resources
import org.json.JSONObject
import java.util.Locale

/**
 * Spoken preview line for a voice, read from the same `Samples.json` the
 * iOS app bundles (assets/samples.json), keyed by full locale code
 * ("en_US"). Exact-code lookup only, mirroring iOS; anything missing
 * falls back to [R.string.preview_sample_default].
 */
object PreviewSamples {

    /** Pure lookup, unit-testable. */
    fun sampleTextFor(samples: Map<String, String>, locale: Locale, default: String): String =
        samples[locale.toString()] ?: default

    /**
     * Parses a Samples.json document into code -> text. Empty map when
     * malformed. Pure, unit-testable; logging is the caller's job
     * (android.util.Log throws in JVM unit tests).
     */
    fun parseSamplesJson(json: String): Map<String, String> {
        val out = mutableMapOf<String, String>()
        try {
            val root = JSONObject(json)
            val keys = root.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val text = root.optString(key, "")
                if (text.isNotEmpty()) out[key] = text
            }
        } catch (_: Exception) {
        }
        return out
    }

    fun sampleTextFor(resources: Resources, locale: Locale): String {
        val samples = loadSamples(resources)
        return sampleTextFor(
            samples,
            locale,
            resources.getString(R.string.preview_sample_default)
        )
    }

    /** Convenience for catalog entries, which carry the code as a string. */
    fun sampleTextFor(resources: Resources, languageCode: String): String =
        sampleTextFor(resources, localeFromCode(languageCode))

    private fun localeFromCode(code: String): Locale {
        val parts = code.split('_', '-')
        return if (parts.size >= 2) Locale(parts[0], parts[1]) else Locale(parts[0])
    }

    @Volatile
    private var cached: Map<String, String>? = null

    private fun loadSamples(resources: Resources): Map<String, String> {
        cached?.let { return it }
        val parsed = try {
            resources.assets.open("samples.json").use { input ->
                parseSamplesJson(input.bufferedReader().readText())
            }
        } catch (_: Exception) {
            emptyMap()
        }
        cached = parsed
        return parsed
    }
}
