package uz.selerown.fevapp.feature.editor

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import uz.selerown.fevapp.core.common.IdGenerator
import uz.selerown.fevapp.core.database.ProjectRepository
import uz.selerown.fevapp.core.model.CanvasFormat
import uz.selerown.fevapp.core.model.Project
import uz.selerown.fevapp.engine.render.MediaImporter
import uz.selerown.fevapp.engine.render.PreviewPlayer
import uz.selerown.fevapp.engine.timeline.AddClipsCommand
import uz.selerown.fevapp.engine.timeline.DeleteClipCommand
import uz.selerown.fevapp.engine.timeline.DuplicateClipCommand
import uz.selerown.fevapp.engine.timeline.EditHistory
import uz.selerown.fevapp.engine.timeline.MoveClipCommand
import uz.selerown.fevapp.engine.timeline.SetCanvasCommand
import uz.selerown.fevapp.engine.timeline.SplitClipCommand
import uz.selerown.fevapp.engine.timeline.TimelineCommand
import uz.selerown.fevapp.engine.timeline.TimelineRules
import uz.selerown.fevapp.engine.timeline.TrimClipCommand
import javax.inject.Inject

/**
 * Editor state holder. All timeline edits go through [EditHistory] so they
 * can be undone; after each change the preview is reloaded and the project
 * is saved to Room (auto-save).
 */
@HiltViewModel
class EditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext context: Context,
    private val repository: ProjectRepository,
    private val importer: MediaImporter,
) : ViewModel() {

    private val projectId = savedStateHandle.toRoute<EditorRoute>().projectId
    private val ids = IdGenerator.Random
    private var history: EditHistory? = null

    val preview = PreviewPlayer(context)
    val positionUs: StateFlow<Long> = preview.positionUs
    val isPlaying: StateFlow<Boolean> = preview.isPlaying

    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    private val _events = Channel<EditorEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            val project = repository.get(projectId)
            if (project == null) {
                _events.send(EditorEvent.Message(R.string.msg_project_missing))
                _events.send(EditorEvent.Close)
                return@launch
            }
            history = EditHistory(project)
            publish(project, reloadPreview = true)
        }
        // Keep the playhead in sync with playback (~30 fps).
        viewModelScope.launch {
            while (isActive) {
                preview.tick()
                delay(33)
            }
        }
    }

    // ---- Playback -------------------------------------------------------

    fun togglePlay() = preview.togglePlay()

    fun onScrubStart() = preview.pause()

    fun seekTo(timeUs: Long) = preview.seekTo(timeUs)

    // ---- Selection ------------------------------------------------------

    fun selectClip(clipId: String?) {
        _state.update { it.copy(selectedClipId = clipId) }
    }

    // ---- Tools ----------------------------------------------------------

    fun onTool(tool: EditorTool) {
        if (tool.opensSheet) {
            preview.pause()
            _state.update { it.copy(openTool = tool) }
            return
        }
        when (tool) {
            EditorTool.EDIT -> selectClipAtPlayhead()
            EditorTool.SPLIT -> split()
            EditorTool.DELETE -> selected()?.let { execute(DeleteClipCommand(it)); selectClip(null) }
            EditorTool.DUPLICATE -> selected()?.let { execute(DuplicateClipCommand(it, ids.newId())) }
            EditorTool.MOVE_LEFT -> moveSelected(-1)
            EditorTool.MOVE_RIGHT -> moveSelected(+1)
            else -> Unit
        }
    }

    fun closeTool() = _state.update { it.copy(openTool = null) }

    fun applyFormat(format: CanvasFormat) {
        execute(SetCanvasCommand(format))
        closeTool()
    }

    private fun selectClipAtPlayhead() {
        val project = history?.current ?: return
        val clip = TimelineRules.clipAt(project.mainTrack, positionUs.value)?.clip
            ?: project.mainTrack.clips.lastOrNull()?.takeIf { positionUs.value >= project.durationUs }
        if (clip == null) message(R.string.msg_no_clip_here) else selectClip(clip.id)
    }

    private fun split() {
        val changed = execute(SplitClipCommand(positionUs.value, ids.newId(), selected()))
        if (!changed) message(R.string.msg_split_failed)
    }

    private fun moveSelected(delta: Int) {
        val id = selected() ?: return
        val index = history?.current?.mainTrack?.indexOf(id) ?: return
        execute(MoveClipCommand(id, index + delta))
    }

    fun moveClip(clipId: String, toIndex: Int) {
        execute(MoveClipCommand(clipId, toIndex))
    }

    // ---- Trim (live draft while dragging, one undo step on release) -----

    fun onTrimDrag(clipId: String, startUs: Long, endUs: Long) {
        val clip = history?.current?.mainTrack?.clips?.firstOrNull { it.id == clipId } ?: return
        val (s, e) = TimelineRules.clampTrim(clip, startUs, endUs)
        preview.pause()
        _state.update { it.copy(trimDraft = TrimDraft(clipId, s, e)) }
    }

    fun onTrimEnd() {
        val draft = _state.value.trimDraft ?: return
        _state.update { it.copy(trimDraft = null) }
        execute(TrimClipCommand(draft.clipId, draft.startUs, draft.endUs))
    }

    // ---- Undo / redo ----------------------------------------------------

    fun undo() {
        val h = history ?: return
        if (h.undo()) publish(h.current, reloadPreview = true, save = true)
    }

    fun redo() {
        val h = history ?: return
        if (h.redo()) publish(h.current, reloadPreview = true, save = true)
    }

    // ---- Media ----------------------------------------------------------

    fun addClips(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(importing = true) }
            val clips = importer.import(uris)
            _state.update { it.copy(importing = false) }
            if (clips.isEmpty()) message(R.string.msg_import_failed) else execute(AddClipsCommand(clips))
        }
    }

    fun export() {
        val project = history?.current ?: return
        if (project.mainTrack.clips.isEmpty()) {
            message(R.string.msg_empty_export)
            return
        }
        preview.pause()
        viewModelScope.launch {
            repository.save(project) // make sure Export reads the latest state
            _events.send(EditorEvent.OpenExport(project.id))
        }
    }

    // ---- Internals ------------------------------------------------------

    private fun selected(): String? = _state.value.selectedClipId

    /** Runs a command through the history. Returns true when something changed. */
    private fun execute(command: TimelineCommand): Boolean {
        val h = history ?: return false
        if (!h.execute(command)) return false
        h.replaceCurrent(h.current.copy(updatedAtMillis = System.currentTimeMillis()))
        publish(h.current, reloadPreview = true, save = true)
        return true
    }

    private fun publish(project: Project, reloadPreview: Boolean, save: Boolean = false) {
        val h = history
        _state.update { s ->
            val selectionStillExists = project.mainTrack.clips.any { it.id == s.selectedClipId }
            s.copy(
                project = project,
                selectedClipId = s.selectedClipId.takeIf { selectionStillExists },
                canUndo = h?.canUndo == true,
                canRedo = h?.canRedo == true,
            )
        }
        if (reloadPreview) preview.setProject(project, positionUs.value)
        if (save) viewModelScope.launch { repository.save(project) }
    }

    private fun message(text: Int) {
        viewModelScope.launch { _events.send(EditorEvent.Message(text)) }
    }

    override fun onCleared() {
        preview.release()
    }
}
