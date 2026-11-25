package uz.selerown.fevapp.engine.timeline

import uz.selerown.fevapp.core.model.Clip
import uz.selerown.fevapp.core.model.MediaSource
import uz.selerown.fevapp.core.model.MediaType
import uz.selerown.fevapp.core.model.Project
import uz.selerown.fevapp.core.model.TransitionType

/*
 * Main-track clip commands for milestone 1.
 * Every command returns the input instance unchanged when it cannot apply.
 */

/** Appends clips (in the given order) to the main track, or inserts at [atIndex]. */
class AddClipsCommand(
    private val clips: List<Clip>,
    private val atIndex: Int? = null,
) : TimelineCommand {
    override fun apply(project: Project): Project {
        if (clips.isEmpty()) return project
        return project.updateMainTrack { track ->
            val index = (atIndex ?: track.clips.size).coerceIn(0, track.clips.size)
            track.copy(clips = track.clips.toMutableList().apply { addAll(index, clips) })
        }
    }
}

/** Sets a clip's source window. Values are clamped by [TimelineRules.clampTrim]. */
class TrimClipCommand(
    private val clipId: String,
    private val trimStartUs: Long,
    private val trimEndUs: Long,
) : TimelineCommand {
    override fun apply(project: Project): Project {
        val track = project.mainTrack
        val index = track.indexOf(clipId)
        if (index < 0) return project
        val clip = track.clips[index]
        val (start, end) = TimelineRules.clampTrim(clip, trimStartUs, trimEndUs)
        if (start == clip.trimStartUs && end == clip.trimEndUs) return project
        val trimmed = clip.copy(trimStartUs = start, trimEndUs = end)
        return project.updateMainTrack { t ->
            t.copy(clips = t.clips.toMutableList().apply { set(index, trimmed) })
        }
    }
}

/**
 * Splits the clip under [atTimeUs] into two clips. If [clipId] is given only
 * that clip may be split. Both halves must be at least [Clip.MIN_DURATION_US].
 */
class SplitClipCommand(
    private val atTimeUs: Long,
    private val newClipId: String,
    private val clipId: String? = null,
) : TimelineCommand {
    override fun apply(project: Project): Project {
        val position = TimelineRules.clipAt(project.mainTrack, atTimeUs) ?: return project
        val clip = position.clip
        if (clipId != null && clip.id != clipId) return project
        val offset = position.offsetUs
        if (offset < Clip.MIN_DURATION_US || clip.durationUs - offset < Clip.MIN_DURATION_US) {
            return project
        }
        val splitSourceUs = clip.sourceTimeAt(offset)
        val first = clip.copy(trimEndUs = splitSourceUs)
        val second = clip.copy(id = newClipId, trimStartUs = splitSourceUs)
        return project.updateMainTrack { t ->
            t.copy(
                clips = t.clips.toMutableList().apply {
                    set(position.index, first)
                    add(position.index + 1, second)
                },
            )
        }
    }
}

/** Removes a clip; the following clips move left (magnetic track). */
class DeleteClipCommand(private val clipId: String) : TimelineCommand {
    override fun apply(project: Project): Project {
        if (project.mainTrack.indexOf(clipId) < 0) return project
        return project.updateMainTrack { t -> t.copy(clips = t.clips.filterNot { it.id == clipId }) }
    }
}

/** Moves a clip to [toIndex] (index in the list AFTER removal semantics are handled). */
class MoveClipCommand(
    private val clipId: String,
    private val toIndex: Int,
) : TimelineCommand {
    override fun apply(project: Project): Project {
        val clips = project.mainTrack.clips
        val from = project.mainTrack.indexOf(clipId)
        if (from < 0) return project
        val target = toIndex.coerceIn(0, clips.lastIndex)
        if (target == from) return project
        return project.updateMainTrack { t ->
            t.copy(
                clips = t.clips.toMutableList().apply {
                    val moved = removeAt(from)
                    add(target, moved)
                },
            )
        }
    }
}

/** Inserts a copy of a clip right after the original. */
class DuplicateClipCommand(
    private val clipId: String,
    private val newClipId: String,
) : TimelineCommand {
    override fun apply(project: Project): Project {
        val index = project.mainTrack.indexOf(clipId)
        if (index < 0) return project
        val copy = project.mainTrack.clips[index].copy(id = newClipId)
        return project.updateMainTrack { t ->
            t.copy(clips = t.clips.toMutableList().apply { add(index + 1, copy) })
        }
    }
}

/**
 * Freeze frame: splits [clipId] at [atTimeUs] and puts a still picture of
 * that moment ([frame], an image file) in between for [Clip.DEFAULT_IMAGE_DURATION_US].
 * Near an edge the still goes before/after the clip instead of splitting.
 * The still keeps the clip's look, color and crop.
 */
class FreezeFrameCommand(
    private val clipId: String,
    private val atTimeUs: Long,
    private val frame: MediaSource,
    private val freezeId: String,
    private val secondPartId: String,
) : TimelineCommand {
    override fun apply(project: Project): Project {
        val position = TimelineRules.clipAt(project.mainTrack, atTimeUs) ?: return project
        if (position.clip.id != clipId) return project
        val clip = position.clip
        val still = Clip(
            id = freezeId,
            source = frame,
            trimStartUs = 0L,
            trimEndUs = Clip.DEFAULT_IMAGE_DURATION_US,
            look = clip.look,
            color = clip.color,
            transform = clip.transform,
        )
        val offset = position.offsetUs
        val parts = when {
            offset < Clip.MIN_DURATION_US -> listOf(still, clip)
            clip.durationUs - offset < Clip.MIN_DURATION_US -> listOf(clip, still)
            else -> {
                val cut = clip.sourceTimeAt(offset)
                // The transition moves to the end of the second part.
                listOf(
                    clip.copy(trimEndUs = cut, transition = TransitionType.NONE),
                    still,
                    clip.copy(id = secondPartId, trimStartUs = cut),
                )
            }
        }
        return project.updateMainTrack { t ->
            t.copy(clips = t.clips.toMutableList().apply {
                removeAt(position.index)
                addAll(position.index, parts)
            })
        }
    }
}

/**
 * Replaces the media of [clipId] with [source], keeping its place, length
 * (when the new file is long enough), volume, color, transition and speed
 * (videos only). Crop is reset because the new picture has another shape.
 */
class ReplaceClipCommand(
    private val clipId: String,
    private val source: MediaSource,
) : TimelineCommand {
    override fun apply(project: Project): Project {
        val index = project.mainTrack.indexOf(clipId)
        if (index < 0) return project
        val old = project.mainTrack.clips[index]
        val updated = if (source.type == MediaType.IMAGE) {
            old.copy(
                source = source,
                trimStartUs = 0L,
                trimEndUs = old.durationUs.coerceIn(Clip.MIN_DURATION_US, Clip.MAX_IMAGE_DURATION_US),
                speed = 1f,
                transform = old.transform.copy(left = 0f, top = 0f, right = 1f, bottom = 1f),
            )
        } else {
            val speed = if (old.source.type == MediaType.VIDEO) old.speed else 1f
            val wanted = (old.durationUs * speed).toLong()
            old.copy(
                source = source,
                trimStartUs = 0L,
                trimEndUs = wanted.coerceIn(Clip.MIN_DURATION_US, source.durationUs),
                speed = speed,
                transform = old.transform.copy(left = 0f, top = 0f, right = 1f, bottom = 1f),
            )
        }
        return project.updateMainTrack { t -> t.copy(clips = t.clips.toMutableList().apply { set(index, updated) }) }
    }
}
