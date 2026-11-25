package uz.selerown.fevapp.feature.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import uz.selerown.fevapp.core.common.TimeFormat
import uz.selerown.fevapp.core.designsystem.component.FevIconButton
import uz.selerown.fevapp.core.designsystem.component.FevPrimaryButton
import uz.selerown.fevapp.core.designsystem.component.FevToolButton
import uz.selerown.fevapp.core.designsystem.theme.FevTheme

/** Back · Undo · Redo · [Eksport] */
@Composable
internal fun EditorTopBar(
    canUndo: Boolean,
    canRedo: Boolean,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onExport: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FevIconButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.editor_back), onBack)
        Spacer(Modifier.weight(1f))
        FevIconButton(Icons.AutoMirrored.Rounded.Undo, stringResource(R.string.editor_undo), onUndo, enabled = canUndo)
        FevIconButton(Icons.AutoMirrored.Rounded.Redo, stringResource(R.string.editor_redo), onRedo, enabled = canRedo)
        FevPrimaryButton(
            text = stringResource(R.string.editor_export),
            onClick = onExport,
            modifier = Modifier.padding(start = 8.dp, end = 8.dp),
        )
    }
}

/** Current/total time, play-pause, zoom buttons (alternative to pinch) and fullscreen. */
@Composable
internal fun PlaybackBar(
    positionUs: Long,
    durationUs: Long,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onFullscreen: () -> Unit,
) {
    val c = FevTheme.colors
    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Text(
            stringResource(R.string.editor_time, TimeFormat.clock(positionUs), TimeFormat.clock(durationUs)),
            style = MaterialTheme.typography.labelLarge,
            color = c.textPrimary,
            modifier = Modifier.align(Alignment.CenterStart),
        )
        PlayButton(isPlaying, onTogglePlay, Modifier.align(Alignment.Center))
        Row(Modifier.align(Alignment.CenterEnd)) {
            FevIconButton(Icons.Rounded.ZoomOut, stringResource(R.string.editor_zoom_out), onZoomOut)
            FevIconButton(Icons.Rounded.ZoomIn, stringResource(R.string.editor_zoom_in), onZoomIn)
            FevIconButton(Icons.Rounded.Fullscreen, stringResource(R.string.editor_fullscreen), onFullscreen)
        }
    }
}

@Composable
internal fun PlayButton(isPlaying: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = FevTheme.colors
    val label = stringResource(if (isPlaying) R.string.editor_pause else R.string.editor_play)
    Box(
        modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(c.surfaceHigh)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = null,
            tint = c.textPrimary,
            modifier = Modifier.size(32.dp),
        )
    }
}

/** Horizontally scrollable tools; content depends on what is selected ([lane] = null: nothing). */
@Composable
internal fun EditorToolbar(
    lane: Lane?,
    onDeselect: () -> Unit,
    onTool: (EditorTool) -> Unit,
) {
    val c = FevTheme.colors
    HorizontalDivider(color = c.border)
    Row(
        Modifier.fillMaxWidth().background(c.surface).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (lane != null) {
            FevIconButton(Icons.Rounded.Close, stringResource(R.string.editor_deselect), onDeselect)
        }
        val tools = EditorTool.toolsFor(lane)
        LazyRow(Modifier.weight(1f)) {
            items(tools, key = { it.name }) { tool ->
                FevToolButton(
                    icon = tool.icon,
                    label = stringResource(tool.label),
                    onClick = { onTool(tool) },
                )
            }
        }
    }
}

/** Replaces the tool bar while a voice-over is recorded: red dot, elapsed time, big Stop button. */
@Composable
internal fun RecordingBar(recording: Recording, onStop: () -> Unit) {
    val c = FevTheme.colors
    var elapsedMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(recording) {
        while (true) {
            elapsedMs = System.currentTimeMillis() - recording.startedAtMillis
            delay(200)
        }
    }
    HorizontalDivider(color = c.border)
    Row(
        Modifier.fillMaxWidth().background(c.surface).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(14.dp).clip(CircleShape).background(c.danger))
        Text(
            stringResource(R.string.recording_label, TimeFormat.clock(elapsedMs * 1000)),
            style = MaterialTheme.typography.titleMedium,
            color = c.textPrimary,
            modifier = Modifier.padding(start = 12.dp).weight(1f),
        )
        FevPrimaryButton(text = stringResource(R.string.recording_stop), onClick = onStop)
    }
}
