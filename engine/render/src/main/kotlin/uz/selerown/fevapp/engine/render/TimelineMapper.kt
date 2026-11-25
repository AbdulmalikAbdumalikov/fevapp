package uz.selerown.fevapp.engine.render

import android.net.Uri
import androidx.media3.common.MediaItem
import uz.selerown.fevapp.core.model.AudioClip
import uz.selerown.fevapp.core.model.Clip
import uz.selerown.fevapp.core.model.MediaType
import uz.selerown.fevapp.core.model.OverlayClip

/**
 * Converts timeline clips into Media3 [MediaItem]s and maps between
 * timeline time (us) and player positions (item index + ms).
 */
object TimelineMapper {

    fun toMediaItem(clip: Clip): MediaItem {
        val builder = MediaItem.Builder()
            .setUri(Uri.parse(clip.source.uri))
            .setMediaId(clip.id)
        return when (clip.source.type) {
            MediaType.IMAGE -> builder.setImageDurationMs(clip.durationUs / 1000).build()
            MediaType.VIDEO, MediaType.AUDIO -> builder.setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(clip.trimStartUs / 1000)
                    .setEndPositionMs(clip.trimEndUs / 1000)
                    .build(),
            ).build()
        }
    }

    /** Audio-lane clip -> its sound window (video sources are played sound-only). */
    fun toMediaItem(clip: AudioClip): MediaItem = MediaItem.Builder()
        .setUri(Uri.parse(clip.source.uri))
        .setMediaId(clip.id)
        .setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(clip.trimStartUs / 1000)
                .setEndPositionMs(clip.trimEndUs / 1000)
                .build(),
        )
        .build()

    /** Overlay clip -> its window (photos get a fixed duration). */
    fun toMediaItem(clip: OverlayClip): MediaItem {
        val builder = MediaItem.Builder().setUri(Uri.parse(clip.source.uri)).setMediaId(clip.id)
        return if (clip.source.type == MediaType.IMAGE) {
            builder.setImageDurationMs(clip.durationUs / 1000).build()
        } else {
            builder.setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(clip.trimStartUs / 1000)
                    .setEndPositionMs(clip.trimEndUs / 1000)
                    .build(),
            ).build()
        }
    }

    /** Timeline time -> (playlist index, position inside the item in ms). */
    fun locate(clips: List<Clip>, timeUs: Long): Pair<Int, Long> {
        var start = 0L
        clips.forEachIndexed { index, clip ->
            val end = start + clip.durationUs
            // Player positions are in SOURCE time: scale by speed.
            if (timeUs < end) return index to (((timeUs - start).coerceAtLeast(0) * clip.speed).toLong() / 1000)
            start = end
        }
        // Past the end: park on the last frame.
        val last = clips.lastIndex.coerceAtLeast(0)
        return last to ((clips.lastOrNull()?.sourceLengthUs ?: 0L) / 1000)
    }

    /** (playlist index, position ms) -> timeline time. */
    fun toTimelineUs(clips: List<Clip>, index: Int, positionMs: Long): Long {
        if (clips.isEmpty()) return 0L
        val i = index.coerceIn(0, clips.lastIndex)
        val start = clips.take(i).sumOf { it.durationUs }
        return (start + (positionMs * 1000 / clips[i].speed).toLong()).coerceAtMost(start + clips[i].durationUs)
    }
}
