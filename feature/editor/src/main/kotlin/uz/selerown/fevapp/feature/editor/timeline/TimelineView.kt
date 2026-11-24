package uz.selerown.fevapp.feature.editor.timeline

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import uz.selerown.fevapp.core.designsystem.theme.FevTheme
import uz.selerown.fevapp.core.model.Project
import uz.selerown.fevapp.feature.editor.R
import uz.selerown.fevapp.feature.editor.TrimDraft
import kotlin.math.abs
import kotlin.math.roundToInt

private val RulerHeight = 22.dp
private val MainTrackHeight = 64.dp
private val LaneHeight = 44.dp

/**
 * CapCut-style timeline: the playhead is FIXED in the center and the content
 * scrolls under it. Dragging/flinging the background scrubs, two fingers zoom.
 */
@Composable
internal fun TimelineView(
    project: Project,
    positionUs: Long,
    selectedClipId: String?,
    trimDraft: TrimDraft?,
    dpPerSecond: Float,
    onZoom: (Float) -> Unit,
    onScrubStart: () -> Unit,
    onScrub: (Long) -> Unit,
    onSelectClip: (String?) -> Unit,
    onTrimDrag: (clipId: String, startUs: Long, endUs: Long) -> Unit,
    onTrimEnd: () -> Unit,
    onMoveClip: (clipId: String, toIndex: Int) -> Unit,
    onAddClip: () -> Unit,
    onTextRow: () -> Unit,
    onAudioRow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = FevTheme.colors
    val density = LocalDensity.current
    val pxPerUs = with(density) { dpPerSecond.dp.toPx() } / 1_000_000f

    val latestPosition by rememberUpdatedState(positionUs)
    val latestPxPerUs by rememberUpdatedState(pxPerUs)
    val latestDuration by rememberUpdatedState(project.durationUs)
    val zoom by rememberUpdatedState(onZoom)
    val scrubStart by rememberUpdatedState(onScrubStart)
    val scrub by rememberUpdatedState(onScrub)
    val select by rememberUpdatedState(onSelectClip)

    val scope = rememberCoroutineScope()
    var flingJob by remember { mutableStateOf<Job?>(null) }

    // Long-press reorder state.
    var draggingClipId by remember { mutableStateOf<String?>(null) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(RulerHeight + MainTrackHeight + LaneHeight * 2 + 20.dp)
            .clipToBounds()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    flingJob?.cancel()
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    var time = latestPosition.toFloat()
                    var panning = false
                    var zooming = false
                    var childOwnsGesture = false
                    var totalDx = 0f
                    var upConsumed = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) {
                            upConsumed = event.changes.any { it.isConsumed }
                            break
                        }
                        if (pressed.size >= 2) {
                            zooming = true
                            val factor = event.calculateZoom()
                            if (factor != 1f) zoom(factor)
                            event.changes.forEach { it.consume() }
                            continue
                        }
                        val change = pressed.first()
                        if (!panning && change.isConsumed) childOwnsGesture = true // trim or reorder
                        if (childOwnsGesture || zooming) continue
                        val dx = change.positionChange().x
                        if (!panning) {
                            totalDx += dx
                            if (abs(totalDx) > viewConfiguration.touchSlop) {
                                panning = true
                                scrubStart()
                            }
                        }
                        if (panning) {
                            time = (time - dx / latestPxPerUs).coerceIn(0f, latestDuration.toFloat())
                            scrub(time.toLong())
                            change.consume()
                            tracker.addPosition(change.uptimeMillis, change.position)
                        }
                    }
                    if (panning) {
                        val velocityUsPerSec = -tracker.calculateVelocity().x / latestPxPerUs
                        val from = time
                        flingJob = scope.launch {
                            AnimationState(from, velocityUsPerSec).animateDecay(exponentialDecay()) {
                                val clamped = value.coerceIn(0f, latestDuration.toFloat())
                                scrub(clamped.toLong())
                                if (clamped != value) cancelAnimation()
                            }
                        }
                    } else if (!childOwnsGesture && !zooming && !upConsumed) {
                        select(null) // tap on empty timeline area clears selection
                    }
                }
            },
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val centerPx = widthPx / 2f
        val xOf = { timeUs: Long -> centerPx + (timeUs - positionUs) * pxPerUs }
        val clips = project.mainTrack.clips

        Column(Modifier.fillMaxWidth()) {
            TimelineRuler(positionUs, pxPerUs, Modifier.fillMaxWidth().height(RulerHeight))
            Spacer(Modifier.height(4.dp))

            // Main video track
            Box(Modifier.fillMaxWidth().height(MainTrackHeight)) {
                var startUs = 0L
                clips.forEachIndexed { index, clip ->
                    val clipStart = startUs
                    startUs += clip.durationUs
                    ClipItem(
                        clip = clip,
                        baseXPx = xOf(clipStart),
                        pxPerUs = pxPerUs,
                        viewportWidthPx = widthPx,
                        selected = clip.id == selectedClipId,
                        draft = trimDraft?.takeIf { it.clipId == clip.id },
                        dragOffsetPx = if (draggingClipId == clip.id) dragOffsetPx else 0f,
                        onTap = { onSelectClip(clip.id) },
                        onReorderStart = {
                            draggingClipId = clip.id
                            dragOffsetPx = 0f
                            onSelectClip(clip.id)
                        },
                        onReorderDrag = { dragOffsetPx += it },
                        onReorderEnd = {
                            val target = reorderTarget(project, index, dragOffsetPx / pxPerUs)
                            draggingClipId = null
                            dragOffsetPx = 0f
                            onMoveClip(clip.id, target)
                        },
                        onTrimDrag = { s, e -> onTrimDrag(clip.id, s, e) },
                        onTrimEnd = onTrimEnd,
                    )
                }
                AddClipButton(
                    xPx = xOf(project.durationUs) + with(density) { 12.dp.toPx() },
                    onClick = onAddClip,
                )
            }
            Spacer(Modifier.height(6.dp))
            LaneChip(stringResource(R.string.editor_add_text), xOf(0L), onTextRow)
            Spacer(Modifier.height(4.dp))
            LaneChip(stringResource(R.string.editor_add_audio), xOf(0L), onAudioRow)
        }

        // Fixed center playhead
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .width(2.dp)
                .fillMaxHeight()
                .background(c.playhead),
        )
    }
}

/** Index the dragged clip should move to, based on where its center ended up. */
private fun reorderTarget(project: Project, index: Int, offsetUs: Float): Int {
    val track = project.mainTrack
    val dragged = track.clips[index]
    val draggedCenter = track.startOf(index) + dragged.durationUs / 2 + offsetUs.toLong()
    var target = 0
    track.clips.forEachIndexed { j, other ->
        if (j != index && track.startOf(j) + other.durationUs / 2 < draggedCenter) target++
    }
    return target
}

@Composable
private fun AddClipButton(xPx: Float, onClick: () -> Unit) {
    val c = FevTheme.colors
    val label = stringResource(R.string.editor_add_clip)
    Box(
        Modifier
            .offset { IntOffset(xPx.roundToInt(), 0) }
            .padding(top = 8.dp)
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(c.accent)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Add, contentDescription = null, tint = c.onAccent)
    }
}

/** Placeholder lanes for text and audio (tap opens the matching tool). */
@Composable
private fun LaneChip(text: String, xPx: Float, onClick: () -> Unit) {
    val c = FevTheme.colors
    Box(Modifier.fillMaxWidth().height(LaneHeight)) {
        Row(
            Modifier
                .offset { IntOffset(xPx.roundToInt(), 0) }
                .fillMaxHeight()
                .clip(RoundedCornerShape(8.dp))
                .background(c.surface)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(18.dp))
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                color = c.textSecondary,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}
