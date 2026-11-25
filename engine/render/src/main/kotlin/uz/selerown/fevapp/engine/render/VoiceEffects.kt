@file:OptIn(UnstableApi::class)

package uz.selerown.fevapp.engine.render

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import uz.selerown.fevapp.core.model.VoiceEffect
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh

/** Audio processors for voice effects (export) and their preview counterparts. */
object VoiceEffects {

    /** Export chain for [effect]: Sonic for pitch, [VoiceEffectProcessor] for the rest. */
    fun exportProcessors(effect: VoiceEffect): List<AudioProcessor> = when (effect) {
        VoiceEffect.NONE -> emptyList()
        VoiceEffect.HIGH, VoiceEffect.LOW -> listOf(SonicAudioProcessor().apply { setPitch(effect.pitch) })
        else -> listOf(VoiceEffectProcessor { effect })
    }
}

/**
 * Robot (ring modulation), echo (feedback delay) and radio (band-pass +
 * soft clipping) on 16-bit or float PCM. The effect is read for every buffer
 * from [effect], so the preview can switch it when the playing clip changes.
 * Pitch effects pass through here (the preview uses PlaybackParameters).
 */
class VoiceEffectProcessor(private val effect: () -> VoiceEffect) : BaseAudioProcessor() {

    private var sampleRate = 44_100
    private var channels = 2
    private var float = false

    // Echo: one ring buffer per channel.
    private var echo: Array<FloatArray> = emptyArray()
    private var echoIndex = 0

    // Robot: carrier phase.
    private var phase = 0.0

    // Radio: one-pole filter states per channel.
    private var lowState = FloatArray(0)
    private var highState = FloatArray(0)

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        sampleRate = inputAudioFormat.sampleRate
        channels = inputAudioFormat.channelCount.coerceAtLeast(1)
        float = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        echo = Array(channels) { FloatArray((sampleRate * ECHO_SECONDS).toInt().coerceAtLeast(1)) }
        echoIndex = 0
        phase = 0.0
        lowState = FloatArray(channels)
        highState = FloatArray(channels)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        val out = replaceOutputBuffer(size)
        val current = effect()
        val input = inputBuffer.order(ByteOrder.nativeOrder())
        val bytesPerSample = if (float) 4 else 2
        val frames = size / (bytesPerSample * channels)
        val phaseStep = 2 * PI * ROBOT_HZ / sampleRate
        val lowA = 1f - exp(-2f * PI.toFloat() * RADIO_LOW_HZ / sampleRate)
        val highA = 1f - exp(-2f * PI.toFloat() * RADIO_HIGH_HZ / sampleRate)
        repeat(frames) {
            val carrier = sin(phase).toFloat()
            for (ch in 0 until channels) {
                val x = if (float) input.float else input.short / 32768f
                val y = when (current) {
                    VoiceEffect.ROBOT -> (x * carrier * 1.6f).coerceIn(-1f, 1f)
                    VoiceEffect.ECHO -> {
                        val buffer = echo[ch]
                        val delayed = buffer[echoIndex]
                        buffer[echoIndex] = x + delayed * ECHO_FEEDBACK
                        (x + delayed * ECHO_MIX).coerceIn(-1f, 1f)
                    }
                    VoiceEffect.RADIO -> {
                        lowState[ch] += lowA * (x - lowState[ch]) // remove the deep part (high-pass)
                        val bright = x - lowState[ch]
                        highState[ch] += highA * (bright - highState[ch]) // and the very high part
                        tanh(highState[ch] * 2.2f)
                    }
                    else -> x
                }
                if (float) out.putFloat(y) else out.putShort((y * 32767f).toInt().toShort())
            }
            phase += phaseStep
            if (phase > 2 * PI) phase -= 2 * PI
            if (echo.isNotEmpty()) echoIndex = (echoIndex + 1) % echo[0].size
        }
        // Copy any incomplete trailing bytes unchanged.
        while (input.hasRemaining()) out.put(input.get())
        out.flip()
    }

    private companion object {
        const val ROBOT_HZ = 60.0
        const val ECHO_SECONDS = 0.25f
        const val ECHO_FEEDBACK = 0.4f
        const val ECHO_MIX = 0.55f
        const val RADIO_LOW_HZ = 450f
        const val RADIO_HIGH_HZ = 2800f
    }
}
