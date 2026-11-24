@file:OptIn(UnstableApi::class)

package uz.selerown.fevapp.engine.render

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import uz.selerown.fevapp.core.model.Project
import java.io.File
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Exports a project to an MP4 file with Media3 Transformer (hardware H.264 +
 * AAC). Cancelling the calling coroutine cancels the export.
 */
class ExportEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** @param onProgress called on the main thread with 0..100. */
    suspend fun export(project: Project, settings: ExportSettings, onProgress: (Int) -> Unit): File =
        withContext(Dispatchers.Main) {
            val dir = File(context.cacheDir, "export").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() } // keep only the latest export in cache
            val output = File(dir, "fevapp_${System.currentTimeMillis()}.mp4")
            val composition = CompositionFactory.create(project, settings)
            runTransformer(composition, output, onProgress)
        }

    private suspend fun runTransformer(
        composition: Composition,
        output: File,
        onProgress: (Int) -> Unit,
    ): File = suspendCancellableCoroutine { cont ->
        val handler = Handler(Looper.getMainLooper())
        val transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    onProgress(100)
                    if (cont.isActive) cont.resume(output)
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException,
                ) {
                    output.delete()
                    if (cont.isActive) cont.resumeWithException(exportException)
                }
            })
            .build()

        val holder = ProgressHolder()
        val poll = object : Runnable {
            override fun run() {
                if (!cont.isActive) return
                if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                    onProgress(holder.progress)
                }
                handler.postDelayed(this, 200)
            }
        }

        transformer.start(composition, output.absolutePath)
        handler.post(poll)
        cont.invokeOnCancellation {
            handler.post {
                transformer.cancel()
                output.delete()
            }
        }
    }
}
