package uz.selerown.fevapp.core.model

import kotlinx.serialization.Serializable

/**
 * A piece of a media file placed on a track.
 *
 * [trimStartUs]..[trimEndUs] is the used window of the SOURCE file.
 * [durationUs] is how long the clip lasts on the timeline (speed applied).
 */
@Serializable
data class Clip(
    val id: String,
    val source: MediaSource,
    val trimStartUs: Long,
    val trimEndUs: Long,
    val speed: Float = 1f,
    val volume: Float = 1f,
    /** Ready-made color look (filter). */
    val look: ClipLook = ClipLook.NONE,
    /** Manual color adjustments, applied after [look]. */
    val color: ColorAdjust = ColorAdjust(),
) {
    val sourceLengthUs: Long
        get() = trimEndUs - trimStartUs

    val durationUs: Long
        get() = (sourceLengthUs / speed).toLong()

    /** Converts a time offset inside this clip (timeline) to a source-file time. */
    fun sourceTimeAt(offsetUs: Long): Long = trimStartUs + (offsetUs * speed).toLong()

    companion object {
        /** Shortest allowed clip on the timeline (0.1 s). */
        const val MIN_DURATION_US: Long = 100_000L

        /** Default length of a photo when it is added. */
        const val DEFAULT_IMAGE_DURATION_US: Long = 3_000_000L

        /** A photo can be stretched up to this length. */
        const val MAX_IMAGE_DURATION_US: Long = 60_000_000L

        const val MIN_SPEED = 0.25f
        const val MAX_SPEED = 4f
    }
}

@Serializable
enum class MediaType { VIDEO, IMAGE, AUDIO }

/** Metadata about an imported file (content:// URI from the Photo Picker). */
@Serializable
data class MediaSource(
    val uri: String,
    val type: MediaType,
    val durationUs: Long,
    val width: Int,
    val height: Int,
)

/** Ready-made color looks. Original names and values (not copied from any app). */
@Serializable
enum class ClipLook { NONE, MONO, NOIR, WARM, COOL, VIVID, FADED }

/** Manual color adjustments, each in -1..1 (0 = unchanged). */
@Serializable
data class ColorAdjust(
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val warmth: Float = 0f,
) {
    val isNeutral: Boolean
        get() = brightness == 0f && contrast == 0f && saturation == 0f && warmth == 0f
}
