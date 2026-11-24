package uz.selerown.fevapp.core.model

import kotlinx.serialization.Serializable

/**
 * A video editing project. Immutable: every edit produces a new instance,
 * which is what makes undo/redo cheap and safe.
 *
 * All times in the model are in MICROSECONDS (Long), matching Media3.
 */
@Serializable
data class Project(
    val id: String,
    val name: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val tracks: List<Track>,
    val canvas: CanvasFormat = CanvasFormat.ORIGINAL,
) {
    /** The magnetic main video track. Always present. */
    val mainTrack: Track
        get() = tracks.first { it.type == TrackType.MAIN_VIDEO }

    /** Total project length = length of the main track. */
    val durationUs: Long
        get() = mainTrack.durationUs

    /** Returns a copy where the main track is replaced by [transform]'s result. */
    fun updateMainTrack(transform: (Track) -> Track): Project =
        copy(tracks = tracks.map { if (it.type == TrackType.MAIN_VIDEO) transform(it) else it })

    companion object {
        /** Creates an empty project with one track of every kind. */
        fun empty(id: String, name: String, nowMillis: Long, trackId: (TrackType) -> String): Project =
            Project(
                id = id,
                name = name,
                createdAtMillis = nowMillis,
                updatedAtMillis = nowMillis,
                tracks = TrackType.entries.map { Track(id = trackId(it), type = it, clips = emptyList()) },
            )
    }
}

/** Output frame shape. ORIGINAL follows the first clip. */
@Serializable
enum class CanvasFormat(val widthRatio: Int, val heightRatio: Int) {
    ORIGINAL(0, 0),
    RATIO_9_16(9, 16),
    RATIO_16_9(16, 9),
    RATIO_1_1(1, 1),
    RATIO_4_5(4, 5),
}
