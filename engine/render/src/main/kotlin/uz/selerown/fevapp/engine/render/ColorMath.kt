package uz.selerown.fevapp.engine.render

import uz.selerown.fevapp.core.model.Clip
import uz.selerown.fevapp.core.model.ClipLook
import uz.selerown.fevapp.core.model.ColorAdjust
import uz.selerown.fevapp.core.model.TransitionType
import kotlin.math.abs

/**
 * One 3×4 color matrix per clip (rows R, G, B; columns R, G, B, offset; values
 * in 0..1 units), built from the look and the manual adjustments.
 *
 * The same matrix is used by:
 * - the preview: [toAndroidColorMatrix] → a color filter on the TextureView
 *   (no GL effects pipeline needed, works on every device)
 * - the export: [toMedia3Matrix] → a Media3 RgbMatrix effect
 * so what you see is what you export.
 */
object ColorMath {

    private val IDENTITY = floatArrayOf(
        1f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f,
        0f, 0f, 1f, 0f,
    )

    // Rec. 709 luma weights.
    private const val LR = 0.2126f
    private const val LG = 0.7152f
    private const val LB = 0.0722f

    /** Null when the clip's colors are unchanged. */
    fun matrixFor(clip: Clip): FloatArray? {
        if (clip.look == ClipLook.NONE && clip.color.isNeutral) return null
        var m = IDENTITY
        look(clip.look).forEach { m = multiply(it, m) }
        adjust(clip.color).forEach { m = multiply(it, m) }
        return m
    }

    private fun look(look: ClipLook): List<FloatArray> = when (look) {
        ClipLook.NONE -> emptyList()
        ClipLook.MONO -> listOf(saturation(-1f))
        ClipLook.NOIR -> listOf(saturation(-1f), contrast(0.35f), brightness(-0.05f))
        ClipLook.WARM -> listOf(scale(1.10f, 1.0f, 0.86f), saturation(0.10f))
        ClipLook.COOL -> listOf(scale(0.90f, 1.0f, 1.12f))
        ClipLook.VIVID -> listOf(saturation(0.35f), contrast(0.15f))
        ClipLook.FADED -> listOf(contrast(-0.25f), saturation(-0.35f), brightness(0.06f))
    }

    private fun adjust(a: ColorAdjust): List<FloatArray> = buildList {
        if (a.brightness != 0f) add(brightness(a.brightness * 0.4f))
        if (a.contrast != 0f) add(contrast(a.contrast * 0.6f))
        if (a.saturation != 0f) add(saturation(a.saturation))
        if (a.warmth != 0f) add(scale(1f + a.warmth * 0.18f, 1f, 1f - a.warmth * 0.18f))
    }

    /** Adds [amount] (−1..1) to every channel. */
    fun brightness(amount: Float) = floatArrayOf(
        1f, 0f, 0f, amount,
        0f, 1f, 0f, amount,
        0f, 0f, 1f, amount,
    )

    /** Stretches values around mid-gray; [amount] −1..1 (0 = unchanged). */
    fun contrast(amount: Float): FloatArray {
        val k = 1f + amount
        val o = 0.5f * (1f - k)
        return floatArrayOf(
            k, 0f, 0f, o,
            0f, k, 0f, o,
            0f, 0f, k, o,
        )
    }

    /** [amount] −1 (gray) .. 0 (unchanged) .. 1 (twice as saturated). */
    fun saturation(amount: Float): FloatArray {
        val s = 1f + amount
        val i = 1f - s
        return floatArrayOf(
            i * LR + s, i * LG, i * LB, 0f,
            i * LR, i * LG + s, i * LB, 0f,
            i * LR, i * LG, i * LB + s, 0f,
        )
    }

    fun scale(r: Float, g: Float, b: Float) = floatArrayOf(
        r, 0f, 0f, 0f,
        0f, g, 0f, 0f,
        0f, 0f, b, 0f,
    )

    /** a × b (apply b first, then a), both 3×4 affine. */
    fun multiply(a: FloatArray, b: FloatArray): FloatArray {
        val out = FloatArray(12)
        for (row in 0 until 3) {
            for (col in 0 until 4) {
                var sum = if (col == 3) a[row * 4 + 3] else 0f
                for (k in 0 until 3) sum += a[row * 4 + k] * b[k * 4 + col]
                out[row * 4 + col] = sum
            }
        }
        return out
    }

    // ---- Transitions (dip to black / white around a cut) -----------------

    /**
     * How far the picture at timeline time [timeUs] is faded for a transition:
     * 0 = not at all, 1 = fully black/white (exactly on the cut). Null when no
     * transition is active there.
     */
    fun fadeAt(clips: List<Clip>, timeUs: Long): Pair<TransitionType, Float>? {
        var cut = 0L
        for (i in 0 until clips.lastIndex) {
            val clip = clips[i]
            cut += clip.durationUs
            if (clip.transition == TransitionType.NONE) continue
            val half = clip.transitionUs / 2
            val distance = abs(timeUs - cut)
            if (half > 0 && distance < half) return clip.transition to (1f - distance.toFloat() / half)
        }
        return null
    }

    /** Matrix that fades toward black or white by [amount] (0..1). */
    fun fadeMatrix(type: TransitionType, amount: Float): FloatArray {
        val k = 1f - amount
        val o = if (type == TransitionType.FADE_WHITE) amount else 0f
        return floatArrayOf(
            k, 0f, 0f, o,
            0f, k, 0f, o,
            0f, 0f, k, o,
        )
    }

    /** Clip colors first, then the transition fade. Null when nothing changes. */
    fun combined(clipMatrix: FloatArray?, fade: Pair<TransitionType, Float>?): FloatArray? = when {
        fade == null -> clipMatrix
        else -> multiply(fadeMatrix(fade.first, fade.second), clipMatrix ?: IDENTITY)
    }

    /** Applies [m] to one color (0..1 values). Used by tests. */
    fun apply(m: FloatArray, r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        fun row(i: Int) = m[i * 4] * r + m[i * 4 + 1] * g + m[i * 4 + 2] * b + m[i * 4 + 3]
        return Triple(row(0), row(1), row(2))
    }

    /** android.graphics.ColorMatrix layout: 4×5 row-major, offsets in 0..255. */
    fun toAndroidColorMatrix(m: FloatArray): FloatArray = floatArrayOf(
        m[0], m[1], m[2], 0f, m[3] * 255f,
        m[4], m[5], m[6], 0f, m[7] * 255f,
        m[8], m[9], m[10], 0f, m[11] * 255f,
        0f, 0f, 0f, 1f, 0f,
    )

    /** Media3 RgbMatrix layout: 4×4 column-major applied to (r, g, b, 1). */
    fun toMedia3Matrix(m: FloatArray): FloatArray = floatArrayOf(
        m[0], m[4], m[8], 0f,
        m[1], m[5], m[9], 0f,
        m[2], m[6], m[10], 0f,
        m[3], m[7], m[11], 1f,
    )
}
