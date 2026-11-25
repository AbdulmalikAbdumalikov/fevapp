package uz.selerown.fevapp.engine.render

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Loudness peaks of a sound, one value (0..1) per [BUCKET_US], used to draw
 * the waveform on audio-lane clips.
 */
class Waveform(val peaks: FloatArray) {
    fun peakAt(sourceUs: Long): Float = peaks.getOrElse((sourceUs / BUCKET_US).toInt()) { 0f }

    companion object {
        const val BUCKET_US = 50_000L
    }
}

/**
 * Decodes audio once (MediaExtractor + MediaCodec, on-device) and keeps the
 * peaks in memory, keyed by source URI. Failures just mean "no waveform".
 */
@Singleton
class WaveformLoader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _waveforms = MutableStateFlow<Map<String, Waveform>>(emptyMap())
    val waveforms: StateFlow<Map<String, Waveform>> = _waveforms.asStateFlow()

    private val requested = mutableSetOf<String>()
    private val decodeSlots = Semaphore(1) // one decode at a time keeps the UI smooth

    /** Starts loading [uri] if it is not known yet. Safe to call often. */
    suspend fun request(uri: String) {
        synchronized(requested) { if (!requested.add(uri)) return }
        val waveform = decodeSlots.withPermit {
            withContext(Dispatchers.Default) { runCatching { decode(uri) }.getOrNull() }
        } ?: return
        _waveforms.update { it + (uri to waveform) }
    }

    private fun decode(uri: String): Waveform? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, Uri.parse(uri), null)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            codec = MediaCodec.createDecoderByType(mime).apply {
                configure(format, null, null, 0)
                start()
            }
            val peaks = ArrayList<Float>()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var floatPcm = false
            while (true) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val buffer = codec.getInputBuffer(inIndex)!!
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIndex = codec.dequeueOutputBuffer(info, 10_000)
                if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val out = codec.outputFormat
                    floatPcm = out.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                        out.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                } else if (outIndex >= 0) {
                    val buffer = codec.getOutputBuffer(outIndex)!!.order(ByteOrder.nativeOrder())
                    buffer.position(info.offset)
                    buffer.limit(info.offset + info.size)
                    var peak = 0f
                    if (floatPcm) {
                        val samples = buffer.asFloatBuffer()
                        while (samples.hasRemaining()) peak = maxOf(peak, abs(samples.get()))
                    } else {
                        val samples = buffer.asShortBuffer()
                        while (samples.hasRemaining()) peak = maxOf(peak, abs(samples.get().toInt()) / 32768f)
                    }
                    val bucket = (info.presentationTimeUs / Waveform.BUCKET_US).toInt()
                    while (peaks.size <= bucket) peaks += 0f
                    if (bucket >= 0) peaks[bucket] = maxOf(peaks[bucket], peak)
                    codec.releaseOutputBuffer(outIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
            val max = peaks.maxOrNull()?.takeIf { it > 0f } ?: return Waveform(FloatArray(peaks.size))
            return Waveform(FloatArray(peaks.size) { (peaks[it] / max).coerceIn(0f, 1f) })
        } finally {
            codec?.runCatching { stop(); release() }
            extractor.release()
        }
    }
}
