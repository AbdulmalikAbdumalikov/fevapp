package uz.selerown.fevapp.feature.editor

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Filter
import androidx.compose.material.icons.rounded.FirstPage
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LastPage
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.VerticalSplit
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Every bottom-toolbar tool. [opensSheet] tools show a bottom sheet with ✓;
 * the others act immediately on the selection. Add new tools here.
 */
enum class EditorTool(
    @StringRes val label: Int,
    val icon: ImageVector,
    val opensSheet: Boolean,
) {
    // Nothing selected
    EDIT(R.string.tool_edit, Icons.Rounded.ContentCut, false),
    AUDIO(R.string.tool_audio, Icons.Rounded.MusicNote, true),
    TEXT(R.string.tool_text, Icons.Rounded.TextFields, true),
    FILTER(R.string.tool_filter, Icons.Rounded.Filter, true),
    SPEED(R.string.tool_speed, Icons.Rounded.Speed, true),
    CAPTIONS(R.string.tool_captions, Icons.Rounded.ClosedCaption, true),
    FORMAT(R.string.tool_format, Icons.Rounded.AspectRatio, true),

    // Shared by several selections
    SPLIT(R.string.tool_split, Icons.Rounded.VerticalSplit, false),
    DELETE(R.string.tool_delete, Icons.Rounded.Delete, false),
    DUPLICATE(R.string.tool_duplicate, Icons.Rounded.ContentCopy, false),

    // Main-track clip
    MOVE_LEFT(R.string.tool_move_left, Icons.AutoMirrored.Rounded.KeyboardArrowLeft, false),
    MOVE_RIGHT(R.string.tool_move_right, Icons.AutoMirrored.Rounded.KeyboardArrowRight, false),
    VOLUME(R.string.tool_volume, Icons.AutoMirrored.Rounded.VolumeUp, true),
    EXTRACT_AUDIO(R.string.tool_extract_audio, Icons.Rounded.GraphicEq, false),
    CLIP_SPEED(R.string.tool_speed, Icons.Rounded.Speed, true),
    REVERSE(R.string.tool_reverse, Icons.Rounded.FastRewind, true),

    // Text / audio lane item: button alternatives to dragging and trimming
    TEXT_EDIT(R.string.tool_text_edit, Icons.Rounded.Edit, true),
    AUDIO_MIX(R.string.tool_volume, Icons.AutoMirrored.Rounded.VolumeUp, true),
    MOVE_HERE(R.string.tool_move_here, Icons.Rounded.Place, false),
    START_HERE(R.string.tool_start_here, Icons.Rounded.FirstPage, false),
    END_HERE(R.string.tool_end_here, Icons.Rounded.LastPage, false),
    ;

    companion object {
        val mainTools = listOf(EDIT, AUDIO, TEXT, FILTER, SPEED, CAPTIONS, FORMAT)

        /** Move left/right are the visible alternative to long-press dragging. */
        val clipTools = listOf(SPLIT, DELETE, DUPLICATE, MOVE_LEFT, MOVE_RIGHT, VOLUME, EXTRACT_AUDIO, CLIP_SPEED, REVERSE)

        val textTools = listOf(TEXT_EDIT, DUPLICATE, DELETE, MOVE_HERE, START_HERE, END_HERE)

        val audioTools = listOf(AUDIO_MIX, SPLIT, DELETE, MOVE_HERE, START_HERE, END_HERE)

        fun toolsFor(lane: Lane?): List<EditorTool> = when (lane) {
            null -> mainTools
            Lane.MAIN -> clipTools
            Lane.TEXT -> textTools
            Lane.AUDIO -> audioTools
        }
    }
}
