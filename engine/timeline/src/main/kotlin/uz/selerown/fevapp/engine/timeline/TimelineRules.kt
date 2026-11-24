package uz.selerown.fevapp.engine.timeline

import uz.selerown.fevapp.core.model.Clip
import uz.selerown.fevapp.core.model.Track

/** Where a timeline time falls on a track. */
data class ClipPosition(
    val index: Int,
    val clip: Clip,
    /** Timeline time at which the clip starts. */
    val startUs: Long,
    /** Offset of the requested time inside the clip. */
    val offsetUs: Long,
)

/** Shared math and limits for timeline edits. */
object TimelineRules {

    /** Smallest source window allowed for [clip] (respects speed). */
    fun minSourceLengthUs(clip: Clip): Long = (Clip.MIN_DURATION_US * clip.speed).toLong()

    /**
     * Clamps a requested trim window to valid values: inside the source file
     * and never shorter than [Clip.MIN_DURATION_US] on the timeline.
     */
    fun clampTrim(clip: Clip, startUs: Long, endUs: Long): Pair<Long, Long> {
        val minLen = minSourceLengthUs(clip)
        val maxEnd = clip.source.durationUs
        val end = endUs.coerceIn(minLen, maxEnd)
        val start = startUs.coerceIn(0L, end - minLen)
        return start to end
    }

    /**
     * Finds the clip under [timeUs] on a magnetic track. A time exactly on a
     * boundary belongs to the clip that starts there. Returns null when the
     * time is outside the track.
     */
    fun clipAt(track: Track, timeUs: Long): ClipPosition? {
        if (timeUs < 0) return null
        var start = 0L
        track.clips.forEachIndexed { index, clip ->
            val end = start + clip.durationUs
            if (timeUs < end) return ClipPosition(index, clip, start, timeUs - start)
            start = end
        }
        return null
    }
}
