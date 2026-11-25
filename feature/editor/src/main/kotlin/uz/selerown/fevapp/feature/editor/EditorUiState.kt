package uz.selerown.fevapp.feature.editor

import androidx.annotation.StringRes
import uz.selerown.fevapp.core.model.Project

/** The timeline row an item lives on. */
enum class Lane { MAIN, OVERLAY, TEXT, AUDIO }

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
    /** Live position of a text being dragged on the preview (committed on release). */
    val textDraft: TextDraft? = null,
    /** Live position of an overlay being dragged on the preview. */
    val overlayDraft: TextDraft? = null,
    /** Set while a voice-over is being recorded. */
    val recording: Recording? = null,
) {
    /** Selected main-track clip, if the selection is on the main track. */
    val selectedClipId: String?
        get() = selection?.takeIf { it.lane == Lane.MAIN }?.id
}

/** Text being dragged on the preview: new center as fractions of the frame. */
data class TextDraft(val id: String, val x: Float, val y: Float)

/** A voice-over in progress: where it will be placed and when it started (wall clock). */
data class Recording(val startUs: Long, val startedAtMillis: Long)

sealed interface EditorEvent {
    data class Message(@StringRes val text: Int) : EditorEvent
    data class OpenExport(val projectId: String) : EditorEvent
    data object Close : EditorEvent

    /** Ask the screen to open the Photo Picker for a picture-in-picture clip. */
    data object PickOverlay : EditorEvent

    /** Ask the screen to open the Photo Picker; picked clips go in at the playhead. */
    data object PickMediaAtPlayhead : EditorEvent
}
