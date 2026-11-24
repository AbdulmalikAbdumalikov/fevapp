@file:OptIn(UnstableApi::class)

package uz.selerown.fevapp.engine.render

import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import uz.selerown.fevapp.core.model.MediaType
import uz.selerown.fevapp.core.model.Project

/**
 * Builds a Media3 [Composition] (used by Transformer for export) from the
 * timeline. Every clip is scaled to fit the same output frame so mixed
 * portrait/landscape clips and photos export correctly.
 */
internal object CompositionFactory {

    fun create(project: Project, settings: ExportSettings): Composition {
        val size = OutputSizeCalculator.calculate(project, settings)
        val items = project.mainTrack.clips.map { clip ->
            val videoEffects = listOf(
                Presentation.createForWidthAndHeight(size.width, size.height, Presentation.LAYOUT_SCALE_TO_FIT),
                FrameDropEffect.createDefaultFrameDropEffect(settings.frameRate.toFloat()),
            )
            EditedMediaItem.Builder(TimelineMapper.toMediaItem(clip))
                .setEffects(Effects(emptyList(), videoEffects))
                .apply { if (clip.source.type == MediaType.IMAGE) setFrameRate(settings.frameRate) }
                .build()
        }
        val sequence = EditedMediaItemSequence.Builder(items)
            // Photos have no sound: fill silence so the output always has audio.
            .experimentalSetForceAudioTrack(true)
            .build()
        return Composition.Builder(sequence).build()
    }
}
