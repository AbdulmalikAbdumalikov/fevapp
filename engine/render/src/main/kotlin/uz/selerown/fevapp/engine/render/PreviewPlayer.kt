package uz.selerown.fevapp.engine.render

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import uz.selerown.fevapp.core.model.Clip
import uz.selerown.fevapp.core.model.Project

/**
 * Preview playback of the main track with ExoPlayer (one playlist item per
 * clip). Exposes the playhead in TIMELINE microseconds so the UI never deals
 * with playlist indexes. Must be used from the main thread.
 */
class PreviewPlayer(context: Context) {

    val player: ExoPlayer = ExoPlayer.Builder(context).build()

    private var clips: List<Clip> = emptyList()
    private var durationUs: Long = 0L

    private val _positionUs = MutableStateFlow(0L)
    val positionUs: StateFlow<Long> = _positionUs.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    player.pause()
                    _positionUs.value = durationUs
                }
            }
        })
    }

    /** Loads the project's main track, keeping the playhead at [timeUs]. */
    fun setProject(project: Project, timeUs: Long) {
        val newClips = project.mainTrack.clips
        clips = newClips
        durationUs = project.durationUs
        val clamped = timeUs.coerceIn(0L, durationUs)
        _positionUs.value = clamped
        if (newClips.isEmpty()) {
            player.clearMediaItems()
            return
        }
        val (index, posMs) = TimelineMapper.locate(newClips, clamped)
        player.setMediaItems(newClips.map(TimelineMapper::toMediaItem), index, posMs)
        player.prepare()
    }

    fun play() {
        if (clips.isEmpty()) return
        if (_positionUs.value >= durationUs - 50_000) seekTo(0L)
        player.play()
    }

    fun pause() = player.pause()

    fun togglePlay() = if (player.isPlaying) pause() else play()

    /** Moves the playhead (used while scrubbing the timeline). */
    fun seekTo(timeUs: Long) {
        val clamped = timeUs.coerceIn(0L, durationUs)
        _positionUs.value = clamped
        if (clips.isEmpty()) return
        val (index, posMs) = TimelineMapper.locate(clips, clamped)
        player.seekTo(index, posMs)
    }

    /** Call regularly (~30x/s) while playing to update [positionUs]. */
    fun tick() {
        if (!player.isPlaying || clips.isEmpty()) return
        _positionUs.value = TimelineMapper.toTimelineUs(clips, player.currentMediaItemIndex, player.currentPosition)
    }

    fun release() = player.release()
}
