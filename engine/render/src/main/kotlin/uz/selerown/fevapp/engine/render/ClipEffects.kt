@file:OptIn(UnstableApi::class)

package uz.selerown.fevapp.engine.render

import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.SpeedParameters
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Crop
import androidx.media3.effect.RgbMatrix
import androidx.media3.effect.ScaleAndRotateTransformation
import uz.selerown.fevapp.core.model.Clip
import uz.selerown.fevapp.core.model.ClipTransform
import uz.selerown.fevapp.core.model.TransitionType

/**
 * Export-side effects of a clip: its color matrix ([ColorMath], the same one
 * the preview uses) and its speed.
 */
object ClipEffects {

    /**
     * Color matrix of the main-track item [index] (starting at [itemStartUs]
     * on the timeline): its own look/adjustments plus any transition fade at
     * its edges. Empty when the item's colors never change.
     */
    fun color(clips: List<Clip>, index: Int, itemStartUs: Long): List<Effect> {
        val clip = clips[index]
        val clipMatrix = ColorMath.matrixFor(clip)
        val touchesTransition = clip.transition != TransitionType.NONE && index < clips.lastIndex ||
            index > 0 && clips[index - 1].transition != TransitionType.NONE
        if (!touchesTransition) {
            val m = clipMatrix ?: return emptyList()
            val media3 = ColorMath.toMedia3Matrix(m)
            return listOf(RgbMatrix { _, _ -> media3 })
        }
        return listOf(TimelineColorMatrix(clips, itemStartUs, clipMatrix))
    }

    /**
     * Time-dependent matrix for items next to a transition. Like the text
     * overlay, the first frame timestamp is taken as the item's start.
     */
    private class TimelineColorMatrix(
        private val clips: List<Clip>,
        private val itemStartUs: Long,
        private val clipMatrix: FloatArray?,
    ) : RgbMatrix {
        private var firstTimestampUs: Long? = null
        private val identity = ColorMath.toMedia3Matrix(floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f))

        override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray {
            val base = firstTimestampUs ?: presentationTimeUs.also { firstTimestampUs = it }
            val timelineUs = itemStartUs + (presentationTimeUs - base)
            val m = ColorMath.combined(clipMatrix, ColorMath.fadeAt(clips, timelineUs)) ?: return identity
            return ColorMath.toMedia3Matrix(m)
        }
    }

    /**
     * Crop (Media3 NDC: x -1..1 left→right, y -1..1 bottom→top), then mirror and
     * rotate. Media3 rotates counter-clockwise, our rotation is clockwise.
     */
    fun transform(t: ClipTransform): List<Effect> {
        if (t.isIdentity) return emptyList()
        val effects = mutableListOf<Effect>()
        if (t.isCropped) {
            effects += Crop(t.left * 2f - 1f, t.right * 2f - 1f, 1f - t.bottom * 2f, 1f - t.top * 2f)
        }
        if (t.rotation != 0 || t.flipH || t.flipV) {
            effects += ScaleAndRotateTransformation.Builder()
                .setScale(if (t.flipH) -1f else 1f, if (t.flipV) -1f else 1f)
                .setRotationDegrees(-t.rotation.toFloat())
                .build()
        }
        return effects
    }

    /** Constant playback speed for export (pitch kept, like the preview). */
    fun speed(speed: Float): SpeedParameters = SpeedParameters(ConstantSpeed(speed), true)

    private class ConstantSpeed(private val speed: Float) : SpeedProvider {
        override fun getSpeed(timeUs: Long): Float = speed
        override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = C.TIME_UNSET
    }
}
