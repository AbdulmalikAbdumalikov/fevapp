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
    /** Transition into the NEXT clip (ignored on the last clip). */
    val transition: TransitionType = TransitionType.NONE,
    /** Total transition length, centered on the cut. */
    val transitionUs: Long = DEFAULT_TRANSITION_US,
    /** Crop, rotation and mirror of the picture. */
    val transform: ClipTransform = ClipTransform(),
) {
    /** Width of the picture after crop and rotation (pixels of the source). */
    val displayWidth: Int
        get() = if (transform.quarterTurn) croppedHeight else croppedWidth

    /** Height of the picture after crop and rotation. */
    val displayHeight: Int
        get() = if (transform.quarterTurn) croppedWidth else croppedHeight

    private val croppedWidth: Int
        get() = (source.width * (transform.right - transform.left)).toInt()

    private val croppedHeight: Int
        get() = (source.height * (transform.bottom - transform.top)).toInt()

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

        const val DEFAULT_TRANSITION_US: Long = 600_000L
        const val MIN_TRANSITION_US: Long = 200_000L
        const val MAX_TRANSITION_US: Long = 1_500_000L

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

/** Original, simple transitions: the cut dips through black or white. */
@Serializable
enum class TransitionType { NONE, FADE_BLACK, FADE_WHITE }

/**
 * Picture transform of a clip: crop window ([left]..[right], [top]..[bottom]
 * as fractions of the source), clockwise [rotation] (0, 90, 180, 270) and
 * mirroring. Applied in this order: crop, mirror, rotate.
 */
@Serializable
data class ClipTransform(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f,
    val rotation: Int = 0,
    val flipH: Boolean = false,
    val flipV: Boolean = false,
) {
    val isIdentity: Boolean
        get() = this == ClipTransform()

    val isCropped: Boolean
        get() = left != 0f || top != 0f || right != 1f || bottom != 1f

    /** True for 90 and 270 degrees (width and height swap). */
    val quarterTurn: Boolean
        get() = rotation % 180 == 90

    companion object {
        /** Smallest crop window side (fraction of the source). */
        const val MIN_SIZE = 0.1f
    }
}
