package uz.selerown.fevapp.core.model

import kotlinx.serialization.Serializable

/*
 * Clips on the free-positioned lanes (text and audio). Unlike main-track
 * clips they keep their own timeline start time and may overlap.
 */

/** In/out animation of a text clip (original, simple motions). */
@Serializable
enum class TextAnimation { NONE, FADE, POP, SLIDE_UP }

/**
 * Text fonts, all offline: generic system families, the bundled Manrope
 * (SIL OFL) and Android's built-in handwriting/casual/condensed families
 * (devices without them fall back to the default font).
 */
@Serializable
enum class TextFont { SANS, SERIF, MONO, MANROPE, CURSIVE, CASUAL, CONDENSED }

/**
 * Look of a text clip. Sizes and positions are RELATIVE to the output frame so
 * preview and export match at any resolution.
 */
@Serializable
data class TextStyle(
    /** Font size as a fraction of the frame's short side. */
    val sizeFraction: Float = 0.07f,
    /** ARGB color. */
    val color: Int = 0xFFFFFFFF.toInt(),
    /** Dark rounded box behind the text for readability. */
    val background: Boolean = false,
    val font: TextFont = TextFont.SANS,
    val bold: Boolean = true,
    /** Vertical center of the text: 0 = top, 1 = bottom. */
    val positionY: Float = 0.8f,
    /** Horizontal center of the text: 0 = left, 1 = right. */
    val positionX: Float = 0.5f,
    /** How the text appears and disappears. */
    val animation: TextAnimation = TextAnimation.NONE,
    /** Outline around the letters. */
    val stroke: Boolean = false,
    /** ARGB outline color. */
    val strokeColor: Int = 0xFF111214.toInt(),
    /** Outline thickness as a fraction of the font size. */
    val strokeWidth: Float = 0.12f,
) {
    companion object {
        const val MIN_SIZE = 0.03f
        const val MAX_SIZE = 0.16f
        const val MIN_STROKE = 0.04f
        const val MAX_STROKE = 0.25f
    }
}

@Serializable
data class TextClip(
    val id: String,
    val text: String,
    val startUs: Long,
    val durationUs: Long,
    val style: TextStyle = TextStyle(),
    /** Animation of position/size/opacity (offsets from [startUs]); empty = still. */
    val keyframes: List<Keyframe> = emptyList(),
) {
    val endUs: Long
        get() = startUs + durationUs

    fun isVisibleAt(timeUs: Long): Boolean = timeUs >= startUs && timeUs < endUs

    companion object {
        const val DEFAULT_DURATION_US: Long = 3_000_000L
    }
}

/**
 * Sound placed on the audio lane. [source] can be an audio file or a video
 * whose sound is used (extract audio). [trimStartUs]..[trimEndUs] is the
 * used window of the source; it plays from [startUs] on the timeline.
 */
@Serializable
data class AudioClip(
    val id: String,
    val name: String,
    val source: MediaSource,
    val startUs: Long,
    val trimStartUs: Long,
    val trimEndUs: Long,
    /** 0..1 */
    val volume: Float = 1f,
    val fadeInUs: Long = 0L,
    val fadeOutUs: Long = 0L,
    /** Voice effect (useful for voice-overs). */
    val voice: VoiceEffect = VoiceEffect.NONE,
) {
    val durationUs: Long
        get() = trimEndUs - trimStartUs

    val endUs: Long
        get() = startUs + durationUs

    /** Gain (volume × fades) at [offsetUs] from the clip's start. */
    fun gainAt(offsetUs: Long): Float {
        if (offsetUs < 0 || offsetUs > durationUs) return 0f
        var gain = volume
        if (fadeInUs > 0 && offsetUs < fadeInUs) gain *= offsetUs.toFloat() / fadeInUs
        val left = durationUs - offsetUs
        if (fadeOutUs > 0 && left < fadeOutUs) gain *= left.toFloat() / fadeOutUs
        return gain.coerceIn(0f, 1f)
    }

    companion object {
        const val MAX_FADE_US: Long = 5_000_000L
    }
}

/**
 * A video or photo shown ON TOP of the main track (picture-in-picture).
 * Timing works like an audio clip; [centerX]/[centerY] place its center in
 * the frame (0..1) and [scale] is its width as a fraction of the frame width.
 * Its own sound is not used (the main track and audio lane carry sound).
 */
@Serializable
data class OverlayClip(
    val id: String,
    val source: MediaSource,
    val startUs: Long,
    val trimStartUs: Long,
    val trimEndUs: Long,
    val centerX: Float = 0.7f,
    val centerY: Float = 0.3f,
    val scale: Float = 0.45f,
    /** Animation of position/size/opacity (offsets from [startUs]); empty = still. */
    val keyframes: List<Keyframe> = emptyList(),
    /** Chroma key and mask (what part of the overlay is shown). */
    val keying: OverlayKeying = OverlayKeying(),
) {
    val durationUs: Long
        get() = trimEndUs - trimStartUs

    val endUs: Long
        get() = startUs + durationUs

    fun isVisibleAt(timeUs: Long): Boolean = timeUs >= startUs && timeUs < endUs

    companion object {
        const val MIN_SCALE = 0.15f
        const val MAX_SCALE = 1f
    }
}

/** Shapes that keep part of an overlay visible. */
@Serializable
enum class MaskShape { NONE, ELLIPSE, ROUNDED, LEFT_HALF, TOP_HALF }

/**
 * Chroma key ([chroma]: remove pixels close to [keyColor]; [strength] 0..1 is
 * how close counts) and mask ([mask], optionally [invert]ed).
 */
@Serializable
data class OverlayKeying(
    val chroma: Boolean = false,
    val keyColor: Int = 0xFF00FF00.toInt(),
    val strength: Float = 0.35f,
    val mask: MaskShape = MaskShape.NONE,
    val invert: Boolean = false,
) {
    val isActive: Boolean
        get() = chroma || mask != MaskShape.NONE
}
