@file:OptIn(UnstableApi::class)

package uz.selerown.fevapp.engine.render

import androidx.media3.common.OverlaySettings
import androidx.media3.common.VideoCompositorSettings
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.StaticOverlaySettings
import uz.selerown.fevapp.core.model.OverlayClip
import uz.selerown.fevapp.core.model.maxScale
import uz.selerown.fevapp.core.model.stateAt
import kotlin.math.roundToInt

/**
 * Places picture-in-picture inputs for export. Media3 draws input 0 on top,
 * so the inputs are the overlays in reverse order (last added = top) followed
 * by the main track (full frame, bottom). Each overlay is centered at its
 * position and hidden (alpha 0) outside its time window.
 */
internal class OverlayCompositor(private val overlays: List<OverlayClip>) : VideoCompositorSettings {

    override fun getOutputSize(inputSizes: List<Size>): Size = inputSizes.getOrElse(overlays.size) { inputSizes.last() }

    override fun getOverlaySettings(inputId: Int, presentationTimeUs: Long): OverlaySettings {
        if (inputId >= overlays.size) return MAIN
        val clip = overlays[overlays.size - 1 - inputId]
        val visible = clip.isVisibleAt(presentationTimeUs)
        val k = clip.stateAt(presentationTimeUs) // keyframes (or the still values)
        val s = k.scale / clip.maxScale // the item is rendered at its largest size
        return StaticOverlaySettings.Builder()
            // Frame anchors are in NDC: x -1..1 left to right, y -1..1 bottom to top.
            .setBackgroundFrameAnchor(k.x * 2f - 1f, 1f - k.y * 2f)
            .setScale(s, s)
            .setAlphaScale(if (visible) k.alpha else 0f)
            .build()
    }

    companion object {
        private val MAIN: OverlaySettings = StaticOverlaySettings.Builder().build()

        /** Overlay size in output pixels (even numbers), keeping the source shape. */
        fun pixelSize(clip: OverlayClip, size: OutputSize): Pair<Int, Int> {
            val w = (size.width * clip.maxScale).roundToInt().coerceAtLeast(16)
            val srcW = clip.source.width.coerceAtLeast(1)
            val srcH = clip.source.height.coerceAtLeast(1)
            val h = (w * srcH.toFloat() / srcW).roundToInt().coerceAtLeast(16)
            return (w / 2 * 2) to (h / 2 * 2)
        }
    }
}
