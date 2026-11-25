package uz.selerown.fevapp.engine.timeline

import uz.selerown.fevapp.core.model.Keyframe
import uz.selerown.fevapp.core.model.KeyframeMath
import uz.selerown.fevapp.core.model.OverlayClip
import uz.selerown.fevapp.core.model.Project
import uz.selerown.fevapp.core.model.TextClip
import uz.selerown.fevapp.core.model.TextStyle

/*
 * Keyframes of text and overlay clips. Offsets are relative to the clip's
 * start and limited to its length; values are clamped like the still values.
 */

class SetTextKeyframeCommand(
    private val clipId: String,
    private val keyframe: Keyframe,
) : TimelineCommand {
    override fun apply(project: Project): Project = project.updateTextClip(clipId) { clip ->
        val k = keyframe.copy(
            offsetUs = keyframe.offsetUs.coerceIn(0L, clip.durationUs),
            x = keyframe.x.coerceIn(0f, 1f),
            y = keyframe.y.coerceIn(0f, 1f),
            scale = keyframe.scale.coerceIn(TextStyle.MIN_SIZE, TextStyle.MAX_SIZE),
            alpha = keyframe.alpha.coerceIn(0f, 1f),
        )
        clip.copy(keyframes = KeyframeMath.upsert(clip.keyframes, k))
    }
}

class DeleteTextKeyframeCommand(
    private val clipId: String,
    private val offsetUs: Long,
) : TimelineCommand {
    override fun apply(project: Project): Project = project.updateTextClip(clipId) { clip ->
        clip.copy(keyframes = KeyframeMath.remove(clip.keyframes, offsetUs))
    }
}

class SetOverlayKeyframeCommand(
    private val clipId: String,
    private val keyframe: Keyframe,
) : TimelineCommand {
    override fun apply(project: Project): Project = project.updateOverlayClip(clipId) { clip ->
        val k = keyframe.copy(
            offsetUs = keyframe.offsetUs.coerceIn(0L, clip.durationUs),
            x = keyframe.x.coerceIn(0f, 1f),
            y = keyframe.y.coerceIn(0f, 1f),
            scale = keyframe.scale.coerceIn(OverlayClip.MIN_SCALE, OverlayClip.MAX_SCALE),
            alpha = keyframe.alpha.coerceIn(0f, 1f),
        )
        clip.copy(keyframes = KeyframeMath.upsert(clip.keyframes, k))
    }
}

class DeleteOverlayKeyframeCommand(
    private val clipId: String,
    private val offsetUs: Long,
) : TimelineCommand {
    override fun apply(project: Project): Project = project.updateOverlayClip(clipId) { clip ->
        clip.copy(keyframes = KeyframeMath.remove(clip.keyframes, offsetUs))
    }
}

private fun Project.updateTextClip(id: String, transform: (TextClip) -> TextClip): Project {
    val index = textClips.indexOfFirst { it.id == id }
    if (index < 0) return this
    val updated = transform(textClips[index])
    if (updated == textClips[index]) return this
    return copy(textClips = textClips.toMutableList().apply { set(index, updated) })
}

private fun Project.updateOverlayClip(id: String, transform: (OverlayClip) -> OverlayClip): Project {
    val index = overlayClips.indexOfFirst { it.id == id }
    if (index < 0) return this
    val updated = transform(overlayClips[index])
    if (updated == overlayClips[index]) return this
    return copy(overlayClips = overlayClips.toMutableList().apply { set(index, updated) })
}
