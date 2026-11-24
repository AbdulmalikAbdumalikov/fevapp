@file:OptIn(UnstableApi::class)

package uz.selerown.fevapp.feature.editor

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import uz.selerown.fevapp.core.designsystem.component.FevIconButton
import uz.selerown.fevapp.core.designsystem.theme.FevTheme

/**
 * Video preview. Tapping the picture toggles play/pause; the visible play
 * button lives in [PlaybackBar] (or bottom-center when fullscreen).
 */
@Composable
internal fun PreviewArea(
    player: Player,
    isPlaying: Boolean,
    fullscreen: Boolean,
    onTogglePlay: () -> Unit,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.background(FevTheme.colors.background)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    this.player = player
                }
            },
            update = { it.player = player },
            onRelease = { it.player = null },
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onTogglePlay,
                ),
        )
        if (fullscreen) {
            PlayButton(isPlaying, onTogglePlay, Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp))
            FevIconButton(
                Icons.Rounded.FullscreenExit,
                stringResource(R.string.editor_exit_fullscreen),
                onToggleFullscreen,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            )
        } else {
            FevIconButton(
                Icons.Rounded.Fullscreen,
                stringResource(R.string.editor_fullscreen),
                onToggleFullscreen,
                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
            )
        }
    }
}
