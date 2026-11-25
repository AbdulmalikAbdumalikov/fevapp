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
import uz.selerown.fevapp.core.model.OverlayClip
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
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
    positionUs: () -> Long,
    colorMatrix: FloatArray?,
    selectedTextId: String?,
    textDraft: TextDraft?,
    onTextTransform: (id: String, x: Float, y: Float, size: Float) -> Unit,
    onTextTransformEnd: () -> Unit,
    overlayPlayers: Map<String, Player>,
    selectedOverlayId: String?,
    overlayDraft: TextDraft?,
    onOverlayTransform: (id: String, x: Float, y: Float, scale: Float) -> Unit,
    onOverlayTransformEnd: () -> Unit,
    onSelectElement: (Selection) -> Unit,
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
            modifier = Modifier.fillMaxSize(),
        )
        // Same order as the export: text, then picture-in-picture above it.
        TextLayer(
            project = project,
            position = positionUs,
            draft = textDraft,
            modifier = Modifier.fillMaxSize(),
        )
        OverlayLayer(
            project = project,
            position = positionUs,
            players = overlayPlayers,
            selectedId = selectedOverlayId,
            draft = overlayDraft,
        )
        ElementGestures(
            project = project,
            position = positionUs,
            selectedTextId = selectedTextId,
            selectedOverlayId = selectedOverlayId,
            onSelect = onSelectElement,
            onTogglePlay = onTogglePlay,
            onTextTransform = onTextTransform,
            onTextTransformEnd = onTextTransformEnd,
            onOverlayTransform = onOverlayTransform,
            onOverlayTransformEnd = onOverlayTransformEnd,
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
    position: () -> Long,
    draft: TextDraft?,
    modifier: Modifier,
) {
    val positionUs = position()
    val visible = project.textClips
        .filter { it.isVisibleAt(positionUs) }
        .map { clip ->
            if (draft?.id == clip.id) {
                clip.copy(style = clip.style.copy(positionX = draft.x, positionY = draft.y, sizeFraction = draft.size))
            } else {
                clip
            }
        }
    if (visible.isEmpty()) return
    val (rw, rh) = project.frameRatio()
    val ratio = rw.toFloat() / rh
    BoxWithConstraints(modifier) {
        val fitsWidth = ratio > maxWidth / maxHeight
        val width = if (fitsWidth) maxWidth else maxHeight * ratio
        val height = if (fitsWidth) maxWidth / ratio else maxHeight
        Canvas(Modifier.align(Alignment.Center).size(width, height)) {
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
    position: () -> Long,
    players: Map<String, Player>,
    selectedId: String?,
    draft: TextDraft?,
) {
    val c = FevTheme.colors
    val density = LocalDensity.current
    val positionUs = position()
    BoxWithConstraints(Modifier.matchParentSize()) {
        val frameW = constraints.maxWidth.toFloat()
        val frameH = constraints.maxHeight.toFloat()
        project.overlayClips.filter { it.isVisibleAt(positionUs) || draft?.id == it.id }.forEach { clip ->
            val player = players[clip.id] ?: return@forEach
            val shown = if (draft?.id == clip.id) clip.copy(centerX = draft.x, centerY = draft.y, scale = draft.size) else clip
            val rect = overlayRect(shown, frameW, frameH)
            key(clip.id) {
                Box(
                    Modifier
                        .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
                        .size(with(density) { rect.width().toDp() }, with(density) { rect.height().toDp() })
                        .then(if (clip.id == selectedId) Modifier.border(2.dp, c.accent) else Modifier),
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

/** Overlay rectangle in frame pixels (same math as the export). */
private fun overlayRect(clip: OverlayClip, frameW: Float, frameH: Float): android.graphics.RectF {
    val w = frameW * clip.scale
    val aspect = clip.source.height.toFloat().coerceAtLeast(1f) / clip.source.width.toFloat().coerceAtLeast(1f)
    val h = w * aspect
    val cx = clip.centerX * frameW
    val cy = clip.centerY * frameH
    return android.graphics.RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
}

/**
 * Touch handling for everything drawn on the preview:
 * - tap an overlay/text: select it; tap elsewhere: play/pause
 * - one finger on an element: move it (it is selected at the same time)
 * - two fingers: resize the element under them (or the selected one)
 * Each gesture is one undo step. Sheets offer the same with sliders/chips.
 */
@Composable
private fun ElementGestures(
    project: Project,
    position: () -> Long,
    selectedTextId: String?,
    selectedOverlayId: String?,
    onSelect: (Selection) -> Unit,
    onTogglePlay: () -> Unit,
    onTextTransform: (String, Float, Float, Float) -> Unit,
    onTextTransformEnd: () -> Unit,
    onOverlayTransform: (String, Float, Float, Float) -> Unit,
    onOverlayTransformEnd: () -> Unit,
) {
    val latestProject by rememberUpdatedState(project)
    val selected by rememberUpdatedState(
        selectedOverlayId?.let { Selection(Lane.OVERLAY, it) } ?: selectedTextId?.let { Selection(Lane.TEXT, it) },
    )
    val select by rememberUpdatedState(onSelect)
    val toggle by rememberUpdatedState(onTogglePlay)
    val textMove by rememberUpdatedState(onTextTransform)
    val textEnd by rememberUpdatedState(onTextTransformEnd)
    val overlayMove by rememberUpdatedState(onOverlayTransform)
    val overlayEnd by rememberUpdatedState(onOverlayTransformEnd)
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    val timeUs = position()
                    val proj = latestProject
                    val hit = hitTest(proj, timeUs, down.position.x, down.position.y, w, h)
                    var target = hit
                    var x = 0f
                    var y = 0f
                    var s = 0f
                    fun loadTarget(sel: Selection?) {
                        when (sel?.lane) {
                            Lane.TEXT -> proj.textClips.firstOrNull { it.id == sel.id }?.let {
                                x = it.style.positionX; y = it.style.positionY; s = it.style.sizeFraction
                            }
                            Lane.OVERLAY -> proj.overlayClips.firstOrNull { it.id == sel.id }?.let {
                                x = it.centerX; y = it.centerY; s = it.scale
                            }
                            else -> Unit
                        }
                    }
                    loadTarget(target)
                    var moved = false
                    var totalPan = Offset.Zero
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break
                        if (target == null && pressed.size >= 2) {
                            target = selected // pinch anywhere resizes the selected element
                            loadTarget(target)
                        }
                        val t = target ?: continue
                        val pan = event.calculatePan()
                        val zoom = event.calculateZoom()
                        totalPan += pan
                        if (!moved && (totalPan.getDistance() > viewConfiguration.touchSlop || zoom != 1f)) {
                            moved = true
                            if (t != selected) select(t)
                        }
                        if (!moved) continue
                        x = (x + pan.x / w).coerceIn(0f, 1f)
                        y = (y + pan.y / h).coerceIn(0f, 1f)
                        s *= zoom
                        if (t.lane == Lane.TEXT) textMove(t.id, x, y, s) else overlayMove(t.id, x, y, s)
                        event.changes.forEach { it.consume() }
                    }
                    when {
                        moved && target?.lane == Lane.TEXT -> textEnd()
                        moved && target?.lane == Lane.OVERLAY -> overlayEnd()
                        hit != null -> select(hit)
                        else -> toggle()
                    }
                }
            },
    )
}

/** Topmost element under (px, py): overlays (drawn last) before texts. */
private fun hitTest(project: Project, timeUs: Long, px: Float, py: Float, w: Float, h: Float): Selection? {
    project.overlayClips.asReversed().firstOrNull { it.isVisibleAt(timeUs) && overlayRect(it, w, h).contains(px, py) }
        ?.let { return Selection(Lane.OVERLAY, it.id) }
    project.textClips.asReversed().firstOrNull {
        it.isVisibleAt(timeUs) && TextRenderer.bounds(it, w.toInt(), h.toInt()).contains(px, py)
    }?.let { return Selection(Lane.TEXT, it.id) }
    return null
}
