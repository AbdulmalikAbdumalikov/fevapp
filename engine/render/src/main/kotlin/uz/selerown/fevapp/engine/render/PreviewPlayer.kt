package uz.selerown.fevapp.engine.render

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import uz.selerown.fevapp.core.model.AudioClip
import uz.selerown.fevapp.core.model.Clip
import uz.selerown.fevapp.core.model.Project
import kotlin.math.abs

/**
 * Preview playback with ExoPlayer.
 * - main track: one player, one playlist item per clip (clip volume applied)
 * - audio lane: one small sound-only player per audio clip, kept in sync with
 *   the main playhead on every [tick] (volume and fades applied live)
 * Exposes the playhead in TIMELINE microseconds so the UI never deals with
 * playlist indexes. Must be used from the main thread.
 */
class PreviewPlayer(private val context: Context) {

    val player: ExoPlayer = ExoPlayer.Builder(context).build()

    /**
     * Color matrix (android.graphics.ColorMatrix layout) of the clip being
     * shown, or null for unchanged colors. The UI applies it as a color filter
     * on the video TextureView — cheaper and more robust than GL effects.
     */
    private val _colorMatrix = MutableStateFlow<FloatArray?>(null)
    val colorMatrix: StateFlow<FloatArray?> = _colorMatrix.asStateFlow()

    /** True while recording a voice-over: the preview must not be heard by the mic. */
    var muted: Boolean = false

    private var appliedClipStyle: Any? = null

    private var clips: List<Clip> = emptyList()
    private var durationUs: Long = 0L
    private val lanePlayers = mutableMapOf<String, LanePlayer>()

    private val _positionUs = MutableStateFlow(0L)
    val positionUs: StateFlow<Long> = _positionUs.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                if (!isPlaying) pauseLanes()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    player.pause()
                    _positionUs.value = durationUs
                }
            }
        })
    }

    /** Loads the project, keeping the playhead at [timeUs]. */
    fun setProject(project: Project, timeUs: Long) {
        val newClips = project.mainTrack.clips
        clips = newClips
        durationUs = project.durationUs
        val clamped = timeUs.coerceIn(0L, durationUs)
        _positionUs.value = clamped
        updateLanePlayers(project.audioClips)
        if (newClips.isEmpty()) {
            player.clearMediaItems()
            return
        }
        val (index, posMs) = TimelineMapper.locate(newClips, clamped)
        player.setMediaItems(newClips.map(TimelineMapper::toMediaItem), index, posMs)
        player.prepare()
        appliedClipStyle = null
        applyClipStyle()
    }

    fun play() {
        if (clips.isEmpty()) return
        if (_positionUs.value >= durationUs - 50_000) seekTo(0L)
        player.play()
    }

    fun pause() {
        player.pause()
        pauseLanes()
    }

    fun togglePlay() = if (player.isPlaying) pause() else play()

    /** Moves the playhead (used while scrubbing the timeline). */
    fun seekTo(timeUs: Long) {
        val clamped = timeUs.coerceIn(0L, durationUs)
        _positionUs.value = clamped
        if (clips.isEmpty()) return
        val (index, posMs) = TimelineMapper.locate(clips, clamped)
        player.seekTo(index, posMs)
    }

    /** Call regularly (~30x/s) to update [positionUs] and keep the audio lane in sync. */
    fun tick() {
        if (clips.isEmpty()) return
        if (player.isPlaying) {
            _positionUs.value = TimelineMapper.toTimelineUs(clips, player.currentMediaItemIndex, player.currentPosition)
        }
        applyClipStyle()
        syncLanes()
    }

    fun release() {
        player.release()
        lanePlayers.values.forEach { it.exo.release() }
        lanePlayers.clear()
    }

    // ---- Per-clip volume, speed and filter -------------------------------

    /**
     * Shows [clip]'s speed/filter instead of the real one (live filter preview
     * in the tool sheet). Null goes back to the project's values.
     */
    fun previewClipStyle(clip: Clip?) {
        styleOverride = clip
        applyClipStyle()
    }

    private var styleOverride: Clip? = null

    private fun applyClipStyle() {
        val clip = styleOverride ?: clips.getOrNull(player.currentMediaItemIndex)
        player.volume = if (muted) 0f else clip?.volume ?: 1f
        val style = clip?.let { Triple(it.speed, it.look, it.color) }
        if (style == appliedClipStyle) return
        appliedClipStyle = style
        player.playbackParameters = PlaybackParameters(clip?.speed ?: 1f)
        _colorMatrix.value = clip?.let(ColorMath::matrixFor)?.let(ColorMath::toAndroidColorMatrix)
    }

    // ---- Audio lane -------------------------------------------------------

    private class LanePlayer(var clip: AudioClip, val exo: ExoPlayer)

    private fun updateLanePlayers(audio: List<AudioClip>) {
        val ids = audio.map { it.id }.toSet()
        lanePlayers.keys.filter { it !in ids }.forEach { lanePlayers.remove(it)?.exo?.release() }
        audio.forEach { clip ->
            val existing = lanePlayers[clip.id]
            when {
                existing == null -> lanePlayers[clip.id] = LanePlayer(clip, createLanePlayer(clip))
                existing.clip.sameSound(clip) -> existing.clip = clip // only volume/position changed
                else -> {
                    existing.clip = clip
                    existing.exo.pause()
                    existing.exo.setMediaItem(TimelineMapper.toMediaItem(clip))
                    existing.exo.prepare()
                }
            }
        }
    }

    private fun createLanePlayer(clip: AudioClip): ExoPlayer = ExoPlayer.Builder(context).build().apply {
        // Sound only: never decode the picture of a video source.
        trackSelectionParameters = trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
            .build()
        setMediaItem(TimelineMapper.toMediaItem(clip))
        prepare()
    }

    /** Starts, stops, re-aligns and sets the volume of every audio-lane player. */
    private fun syncLanes() {
        val timeUs = _positionUs.value
        val playing = player.isPlaying
        lanePlayers.values.forEach { lane ->
            val clip = lane.clip
            val offsetUs = timeUs - clip.startUs
            val inside = playing && offsetUs >= 0 && offsetUs < clip.durationUs && timeUs < durationUs
            val exo = lane.exo
            if (!inside) {
                if (exo.playWhenReady) exo.pause()
                return@forEach
            }
            val targetMs = offsetUs / 1000
            if (!exo.playWhenReady) {
                exo.seekTo(targetMs)
                exo.play()
            } else if (exo.isPlaying && abs(exo.currentPosition - targetMs) > MAX_DRIFT_MS) {
                exo.seekTo(targetMs)
            }
            exo.volume = if (muted) 0f else clip.gainAt(offsetUs)
        }
    }

    private fun pauseLanes() = lanePlayers.values.forEach { if (it.exo.playWhenReady) it.exo.pause() }

    private fun AudioClip.sameSound(other: AudioClip) =
        source.uri == other.source.uri && trimStartUs == other.trimStartUs && trimEndUs == other.trimEndUs

    private companion object {
        const val MAX_DRIFT_MS = 200L
    }
}
