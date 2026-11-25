@file:OptIn(UnstableApi::class)

package uz.selerown.fevapp.engine.render

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.GainProcessor
import androidx.media3.common.util.UnstableApi
import uz.selerown.fevapp.core.model.AudioClip

/**
 * Export-time volume and fades, built on Media3's [GainProcessor].
 * Sample positions start at 0 at the beginning of the (trimmed) item.
 */
internal object AudioGain {

    /** Constant volume (main-track clip). Null when nothing needs changing. */
    fun constant(volume: Float): AudioProcessor? =
        if (volume >= 1f) null else GainProcessor(Provider { volume.coerceIn(0f, 1f) })

    /** Volume × fade-in × fade-out of an audio-lane clip. Null when flat 100 %. */
    fun forClip(clip: AudioClip): AudioProcessor? {
        if (clip.volume >= 1f && clip.fadeInUs == 0L && clip.fadeOutUs == 0L) return null
        return GainProcessor(Provider { offsetUs -> clip.gainAt(offsetUs) })
    }

    private class Provider(private val gainAtUs: (Long) -> Float) : GainProcessor.GainProvider {
        override fun getGainFactorAtSamplePosition(samplePosition: Long, sampleRate: Int): Float =
            gainAtUs(samplePosition * C.MICROS_PER_SECOND / sampleRate)

        /** Exclusive end of the unity-gain range starting here, or TIME_UNSET if not unity. */
        override fun isUnityUntil(samplePosition: Long, sampleRate: Int): Long =
            if (getGainFactorAtSamplePosition(samplePosition, sampleRate) == 1f) samplePosition + 1 else C.TIME_UNSET
    }
}
