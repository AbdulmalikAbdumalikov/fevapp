package uz.selerown.fevapp.engine.timeline

import uz.selerown.fevapp.core.model.Clip
import uz.selerown.fevapp.core.model.ClipLook
import uz.selerown.fevapp.core.model.ClipTransform
import uz.selerown.fevapp.core.model.ColorAdjust
import uz.selerown.fevapp.core.model.MediaType
import uz.selerown.fevapp.core.model.Project
import uz.selerown.fevapp.core.model.TransitionType

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
 * Sets the transition after [clipId] (into the next clip), or after every
 * clip but the last when [clipId] is null. The length is limited so the
 * transition never covers more than half of either neighbour.
 */
class SetTransitionCommand(
    private val clipId: String?,
    private val type: TransitionType,
    private val durationUs: Long,
) : TimelineCommand {
    override fun apply(project: Project): Project {
        val clips = project.mainTrack.clips
        val targets = if (clipId == null) clips.indices.toList().dropLast(1) else listOf(clips.indexOfFirst { it.id == clipId })
        if (targets.isEmpty() || targets.any { it < 0 || it >= clips.lastIndex }) return project
        var changed = false
        val updated = clips.mapIndexed { i, clip ->
            if (i !in targets) return@mapIndexed clip
            val maxLen = minOf(clip.durationUs, clips[i + 1].durationUs)
            val length = durationUs.coerceIn(Clip.MIN_TRANSITION_US, Clip.MAX_TRANSITION_US).coerceAtMost(maxLen)
            val next = clip.copy(transition = type, transitionUs = length)
            if (next != clip) changed = true
            next
        }
        if (!changed) return project
        return project.updateMainTrack { it.copy(clips = updated) }
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

/**
 * Crop / rotate / mirror one clip. The crop window is kept inside the source
 * and at least [ClipTransform.MIN_SIZE] on each side; rotation snaps to 90°.
 */
class SetClipTransformCommand(
    private val clipId: String,
    private val transform: ClipTransform,
) : TimelineCommand {
    override fun apply(project: Project): Project {
        val index = project.mainTrack.indexOf(clipId)
        if (index < 0) return project
        val min = ClipTransform.MIN_SIZE
        val left = transform.left.coerceIn(0f, 1f - min)
        val top = transform.top.coerceIn(0f, 1f - min)
        val fixed = transform.copy(
            left = left,
            top = top,
            right = transform.right.coerceIn(left + min, 1f),
            bottom = transform.bottom.coerceIn(top + min, 1f),
            rotation = ((transform.rotation / 90 % 4 + 4) % 4) * 90,
        )
        val clip = project.mainTrack.clips[index]
        if (clip.transform == fixed) return project
        return project.updateMainTrack { t ->
            t.copy(clips = t.clips.toMutableList().apply { set(index, clip.copy(transform = fixed)) })
        }
    }
}
