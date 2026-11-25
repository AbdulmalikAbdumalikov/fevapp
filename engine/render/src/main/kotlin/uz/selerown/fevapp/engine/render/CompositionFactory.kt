@file:OptIn(UnstableApi::class)

package uz.selerown.fevapp.engine.render

import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.GaussianBlurWithFrameOverlaid
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import uz.selerown.fevapp.core.model.AudioClip
import uz.selerown.fevapp.core.model.Clip
import uz.selerown.fevapp.core.model.MediaType
import uz.selerown.fevapp.core.model.OverlayClip
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

    private const val BLACK = 0xFF000000.toInt()
    private const val BLUR_SIGMA = 30f

    /** Size of [clip] fitted into the frame, in pixels. */
    private fun fitSize(clip: Clip, size: OutputSize): Pair<Float, Float> {
        val w = clip.source.width.coerceAtLeast(1).toFloat()
        val h = clip.source.height.coerceAtLeast(1).toFloat()
        val scale = minOf(size.width / w, size.height / h)
        return w * scale to h * scale
    }

    private fun fillsFrame(clip: Clip, size: OutputSize): Boolean {
        if (clip.source.width <= 0 || clip.source.height <= 0) return true
        val (w, h) = fitSize(clip, size)
        return size.width - w <= 2f && size.height - h <= 2f
    }

    fun create(project: Project, settings: ExportSettings): Composition {
        val size = OutputSizeCalculator.calculate(project, settings)
        val texts = project.textClips.filter { it.startUs < project.durationUs }
        var startUs = 0L
        val clips = project.mainTrack.clips
        val items = clips.mapIndexed { index, clip ->
            mainItem(clips, index, startUs, texts, project.backgroundColor, project.backgroundBlur, size, settings).also { startUs += clip.durationUs }
        }
        val main = EditedMediaItemSequence.Builder(items)
            // Photos have no sound: fill silence so the output always has audio.
            .experimentalSetForceAudioTrack(true)
            .build()
        // Picture-in-picture: one video sequence per overlay, placed by the compositor.
        val overlays = project.overlayClips.filter { it.startUs < project.durationUs }
        val overlaySequences = overlays.map { overlaySequence(it, project.durationUs, size, settings) }
        val audio = project.audioClips.mapNotNull { audioSequence(it, project.durationUs) }
        // Media3 draws input 0 on TOP, so overlays come first and the main track last.
        return Composition.Builder(overlaySequences.reversed() + main + audio)
            .apply { if (overlays.isNotEmpty()) setVideoCompositorSettings(OverlayCompositor(overlays)) }
            .build()
    }

    private fun mainItem(
        clips: List<Clip>,
        index: Int,
        startUs: Long,
        texts: List<TextClip>,
        backgroundColor: Int,
        backgroundBlur: Boolean,
        size: OutputSize,
        settings: ExportSettings,
    ): EditedMediaItem {
        val clip = clips[index]
        val endUs = startUs + clip.durationUs
        val videoEffects = mutableListOf<Effect>()
        videoEffects += ClipEffects.color(clips, index, startUs) // before the overlay, so text keeps its colors
        val blurBars = backgroundBlur && !fillsFrame(clip, size)
        videoEffects += if (blurBars) {
            // Stretch to the frame, blur it, and put the sharp picture back on top at its
            // real shape (scaling the stretched frame by fit/frame undoes the stretch).
            val (fitW, fitH) = fitSize(clip, size)
            listOf(
                Presentation.createForWidthAndHeight(size.width, size.height, Presentation.LAYOUT_STRETCH_TO_FIT),
                GaussianBlurWithFrameOverlaid(BLUR_SIGMA, 2f, fitW / size.width, fitH / size.height),
                // The blur pass changes the frame size (seen on API 36): force it back.
                Presentation.createForWidthAndHeight(size.width, size.height, Presentation.LAYOUT_STRETCH_TO_FIT),
            )
        } else {
            listOf(Presentation.createForWidthAndHeight(size.width, size.height, Presentation.LAYOUT_SCALE_TO_FIT))
        }
        videoEffects += listOf(
            FrameDropEffect.createDefaultFrameDropEffect(settings.frameRate.toFloat()),
        )
        val overlays = mutableListOf<TextureOverlay>()
        // Colored background: paint the letterbox bars (overlays draw on top, so only the bars).
        if (!blurBars && backgroundColor != BLACK) {
            BackgroundBars.create(clip, size, backgroundColor)?.let { overlays += it }
        }
        val visibleTexts = texts.filter { it.startUs < endUs && it.endUs > startUs }
        if (visibleTexts.isNotEmpty()) {
            overlays += TimelineTextOverlay(visibleTexts, startUs, size.width, size.height)
        }
        if (overlays.isNotEmpty()) videoEffects += OverlayEffect(overlays)
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

    /**
     * [gap until start] + overlay (scaled to its size, no sound) + [gap until
     * the project ends]. Outside its time the compositor hides it (alpha 0).
     */
    private fun overlaySequence(
        clip: OverlayClip,
        projectDurationUs: Long,
        size: OutputSize,
        settings: ExportSettings,
    ): EditedMediaItemSequence {
        val visible = if (clip.endUs > projectDurationUs) {
            clip.copy(trimEndUs = clip.trimStartUs + (projectDurationUs - clip.startUs))
        } else {
            clip
        }
        val (w, h) = OverlayCompositor.pixelSize(clip, size)
        val item = EditedMediaItem.Builder(TimelineMapper.toMediaItem(visible))
            .setRemoveAudio(true)
            .setEffects(
                Effects(
                    emptyList(),
                    listOf(
                        Presentation.createForWidthAndHeight(w, h, Presentation.LAYOUT_SCALE_TO_FIT),
                        FrameDropEffect.createDefaultFrameDropEffect(settings.frameRate.toFloat()),
                    ),
                ),
            )
            .apply { if (clip.source.type == MediaType.IMAGE) setFrameRate(settings.frameRate) }
            .build()
        val tail = projectDurationUs - visible.endUs
        return EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_VIDEO))
            .apply { if (visible.startUs > 0) addGap(visible.startUs) }
            .addItem(item)
            .apply { if (tail > 0) addGap(tail) }
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
