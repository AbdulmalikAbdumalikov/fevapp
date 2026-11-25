package uz.selerown.fevapp.engine.timeline

import uz.selerown.fevapp.core.model.ClipLook
import uz.selerown.fevapp.core.model.ColorAdjust
import uz.selerown.fevapp.core.model.MediaType
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
}
