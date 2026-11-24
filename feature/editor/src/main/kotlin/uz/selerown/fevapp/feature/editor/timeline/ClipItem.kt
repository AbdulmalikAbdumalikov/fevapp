package uz.selerown.fevapp.feature.editor.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.video.videoFrameMicros
import uz.selerown.fevapp.core.common.TimeFormat
import uz.selerown.fevapp.core.designsystem.theme.FevTheme
import uz.selerown.fevapp.core.model.Clip
import uz.selerown.fevapp.core.model.MediaType
import uz.selerown.fevapp.feature.editor.R
import uz.selerown.fevapp.feature.editor.TrimDraft
import kotlin.math.ceil
import kotlin.math.roundToInt

private val ClipShape = RoundedCornerShape(6.dp)
private val HandleWidth = 22.dp

/**
 * One clip on the main track.
 * - tap: select
 * - long-press + drag: reorder
 * - drag a handle (when selected): trim (live draft, committed on release)
 */
@Composable
internal fun ClipItem(
    clip: Clip,
    baseXPx: Float,
    pxPerUs: Float,
    viewportWidthPx: Float,
    selected: Boolean,
    draft: TrimDraft?,
    dragOffsetPx: Float,
    onTap: () -> Unit,
    onReorderStart: () -> Unit,
    onReorderDrag: (Float) -> Unit,
    onReorderEnd: () -> Unit,
    onTrimDrag: (startUs: Long, endUs: Long) -> Unit,
    onTrimEnd: () -> Unit,
) {
    val c = FevTheme.colors
    val density = LocalDensity.current
    val trimStart = draft?.startUs ?: clip.trimStartUs
    val trimEnd = draft?.endUs ?: clip.trimEndUs
    // Left-trim moves the left edge with the finger; the gap closes on release.
    val xPx = baseXPx + (trimStart - clip.trimStartUs) / clip.speed * pxPerUs + dragOffsetPx
    val widthPx = ((trimEnd - trimStart) / clip.speed * pxPerUs).coerceAtLeast(2f)

    // Skip clips that are completely off screen.
    if (xPx > viewportWidthPx + 200f || xPx + widthPx < -200f) return

    val tap by rememberUpdatedState(onTap)
    val reorderStart by rememberUpdatedState(onReorderStart)
    val reorderDrag by rememberUpdatedState(onReorderDrag)
    val reorderEnd by rememberUpdatedState(onReorderEnd)
    val dragging = dragOffsetPx != 0f

    Box(
        Modifier
            .offset { IntOffset(xPx.roundToInt(), 0) }
            .width(with(density) { widthPx.toDp() })
            .fillMaxHeight()
            .zIndex(if (dragging || selected) 1f else 0f)
            .alpha(if (dragging) 0.85f else 1f)
            .clip(ClipShape)
            .background(c.clipFill)
            .then(if (selected) Modifier.border(2.dp, c.accent, ClipShape) else Modifier)
            .pointerInput(clip.id) { detectTapGestures(onTap = { tap() }) }
            .pointerInput(clip.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { reorderStart() },
                    onDrag = { change, amount ->
                        change.consume()
                        reorderDrag(amount.x)
                    },
                    onDragEnd = { reorderEnd() },
                    onDragCancel = { reorderEnd() },
                )
            },
    ) {
        ClipThumbnails(clip, trimStart, widthPx, pxPerUs)
        // Duration label: single line, and only when there is room for it
        // (between the trim handles when the clip is selected).
        val reservedPx = with(density) { (if (selected) HandleWidth * 2 else 0.dp).toPx() + 40.dp.toPx() }
        if (widthPx >= reservedPx) {
            Text(
                TimeFormat.short(((trimEnd - trimStart) / clip.speed).toLong()),
                style = MaterialTheme.typography.labelSmall,
                color = c.textPrimary,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = if (selected) HandleWidth + 2.dp else 4.dp, bottom = 2.dp)
                    .background(c.background.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp),
            )
        }
        if (selected) {
            TrimHandle(clip, isStart = true, pxPerUs, onTrimDrag, onTrimEnd, stringResource(R.string.editor_trim_start))
            TrimHandle(clip, isStart = false, pxPerUs, onTrimDrag, onTrimEnd, stringResource(R.string.editor_trim_end))
        }
    }
}

/** Accent-colored grip on a clip edge; dragging it trims that side. */
@Composable
private fun BoxScope.TrimHandle(
    clip: Clip,
    isStart: Boolean,
    pxPerUs: Float,
    onTrimDrag: (Long, Long) -> Unit,
    onTrimEnd: () -> Unit,
    description: String,
) {
    val c = FevTheme.colors
    val currentClip by rememberUpdatedState(clip)
    val scale by rememberUpdatedState(pxPerUs)
    val drag by rememberUpdatedState(onTrimDrag)
    val end by rememberUpdatedState(onTrimEnd)
    Box(
        Modifier
            .align(if (isStart) Alignment.CenterStart else Alignment.CenterEnd)
            .width(HandleWidth)
            .fillMaxHeight()
            .background(c.accent)
            .semantics { contentDescription = description }
            .pointerInput(clip.id, isStart) {
                var startUs = 0L
                var endUs = 0L
                detectHorizontalDragGestures(
                    onDragStart = {
                        startUs = currentClip.trimStartUs
                        endUs = currentClip.trimEndUs
                    },
                    onDragEnd = { end() },
                    onDragCancel = { end() },
                    onHorizontalDrag = { change, dx ->
                        change.consume()
                        val deltaUs = (dx / scale * currentClip.speed).toLong()
                        if (isStart) startUs += deltaUs else endUs += deltaUs
                        drag(startUs, endUs)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.width(3.dp).height(20.dp).background(c.onAccent, RoundedCornerShape(2.dp)))
    }
}

/** Frame strip: one thumbnail per tile, taken at the time under that tile. */
@Composable
private fun ClipThumbnails(clip: Clip, trimStartUs: Long, widthPx: Float, pxPerUs: Float) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val tileMinPx = with(density) { 56.dp.toPx() }
    val count = ceil(widthPx / tileMinPx).toInt().coerceIn(1, 40)
    val tilePx = maxOf(tileMinPx, widthPx / count)
    val tileDp = with(density) { tilePx.toDp() }
    Row(Modifier.fillMaxSize()) {
        repeat(count) { i ->
            // Quantize to 0.2 s so trimming reuses cached frames.
            val frameUs = (trimStartUs + (i * tilePx / pxPerUs * clip.speed).toLong()) / 200_000 * 200_000
            val request = ImageRequest.Builder(context)
                .data(clip.source.uri)
                .size(tilePx.roundToInt())
                .apply { if (clip.source.type == MediaType.VIDEO) videoFrameMicros(frameUs) }
                .build()
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(tileDp).fillMaxHeight(),
            )
        }
    }
}
