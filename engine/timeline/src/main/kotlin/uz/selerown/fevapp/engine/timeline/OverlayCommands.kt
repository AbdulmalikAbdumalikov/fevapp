package uz.selerown.fevapp.engine.timeline

import uz.selerown.fevapp.core.model.Clip
import uz.selerown.fevapp.core.model.OverlayClip
import uz.selerown.fevapp.core.model.OverlayKeying
import uz.selerown.fevapp.core.model.Project

/*
 * Commands for picture-in-picture overlay clips. Timing rules match the
 * audio lane (TrimAudioCommand): a left trim moves the source window.
 */

class AddOverlayCommand(private val clip: OverlayClip) : TimelineCommand {
    override fun apply(project: Project): Project {
        if (clip.durationUs < Clip.MIN_DURATION_US) return project
        return project.copy(overlayClips = project.overlayClips + clip.copy(startUs = clip.startUs.coerceAtLeast(0L)))
    }
}

class MoveOverlayCommand(
    private val clipId: String,
    private val startUs: Long,
) : TimelineCommand {
    override fun apply(project: Project): Project =
        project.updateOverlay(clipId) { it.copy(startUs = startUs.coerceAtLeast(0L)) }
}

/** Moves the overlay's timeline edges; limited by the source and [Clip.MIN_DURATION_US]. */
class TrimOverlayCommand(
    private val clipId: String,
    private val startUs: Long,
    private val endUs: Long,
) : TimelineCommand {
    override fun apply(project: Project): Project = project.updateOverlay(clipId) { clip ->
        val minStart = maxOf(clip.startUs - clip.trimStartUs, 0L)
        val maxEnd = clip.startUs + (clip.source.durationUs - clip.trimStartUs)
        var start = startUs.coerceAtLeast(minStart)
        var end = endUs.coerceAtMost(maxEnd)
        if (end - start < Clip.MIN_DURATION_US) {
            if (start != clip.startUs) start = end - Clip.MIN_DURATION_US else end = start + Clip.MIN_DURATION_US
        }
        start = start.coerceAtLeast(minStart)
        end = end.coerceAtMost(maxEnd)
        val trimStart = clip.trimStartUs + (start - clip.startUs)
        clip.copy(startUs = start, trimStartUs = trimStart, trimEndUs = trimStart + (end - start))
    }
}

/** Position (center, 0..1) and size (fraction of the frame width) of an overlay. */
class SetOverlayTransformCommand(
    private val clipId: String,
    private val centerX: Float,
    private val centerY: Float,
    private val scale: Float,
) : TimelineCommand {
    override fun apply(project: Project): Project = project.updateOverlay(clipId) {
        it.copy(
            centerX = centerX.coerceIn(0f, 1f),
            centerY = centerY.coerceIn(0f, 1f),
            scale = scale.coerceIn(OverlayClip.MIN_SCALE, OverlayClip.MAX_SCALE),
        )
    }
}

/** Chroma key / mask of an overlay; strength is kept in a useful range. */
class SetOverlayKeyingCommand(
    private val clipId: String,
    private val keying: OverlayKeying,
) : TimelineCommand {
    override fun apply(project: Project): Project = project.updateOverlay(clipId) {
        it.copy(keying = keying.copy(strength = keying.strength.coerceIn(0.05f, 0.9f)))
    }
}

class DeleteOverlayCommand(private val clipId: String) : TimelineCommand {
    override fun apply(project: Project): Project {
        if (project.overlayClips.none { it.id == clipId }) return project
        return project.copy(overlayClips = project.overlayClips.filterNot { it.id == clipId })
    }
}

private fun Project.updateOverlay(id: String, transform: (OverlayClip) -> OverlayClip): Project {
    val index = overlayClips.indexOfFirst { it.id == id }
    if (index < 0) return this
    val updated = transform(overlayClips[index])
    if (updated == overlayClips[index]) return this
    return copy(overlayClips = overlayClips.toMutableList().apply { set(index, updated) })
}
