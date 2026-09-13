package dev.ihorshevchuk.piper.app.reader

import dev.ihorshevchuk.piper.utils.MarkerRange
import dev.ihorshevchuk.piper.utils.SpeechMarker
import dev.ihorshevchuk.piper.utils.SpeechMarkerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * WordTracker maps a playback position (float32 PCM bytes, the unit of
 * [SpeechMarker.byteOffset]) to the word being spoken.
 */
class WordTrackerTest {

    private fun word(text: String, at: Int, byteOffset: Int) =
        SpeechMarker(MarkerRange(at, text.length), byteOffset, SpeechMarkerType.WORD)

    private fun sentence(at: Int, len: Int, byteOffset: Int) =
        SpeechMarker(MarkerRange(at, len), byteOffset, SpeechMarkerType.SENTENCE)

    @Test
    fun emptyMarkers_returnsNull() {
        assertNull(WordTracker(emptyList()).wordRangeAt(0))
    }

    @Test
    fun beforeFirstWord_returnsNull() {
        val tracker = WordTracker(listOf(word("Hello", 0, 400)))
        assertNull(tracker.wordRangeAt(0))
        assertNull(tracker.wordRangeAt(399))
    }

    @Test
    fun atWordOffset_returnsWord() {
        val tracker = WordTracker(listOf(word("Hello", 0, 400)))
        assertEquals(MarkerRange(0, 5), tracker.wordRangeAt(400))
        assertEquals(MarkerRange(0, 5), tracker.wordRangeAt(10_000))
    }

    @Test
    fun betweenWords_returnsEarlierWord() {
        val tracker = WordTracker(
            listOf(
                word("Hello", 0, 400),
                word("world", 6, 1200)
            )
        )
        assertEquals(MarkerRange(0, 5), tracker.wordRangeAt(400))
        assertEquals(MarkerRange(0, 5), tracker.wordRangeAt(1199))
        assertEquals(MarkerRange(6, 5), tracker.wordRangeAt(1200))
        assertEquals(MarkerRange(6, 5), tracker.wordRangeAt(50_000))
    }

    @Test
    fun sentenceMarkers_areIgnored() {
        val tracker = WordTracker(
            listOf(
                sentence(0, 11, 0),
                word("Hello", 0, 400),
                word("world", 6, 1200)
            )
        )
        assertNull(tracker.wordRangeAt(0))
        assertEquals(MarkerRange(0, 5), tracker.wordRangeAt(400))
    }

    @Test
    fun unsortedMarkers_stillWork() {
        val tracker = WordTracker(
            listOf(
                word("world", 6, 1200),
                word("Hello", 0, 400)
            )
        )
        assertEquals(MarkerRange(6, 5), tracker.wordRangeAt(2000))
        assertEquals(MarkerRange(0, 5), tracker.wordRangeAt(500))
    }
}
