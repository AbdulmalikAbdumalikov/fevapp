package uz.selerown.fevapp.engine.timeline

import uz.selerown.fevapp.core.model.AudioClip
import uz.selerown.fevapp.core.model.MediaSource
import uz.selerown.fevapp.core.model.MediaType
import uz.selerown.fevapp.core.model.TextClip
import uz.selerown.fevapp.core.model.TextStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class LaneCommandsTest {

    /** 10 s of a 30 s song (source 5..15 s), placed at [startSec]. */
    private fun song(id: String = "m", startSec: Long = 0) = AudioClip(
        id = id,
        name = "song",
        source = MediaSource("content://test/$id", MediaType.AUDIO, 30 * SEC, 0, 0),
        startUs = startSec * SEC,
        trimStartUs = 5 * SEC,
        trimEndUs = 15 * SEC,
    )

    private fun withSong() = AddAudioCommand(song()).apply(sampleProject())

    @Test
    fun `add text clamps start and ignores blank text`() {
        val p = sampleProject()
        assertSame(p, AddTextCommand(TextClip("t", "  ", 0, SEC)).apply(p))
        val added = AddTextCommand(TextClip("t", "Salom", -5, 10)).apply(p).textClips.single()
        assertEquals(0L, added.startUs)
        assertEquals(100_000L, added.durationUs)
    }

    @Test
    fun `text timing, update, duplicate and delete`() {
        var p = AddTextCommand(TextClip("t", "Salom", SEC, 2 * SEC)).apply(sampleProject())
        p = SetTextTimingCommand("t", 4 * SEC, 6 * SEC).apply(p)
        assertEquals(4 * SEC to 6 * SEC, p.textClips[0].startUs to p.textClips[0].endUs)
        p = UpdateTextCommand("t", "Xayr", TextStyle(background = true)).apply(p)
        assertEquals("Xayr", p.textClips[0].text)
        assertSame(p, UpdateTextCommand("t", "", TextStyle()).apply(p))
        p = DuplicateTextCommand("t", "t2").apply(p)
        assertEquals(6 * SEC, p.textClips[1].startUs)
        p = DeleteTextCommand("t").apply(p)
        assertEquals(listOf("t2"), p.textClips.map { it.id })
    }

    @Test
    fun `left trim of audio moves the source window too`() {
        val clip = TrimAudioCommand("m", 2 * SEC, 10 * SEC).apply(withSong()).audioClips.single()
        assertEquals(2 * SEC, clip.startUs)
        assertEquals(7 * SEC, clip.trimStartUs) // 5 s + 2 s
        assertEquals(15 * SEC, clip.trimEndUs)
    }

    @Test
    fun `audio trim is limited by the source file and time zero`() {
        // The source has 5 s before the clip, but the timeline cannot go below 0.
        val clip = TrimAudioCommand("m", -9 * SEC, 99 * SEC).apply(withSong()).audioClips.single()
        assertEquals(0L, clip.startUs)
        assertEquals(5 * SEC, clip.trimStartUs)
        assertEquals(30 * SEC, clip.trimEndUs) // end of the 30 s source
    }

    @Test
    fun `audio trim keeps the minimum length`() {
        val clip = TrimAudioCommand("m", 9_990_000, 10 * SEC).apply(withSong()).audioClips.single()
        assertEquals(100_000L, clip.durationUs)
        assertEquals(10 * SEC, clip.endUs)
    }

    @Test
    fun `move audio never goes before zero`() {
        val p = MoveAudioCommand("m", -3 * SEC).apply(MoveAudioCommand("m", 4 * SEC).apply(withSong()))
        assertEquals(0L, p.audioClips.single().startUs)
    }

    @Test
    fun `volume and fades are clamped`() {
        val clip = SetAudioMixCommand("m", 2f, 9 * SEC, 1 * SEC).apply(withSong()).audioClips.single()
        assertEquals(1f, clip.volume)
        assertEquals(5 * SEC, clip.fadeInUs) // half of 10 s
        assertEquals(1 * SEC, clip.fadeOutUs)
    }

    @Test
    fun `gain follows volume and fades`() {
        val clip = song().copy(volume = 0.5f, fadeInUs = 2 * SEC, fadeOutUs = 2 * SEC)
        assertEquals(0f, clip.gainAt(0))
        assertEquals(0.25f, clip.gainAt(1 * SEC))
        assertEquals(0.5f, clip.gainAt(5 * SEC))
        assertEquals(0.25f, clip.gainAt(9 * SEC))
    }

    @Test
    fun `split audio keeps both halves in place`() {
        val p = SplitAudioCommand("m", 4 * SEC, "m2").apply(withSong())
        val (a, b) = p.audioClips
        assertEquals(0L to 4 * SEC, a.startUs to a.endUs)
        assertEquals(4 * SEC to 10 * SEC, b.startUs to b.endUs)
        assertEquals(9 * SEC, b.trimStartUs)
        assertSame(p, SplitAudioCommand("m", 50_000, "x").apply(p))
    }

    @Test
    fun `extract audio copies the sound and mutes the clip`() {
        val p = ExtractAudioCommand("b", "ab", "b").apply(sampleProject())
        val audio = p.audioClips.single()
        assertEquals(5 * SEC, audio.startUs) // b starts after a (5 s)
        assertEquals(3 * SEC, audio.durationUs)
        assertEquals(0f, p.mainTrack.clips[1].volume)
        // A muted clip has nothing left to extract.
        assertSame(p, ExtractAudioCommand("b", "ab2", "b").apply(p))
    }

    @Test
    fun `clip volume is clamped and no-op when unchanged`() {
        val base = sampleProject()
        assertSame(base, SetClipVolumeCommand("a", 3f).apply(base)) // clamps to 1 = current
        val quiet = SetClipVolumeCommand("a", 0.3f).apply(base)
        assertEquals(0.3f, quiet.mainTrack.clips[0].volume)
    }
}
