package uz.selerown.fevapp.core.model

import kotlinx.serialization.Serializable

/** Kinds of rows in the timeline. Only MAIN_VIDEO is editable in milestone 1. */
@Serializable
enum class TrackType { MAIN_VIDEO, OVERLAY, TEXT, AUDIO }

/**
 * A row of clips. On the main track clips are "magnetic": they play back to
 * back in list order, so a clip's start time is derived from its position.
 */
@Serializable
data class Track(
    val id: String,
    val type: TrackType,
    val clips: List<Clip>,
) {
    val durationUs: Long
        get() = clips.sumOf { it.durationUs }

    /** Timeline start time of the clip at [index] (main-track semantics). */
    fun startOf(index: Int): Long = clips.take(index).sumOf { it.durationUs }

    fun indexOf(clipId: String): Int = clips.indexOfFirst { it.id == clipId }
}
