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
import uz.selerown.fevapp.core.model.Clip
import uz.selerown.fevapp.core.model.ClipLook
import uz.selerown.fevapp.core.model.ColorAdjust
import uz.selerown.fevapp.core.model.MediaType
import uz.selerown.fevapp.core.model.Project
import uz.selerown.fevapp.core.model.TextClip
import uz.selerown.fevapp.core.model.TextStyle
import uz.selerown.fevapp.engine.render.MediaImporter
import uz.selerown.fevapp.engine.render.PreviewPlayer
import uz.selerown.fevapp.engine.timeline.AddAudioCommand
import uz.selerown.fevapp.engine.timeline.AddClipsCommand
import uz.selerown.fevapp.engine.timeline.AddTextCommand
import uz.selerown.fevapp.engine.timeline.DeleteAudioCommand
import uz.selerown.fevapp.engine.timeline.DeleteClipCommand
import uz.selerown.fevapp.engine.timeline.DeleteTextCommand
import uz.selerown.fevapp.engine.timeline.DuplicateClipCommand
import uz.selerown.fevapp.engine.timeline.DuplicateTextCommand
import uz.selerown.fevapp.engine.timeline.EditHistory
import uz.selerown.fevapp.engine.timeline.ExtractAudioCommand
import uz.selerown.fevapp.engine.timeline.MoveAudioCommand
import uz.selerown.fevapp.engine.timeline.MoveClipCommand
import uz.selerown.fevapp.engine.timeline.SetAudioMixCommand
import uz.selerown.fevapp.engine.timeline.SetCanvasCommand
import uz.selerown.fevapp.engine.timeline.SetClipColorCommand
import uz.selerown.fevapp.engine.timeline.SetClipSpeedCommand
import uz.selerown.fevapp.engine.timeline.SetClipVolumeCommand
import uz.selerown.fevapp.engine.timeline.SetTextTimingCommand
import uz.selerown.fevapp.engine.timeline.SplitAudioCommand
import uz.selerown.fevapp.engine.timeline.SplitClipCommand
import uz.selerown.fevapp.engine.timeline.TimelineCommand
import uz.selerown.fevapp.engine.timeline.TimelineRules
import uz.selerown.fevapp.engine.timeline.TrimAudioCommand
import uz.selerown.fevapp.engine.timeline.TrimClipCommand
import uz.selerown.fevapp.engine.timeline.UpdateTextCommand
import javax.inject.Inject

/**
 * Editor state holder. All timeline edits go through [EditHistory] so they
 * can be undone; after each change the preview is reloaded and the project
 * is saved to Room (auto-save).
 */
@HiltViewModel
class EditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val repository: ProjectRepository,
    private val importer: MediaImporter,
) : ViewModel() {

    private val projectId = savedStateHandle.toRoute<EditorRoute>().projectId
    private val ids = IdGenerator.Random
    private var history: EditHistory? = null

    /** Command shown live while a text/audio clip is dragged; committed on release. */
    private var pendingLaneCommand: TimelineCommand? = null

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
        // Keep the playhead and the audio lane in sync with playback (~30 fps).
        viewModelScope.launch {
            while (isActive) {
                preview.tick()
                delay(33)
            }
        }
    }

    private val current: Project? get() = history?.current

    // ---- Playback -------------------------------------------------------

    fun togglePlay() = preview.togglePlay()

    fun onScrubStart() = preview.pause()

    fun seekTo(timeUs: Long) = preview.seekTo(timeUs)

    // ---- Selection ------------------------------------------------------

    fun select(selection: Selection?) {
        _state.update { it.copy(selection = selection) }
    }

    fun selectClip(clipId: String?) = select(clipId?.let { Selection(Lane.MAIN, it) })

    // ---- Tools ----------------------------------------------------------

    fun onTool(tool: EditorTool) {
        if (tool.opensSheet) {
            openSheet(tool)
            return
        }
        val selection = _state.value.selection
        when (tool) {
            EditorTool.EDIT -> selectClipAtPlayhead()
            EditorTool.SPEED -> if (selectClipAtPlayhead()) openSheet(EditorTool.CLIP_SPEED)
            EditorTool.FILTER -> if (selectClipAtPlayhead()) openSheet(EditorTool.CLIP_FILTER)
            EditorTool.SPLIT -> split(selection)
            EditorTool.DELETE -> delete(selection)
            EditorTool.DUPLICATE -> duplicate(selection)
            EditorTool.MOVE_LEFT -> moveSelected(-1)
            EditorTool.MOVE_RIGHT -> moveSelected(+1)
            EditorTool.EXTRACT_AUDIO -> extractAudio()
            EditorTool.MOVE_HERE, EditorTool.START_HERE, EditorTool.END_HERE -> placeAtPlayhead(tool, selection)
            else -> Unit
        }
    }

    private fun openSheet(tool: EditorTool) {
        preview.pause()
        if (tool == EditorTool.CLIP_FILTER) showSelectedClipInPreview()
        _state.update { it.copy(openTool = tool) }
    }

    fun closeTool() {
        preview.previewClipStyle(null)
        _state.update { it.copy(openTool = null) }
    }

    /** Moves the playhead into the selected clip so filter changes are visible. */
    private fun showSelectedClipInPreview() {
        val project = current ?: return
        val id = _state.value.selectedClipId ?: return
        val index = project.mainTrack.indexOf(id)
        if (index < 0) return
        val start = project.mainTrack.startOf(index)
        val end = start + project.mainTrack.clips[index].durationUs
        if (positionUs.value !in start until end) preview.seekTo(start + minOf(100_000L, (end - start) / 2))
    }

    fun applyFormat(format: CanvasFormat) {
        execute(SetCanvasCommand(format))
        closeTool()
    }

    /** Selects the main-track clip under the playhead. Returns false when there is none. */
    private fun selectClipAtPlayhead(): Boolean {
        val project = current ?: return false
        val clip = TimelineRules.clipAt(project.mainTrack, positionUs.value)?.clip
            ?: project.mainTrack.clips.lastOrNull()?.takeIf { positionUs.value >= project.durationUs }
        if (clip == null) message(R.string.msg_no_clip_here) else selectClip(clip.id)
        return clip != null
    }

    private fun split(selection: Selection?) {
        val changed = if (selection?.lane == Lane.AUDIO) {
            execute(SplitAudioCommand(selection.id, positionUs.value, ids.newId()))
        } else {
            execute(SplitClipCommand(positionUs.value, ids.newId(), selection?.id))
        }
        if (!changed) message(R.string.msg_split_failed)
    }

    private fun delete(selection: Selection?) {
        selection ?: return
        val command = when (selection.lane) {
            Lane.MAIN -> DeleteClipCommand(selection.id)
            Lane.TEXT -> DeleteTextCommand(selection.id)
            Lane.AUDIO -> DeleteAudioCommand(selection.id)
        }
        execute(command)
        select(null)
    }

    private fun duplicate(selection: Selection?) {
        selection ?: return
        when (selection.lane) {
            Lane.MAIN -> execute(DuplicateClipCommand(selection.id, ids.newId()))
            Lane.TEXT -> execute(DuplicateTextCommand(selection.id, ids.newId()))
            Lane.AUDIO -> Unit
        }
    }

    private fun moveSelected(delta: Int) {
        val id = _state.value.selectedClipId ?: return
        val index = current?.mainTrack?.indexOf(id) ?: return
        execute(MoveClipCommand(id, index + delta))
    }

    fun moveClip(clipId: String, toIndex: Int) {
        execute(MoveClipCommand(clipId, toIndex))
    }

    private fun extractAudio() {
        val id = _state.value.selectedClipId ?: return
        val name = context.getString(R.string.audio_extracted_name)
        if (!execute(ExtractAudioCommand(id, ids.newId(), name))) message(R.string.msg_extract_failed)
    }

    /** "Shu yerga" / "Boshi shu yerda" / "Oxiri shu yerda" for text and audio clips. */
    private fun placeAtPlayhead(tool: EditorTool, selection: Selection?) {
        val project = current ?: return
        selection ?: return
        val (start, end) = laneTiming(project, selection) ?: return
        val t = positionUs.value
        val valid = when (tool) {
            EditorTool.START_HERE -> t < end - Clip.MIN_DURATION_US
            EditorTool.END_HERE -> t > start + Clip.MIN_DURATION_US
            else -> true
        }
        if (!valid) {
            message(R.string.msg_playhead_outside)
            return
        }
        val command = when (tool) {
            EditorTool.MOVE_HERE -> moveLaneCommand(selection, t, project)
            EditorTool.START_HERE -> trimLaneCommand(selection, t, end)
            else -> trimLaneCommand(selection, start, t)
        }
        command?.let(::execute)
    }

    // ---- Text -----------------------------------------------------------

    /** Adds a new text at the playhead, or updates the selected one (TEXT_EDIT). */
    fun saveText(editingId: String?, text: String, style: TextStyle) {
        closeTool()
        if (text.isBlank()) {
            message(R.string.msg_text_empty)
            return
        }
        if (editingId != null) {
            execute(UpdateTextCommand(editingId, text.trim(), style))
            return
        }
        val clip = TextClip(
            id = ids.newId(),
            text = text.trim(),
            startUs = positionUs.value,
            durationUs = TextClip.DEFAULT_DURATION_US,
            style = style,
        )
        if (execute(AddTextCommand(clip))) select(Selection(Lane.TEXT, clip.id))
    }

    // ---- Audio ----------------------------------------------------------

    /** Adds a music file (or a video's sound) at the playhead. */
    fun addAudio(uri: Uri?) {
        uri ?: return
        closeTool()
        viewModelScope.launch {
            _state.update { it.copy(importing = true) }
            val clip = importer.importAudio(uri, positionUs.value)
            _state.update { it.copy(importing = false) }
            if (clip == null) {
                message(R.string.msg_import_failed)
            } else if (execute(AddAudioCommand(clip))) {
                select(Selection(Lane.AUDIO, clip.id))
            }
        }
    }

    // ---- Speed and color -------------------------------------------------

    fun setClipSpeed(speed: Float) {
        closeTool()
        val id = _state.value.selectedClipId ?: return
        val clip = current?.mainTrack?.clips?.firstOrNull { it.id == id } ?: return
        if (clip.source.type != MediaType.VIDEO) {
            message(R.string.msg_speed_photo)
            return
        }
        execute(SetClipSpeedCommand(id, speed))
    }

    /** Live filter preview while the sheet is open (nothing is saved). */
    fun previewColor(look: ClipLook, color: ColorAdjust) {
        val id = _state.value.selectedClipId ?: return
        val clip = current?.mainTrack?.clips?.firstOrNull { it.id == id } ?: return
        preview.previewClipStyle(clip.copy(look = look, color = color))
    }

    fun setClipColor(look: ClipLook, color: ColorAdjust, applyToAll: Boolean) {
        val id = _state.value.selectedClipId
        closeTool()
        execute(SetClipColorCommand(if (applyToAll) null else id ?: return, look, color))
    }

    fun setClipVolume(volume: Float) {
        closeTool()
        _state.value.selectedClipId?.let { execute(SetClipVolumeCommand(it, volume)) }
    }

    fun setAudioMix(volume: Float, fadeInUs: Long, fadeOutUs: Long) {
        closeTool()
        val id = _state.value.selection?.takeIf { it.lane == Lane.AUDIO }?.id ?: return
        execute(SetAudioMixCommand(id, volume, fadeInUs, fadeOutUs))
    }

    // ---- Dragging text/audio clips (live draft, one undo step on release)

    fun onLaneMove(selection: Selection, startUs: Long) {
        val project = current ?: return
        moveLaneCommand(selection, startUs, project)?.let { showLaneDraft(selection, it) }
    }

    fun onLaneTrim(selection: Selection, startUs: Long, endUs: Long) {
        trimLaneCommand(selection, startUs, endUs)?.let { showLaneDraft(selection, it) }
    }

    fun onLaneDragEnd() {
        val command = pendingLaneCommand
        pendingLaneCommand = null
        _state.update { it.copy(laneDraft = null) }
        command?.let(::execute)
    }

    private fun showLaneDraft(selection: Selection, command: TimelineCommand) {
        val project = current ?: return
        // Commands are pure: apply to a copy to get the clamped result without committing.
        val (start, end) = laneTiming(command.apply(project), selection) ?: return
        preview.pause()
        pendingLaneCommand = command
        _state.update { it.copy(laneDraft = LaneDraft(selection.id, start, end), selection = selection) }
    }

    private fun moveLaneCommand(selection: Selection, startUs: Long, project: Project): TimelineCommand? =
        when (selection.lane) {
            Lane.TEXT -> project.textClips.firstOrNull { it.id == selection.id }
                ?.let { SetTextTimingCommand(it.id, startUs, startUs + it.durationUs) }
            Lane.AUDIO -> MoveAudioCommand(selection.id, startUs)
            Lane.MAIN -> null
        }

    private fun trimLaneCommand(selection: Selection, startUs: Long, endUs: Long): TimelineCommand? =
        when (selection.lane) {
            Lane.TEXT -> SetTextTimingCommand(selection.id, startUs, endUs)
            Lane.AUDIO -> TrimAudioCommand(selection.id, startUs, endUs)
            Lane.MAIN -> null
        }

    private fun laneTiming(project: Project, selection: Selection): Pair<Long, Long>? = when (selection.lane) {
        Lane.TEXT -> project.textClips.firstOrNull { it.id == selection.id }?.let { it.startUs to it.endUs }
        Lane.AUDIO -> project.audioClips.firstOrNull { it.id == selection.id }?.let { it.startUs to it.endUs }
        Lane.MAIN -> null
    }

    // ---- Trim of main-track clips (live draft, one undo step on release) -

    fun onTrimDrag(clipId: String, startUs: Long, endUs: Long) {
        val clip = current?.mainTrack?.clips?.firstOrNull { it.id == clipId } ?: return
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
        val project = current ?: return
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
            s.copy(
                project = project,
                selection = s.selection?.takeIf { project.contains(it) },
                canUndo = h?.canUndo == true,
                canRedo = h?.canRedo == true,
            )
        }
        if (reloadPreview) preview.setProject(project, positionUs.value)
        if (save) viewModelScope.launch { repository.save(project) }
    }

    private fun Project.contains(selection: Selection): Boolean = when (selection.lane) {
        Lane.MAIN -> mainTrack.clips.any { it.id == selection.id }
        Lane.TEXT -> textClips.any { it.id == selection.id }
        Lane.AUDIO -> audioClips.any { it.id == selection.id }
    }

    private fun message(text: Int) {
        viewModelScope.launch { _events.send(EditorEvent.Message(text)) }
    }

    override fun onCleared() {
        preview.release()
    }
}
