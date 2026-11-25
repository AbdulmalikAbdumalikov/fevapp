package uz.selerown.fevapp.core.model

import kotlinx.serialization.Serializable

/**
 * A stored state of an element at [offsetUs] from the element's start.
 * [x]/[y] are the center (0..1 of the frame), [scale] is the size (text:
 * sizeFraction, overlay: width fraction), [alpha] the opacity (0..1).
 */
@Serializable
data class Keyframe(
    val offsetUs: Long,
    val x: Float,
    val y: Float,
    val scale: Float,
    val alpha: Float = 1f,
)

/** Pure keyframe helpers shared by preview, export and commands. */
object KeyframeMath {

    /** Two keyframes closer than this are the same one. */
    const val SAME_TIME_US: Long = 50_000L

    /**
     * Value at [offsetUs]: before the first / after the last keyframe the
     * nearest one holds; in between it eases smoothly (ease-in-out).
     * Null when there are no keyframes.
     */
    fun at(frames: List<Keyframe>, offsetUs: Long): Keyframe? {
        if (frames.isEmpty()) return null
        if (offsetUs <= frames.first().offsetUs) return frames.first().copy(offsetUs = offsetUs)
        if (offsetUs >= frames.last().offsetUs) return frames.last().copy(offsetUs = offsetUs)
        val next = frames.indexOfFirst { it.offsetUs > offsetUs }
        val a = frames[next - 1]
        val b = frames[next]
        val raw = (offsetUs - a.offsetUs).toFloat() / (b.offsetUs - a.offsetUs)
        val t = raw * raw * (3 - 2 * raw) // smoothstep
        fun mix(p: Float, q: Float) = p + (q - p) * t
        return Keyframe(offsetUs, mix(a.x, b.x), mix(a.y, b.y), mix(a.scale, b.scale), mix(a.alpha, b.alpha))
    }

    /** Adds [frame], replacing one at (almost) the same time; result is sorted. */
    fun upsert(frames: List<Keyframe>, frame: Keyframe): List<Keyframe> =
        (frames.filterNot { kotlin.math.abs(it.offsetUs - frame.offsetUs) < SAME_TIME_US } + frame).sortedBy { it.offsetUs }

    fun remove(frames: List<Keyframe>, offsetUs: Long): List<Keyframe> =
        frames.filterNot { kotlin.math.abs(it.offsetUs - offsetUs) < SAME_TIME_US }

    fun has(frames: List<Keyframe>, offsetUs: Long): Boolean =
        frames.any { kotlin.math.abs(it.offsetUs - offsetUs) < SAME_TIME_US }
}

/** Text position/size/opacity at timeline [timeUs] (keyframes win over the style). */
fun TextClip.stateAt(timeUs: Long): Keyframe =
    KeyframeMath.at(keyframes, timeUs - startUs)
        ?: Keyframe(timeUs - startUs, style.positionX, style.positionY, style.sizeFraction, 1f)

/** Overlay position/size/opacity at timeline [timeUs]. */
fun OverlayClip.stateAt(timeUs: Long): Keyframe =
    KeyframeMath.at(keyframes, timeUs - startUs) ?: Keyframe(timeUs - startUs, centerX, centerY, scale, 1f)

/** Largest size an overlay reaches (export renders it at this size, then scales down). */
val OverlayClip.maxScale: Float
    get() = (keyframes.map { it.scale } + scale).max()
