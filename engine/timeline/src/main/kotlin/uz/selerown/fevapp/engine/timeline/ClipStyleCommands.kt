package uz.selerown.fevapp.engine.timeline

import uz.selerown.fevapp.core.model.Clip
import uz.selerown.fevapp.core.model.ClipLook
import uz.selerown.fevapp.core.model.ColorAdjust
import uz.selerown.fevapp.core.model.MediaType
import uz.selerown.fevapp.core.model.Project

/*
 * Commands that change how a main-track clip plays or looks (speed, color).
 */

/**
 * Constant speed of a VIDEO clip (0.25×–4×). The source window stays the
 * same, so the clip gets shorter or longer on the timeline. Photos keep 1×
 * (their length is set by trimming instead).
 */
class SetClipSpeedCommand(
    private val clipId: String,
    private val speed: Float,
) : TimelineCommand {
    override fun apply(project: Project): Project {
        val index = project.mainTrack.indexOf(clipId)
        if (index < 0) return project
        val clip = project.mainTrack.clips[index]
        if (clip.source.type != MediaType.VIDEO) return project
        val value = speed.coerceIn(Clip.MIN_SPEED, Clip.MAX_SPEED)
        if (value == clip.speed) return project
        // Keep at least MIN_DURATION on the timeline at the new speed.
        val minSource = (Clip.MIN_DURATION_US * value).toLong()
        val end = maxOf(clip.trimEndUs, minOf(clip.trimStartUs + minSource, clip.source.durationUs))
        val updated = clip.copy(speed = value, trimEndUs = end)
        return project.updateMainTrack { t -> t.copy(clips = t.clips.toMutableList().apply { set(index, updated) }) }
    }
}

/**
 * Sets the look and color adjustments of one clip, or of every clip when
 * [clipId] is null ("apply to all").
 */
class SetClipColorCommand(
    private val clipId: String?,
    private val look: ClipLook,
    private val color: ColorAdjust,
) : TimelineCommand {
    override fun apply(project: Project): Project {
        val fixed = ColorAdjust(
            brightness = color.brightness.coerceIn(-1f, 1f),
            contrast = color.contrast.coerceIn(-1f, 1f),
            saturation = color.saturation.coerceIn(-1f, 1f),
            warmth = color.warmth.coerceIn(-1f, 1f),
        )
        if (clipId != null && project.mainTrack.indexOf(clipId) < 0) return project
        var changed = false
        val updated = project.mainTrack.clips.map { clip ->
            if ((clipId == null || clip.id == clipId) && (clip.look != look || clip.color != fixed)) {
                changed = true
                clip.copy(look = look, color = fixed)
            } else {
                clip
            }
        }
        if (!changed) return project
        return project.updateMainTrack { it.copy(clips = updated) }
    }
}
