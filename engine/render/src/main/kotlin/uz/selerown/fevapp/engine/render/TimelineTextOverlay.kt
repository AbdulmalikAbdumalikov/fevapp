@file:OptIn(UnstableApi::class)

package uz.selerown.fevapp.engine.render

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import uz.selerown.fevapp.core.model.TextClip

/**
 * Export overlay for ONE main-track item: draws the text clips that are
 * visible at each frame's timeline time.
 *
 * Frame timestamps inside an item may start at 0 or be offset (it depends
 * on the Media3 pipeline), so the first timestamp seen is taken as the
 * item's start and [itemStartUs] maps it onto the timeline.
 */
internal class TimelineTextOverlay(
    private val texts: List<TextClip>,
    private val itemStartUs: Long,
    private val width: Int,
    private val height: Int,
) : BitmapOverlay() {

    private var firstTimestampUs: Long? = null
    private var lastKey: List<String>? = null
    private var lastBitmap: Bitmap? = null

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val base = firstTimestampUs ?: presentationTimeUs.also { firstTimestampUs = it }
        val timelineUs = itemStartUs + (presentationTimeUs - base)
        val visible = texts.filter { it.isVisibleAt(timelineUs) }
        val key = visible.map { it.id }
        lastBitmap?.let { if (key == lastKey) return it }
        // A new bitmap (not a redraw) so the overlay knows to upload it again.
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        TextRenderer.draw(Canvas(bitmap), width, height, visible)
        lastKey = key
        lastBitmap = bitmap
        return bitmap
    }
}
