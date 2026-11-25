package uz.selerown.fevapp.engine.timeline

import uz.selerown.fevapp.core.model.AudioClip
import uz.selerown.fevapp.core.model.Clip
import uz.selerown.fevapp.core.model.MediaType
import uz.selerown.fevapp.core.model.Project
import uz.selerown.fevapp.core.model.TextClip
import uz.selerown.fevapp.core.model.TextStyle

/*
 * Commands for the text and audio lanes, plus clip volume / extract audio.
 * Same contract as ClipCommands: return the input instance when nothing changes.
 */

// ---- Text -------------------------------------------------------------------

class AddTextCommand(private val clip: TextClip) : TimelineCommand {
    override fun apply(project: Project): Project {
        if (clip.text.isBlank()) return project
        val fixed = clip.copy(
            startUs = clip.startUs.coerceAtLeast(0L),
            durationUs = clip.durationUs.coerceAtLeast(Clip.MIN_DURATION_US),
        )
        return project.copy(textClips = project.textClips + fixed)
    }
}

/** Changes the words and the look of a text clip. Blank text is ignored. */
class UpdateTextCommand(
    private val clipId: String,
    private val text: String,
    private val style: TextStyle,
) : TimelineCommand {
    override fun apply(project: Project): Project {
        if (text.isBlank()) return project
        return project.updateText(clipId) { it.copy(text = text, style = style) }
    }
}

/** Sets where a text clip starts and ends on the timeline (move or trim). */
class SetTextTimingCommand(
    private val clipId: String,
    private val startUs: Long,
    private val endUs: Long,
) : TimelineCommand {
    override fun apply(project: Project): Project {
        val start = startUs.coerceAtLeast(0L)
        val end = endUs.coerceAtLeast(start + Clip.MIN_DURATION_US)
        return project.updateText(clipId) { it.copy(startUs = start, durationUs = end - start) }
    }
}

class DeleteTextCommand(private val clipId: String) : TimelineCommand {
    override fun apply(project: Project): Project {
        if (project.textClips.none { it.id == clipId }) return project
        return project.copy(textClips = project.textClips.filterNot { it.id == clipId })
    }
}

/** Copies a text clip and places the copy right after the original. */
class DuplicateTextCommand(
    private val clipId: String,
    private val newClipId: String,
) : TimelineCommand {
    override fun apply(project: Project): Project {
        val original = project.textClips.firstOrNull { it.id == clipId } ?: return project
        return project.copy(textClips = project.textClips + original.copy(id = newClipId, startUs = original.endUs))
    }
}

// ---- Audio ------------------------------------------------------------------

class AddAudioCommand(private val clip: AudioClip) : TimelineCommand {
    override fun apply(project: Project): Project {
        if (clip.durationUs < Clip.MIN_DURATION_US) return project
        return project.copy(audioClips = project.audioClips + clip.copy(startUs = clip.startUs.coerceAtLeast(0L)))
    }
}

/** Moves an audio clip on the timeline; its sound window stays the same. */
class MoveAudioCommand(
    private val clipId: String,
    private val startUs: Long,
) : TimelineCommand {
    override fun apply(project: Project): Project =
        project.updateAudio(clipId) { it.copy(startUs = startUs.coerceAtLeast(0L)) }
}

/**
 * Trims an audio clip by moving its timeline edges to [startUs] / [endUs].
 * Moving the left edge also moves the source window (the sound stays in
 * place, only less of it is used). Edges are limited by the source file and
 * [Clip.MIN_DURATION_US].
 */
class TrimAudioCommand(
    private val clipId: String,
    private val startUs: Long,
    private val endUs: Long,
) : TimelineCommand {
    override fun apply(project: Project): Project = project.updateAudio(clipId) { clip ->
        val minStart = maxOf(clip.startUs - clip.trimStartUs, 0L) // first sample of the source
        val maxEnd = clip.startUs + (clip.source.durationUs - clip.trimStartUs) // last sample
        var start = startUs.coerceAtLeast(minStart)
        var end = endUs.coerceAtMost(maxEnd)
        if (end - start < Clip.MIN_DURATION_US) {
            // Keep the edge that did not move and push the dragged one back.
            if (start != clip.startUs) start = end - Clip.MIN_DURATION_US else end = start + Clip.MIN_DURATION_US
        }
        start = start.coerceAtLeast(minStart)
        end = end.coerceAtMost(maxEnd)
        val trimStart = clip.trimStartUs + (start - clip.startUs)
        clip.copy(startUs = start, trimStartUs = trimStart, trimEndUs = trimStart + (end - start)).fitFades()
    }
}

/** Volume (0..1) and fade lengths. Each fade is limited to half the clip. */
class SetAudioMixCommand(
    private val clipId: String,
    private val volume: Float,
    private val fadeInUs: Long,
    private val fadeOutUs: Long,
) : TimelineCommand {
    override fun apply(project: Project): Project = project.updateAudio(clipId) {
        it.copy(volume = volume.coerceIn(0f, 1f), fadeInUs = fadeInUs, fadeOutUs = fadeOutUs).fitFades()
    }
}

/** Splits the audio clip [clipId] at timeline time [atTimeUs]. */
class SplitAudioCommand(
    private val clipId: String,
    private val atTimeUs: Long,
    private val newClipId: String,
) : TimelineCommand {
    override fun apply(project: Project): Project {
        val index = project.audioClips.indexOfFirst { it.id == clipId }
        if (index < 0) return project
        val clip = project.audioClips[index]
        val offset = atTimeUs - clip.startUs
        if (offset < Clip.MIN_DURATION_US || clip.durationUs - offset < Clip.MIN_DURATION_US) return project
        val cut = clip.trimStartUs + offset
        val first = clip.copy(trimEndUs = cut, fadeOutUs = 0L).fitFades()
        val second = clip.copy(id = newClipId, startUs = atTimeUs, trimStartUs = cut, fadeInUs = 0L).fitFades()
        return project.copy(
            audioClips = project.audioClips.toMutableList().apply {
                set(index, first)
                add(index + 1, second)
            },
        )
    }
}

class DeleteAudioCommand(private val clipId: String) : TimelineCommand {
    override fun apply(project: Project): Project {
        if (project.audioClips.none { it.id == clipId }) return project
        return project.copy(audioClips = project.audioClips.filterNot { it.id == clipId })
    }
}

// ---- Main-track sound -------------------------------------------------------

/** Volume of a main-track clip's own sound (0..1). */
class SetClipVolumeCommand(
    private val clipId: String,
    private val volume: Float,
) : TimelineCommand {
    override fun apply(project: Project): Project {
        val index = project.mainTrack.indexOf(clipId)
        if (index < 0) return project
        val clip = project.mainTrack.clips[index]
        val value = volume.coerceIn(0f, 1f)
        if (clip.volume == value) return project
        return project.updateMainTrack { t ->
            t.copy(clips = t.clips.toMutableList().apply { set(index, clip.copy(volume = value)) })
        }
    }
}

/**
 * Copies a video clip's sound to the audio lane (same place and window) and
 * mutes the clip itself, so the sound can be edited separately.
 */
class ExtractAudioCommand(
    private val clipId: String,
    private val newAudioId: String,
    private val name: String,
) : TimelineCommand {
    override fun apply(project: Project): Project {
        val track = project.mainTrack
        val index = track.indexOf(clipId)
        if (index < 0) return project
        val clip = track.clips[index]
        if (clip.source.type != MediaType.VIDEO || clip.volume == 0f || clip.speed != 1f) return project
        val audio = AudioClip(
            id = newAudioId,
            name = name,
            source = clip.source,
            startUs = track.startOf(index),
            trimStartUs = clip.trimStartUs,
            trimEndUs = clip.trimEndUs,
            volume = clip.volume,
        )
        return SetClipVolumeCommand(clipId, 0f).apply(project).copy(audioClips = project.audioClips + audio)
    }
}

// ---- Helpers ----------------------------------------------------------------

private fun Project.updateText(id: String, transform: (TextClip) -> TextClip): Project {
    val index = textClips.indexOfFirst { it.id == id }
    if (index < 0) return this
    val updated = transform(textClips[index])
    if (updated == textClips[index]) return this
    return copy(textClips = textClips.toMutableList().apply { set(index, updated) })
}

private fun Project.updateAudio(id: String, transform: (AudioClip) -> AudioClip): Project {
    val index = audioClips.indexOfFirst { it.id == id }
    if (index < 0) return this
    val updated = transform(audioClips[index])
    if (updated == audioClips[index]) return this
    return copy(audioClips = audioClips.toMutableList().apply { set(index, updated) })
}

private fun AudioClip.fitFades(): AudioClip {
    val maxFade = minOf(durationUs / 2, AudioClip.MAX_FADE_US)
    return copy(fadeInUs = fadeInUs.coerceIn(0L, maxFade), fadeOutUs = fadeOutUs.coerceIn(0L, maxFade))
}
