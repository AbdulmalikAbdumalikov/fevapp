package uz.selerown.fevapp.engine.timeline

import uz.selerown.fevapp.core.model.Clip
import uz.selerown.fevapp.core.model.MediaSource
import uz.selerown.fevapp.core.model.MediaType
import uz.selerown.fevapp.core.model.Project

internal const val SEC = 1_000_000L

internal fun videoClip(id: String, lengthSec: Long, sourceSec: Long = lengthSec) = Clip(
    id = id,
    source = MediaSource("content://test/$id", MediaType.VIDEO, sourceSec * SEC, 1080, 1920),
    trimStartUs = 0,
    trimEndUs = lengthSec * SEC,
)

/** Project with clips a(5s) b(3s) c(4s) on the main track. */
internal fun sampleProject(): Project {
    val empty = Project.empty("p1", "Test", 0L) { "track-${it.name}" }
    return AddClipsCommand(listOf(videoClip("a", 5), videoClip("b", 3), videoClip("c", 4))).apply(empty)
}

internal fun Project.ids() = mainTrack.clips.map { it.id }
