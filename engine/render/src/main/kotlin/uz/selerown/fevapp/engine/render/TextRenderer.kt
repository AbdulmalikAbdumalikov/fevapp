package uz.selerown.fevapp.engine.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import uz.selerown.fevapp.core.model.TextClip
import uz.selerown.fevapp.core.model.TextFont
import kotlin.math.min

/**
 * Draws text clips onto an Android [Canvas] of the output frame size. The
 * SAME code draws the editor preview and the exported video, so both look
 * identical.
 */
object TextRenderer {

    private const val MAX_WIDTH = 0.9f
    private const val BOX_COLOR = 0x99000000.toInt()
    private const val SHADOW_COLOR = 0xAA000000.toInt()

    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BOX_COLOR }

    fun draw(canvas: Canvas, width: Int, height: Int, clips: List<TextClip>) {
        if (width <= 0 || height <= 0) return
        clips.forEach { drawOne(canvas, width, height, it) }
    }

    private fun drawOne(canvas: Canvas, width: Int, height: Int, clip: TextClip) {
        val style = clip.style
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = style.color
            textSize = style.sizeFraction * min(width, height)
            typeface = typeface(style.font, style.bold)
            // Without a box, a soft shadow keeps text readable on bright video.
            if (!style.background) setShadowLayer(textSize * 0.08f, 0f, 0f, SHADOW_COLOR)
        }
        val layoutWidth = (width * MAX_WIDTH).toInt()
        val layout = StaticLayout.Builder.obtain(clip.text, 0, clip.text.length, paint, layoutWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .build()
        val left = (width - layoutWidth) / 2f
        val top = (style.positionY * height - layout.height / 2f)
            .coerceIn(0f, (height - layout.height).coerceAtLeast(0).toFloat())

        if (style.background) {
            val contentWidth = (0 until layout.lineCount).maxOf { layout.getLineWidth(it) }
            val pad = paint.textSize * 0.3f
            val box = RectF(
                width / 2f - contentWidth / 2f - pad,
                top - pad / 2f,
                width / 2f + contentWidth / 2f + pad,
                top + layout.height + pad / 2f,
            )
            canvas.drawRoundRect(box, pad, pad, boxPaint)
        }
        canvas.save()
        canvas.translate(left, top)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun typeface(font: TextFont, bold: Boolean): Typeface {
        val family = when (font) {
            TextFont.SANS -> Typeface.SANS_SERIF
            TextFont.SERIF -> Typeface.SERIF
            TextFont.MONO -> Typeface.MONOSPACE
        }
        return Typeface.create(family, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }
}
