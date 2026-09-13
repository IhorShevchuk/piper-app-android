package dev.ihorshevchuk.piper.app.reader

import dev.ihorshevchuk.piper.utils.MarkerRange
import dev.ihorshevchuk.piper.utils.SpeechMarker
import dev.ihorshevchuk.piper.utils.SpeechMarkerType

/**
 * Maps a playback position to the word being spoken, driving the reader's
 * word highlighting.
 *
 * [playedFloat32Bytes] is the number of float32 PCM bytes played so far -
 * the same unit as [SpeechMarker.byteOffset]. Callers deriving the position
 * from an AudioTrack use `playbackHeadPosition * 4` (mono float32).
 *
 * Pure JVM logic (no Android dependencies) so it stays unit-testable.
 */
class WordTracker(markers: List<SpeechMarker>) {

    private val words: List<SpeechMarker> = markers
        .filter { it.type == SpeechMarkerType.WORD }
        .sortedBy { it.byteOffset }

    /**
     * The range of the word playing at [playedFloat32Bytes]: the last word
     * whose offset is at or before the position. Null before the first word.
     */
    fun wordRangeAt(playedFloat32Bytes: Long): MarkerRange? {
        var current: SpeechMarker? = null
        for (w in words) {
            if (w.byteOffset.toLong() > playedFloat32Bytes) break
            current = w
        }
        return current?.range
    }
}
