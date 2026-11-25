@file:OptIn(UnstableApi::class)

package uz.selerown.fevapp.engine.render

import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import uz.selerown.fevapp.core.model.AudioClip
import uz.selerown.fevapp.core.model.Clip
import uz.selerown.fevapp.core.model.MediaType
import uz.selerown.fevapp.core.model.Project
import uz.selerown.fevapp.core.model.TextClip

/**
 * Builds a Media3 [Composition] (used by Transformer for export) from the
 * timeline:
 * - sequence 1: the main track. Every clip is scaled to the same output frame,
 *   gets its own volume and the text clips drawn on top.
 * - one extra audio-only sequence per audio-lane clip: a gap until the clip's
 *   start, then the sound. Transformer mixes all sequences.
 * Lane clips are cut at the end of the main track (the project length).
 */
internal object CompositionFactory {

    fun create(project: Project, settings: ExportSettings): Composition {
        val size = OutputSizeCalculator.calculate(project, settings)
        val texts = project.textClips.filter { it.startUs < project.durationUs }
        var startUs = 0L
        val items = project.mainTrack.clips.map { clip ->
            mainItem(clip, startUs, texts, size, settings).also { startUs += clip.durationUs }
        }
        val main = EditedMediaItemSequence.Builder(items)
            // Photos have no sound: fill silence so the output always has audio.
            .experimentalSetForceAudioTrack(true)
            .build()
        val audio = project.audioClips.mapNotNull { audioSequence(it, project.durationUs) }
        return Composition.Builder(listOf(main) + audio).build()
    }

    private fun mainItem(
        clip: Clip,
        startUs: Long,
        texts: List<TextClip>,
        size: OutputSize,
        settings: ExportSettings,
    ): EditedMediaItem {
        val endUs = startUs + clip.durationUs
        val videoEffects = mutableListOf<Effect>()
        videoEffects += ClipEffects.color(clip) // before the overlay, so text keeps its colors
        videoEffects += listOf(
            Presentation.createForWidthAndHeight(size.width, size.height, Presentation.LAYOUT_SCALE_TO_FIT),
            FrameDropEffect.createDefaultFrameDropEffect(settings.frameRate.toFloat()),
        )
        val visibleTexts = texts.filter { it.startUs < endUs && it.endUs > startUs }
        if (visibleTexts.isNotEmpty()) {
            videoEffects += OverlayEffect(listOf(TimelineTextOverlay(visibleTexts, startUs, size.width, size.height)))
        }
        val audioEffects = if (clip.source.type == MediaType.VIDEO) listOfNotNull(AudioGain.constant(clip.volume)) else emptyList()
        return EditedMediaItem.Builder(TimelineMapper.toMediaItem(clip))
            .setEffects(Effects(audioEffects, videoEffects))
            .apply {
                if (clip.source.type == MediaType.IMAGE) setFrameRate(settings.frameRate)
                if (clip.volume == 0f) setRemoveAudio(true)
                if (clip.speed != 1f) setSpeed(ClipEffects.speed(clip.speed))
            }
            .build()
    }

    private fun audioSequence(clip: AudioClip, projectDurationUs: Long): EditedMediaItemSequence? {
        if (clip.startUs >= projectDurationUs || clip.volume == 0f) return null
        val visible = if (clip.endUs > projectDurationUs) {
            clip.copy(trimEndUs = clip.trimStartUs + (projectDurationUs - clip.startUs))
        } else {
            clip
        }
        val item = EditedMediaItem.Builder(TimelineMapper.toMediaItem(visible))
            .setRemoveVideo(true)
            .setEffects(Effects(listOfNotNull(AudioGain.forClip(visible)), emptyList()))
            .build()
        return EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO))
            .apply { if (visible.startUs > 0) addGap(visible.startUs) }
            .addItem(item)
            .build()
    }
}
