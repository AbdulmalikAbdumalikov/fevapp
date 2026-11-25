package uz.selerown.fevapp.feature.editor.timeline

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PictureInPicture
import androidx.compose.material.icons.rounded.TextFields
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.ui.zIndex
import uz.selerown.fevapp.core.model.TransitionType
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import uz.selerown.fevapp.core.designsystem.theme.FevTheme
import uz.selerown.fevapp.core.model.Project
import uz.selerown.fevapp.feature.editor.R
import uz.selerown.fevapp.engine.render.Waveform
import uz.selerown.fevapp.feature.editor.Lane
import uz.selerown.fevapp.feature.editor.LaneDraft
import uz.selerown.fevapp.feature.editor.Selection
import uz.selerown.fevapp.feature.editor.TrimDraft
import kotlin.math.abs
import kotlin.math.roundToInt

private val RulerHeight = 22.dp
private val MainTrackHeight = 64.dp
private val LaneHeight = 48.dp // one row = minimum touch target

/** A lane grows up to this many 48 dp rows; beyond that rows share the space. */
private const val MAX_LANE_ROWS = 3

/**
 * CapCut-style timeline: the playhead is FIXED in the center and the content
 * scrolls under it. Dragging/flinging the background scrubs, two fingers zoom.
 */
@Composable
internal fun TimelineView(
    project: Project,
    positionUs: Long,
    selection: Selection?,
    trimDraft: TrimDraft?,
    laneDraft: LaneDraft?,
    waveforms: Map<String, Waveform>,
    dpPerSecond: Float,
    onZoom: (Float) -> Unit,
    onScrubStart: () -> Unit,
    onScrub: (Long) -> Unit,
    onSelect: (Selection?) -> Unit,
    onTrimDrag: (clipId: String, startUs: Long, endUs: Long) -> Unit,
    onTrimEnd: () -> Unit,
    onMoveClip: (clipId: String, toIndex: Int) -> Unit,
    onLaneMove: (Selection, startUs: Long) -> Unit,
    onLaneTrim: (Selection, startUs: Long, endUs: Long) -> Unit,
    onLaneDragEnd: () -> Unit,
    onAddClip: () -> Unit,
    onAddOverlay: () -> Unit,
    onAddText: () -> Unit,
    onAddAudio: () -> Unit,
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
    val select by rememberUpdatedState(onSelect)
    val selectedClipId = selection?.takeIf { it.lane == Lane.MAIN }?.id

    val scope = rememberCoroutineScope()
    var flingJob by remember { mutableStateOf<Job?>(null) }

    // Long-press reorder state.
    var draggingClipId by remember { mutableStateOf<String?>(null) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }

    // Lanes grow when clips overlap so every row stays 48 dp tall.
    val overlayRows = laneRowCount(project.overlayClips.map { it.startUs to it.endUs })
    val textRows = laneRowCount(project.textClips.map { it.startUs to it.endUs })
    val audioRows = laneRowCount(project.audioClips.map { it.startUs to it.endUs })

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(RulerHeight + MainTrackHeight + LaneHeight * (overlayRows + textRows + audioRows) + 24.dp)
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
                        onTap = { onSelect(Selection(Lane.MAIN, clip.id)) },
                        onReorderStart = {
                            draggingClipId = clip.id
                            dragOffsetPx = 0f
                            onSelect(Selection(Lane.MAIN, clip.id))
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
                // Small badge on every cut that has a transition.
                var cutUs = 0L
                clips.dropLast(1).forEach { clip ->
                    cutUs += clip.durationUs
                    if (clip.transition != TransitionType.NONE) TransitionMarker(xOf(cutUs))
                }
                AddClipButton(
                    xPx = xOf(project.durationUs) + with(density) { 12.dp.toPx() },
                    onClick = onAddClip,
                )
            }
            Spacer(Modifier.height(6.dp))
            LaneRow(
                lane = Lane.OVERLAY,
                items = project.overlayClips.map { clip ->
                    LaneItemModel(
                        clip.id,
                        stringResource(R.string.tool_overlay),
                        stringResource(R.string.lane_overlay_item),
                        clip.startUs,
                        clip.endUs,
                    )
                },
                icon = Icons.Rounded.PictureInPicture,
                fill = c.overlayClipFill,
                addLabel = stringResource(R.string.editor_add_overlay),
                xOf = xOf,
                pxPerUs = pxPerUs,
                viewportWidthPx = widthPx,
                selection = selection,
                draft = laneDraft,
                onAdd = onAddOverlay,
                onSelect = onSelect,
                onMove = onLaneMove,
                onTrim = onLaneTrim,
                onDragEnd = onLaneDragEnd,
            )
            Spacer(Modifier.height(4.dp))
            LaneRow(
                lane = Lane.TEXT,
                items = project.textClips.map { clip ->
                    LaneItemModel(
                        clip.id,
                        clip.text.lineSequence().first(),
                        stringResource(R.string.lane_text_item, clip.text),
                        clip.startUs,
                        clip.endUs,
                    )
                },
                icon = Icons.Rounded.TextFields,
                fill = c.textClipFill,
                addLabel = stringResource(R.string.editor_add_text),
                xOf = xOf,
                pxPerUs = pxPerUs,
                viewportWidthPx = widthPx,
                selection = selection,
                draft = laneDraft,
                onAdd = onAddText,
                onSelect = onSelect,
                onMove = onLaneMove,
                onTrim = onLaneTrim,
                onDragEnd = onLaneDragEnd,
            )
            Spacer(Modifier.height(4.dp))
            LaneRow(
                lane = Lane.AUDIO,
                items = project.audioClips.map { clip ->
                    LaneItemModel(
                        clip.id,
                        clip.name,
                        stringResource(R.string.lane_audio_item, clip.name),
                        clip.startUs,
                        clip.endUs,
                        sourceUri = clip.source.uri,
                        sourceStartUs = clip.trimStartUs,
                    )
                },
                icon = Icons.Rounded.MusicNote,
                fill = c.audioClipFill,
                waveforms = waveforms,
                addLabel = stringResource(R.string.editor_add_audio),
                xOf = xOf,
                pxPerUs = pxPerUs,
                viewportWidthPx = widthPx,
                selection = selection,
                draft = laneDraft,
                onAdd = onAddAudio,
                onSelect = onSelect,
                onMove = onLaneMove,
                onTrim = onLaneTrim,
                onDragEnd = onLaneDragEnd,
            )
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

/**
 * One free-positioned lane (text or audio): its clips plus an "add" chip
 * (at time 0 when empty, otherwise right after the last clip).
 */
@Composable
private fun LaneRow(
    lane: Lane,
    items: List<LaneItemModel>,
    icon: ImageVector,
    fill: Color,
    waveforms: Map<String, Waveform> = emptyMap(),
    addLabel: String,
    xOf: (Long) -> Float,
    pxPerUs: Float,
    viewportWidthPx: Float,
    selection: Selection?,
    draft: LaneDraft?,
    onAdd: () -> Unit,
    onSelect: (Selection?) -> Unit,
    onMove: (Selection, Long) -> Unit,
    onTrim: (Selection, Long, Long) -> Unit,
    onDragEnd: () -> Unit,
) {
    val density = LocalDensity.current
    // Overlapping clips get their own sub-row so none is hidden under another.
    val rows = remember(items) { packRows(items) }
    val rowCount = (rows.values.maxOrNull() ?: 0) + 1
    val laneHeight = LaneHeight * minOf(rowCount, MAX_LANE_ROWS)
    val rowHeight = laneHeight / rowCount
    Box(Modifier.fillMaxWidth().height(laneHeight)) {
        items.forEach { item ->
            val itemSelection = Selection(lane, item.id)
            val itemDraft = draft?.takeIf { it.id == item.id }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(rowHeight)
                    .offset(y = rowHeight * (rows[item.id] ?: 0)),
            ) {
            LaneItem(
                model = item,
                icon = icon,
                fill = fill,
                xOfUs = xOf,
                pxPerUs = pxPerUs,
                viewportWidthPx = viewportWidthPx,
                selected = selection == itemSelection,
                draftStartUs = itemDraft?.startUs,
                draftEndUs = itemDraft?.endUs,
                waveform = item.sourceUri?.let { waveforms[it] },
                onTap = { onSelect(itemSelection) },
                onMove = { onMove(itemSelection, it) },
                onTrim = { s, e -> onTrim(itemSelection, s, e) },
                onDragEnd = onDragEnd,
            )
            }
        }
        val lastEnd = items.maxOfOrNull { it.endUs }
        val chipX = if (lastEnd == null) xOf(0L) else xOf(lastEnd) + with(density) { 8.dp.toPx() }
        AddChip(if (lastEnd == null) addLabel else null, addLabel, chipX, onAdd)
    }
}

@Composable
private fun TransitionMarker(xPx: Float) {
    val c = FevTheme.colors
    val density = LocalDensity.current
    val size = 22.dp
    val label = stringResource(R.string.transition_marker)
    Box(
        Modifier
            .offset { IntOffset((xPx - with(density) { size.toPx() } / 2).roundToInt(), with(density) { 21.dp.roundToPx() }) }
            .zIndex(2f)
            .size(size)
            .clip(CircleShape)
            .background(c.surfaceHigh)
            .border(1.dp, c.accent, CircleShape)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Animation, contentDescription = null, tint = c.accent, modifier = Modifier.size(14.dp))
    }
}

/** Number of 48 dp rows a lane needs (1..MAX_LANE_ROWS). */
private fun laneRowCount(spans: List<Pair<Long, Long>>): Int {
    val rowEnds = mutableListOf<Long>()
    spans.sortedBy { it.first }.forEach { (start, end) ->
        val row = rowEnds.indexOfFirst { it <= start }
        if (row >= 0) rowEnds[row] = end else rowEnds += end
    }
    return rowEnds.size.coerceIn(1, MAX_LANE_ROWS)
}

/** Greedy interval packing: each clip goes to the first sub-row where it does not overlap. */
private fun packRows(items: List<LaneItemModel>): Map<String, Int> {
    val rowEnds = mutableListOf<Long>()
    val result = mutableMapOf<String, Int>()
    items.sortedBy { it.startUs }.forEach { item ->
        val row = rowEnds.indexOfFirst { it <= item.startUs }.takeIf { it >= 0 } ?: rowEnds.size.also { rowEnds += 0L }
        rowEnds[row] = item.endUs
        result[item.id] = row
    }
    return result
}

/** "+ Matn qoʻshish" chip; icon-only (with the same spoken label) when the lane has clips. */
@Composable
private fun AddChip(text: String?, description: String, xPx: Float, onClick: () -> Unit) {
    val c = FevTheme.colors
    Row(
        Modifier
            .offset { IntOffset(xPx.roundToInt(), 0) }
            .height(LaneHeight)
            .widthIn(min = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(c.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Add, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(18.dp))
        if (text != null) {
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                color = c.textSecondary,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}
