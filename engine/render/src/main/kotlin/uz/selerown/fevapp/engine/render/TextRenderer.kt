package uz.selerown.fevapp.engine.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import uz.selerown.fevapp.core.model.TextAnimation
import uz.selerown.fevapp.core.model.TextClip
import uz.selerown.fevapp.core.model.TextFont
import uz.selerown.fevapp.core.model.stateAt
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

    /** Length of the in and out parts of an animation. */
    const val ANIMATION_US = 400_000L

    /** Alpha (0..1), scale and vertical shift (fraction of height) of a clip at a time. */
    data class Motion(val alpha: Float, val scale: Float, val shiftY: Float) {
        companion object {
            val STILL = Motion(1f, 1f, 0f)
        }
    }

    /** Where [clip] is in its animation at timeline time [timeUs] (null = no animation). */
    fun motion(clip: TextClip, timeUs: Long?): Motion {
        if (timeUs == null || clip.style.animation == TextAnimation.NONE) return Motion.STILL
        val length = minOf(ANIMATION_US, clip.durationUs / 2).coerceAtLeast(1)
        val inT = ((timeUs - clip.startUs).toFloat() / length).coerceIn(0f, 1f)
        val outT = ((clip.endUs - timeUs).toFloat() / length).coerceIn(0f, 1f)
        val ease = { t: Float -> 1f - (1f - t) * (1f - t) } // ease-out
        val visible = minOf(ease(inT), ease(outT))
        return when (clip.style.animation) {
            TextAnimation.NONE -> Motion.STILL
            TextAnimation.FADE -> Motion(visible, 1f, 0f)
            TextAnimation.POP -> Motion(visible, 0.6f + 0.4f * ease(inT), 0f)
            TextAnimation.SLIDE_UP -> Motion(visible, 1f, 0.06f * (1f - ease(inT)))
        }
    }

    /**
     * Draws [clips]. With [timeUs] (timeline time) in/out animations are
     * applied; without it every clip is drawn still.
     */
    fun draw(canvas: Canvas, width: Int, height: Int, clips: List<TextClip>, timeUs: Long? = null) {
        if (width <= 0 || height <= 0) return
        clips.forEach { clip ->
            val (animated, alpha) = animated(clip, timeUs)
            val motion = motion(clip, timeUs)
            drawOne(canvas, width, height, animated, motion.copy(alpha = motion.alpha * alpha))
        }
    }

    /** The clip with its keyframed position/size at [timeUs], plus the keyframed opacity. */
    private fun animated(clip: TextClip, timeUs: Long?): Pair<TextClip, Float> {
        if (timeUs == null || clip.keyframes.isEmpty()) return clip to 1f
        val k = clip.stateAt(timeUs)
        return clip.copy(style = clip.style.copy(positionX = k.x, positionY = k.y, sizeFraction = k.scale)) to k.alpha
    }

    private fun drawOne(canvas: Canvas, width: Int, height: Int, clip: TextClip, motion: Motion) {
        if (motion.alpha <= 0f) return
        val alpha = (motion.alpha * 255).toInt()
        val style = clip.style
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = style.color
            textSize = style.sizeFraction * min(width, height)
            typeface = typeface(style.font, style.bold)
            // Without a box, a soft shadow keeps text readable on bright video.
            if (!style.background) setShadowLayer(textSize * 0.08f, 0f, 0f, SHADOW_COLOR)
            this.alpha = Color.alpha(style.color) * alpha / 255
        }
        val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = BOX_COLOR
            this.alpha = Color.alpha(BOX_COLOR) * alpha / 255
        }
        val layoutWidth = (width * MAX_WIDTH).toInt()
        val layout = StaticLayout.Builder.obtain(clip.text, 0, clip.text.length, paint, layoutWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .build()
        val contentWidth = (0 until layout.lineCount).maxOf { layout.getLineWidth(it) }
        // Horizontal center, kept so the text never leaves the frame.
        val pad = paint.textSize * 0.3f
        val halfWidth = contentWidth / 2f + if (style.background) pad else 0f
        val centerX = if (halfWidth * 2 >= width) {
            width / 2f
        } else {
            (style.positionX * width).coerceIn(halfWidth, width - halfWidth)
        }
        val left = (width - layoutWidth) / 2f + (centerX - width / 2f)
        val top = (style.positionY * height - layout.height / 2f)
            .coerceIn(0f, (height - layout.height).coerceAtLeast(0).toFloat()) + motion.shiftY * height
        canvas.save()
        canvas.scale(motion.scale, motion.scale, centerX, top + layout.height / 2f)

        if (style.background) {
            val box = RectF(
                centerX - contentWidth / 2f - pad,
                top - pad / 2f,
                centerX + contentWidth / 2f + pad,
                top + layout.height + pad / 2f,
            )
            canvas.drawRoundRect(box, pad, pad, boxPaint)
        }
        canvas.translate(left, top)
        layout.draw(canvas)
        canvas.restore()
    }

    /**
     * Where [clip] is drawn in a [width]×[height] frame (without animation),
     * used to hit-test taps on the preview.
     */
    fun bounds(clip: TextClip, width: Int, height: Int, timeUs: Long? = null): RectF {
        val style = animated(clip, timeUs).first.style
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = style.sizeFraction * min(width, height)
            typeface = typeface(style.font, style.bold)
        }
        val layoutWidth = (width * MAX_WIDTH).toInt().coerceAtLeast(1)
        val layout = StaticLayout.Builder.obtain(clip.text, 0, clip.text.length, paint, layoutWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .build()
        val contentWidth = (0 until layout.lineCount).maxOf { layout.getLineWidth(it) }
        val pad = paint.textSize * 0.3f
        val halfWidth = contentWidth / 2f + pad
        val centerX = if (halfWidth * 2 >= width) width / 2f else (style.positionX * width).coerceIn(halfWidth, width - halfWidth)
        val top = (style.positionY * height - layout.height / 2f).coerceIn(0f, (height - layout.height).coerceAtLeast(0).toFloat())
        return RectF(centerX - halfWidth, top - pad, centerX + halfWidth, top + layout.height + pad)
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
