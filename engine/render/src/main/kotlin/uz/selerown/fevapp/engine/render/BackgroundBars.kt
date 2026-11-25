@file:OptIn(UnstableApi::class)

package uz.selerown.fevapp.engine.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import uz.selerown.fevapp.core.model.Clip
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Export-side background color: a frame-sized overlay that paints only the
 * letterbox bars around a fitted clip, leaving the picture area transparent.
 */
internal object BackgroundBars {

    fun create(clip: Clip, size: OutputSize, color: Int): BitmapOverlay? {
        val w = clip.displayWidth
        val h = clip.displayHeight
        if (w <= 0 || h <= 0) return null
        val scale = min(size.width.toFloat() / w, size.height.toFloat() / h)
        val contentW = (w * scale).roundToInt()
        val contentH = (h * scale).roundToInt()
        // Nothing to paint when the clip fills the frame (allow 2 px rounding).
        if (size.width - contentW <= 2 && size.height - contentH <= 2) return null
        val bitmap = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply { this.color = color }
        val left = (size.width - contentW) / 2f
        val top = (size.height - contentH) / 2f
        Canvas(bitmap).apply {
            drawRect(0f, 0f, size.width.toFloat(), top, paint)
            drawRect(0f, top + contentH, size.width.toFloat(), size.height.toFloat(), paint)
            drawRect(0f, top, left, top + contentH, paint)
            drawRect(left + contentW, top, size.width.toFloat(), top + contentH, paint)
        }
        return BitmapOverlay.createStaticBitmapOverlay(bitmap)
    }
}
