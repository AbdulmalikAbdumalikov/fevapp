@file:OptIn(UnstableApi::class)

package uz.selerown.fevapp.feature.editor

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.TextureView
import android.view.ViewGroup
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import kotlin.math.roundToInt
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.key
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.border
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
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
    colorMatrix: FloatArray?,
    selectedTextId: String?,
    textDraft: TextDraft?,
    onTextDrag: (id: String, x: Float, y: Float) -> Unit,
    onTextDragEnd: () -> Unit,
    overlayPlayers: Map<String, Player>,
    selectedOverlayId: String?,
    overlayDraft: TextDraft?,
    onOverlayDrag: (id: String, x: Float, y: Float) -> Unit,
    onOverlayDragEnd: () -> Unit,
    isPlaying: Boolean,
    fullscreen: Boolean,
    onTogglePlay: () -> Unit,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (rw, rh) = project.frameRatio()
    val ratio = rw.toFloat() / rh
    BoxWithConstraints(modifier.background(FevTheme.colors.background)) {
        // The video frame has the project's output shape (like the export):
        // clips are fitted inside it and the bars are black, as in the file.
        val fitsWidth = ratio > maxWidth / maxHeight
        val frameWidth = if (fitsWidth) maxWidth else maxHeight * ratio
        val frameHeight = if (fitsWidth) maxWidth / ratio else maxHeight
        Box(
            Modifier
                .align(Alignment.Center)
                .size(frameWidth, frameHeight)
                .background(Color(project.backgroundColor)), // video content color, not a UI color
        ) {
        AndroidView(
            factory = { ctx ->
                (LayoutInflater.from(ctx).inflate(R.layout.preview_player_view, null) as PlayerView).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    this.player = player
                }
            },
            update = { view ->
                view.player = player
                // Live filter: color matrix as a layer paint on the TextureView.
                (view.videoSurfaceView as? TextureView)?.setLayerPaint(
                    colorMatrix?.let { Paint().apply { colorFilter = ColorMatrixColorFilter(ColorMatrix(it)) } },
                )
            },
            onRelease = { it.player = null },
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onTogglePlay,
                ),
        )
        OverlayLayer(
            project = project,
            positionUs = positionUs,
            players = overlayPlayers,
            selectedId = selectedOverlayId,
            draft = overlayDraft,
            onDrag = onOverlayDrag,
            onDragEnd = onOverlayDragEnd,
        )
        TextLayer(
            project = project,
            positionUs = positionUs,
            selectedTextId = selectedTextId,
            draft = textDraft,
            onDrag = onTextDrag,
            onDragEnd = onTextDragEnd,
            onTap = onTogglePlay,
            modifier = Modifier.fillMaxSize(),
        )
        }
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
private fun TextLayer(
    project: Project,
    positionUs: Long,
    selectedTextId: String?,
    draft: TextDraft?,
    onDrag: (String, Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    onTap: () -> Unit,
    modifier: Modifier,
) {
    val visible = project.textClips
        .filter { it.isVisibleAt(positionUs) }
        .map { clip ->
            if (draft?.id == clip.id) clip.copy(style = clip.style.copy(positionX = draft.x, positionY = draft.y)) else clip
        }
    if (visible.isEmpty()) return
    // A selected, visible text can be dragged with a finger (buttons in its sheet do the same).
    val draggable = visible.firstOrNull { it.id == selectedTextId }
    val latest by rememberUpdatedState(draggable)
    val drag by rememberUpdatedState(onDrag)
    val dragEnd by rememberUpdatedState(onDragEnd)
    val tap by rememberUpdatedState(onTap)
    val (rw, rh) = project.frameRatio()
    val ratio = rw.toFloat() / rh
    BoxWithConstraints(modifier) {
        val fitsWidth = ratio > maxWidth / maxHeight
        val width = if (fitsWidth) maxWidth else maxHeight * ratio
        val height = if (fitsWidth) maxWidth / ratio else maxHeight
        val gestures = if (draggable == null) {
            Modifier
        } else {
            Modifier
                .pointerInput(Unit) { detectTapGestures { tap() } }
                .pointerInput(Unit) {
                    var x = 0f
                    var y = 0f
                    detectDragGestures(
                        onDragStart = {
                            latest?.style?.let { x = it.positionX; y = it.positionY }
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            val clip = latest ?: return@detectDragGestures
                            x = (x + amount.x / size.width).coerceIn(0f, 1f)
                            y = (y + amount.y / size.height).coerceIn(0f, 1f)
                            drag(clip.id, x, y)
                        },
                        onDragEnd = { dragEnd() },
                        onDragCancel = { dragEnd() },
                    )
                }
        }
        Canvas(Modifier.align(Alignment.Center).size(width, height).then(gestures)) {
            drawIntoCanvas { TextRenderer.draw(it.nativeCanvas, size.width.toInt(), size.height.toInt(), visible, positionUs) }
        }
    }
}

/**
 * Picture-in-picture clips visible at [positionUs], each in its own small
 * player view placed like in the export. The selected one has an accent
 * border and can be dragged (the "Joylashuv" sheet does the same with sliders).
 */
@Composable
private fun BoxScope.OverlayLayer(
    project: Project,
    positionUs: Long,
    players: Map<String, Player>,
    selectedId: String?,
    draft: TextDraft?,
    onDrag: (String, Float, Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val c = FevTheme.colors
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.matchParentSize()) {
        val frameW = constraints.maxWidth.toFloat()
        val frameH = constraints.maxHeight.toFloat()
        project.overlayClips.filter { it.isVisibleAt(positionUs) || it.id == selectedId && draft?.id == it.id }.forEach { clip ->
            val player = players[clip.id] ?: return@forEach
            val cx = if (draft?.id == clip.id) draft.x else clip.centerX
            val cy = if (draft?.id == clip.id) draft.y else clip.centerY
            val wPx = frameW * clip.scale
            val aspect = clip.source.height.toFloat().coerceAtLeast(1f) / clip.source.width.toFloat().coerceAtLeast(1f)
            val hPx = wPx * aspect
            key(clip.id) {
                val latestClip by rememberUpdatedState(clip)
                val drag by rememberUpdatedState(onDrag)
                val end by rememberUpdatedState(onDragEnd)
                val selected = clip.id == selectedId
                Box(
                    Modifier
                        .offset { IntOffset((cx * frameW - wPx / 2).roundToInt(), (cy * frameH - hPx / 2).roundToInt()) }
                        .size(with(density) { wPx.toDp() }, with(density) { hPx.toDp() })
                        .then(if (selected) Modifier.border(2.dp, c.accent) else Modifier)
                        .then(
                            if (!selected) {
                                Modifier
                            } else {
                                Modifier.pointerInput(clip.id) {
                                    var x = 0f
                                    var y = 0f
                                    detectDragGestures(
                                        onDragStart = { x = latestClip.centerX; y = latestClip.centerY },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            x = (x + amount.x / frameW).coerceIn(0f, 1f)
                                            y = (y + amount.y / frameH).coerceIn(0f, 1f)
                                            drag(latestClip.id, x, y)
                                        },
                                        onDragEnd = { end() },
                                        onDragCancel = { end() },
                                    )
                                }
                            },
                        ),
                ) {
                    AndroidView(
                        factory = { ctx ->
                            (LayoutInflater.from(ctx).inflate(R.layout.preview_player_view, null) as PlayerView).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                )
                                this.player = player
                            }
                        },
                        update = { it.player = player },
                        onRelease = { it.player = null },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
