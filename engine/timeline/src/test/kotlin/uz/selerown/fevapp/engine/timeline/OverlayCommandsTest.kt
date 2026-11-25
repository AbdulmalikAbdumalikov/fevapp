package uz.selerown.fevapp.engine.timeline

import uz.selerown.fevapp.core.model.MediaSource
import uz.selerown.fevapp.core.model.MediaType
import uz.selerown.fevapp.core.model.MaskShape
import uz.selerown.fevapp.core.model.OverlayClip
import uz.selerown.fevapp.core.model.OverlayKeying
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class OverlayCommandsTest {

    private val pip = OverlayClip(
        id = "o",
        source = MediaSource("content://test/o", MediaType.VIDEO, 10 * SEC, 1920, 1080),
        startUs = 2 * SEC,
        trimStartUs = 0,
        trimEndUs = 4 * SEC,
    )

    private fun withPip() = AddOverlayCommand(pip).apply(sampleProject())

    @Test
    fun `move and trim keep the source window rules`() {
        var p = MoveOverlayCommand("o", -1).apply(withPip())
        assertEquals(0L, p.overlayClips.single().startUs)
        p = TrimOverlayCommand("o", SEC, 3 * SEC).apply(p)
        val clip = p.overlayClips.single()
        assertEquals(SEC to 3 * SEC, clip.startUs to clip.endUs)
        assertEquals(SEC, clip.trimStartUs)
    }

    @Test
    fun `transform is clamped`() {
        val clip = SetOverlayTransformCommand("o", 2f, -1f, 9f).apply(withPip()).overlayClips.single()
        assertEquals(1f, clip.centerX)
        assertEquals(0f, clip.centerY)
        assertEquals(OverlayClip.MAX_SCALE, clip.scale)
    }

    @Test
    fun `keying is stored with a clamped strength`() {
        val k = OverlayKeying(chroma = true, strength = 5f, mask = MaskShape.ELLIPSE)
        val clip = SetOverlayKeyingCommand("o", k).apply(withPip()).overlayClips.single()
        assertEquals(0.9f, clip.keying.strength)
        assertEquals(MaskShape.ELLIPSE, clip.keying.mask)
        assertEquals(true, clip.keying.isActive)
    }

    @Test
    fun `delete and unknown ids`() {
        val p = withPip()
        assertSame(p, DeleteOverlayCommand("x").apply(p))
        assertEquals(emptyList(), DeleteOverlayCommand("o").apply(p).overlayClips)
    }
}
