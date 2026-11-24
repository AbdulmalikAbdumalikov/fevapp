package uz.selerown.fevapp.engine.timeline

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ClipCommandsTest {

    @Test
    fun `add appends clips in selection order`() {
        val p = sampleProject()
        assertEquals(listOf("a", "b", "c"), p.ids())
        assertEquals(12 * SEC, p.durationUs)
    }

    @Test
    fun `trim changes window and total duration`() {
        val p = TrimClipCommand("a", 1 * SEC, 4 * SEC).apply(sampleProject())
        val a = p.mainTrack.clips[0]
        assertEquals(1 * SEC, a.trimStartUs)
        assertEquals(4 * SEC, a.trimEndUs)
        assertEquals(10 * SEC, p.durationUs)
    }

    @Test
    fun `trim is clamped to source and minimum length`() {
        val p = TrimClipCommand("b", -5 * SEC, 99 * SEC).apply(sampleProject())
        val b = p.mainTrack.clips[1]
        assertEquals(0, b.trimStartUs)
        assertEquals(3 * SEC, b.trimEndUs)

        val tiny = TrimClipCommand("b", 2 * SEC, 2 * SEC).apply(sampleProject()).mainTrack.clips[1]
        assertEquals(TimelineRules.minSourceLengthUs(tiny), tiny.sourceLengthUs)
    }

    @Test
    fun `split at playhead creates two halves`() {
        // 6.5s is 1.5s into clip b (which starts at 5s)
        val p = SplitClipCommand(6_500_000, newClipId = "b2").apply(sampleProject())
        assertEquals(listOf("a", "b", "b2", "c"), p.ids())
        val (b, b2) = p.mainTrack.clips[1] to p.mainTrack.clips[2]
        assertEquals(1_500_000, b.trimEndUs)
        assertEquals(1_500_000, b2.trimStartUs)
        assertEquals(3 * SEC, b2.trimEndUs)
        assertEquals(12 * SEC, p.durationUs)
    }

    @Test
    fun `split too close to an edge is ignored`() {
        val original = sampleProject()
        assertSame(original, SplitClipCommand(5 * SEC, "x").apply(original))
        assertSame(original, SplitClipCommand(5 * SEC + 50_000, "x").apply(original))
        assertSame(original, SplitClipCommand(99 * SEC, "x").apply(original))
    }

    @Test
    fun `split respects selected clip`() {
        val original = sampleProject()
        assertSame(original, SplitClipCommand(6 * SEC, "x", clipId = "a").apply(original))
    }

    @Test
    fun `delete removes clip and closes gap`() {
        val p = DeleteClipCommand("b").apply(sampleProject())
        assertEquals(listOf("a", "c"), p.ids())
        assertEquals(9 * SEC, p.durationUs)
    }

    @Test
    fun `move reorders clips`() {
        val p = MoveClipCommand("a", 2).apply(sampleProject())
        assertEquals(listOf("b", "c", "a"), p.ids())
        val back = MoveClipCommand("a", 0).apply(p)
        assertEquals(listOf("a", "b", "c"), back.ids())
    }

    @Test
    fun `duplicate inserts copy after original`() {
        val p = DuplicateClipCommand("b", "b-copy").apply(sampleProject())
        assertEquals(listOf("a", "b", "b-copy", "c"), p.ids())
    }

    @Test
    fun `unknown clip id is a no-op`() {
        val original = sampleProject()
        assertSame(original, DeleteClipCommand("nope").apply(original))
        assertSame(original, TrimClipCommand("nope", 0, SEC).apply(original))
        assertSame(original, MoveClipCommand("nope", 0).apply(original))
    }

    @Test
    fun `clipAt finds clip and offset`() {
        val track = sampleProject().mainTrack
        val pos = TimelineRules.clipAt(track, 9 * SEC)!!
        assertEquals("c", pos.clip.id)
        assertEquals(8 * SEC, pos.startUs)
        assertEquals(1 * SEC, pos.offsetUs)
    }
}
