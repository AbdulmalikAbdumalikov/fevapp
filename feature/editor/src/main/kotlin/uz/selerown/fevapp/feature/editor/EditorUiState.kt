package uz.selerown.fevapp.feature.editor

import androidx.annotation.StringRes
import uz.selerown.fevapp.core.model.Project

/** The timeline row an item lives on. */
enum class Lane { MAIN, TEXT, AUDIO }

/** What is selected on the timeline (at most one item). */
data class Selection(val lane: Lane, val id: String)

/** Temporary trim of a main-track clip while a handle is dragged (committed on release). */
data class TrimDraft(
    val clipId: String,
    val startUs: Long,
    val endUs: Long,
)

/** Temporary timeline position of a text/audio clip while it is moved or trimmed. */
data class LaneDraft(
    val id: String,
    val startUs: Long,
    val endUs: Long,
)

data class EditorUiState(
    val project: Project? = null,
    val selection: Selection? = null,
    val trimDraft: TrimDraft? = null,
    val laneDraft: LaneDraft? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val openTool: EditorTool? = null,
    val importing: Boolean = false,
    /** Set while a voice-over is being recorded. */
    val recording: Recording? = null,
) {
    /** Selected main-track clip, if the selection is on the main track. */
    val selectedClipId: String?
        get() = selection?.takeIf { it.lane == Lane.MAIN }?.id
}

/** A voice-over in progress: where it will be placed and when it started (wall clock). */
data class Recording(val startUs: Long, val startedAtMillis: Long)

sealed interface EditorEvent {
    data class Message(@StringRes val text: Int) : EditorEvent
    data class OpenExport(val projectId: String) : EditorEvent
    data object Close : EditorEvent
}
