package dev.ihorshevchuk.piper.voicedownload

import android.content.res.Resources

/**
 * Spoken preview line for a voice, picked by language family
 * (the part of the catalog language code before '_').
 *
 * The lines live in string resources so they can be localized like any
 * other user-facing string; they are heard rather than shown.
 */
object PreviewSamples {

    fun sampleTextFor(resources: Resources, languageCode: String): String {
        val family = languageCode.substringBefore('_').lowercase()
        val resId = when (family) {
            "uk" -> R.string.preview_sample_uk
            "de" -> R.string.preview_sample_de
            "fr" -> R.string.preview_sample_fr
            "es" -> R.string.preview_sample_es
            "pt" -> R.string.preview_sample_pt
            "it" -> R.string.preview_sample_it
            "ru" -> R.string.preview_sample_ru
            "zh" -> R.string.preview_sample_zh
            "ar" -> R.string.preview_sample_ar
            else -> R.string.preview_sample_default
        }
        return resources.getString(resId)
    }
}
