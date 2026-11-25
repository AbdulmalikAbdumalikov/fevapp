package uz.selerown.fevapp.feature.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import uz.selerown.fevapp.core.designsystem.component.FevToolSheet
import uz.selerown.fevapp.core.designsystem.theme.FevTheme
import uz.selerown.fevapp.core.model.CanvasFormat
import uz.selerown.fevapp.core.model.ClipLook
import uz.selerown.fevapp.core.model.ColorAdjust
import uz.selerown.fevapp.core.model.Project
import uz.selerown.fevapp.core.model.TextStyle
import uz.selerown.fevapp.core.model.TransitionType

/**
 * Opens the bottom sheet of [tool]. Text, audio, volume and format sheets
 * work; the others still say "coming soon".
 */
@Composable
internal fun ToolSheet(
    tool: EditorTool,
    project: Project?,
    selection: Selection?,
    onDismiss: () -> Unit,
    onApplyFormat: (CanvasFormat, Int) -> Unit,
    onSaveText: (editingId: String?, text: String, style: TextStyle) -> Unit,
    onPickMusic: () -> Unit,
    onPickVideoSound: () -> Unit,
    onRecordVoice: () -> Unit,
    onSetClipVolume: (Float) -> Unit,
    onSetAudioMix: (volume: Float, fadeInUs: Long, fadeOutUs: Long) -> Unit,
    onSetSpeed: (Float) -> Unit,
    onPreviewColor: (ClipLook, ColorAdjust) -> Unit,
    onSetColor: (ClipLook, ColorAdjust, Boolean) -> Unit,
    onSetTransition: (TransitionType, Long, Boolean) -> Unit,
) {
    val selectedClip = project?.mainTrack?.clips?.firstOrNull { it.id == selection?.id }
    val selectedId = selection?.id
    when (tool) {
        EditorTool.TEXT -> TextSheet(null, stringResource(R.string.editor_add_text), onDismiss) { text, style ->
            onSaveText(null, text, style)
        }
        EditorTool.TEXT_EDIT -> project?.textClips?.firstOrNull { it.id == selectedId }?.let { clip ->
            TextSheet(clip, stringResource(R.string.tool_text_edit), onDismiss) { text, style ->
                onSaveText(clip.id, text, style)
            }
        } ?: LaunchedEffect(Unit) { onDismiss() } // selection vanished (e.g. undo)
        EditorTool.CLIP_SPEED -> selectedClip?.let { SpeedSheet(it, onDismiss, onSetSpeed) }
            ?: LaunchedEffect(Unit) { onDismiss() }
        EditorTool.CLIP_FILTER -> selectedClip?.let { FilterSheet(it, onPreviewColor, onDismiss, onSetColor) }
            ?: LaunchedEffect(Unit) { onDismiss() }
        EditorTool.TRANSITION -> selectedClip?.let { TransitionSheet(it, onDismiss, onSetTransition) }
            ?: LaunchedEffect(Unit) { onDismiss() }
        EditorTool.AUDIO -> AudioAddSheet(onDismiss, onPickMusic, onPickVideoSound, onRecordVoice)
        EditorTool.VOLUME -> project?.mainTrack?.clips?.firstOrNull { it.id == selectedId }?.let { clip ->
            ClipVolumeSheet(clip.volume, onDismiss, onSetClipVolume)
        } ?: LaunchedEffect(Unit) { onDismiss() } // selection vanished (e.g. undo)
        EditorTool.AUDIO_MIX -> project?.audioClips?.firstOrNull { it.id == selectedId }?.let { clip ->
            AudioMixSheet(clip, onDismiss, onSetAudioMix)
        } ?: LaunchedEffect(Unit) { onDismiss() } // selection vanished (e.g. undo)
        else -> GenericSheet(tool, project?.canvas, project?.backgroundColor, onDismiss, onApplyFormat)
    }
}

/** Format (canvas) picker and the "coming soon" placeholder for unfinished tools. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GenericSheet(
    tool: EditorTool,
    currentFormat: CanvasFormat?,
    currentBackground: Int?,
    onDismiss: () -> Unit,
    onApplyFormat: (CanvasFormat, Int) -> Unit,
) {
    var format by remember { mutableStateOf(currentFormat ?: CanvasFormat.ORIGINAL) }
    var background by remember { mutableStateOf(currentBackground ?: ContentPalette[1].first) }
    FevToolSheet(
        title = stringResource(tool.label),
        closeLabel = stringResource(R.string.sheet_close),
        applyLabel = stringResource(R.string.sheet_apply),
        onDismiss = onDismiss,
        onApply = { if (tool == EditorTool.FORMAT) onApplyFormat(format, background) else onDismiss() },
    ) {
        when (tool) {
            EditorTool.FORMAT -> Column {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 12.dp),
                ) {
                    CanvasFormat.entries.forEach { option ->
                        ChoiceChip(option.label(), option == format) { format = option }
                    }
                }
                SheetSection(R.string.format_background)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Black first: the classic bars.
                    (listOf(ContentPalette[1]) + ContentPalette.filterIndexed { i, _ -> i != 1 }).forEach { (argb, name) ->
                        ColorSwatch(argb, stringResource(name), selected = background == argb) { background = argb }
                    }
                }
            }
            else -> Text(
                stringResource(R.string.sheet_coming_soon),
                style = MaterialTheme.typography.bodyLarge,
                color = FevTheme.colors.textSecondary,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }
    }
}

@Composable
private fun CanvasFormat.label(): String =
    if (this == CanvasFormat.ORIGINAL) stringResource(R.string.format_original) else "$widthRatio:$heightRatio"
