@file:OptIn(UnstableApi::class)

package uz.selerown.fevapp.feature.editor

import android.view.ViewGroup
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import uz.selerown.fevapp.core.designsystem.component.FevIconButton
import uz.selerown.fevapp.core.designsystem.theme.FevTheme
import uz.selerown.fevapp.core.model.Project
import uz.selerown.fevapp.engine.render.TextRenderer

/**
 * Video preview with the text clips drawn on top. Tapping the picture toggles
 * play/pause; the visible play button lives in [PlaybackBar] (or
 * bottom-center when fullscreen).
 */
@Composable
internal fun PreviewArea(
    player: Player,
    project: Project,
    positionUs: Long,
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
        TextLayer(project, positionUs, Modifier.fillMaxSize())
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

/**
 * Text clips visible at [positionUs], drawn by the same [TextRenderer] the
 * export uses, inside a box with the project's frame shape.
 */
@Composable
private fun TextLayer(project: Project, positionUs: Long, modifier: Modifier) {
    val visible = project.textClips.filter { it.isVisibleAt(positionUs) }
    if (visible.isEmpty()) return
    val (rw, rh) = project.frameRatio()
    val ratio = rw.toFloat() / rh
    BoxWithConstraints(modifier) {
        val fitsWidth = ratio > maxWidth / maxHeight
        val width = if (fitsWidth) maxWidth else maxHeight * ratio
        val height = if (fitsWidth) maxWidth / ratio else maxHeight
        Canvas(Modifier.align(Alignment.Center).size(width, height)) {
            drawIntoCanvas { TextRenderer.draw(it.nativeCanvas, size.width.toInt(), size.height.toInt(), visible) }
        }
    }
}
