package uz.selerown.fevapp.engine.timeline

import uz.selerown.fevapp.core.model.ClipLook
import uz.selerown.fevapp.core.model.ClipTransform
import uz.selerown.fevapp.core.model.ColorAdjust
import uz.selerown.fevapp.core.model.MediaType
import uz.selerown.fevapp.core.model.TransitionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ClipStyleCommandsTest {

    @Test
    fun `speed changes timeline length but not the source window`() {
        val p = SetClipSpeedCommand("a", 2f).apply(sampleProject())
        val a = p.mainTrack.clips[0]
        assertEquals(5 * SEC, a.trimEndUs)
        assertEquals(2_500_000L, a.durationUs)
        assertEquals(9_500_000L, p.durationUs) // 2.5 + 3 + 4
    }

    @Test
    fun `speed is clamped and photos are ignored`() {
        val fast = SetClipSpeedCommand("a", 10f).apply(sampleProject())
        assertEquals(4f, fast.mainTrack.clips[0].speed)
        val slow = SetClipSpeedCommand("a", 0.01f).apply(sampleProject())
        assertEquals(0.25f, slow.mainTrack.clips[0].speed)
        val photo = sampleProject().updateMainTrack { t ->
            t.copy(clips = t.clips.mapIndexed { i, c -> if (i == 0) c.copy(source = c.source.copy(type = MediaType.IMAGE)) else c })
        }
        assertSame(photo, SetClipSpeedCommand("a", 2f).apply(photo))
    }

    @Test
    fun `split respects speed`() {
        val p = SetClipSpeedCommand("a", 2f).apply(sampleProject())
        val split = SplitClipCommand(1 * SEC, "a2").apply(p)
        assertEquals(2 * SEC, split.mainTrack.clips[0].trimEndUs) // 1 s of timeline = 2 s of source
        assertEquals(2 * SEC, split.mainTrack.clips[1].trimStartUs)
    }

    @Test
    fun `color applies to one clip or to all`() {
        val warm = ColorAdjust(warmth = 0.5f, brightness = 3f)
        val one = SetClipColorCommand("b", ClipLook.WARM, warm).apply(sampleProject())
        assertEquals(listOf(ClipLook.NONE, ClipLook.WARM, ClipLook.NONE), one.mainTrack.clips.map { it.look })
        assertEquals(1f, one.mainTrack.clips[1].color.brightness) // clamped
        val all = SetClipColorCommand(null, ClipLook.MONO, ColorAdjust()).apply(sampleProject())
        assertEquals(setOf(ClipLook.MONO), all.mainTrack.clips.map { it.look }.toSet())
        assertSame(all, SetClipColorCommand(null, ClipLook.MONO, ColorAdjust()).apply(all))
    }

    @Test
    fun `transition is clamped and not allowed after the last clip`() {
        val p = SetTransitionCommand("a", TransitionType.FADE_BLACK, 9 * SEC).apply(sampleProject())
        assertEquals(TransitionType.FADE_BLACK, p.mainTrack.clips[0].transition)
        assertEquals(1_500_000L, p.mainTrack.clips[0].transitionUs)
        val base = sampleProject()
        assertSame(base, SetTransitionCommand("c", TransitionType.FADE_WHITE, SEC).apply(base))
        val all = SetTransitionCommand(null, TransitionType.FADE_WHITE, SEC).apply(base)
        assertEquals(listOf(TransitionType.FADE_WHITE, TransitionType.FADE_WHITE, TransitionType.NONE), all.mainTrack.clips.map { it.transition })
    }

    @Test
    fun `crop and rotation change the display size and are clamped`() {
        // sample clips are 1080x1920
        val t = ClipTransform(left = 0.25f, right = 0.75f, rotation = 450, top = 0.9f, bottom = 0.95f)
        val clip = SetClipTransformCommand("a", t).apply(sampleProject()).mainTrack.clips[0]
        assertEquals(90, clip.transform.rotation)
        assertEquals(0.9f, clip.transform.top)
        assertEquals(1f, clip.transform.bottom) // at least MIN_SIZE tall
        assertEquals(192, clip.displayWidth) // rotated: cropped height (1920 * 0.1)
        assertEquals(540, clip.displayHeight) // rotated: cropped width (1080 * 0.5)
        val base = sampleProject()
        assertSame(base, SetClipTransformCommand("a", ClipTransform()).apply(base))
    }
}
