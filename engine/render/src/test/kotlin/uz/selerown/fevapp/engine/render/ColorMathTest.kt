package uz.selerown.fevapp.engine.render

import uz.selerown.fevapp.core.model.Clip
import uz.selerown.fevapp.core.model.ClipLook
import uz.selerown.fevapp.core.model.ColorAdjust
import uz.selerown.fevapp.core.model.MediaSource
import uz.selerown.fevapp.core.model.MediaType
import uz.selerown.fevapp.core.model.TransitionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ColorMathTest {

    private val clip = Clip("c", MediaSource("u", MediaType.VIDEO, 1_000_000, 10, 10), 0, 1_000_000)

    private fun close(expected: Float, actual: Float) = assertEquals(expected, actual, 0.001f)

    @Test
    fun `neutral clip has no matrix`() {
        assertNull(ColorMath.matrixFor(clip))
    }

    @Test
    fun `mono makes every channel equal to luma`() {
        val m = ColorMath.matrixFor(clip.copy(look = ClipLook.MONO))!!
        val (r, g, b) = ColorMath.apply(m, 1f, 0f, 0f)
        close(0.2126f, r)
        close(r, g)
        close(r, b)
    }

    @Test
    fun `brightness then contrast compose in order`() {
        val m = ColorMath.multiply(ColorMath.contrast(1f), ColorMath.brightness(0.1f))
        val (r, _, _) = ColorMath.apply(m, 0.5f, 0.5f, 0.5f)
        close(0.7f, r) // (0.5 + 0.1) * 2 - 0.5
    }

    @Test
    fun `layouts keep the offset in the right place`() {
        val m = ColorMath.matrixFor(clip.copy(color = ColorAdjust(brightness = 0.5f)))!!
        val android = ColorMath.toAndroidColorMatrix(m)
        close(0.2f * 255f, android[4])
        close(1f, android[18])
        val media3 = ColorMath.toMedia3Matrix(m)
        close(0.2f, media3[12])
        close(1f, media3[15])
    }

    @Test
    fun `fade peaks on the cut and is gone outside the transition`() {
        val a = clip.copy(id = "a", transition = TransitionType.FADE_BLACK, transitionUs = 400_000)
        val b = clip.copy(id = "b")
        val clips = listOf(a, b) // cut at 1 s
        assertNull(ColorMath.fadeAt(clips, 700_000))
        close(1f, ColorMath.fadeAt(clips, 1_000_000)!!.second)
        close(0.5f, ColorMath.fadeAt(clips, 1_100_000)!!.second)
        val (r, _, _) = ColorMath.apply(ColorMath.fadeMatrix(TransitionType.FADE_WHITE, 1f), 0.2f, 0.2f, 0.2f)
        close(1f, r)
    }
}
