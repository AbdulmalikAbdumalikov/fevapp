package uz.selerown.fevapp.engine.render

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

/**
 * Records a voice-over from the microphone into an AAC (.m4a) file inside
 * the app's private storage. Nothing leaves the device.
 */
class VoiceRecorder @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null

    val isRecording: Boolean
        get() = recorder != null

    /** Starts recording. Returns false if the microphone could not be opened. */
    fun start(): Boolean {
        if (recorder != null) return true
        val dir = File(context.filesDir, "voice").apply { mkdirs() }
        val output = File(dir, "voice_${System.currentTimeMillis()}.m4a")
        val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        return try {
            mr.setAudioSource(MediaRecorder.AudioSource.MIC)
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mr.setAudioSamplingRate(44_100)
            mr.setAudioEncodingBitRate(128_000)
            mr.setOutputFile(output.absolutePath)
            mr.prepare()
            mr.start()
            recorder = mr
            file = output
            true
        } catch (e: Exception) {
            mr.release()
            output.delete()
            false
        }
    }

    /** Stops recording and returns the file, or null if nothing usable was recorded. */
    fun stop(): File? {
        val mr = recorder ?: return null
        val output = file
        recorder = null
        file = null
        val ok = runCatching { mr.stop() }.isSuccess // throws if stopped right after start
        mr.release()
        if (!ok) output?.delete()
        return output?.takeIf { ok && it.length() > 0 }
    }
}
