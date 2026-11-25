@file:OptIn(UnstableApi::class)

package uz.selerown.fevapp.engine.render

import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.SpeedParameters
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.RgbMatrix
import uz.selerown.fevapp.core.model.Clip

/**
 * Export-side effects of a clip: its color matrix ([ColorMath], the same one
 * the preview uses) and its speed.
 */
object ClipEffects {

    fun color(clip: Clip): List<Effect> {
        val matrix = ColorMath.matrixFor(clip) ?: return emptyList()
        val media3 = ColorMath.toMedia3Matrix(matrix)
        return listOf(RgbMatrix { _, _ -> media3 })
    }

    /** Constant playback speed for export (pitch kept, like the preview). */
    fun speed(speed: Float): SpeedParameters = SpeedParameters(ConstantSpeed(speed), true)

    private class ConstantSpeed(private val speed: Float) : SpeedProvider {
        override fun getSpeed(timeUs: Long): Float = speed
        override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = C.TIME_UNSET
    }
}
