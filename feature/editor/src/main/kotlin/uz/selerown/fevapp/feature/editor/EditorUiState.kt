package uz.selerown.fevapp.feature.editor

import androidx.annotation.StringRes
import uz.selerown.fevapp.core.model.Project

/** Temporary trim while a handle is being dragged (committed on release). */
data class TrimDraft(
    val clipId: String,
    val startUs: Long,
    val endUs: Long,
)

data class EditorUiState(
    val project: Project? = null,
    val selectedClipId: String? = null,
    val trimDraft: TrimDraft? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val openTool: EditorTool? = null,
    val importing: Boolean = false,
)

sealed interface EditorEvent {
    data class Message(@StringRes val text: Int) : EditorEvent
    data class OpenExport(val projectId: String) : EditorEvent
    data object Close : EditorEvent
}
