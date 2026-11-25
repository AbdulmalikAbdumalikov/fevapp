package uz.selerown.fevapp.feature.editor.timeline

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import uz.selerown.fevapp.core.designsystem.theme.FevTheme
import uz.selerown.fevapp.engine.render.Waveform
import kotlin.math.roundToInt

private val Shape = RoundedCornerShape(6.dp)
private val HandleWidth = 18.dp

/** What a lane item shows. Times are the COMMITTED values (drafts come separately). */
internal data class LaneItemModel(
    val id: String,
    val label: String,
    val description: String,
    val startUs: Long,
    val endUs: Long,
    /** Audio only: source file (for the waveform) and where the used window starts in it. */
    val sourceUri: String? = null,
    val sourceStartUs: Long = 0L,
    /** Keyframe offsets from the clip start (drawn as small diamonds). */
    val keyframeOffsets: List<Long> = emptyList(),
)

/**
 * A text or audio clip on its lane.
 * - tap: select
 * - long-press + drag: move along the timeline
 * - drag a handle (when selected): change start / end
 * Every gesture also has a toolbar button (Shu yerga, Boshi/Oxiri shu yerda).
 */
@Composable
internal fun LaneItem(
    model: LaneItemModel,
    icon: ImageVector,
    fill: Color,
    xOfUs: (Long) -> Float,
    pxPerUs: Float,
    visibleRange: ClosedFloatingPointRange<Float>,
    selected: Boolean,
    draftStartUs: Long?,
    draftEndUs: Long?,
    waveform: Waveform? = null,
    onTap: () -> Unit,
    onMove: (startUs: Long) -> Unit,
    onTrim: (startUs: Long, endUs: Long) -> Unit,
    onDragEnd: () -> Unit,
) {
    val c = FevTheme.colors
    val density = LocalDensity.current
    val startUs = draftStartUs ?: model.startUs
    val endUs = draftEndUs ?: model.endUs
    val xPx = xOfUs(startUs)
    val widthPx = ((endUs - startUs) * pxPerUs).coerceAtLeast(2f)
    if (xPx > visibleRange.endInclusive || xPx + widthPx < visibleRange.start) return

    val current by rememberUpdatedState(model)
    val scale by rememberUpdatedState(pxPerUs)
    val tap by rememberUpdatedState(onTap)
    val move by rememberUpdatedState(onMove)
    val end by rememberUpdatedState(onDragEnd)

    Box(
        Modifier
            .offset { IntOffset(xPx.roundToInt(), 0) }
            .width(with(density) { widthPx.toDp() })
            .fillMaxHeight()
            .zIndex(if (selected) 1f else 0f)
            .clip(Shape)
            .background(fill)
            .then(if (selected) Modifier.border(2.dp, c.accent, Shape) else Modifier)
            .semantics { contentDescription = model.description }
            .pointerInput(model.id) { detectTapGestures(onTap = { tap() }) }
            .pointerInput(model.id) {
                var dx = 0f
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        dx = 0f
                        tap()
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        dx += amount.x
                        move(current.startUs + (dx / scale).toLong())
                    },
                    onDragEnd = { end() },
                    onDragCancel = { end() },
                )
            },
    ) {
        if (waveform != null) {
            // A left trim moves the source window; a move keeps it.
            val moved = endUs - startUs == model.endUs - model.startUs
            val sourceStart = model.sourceStartUs + if (moved) 0L else startUs - model.startUs
            WaveformBars(waveform, sourceStart, pxPerUs, visibleFromPx = visibleRange.start - xPx, visibleToPx = visibleRange.endInclusive - xPx, c.textPrimary.copy(alpha = 0.35f))
        }
        Row(
            Modifier
                .fillMaxSize()
                .padding(start = if (selected) HandleWidth + 4.dp else 6.dp, end = if (selected) HandleWidth else 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = c.textPrimary, modifier = Modifier.size(16.dp))
            Text(
                model.label,
                style = MaterialTheme.typography.labelSmall,
                color = c.textPrimary,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        if (model.keyframeOffsets.isNotEmpty()) {
            val diamond = c.textPrimary
            Canvas(Modifier.fillMaxSize()) {
                val half = 4.dp.toPx()
                val cy = size.height - half - 2.dp.toPx()
                model.keyframeOffsets.forEach { off ->
                    val cx = off * pxPerUs
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(cx, cy - half); lineTo(cx + half, cy); lineTo(cx, cy + half); lineTo(cx - half, cy); close()
                    }
                    drawPath(path, diamond)
                }
            }
        }
        if (selected) {
            EdgeHandle(model.id, isStart = true, current = { current }, scale = { scale }, onTrim, onDragEnd)
            EdgeHandle(model.id, isStart = false, current = { current }, scale = { scale }, onTrim, onDragEnd)
        }
    }
}

/** Vertical bars every 3 dp; only the part inside the viewport is drawn. */
@Composable
private fun WaveformBars(
    waveform: Waveform,
    sourceStartUs: Long,
    pxPerUs: Float,
    visibleFromPx: Float,
    visibleToPx: Float,
    color: Color,
) {
    Canvas(Modifier.fillMaxSize()) {
        val step = 3.dp.toPx()
        val centerY = size.height / 2
        var x = (maxOf(0f, visibleFromPx) / step).toInt() * step
        val end = minOf(size.width, visibleToPx)
        while (x < end) {
            val half = waveform.peakAt(sourceStartUs + (x / pxPerUs).toLong()) * size.height * 0.4f
            drawLine(color, Offset(x, centerY - half - 1f), Offset(x, centerY + half + 1f), strokeWidth = step * 0.6f)
            x += step
        }
    }
}

@Composable
private fun BoxScope.EdgeHandle(
    id: String,
    isStart: Boolean,
    current: () -> LaneItemModel,
    scale: () -> Float,
    onTrim: (Long, Long) -> Unit,
    onDragEnd: () -> Unit,
) {
    val c = FevTheme.colors
    val trim by rememberUpdatedState(onTrim)
    val end by rememberUpdatedState(onDragEnd)
    Box(
        Modifier
            .align(if (isStart) Alignment.CenterStart else Alignment.CenterEnd)
            .width(HandleWidth)
            .fillMaxHeight()
            .background(c.accent)
            .pointerInput(id, isStart) {
                var dx = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dx = 0f },
                    onDragEnd = { end() },
                    onDragCancel = { end() },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        dx += amount
                        val model = current()
                        val delta = (dx / scale()).toLong()
                        if (isStart) trim(model.startUs + delta, model.endUs) else trim(model.startUs, model.endUs + delta)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.width(3.dp).height(16.dp).background(c.onAccent, RoundedCornerShape(2.dp)))
    }
}
