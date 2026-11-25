package uz.selerown.fevapp.engine.timeline

import uz.selerown.fevapp.core.model.Keyframe
import uz.selerown.fevapp.core.model.KeyframeMath
import uz.selerown.fevapp.core.model.TextClip
import uz.selerown.fevapp.core.model.stateAt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class KeyframeCommandsTest {

    private fun close(expected: Float, actual: Float) = assertEquals(expected, actual, 0.001f)

    @Test
    fun `math holds before, eases between, holds after`() {
        val frames = listOf(Keyframe(0, 0f, 0f, 0.1f, 0f), Keyframe(SEC, 1f, 1f, 0.2f, 1f))
        assertNull(KeyframeMath.at(emptyList(), 0))
        close(0f, KeyframeMath.at(frames, -5)!!.x)
        close(0.5f, KeyframeMath.at(frames, SEC / 2)!!.x) // smoothstep is 0.5 in the middle
        close(0.15625f, KeyframeMath.at(frames, SEC / 4)!!.x) // eased, not linear
        close(1f, KeyframeMath.at(frames, 3 * SEC)!!.alpha)
    }

    @Test
    fun `upsert replaces a keyframe at almost the same time`() {
        val one = KeyframeMath.upsert(emptyList(), Keyframe(SEC, 0f, 0f, 0.1f))
        val two = KeyframeMath.upsert(one, Keyframe(SEC + 20_000, 1f, 1f, 0.1f))
        assertEquals(1, two.size)
        close(1f, two.single().x)
    }

    @Test
    fun `text keyframes are clamped and drive stateAt`() {
        var p = AddTextCommand(TextClip("t", "Salom", SEC, 2 * SEC)).apply(sampleProject())
        p = SetTextKeyframeCommand("t", Keyframe(9 * SEC, 2f, 0.5f, 1f, 3f)).apply(p)
        val k = p.textClips.single().keyframes.single()
        assertEquals(2 * SEC, k.offsetUs) // limited to the clip length
        close(1f, k.x)
        close(1f, k.alpha)
        close(1f, p.textClips.single().stateAt(SEC).x) // single keyframe holds everywhere
        p = DeleteTextKeyframeCommand("t", 2 * SEC).apply(p)
        assertEquals(emptyList(), p.textClips.single().keyframes)
        assertSame(p, DeleteTextKeyframeCommand("t", SEC).apply(p))
    }
}
