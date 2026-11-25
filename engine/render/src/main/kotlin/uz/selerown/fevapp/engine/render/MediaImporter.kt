package uz.selerown.fevapp.engine.render

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uz.selerown.fevapp.core.common.IdGenerator
import uz.selerown.fevapp.core.model.AudioClip
import uz.selerown.fevapp.core.model.Clip
import uz.selerown.fevapp.core.model.MediaSource
import uz.selerown.fevapp.core.model.MediaType
import java.io.File
import javax.inject.Inject

/**
 * Turns picked files into timeline clips. Photo Picker URIs become [Clip]s (reads duration and size).
 * Files that cannot be read are skipped. Order of [uris] is preserved.
 */
class MediaImporter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val ids = IdGenerator.Random

    suspend fun import(uris: List<Uri>): List<Clip> = withContext(Dispatchers.IO) {
        uris.mapNotNull { uri -> runCatching { importOne(uri) }.getOrNull() }
    }

    /**
     * Turns a music file (or a video, for its sound) into an audio-lane clip
     * starting at [atUs]. Returns null when the file cannot be read.
     */
    suspend fun importAudio(uri: Uri, atUs: Long): AudioClip? = withContext(Dispatchers.IO) {
        runCatching {
            keepAccess(uri)
            val mime = context.contentResolver.getType(uri).orEmpty()
            val source = readVideo(uri)?.let { if (mime.startsWith("video/")) it else it.copy(type = MediaType.AUDIO) }
                ?: return@runCatching null
            AudioClip(
                id = ids.newId(),
                name = displayName(uri),
                source = source,
                startUs = atUs,
                trimStartUs = 0L,
                trimEndUs = source.durationUs,
            )
        }.getOrNull()
    }

    /**
     * Saves the picture of [clip] at source time [sourceUs] as a JPEG in app
     * storage and returns it as an image source (for freeze frame). Photos are
     * returned as they are. Null when the frame cannot be read.
     */
    suspend fun grabFrame(clip: Clip, sourceUs: Long): MediaSource? = withContext(Dispatchers.IO) {
        if (clip.source.type == MediaType.IMAGE) return@withContext clip.source
        runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, Uri.parse(clip.source.uri))
                var frame = retriever.getFrameAtTime(sourceUs, MediaMetadataRetriever.OPTION_CLOSEST)
                    ?: return@runCatching null
                // Some devices return the frame without the video's rotation: turn it if needed.
                val wantPortrait = clip.source.height > clip.source.width
                if ((frame.height > frame.width) != wantPortrait) {
                    val degrees = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                        ?.toFloatOrNull() ?: 90f
                    val m = Matrix().apply { postRotate(degrees) }
                    frame = Bitmap.createBitmap(frame, 0, 0, frame.width, frame.height, m, true)
                }
                val dir = File(context.filesDir, "frames").apply { mkdirs() }
                val file = File(dir, "frame_${System.currentTimeMillis()}.jpg")
                file.outputStream().use { frame.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                MediaSource(
                    uri = Uri.fromFile(file).toString(),
                    type = MediaType.IMAGE,
                    durationUs = Clip.MAX_IMAGE_DURATION_US,
                    width = frame.width,
                    height = frame.height,
                )
            } finally {
                retriever.release()
            }
        }.getOrNull()
    }

    private fun displayName(uri: Uri): String {
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
        return name?.substringBeforeLast('.')?.takeIf { it.isNotBlank() } ?: "Audio"
    }

    private fun importOne(uri: Uri): Clip? {
        keepAccess(uri)
        val mime = context.contentResolver.getType(uri).orEmpty()
        val source = if (mime.startsWith("image/")) readImage(uri) else readVideo(uri)
        source ?: return null
        val trimEnd = if (source.type == MediaType.IMAGE) Clip.DEFAULT_IMAGE_DURATION_US else source.durationUs
        return Clip(id = ids.newId(), source = source, trimStartUs = 0L, trimEndUs = trimEnd)
    }

    /** Keeps read access after an app restart so saved projects can reopen. */
    private fun keepAccess(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun readVideo(uri: Uri): MediaSource? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: return null
            var w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            var h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull() ?: 0
            if (rotation == 90 || rotation == 270) w = h.also { h = w }
            if (durationMs <= 0) null else MediaSource(uri.toString(), MediaType.VIDEO, durationMs * 1000, w, h)
        } finally {
            retriever.release()
        }
    }

    private fun readImage(uri: Uri): MediaSource {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        return MediaSource(
            uri = uri.toString(),
            type = MediaType.IMAGE,
            durationUs = Clip.MAX_IMAGE_DURATION_US,
            width = options.outWidth.coerceAtLeast(0),
            height = options.outHeight.coerceAtLeast(0),
        )
    }
}
