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
import uz.selerown.fevapp.core.model.ClipTransform
import uz.selerown.fevapp.core.model.ColorAdjust
import uz.selerown.fevapp.core.model.Keyframe
import uz.selerown.fevapp.core.model.MediaType
import uz.selerown.fevapp.core.model.stateAt
import uz.selerown.fevapp.core.model.OverlayClip
import uz.selerown.fevapp.core.model.Project
import uz.selerown.fevapp.core.model.TextClip
import uz.selerown.fevapp.core.model.TextStyle
import uz.selerown.fevapp.core.model.TransitionType
import uz.selerown.fevapp.core.model.VoiceEffect
import uz.selerown.fevapp.engine.render.MediaImporter
import uz.selerown.fevapp.engine.render.PreviewPlayer
import uz.selerown.fevapp.engine.render.VoiceRecorder
import uz.selerown.fevapp.engine.render.Waveform
import uz.selerown.fevapp.engine.render.WaveformLoader
import uz.selerown.fevapp.engine.timeline.AddAudioCommand
import uz.selerown.fevapp.engine.timeline.AddClipsCommand
import uz.selerown.fevapp.engine.timeline.DeleteOverlayKeyframeCommand
import uz.selerown.fevapp.engine.timeline.DeleteTextKeyframeCommand
import uz.selerown.fevapp.engine.timeline.SetOverlayKeyframeCommand
import uz.selerown.fevapp.engine.timeline.SetTextKeyframeCommand
import uz.selerown.fevapp.engine.timeline.AddOverlayCommand
import uz.selerown.fevapp.engine.timeline.DeleteOverlayCommand
import uz.selerown.fevapp.engine.timeline.MoveOverlayCommand
import uz.selerown.fevapp.engine.timeline.SetOverlayTransformCommand
import uz.selerown.fevapp.engine.timeline.TrimOverlayCommand
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
import uz.selerown.fevapp.engine.timeline.FreezeFrameCommand
import uz.selerown.fevapp.engine.timeline.ReplaceClipCommand
import uz.selerown.fevapp.engine.timeline.SetAudioVoiceCommand
import uz.selerown.fevapp.engine.timeline.SetClipColorCommand
import uz.selerown.fevapp.engine.timeline.SetClipVoiceCommand
import uz.selerown.fevapp.engine.timeline.SetClipTransformCommand
import uz.selerown.fevapp.engine.timeline.SetClipSpeedCommand
import uz.selerown.fevapp.engine.timeline.SetClipVolumeCommand
import uz.selerown.fevapp.engine.timeline.SetTextTimingCommand
import uz.selerown.fevapp.engine.timeline.SetTransitionCommand
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
    private val recorder: VoiceRecorder,
    private val waveformLoader: WaveformLoader,
) : ViewModel() {

    private val projectId = savedStateHandle.toRoute<EditorRoute>().projectId
    private val ids = IdGenerator.Random
    private var history: EditHistory? = null

    /** Command shown live while a text/audio clip is dragged; committed on release. */
    private var pendingLaneCommand: TimelineCommand? = null

    val preview = PreviewPlayer(context)
    val positionUs: StateFlow<Long> = preview.positionUs
    val isPlaying: StateFlow<Boolean> = preview.isPlaying

    /** Waveforms of audio-lane sources, keyed by URI (filled in the background). */
    val waveforms: StateFlow<Map<String, Waveform>> = waveformLoader.waveforms

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
        if (tool == EditorTool.KEYFRAME && keyframeTarget() == null) {
            message(R.string.msg_playhead_outside)
            return
        }
        if (tool.opensSheet) {
            openSheet(tool)
            return
        }
        val selection = _state.value.selection
        when (tool) {
            EditorTool.EDIT -> selectClipAtPlayhead()
            EditorTool.OVERLAY -> viewModelScope.launch { _events.send(EditorEvent.PickOverlay) }
            EditorTool.ADD_MEDIA -> viewModelScope.launch { _events.send(EditorEvent.PickMediaAtPlayhead) }
            EditorTool.FREEZE -> freezeFrame()
            EditorTool.REPLACE -> if (_state.value.selectedClipId != null) {
                viewModelScope.launch { _events.send(EditorEvent.PickReplacement) }
            }
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
        if (tool == EditorTool.CLIP_FILTER || tool == EditorTool.CROP) showSelectedClipInPreview()
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

    fun applyFormat(format: CanvasFormat, backgroundColor: Int, blur: Boolean) {
        execute(SetCanvasCommand(format, backgroundColor, blur))
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
            Lane.OVERLAY -> DeleteOverlayCommand(selection.id)
        }
        execute(command)
        select(null)
    }

    private fun duplicate(selection: Selection?) {
        selection ?: return
        when (selection.lane) {
            Lane.MAIN -> execute(DuplicateClipCommand(selection.id, ids.newId()))
            Lane.TEXT -> execute(DuplicateTextCommand(selection.id, ids.newId()))
            Lane.AUDIO, Lane.OVERLAY -> Unit
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

    /** Live move/resize of a text on the preview (one undo step on release). */
    fun onTextTransform(id: String, x: Float, y: Float, size: Float) {
        preview.pause()
        val fixed = size.coerceIn(TextStyle.MIN_SIZE, TextStyle.MAX_SIZE)
        _state.update { it.copy(textDraft = TextDraft(id, x.coerceIn(0f, 1f), y.coerceIn(0f, 1f), fixed)) }
    }

    fun onTextTransformEnd() {
        val draft = _state.value.textDraft ?: return
        _state.update { it.copy(textDraft = null) }
        val clip = current?.textClips?.firstOrNull { it.id == draft.id } ?: return
        if (clip.keyframes.isNotEmpty()) {
            // Animated text: the gesture edits the keyframe at the playhead.
            val offset = (positionUs.value - clip.startUs).coerceIn(0L, clip.durationUs)
            val alpha = clip.stateAt(positionUs.value).alpha
            execute(SetTextKeyframeCommand(clip.id, Keyframe(offset, draft.x, draft.y, draft.size, alpha)))
            return
        }
        val style = clip.style.copy(positionX = draft.x, positionY = draft.y, sizeFraction = draft.size)
        execute(UpdateTextCommand(clip.id, clip.text, style))
    }

    // ---- Overlays (picture-in-picture) -----------------------------------

    /** Adds a video or photo on top of the main track at the playhead. */
    fun addOverlay(uri: Uri?) {
        uri ?: return
        viewModelScope.launch {
            _state.update { it.copy(importing = true) }
            val clip = importer.import(listOf(uri)).firstOrNull()
            _state.update { it.copy(importing = false) }
            if (clip == null) {
                message(R.string.msg_import_failed)
                return@launch
            }
            val overlay = OverlayClip(
                id = ids.newId(),
                source = clip.source,
                startUs = positionUs.value,
                trimStartUs = clip.trimStartUs,
                trimEndUs = clip.trimEndUs,
            )
            if (execute(AddOverlayCommand(overlay))) select(Selection(Lane.OVERLAY, overlay.id))
        }
    }

    fun setOverlayTransform(centerX: Float, centerY: Float, scale: Float) {
        closeTool()
        val id = _state.value.selection?.takeIf { it.lane == Lane.OVERLAY }?.id ?: return
        execute(SetOverlayTransformCommand(id, centerX, centerY, scale))
    }

    /** Live move/resize of an overlay on the preview (one undo step on release). */
    fun onOverlayTransform(id: String, x: Float, y: Float, scale: Float) {
        preview.pause()
        val fixed = scale.coerceIn(OverlayClip.MIN_SCALE, OverlayClip.MAX_SCALE)
        _state.update { it.copy(overlayDraft = TextDraft(id, x.coerceIn(0f, 1f), y.coerceIn(0f, 1f), fixed)) }
    }

    fun onOverlayTransformEnd() {
        val draft = _state.value.overlayDraft ?: return
        _state.update { it.copy(overlayDraft = null) }
        val clip = current?.overlayClips?.firstOrNull { it.id == draft.id } ?: return
        if (clip.keyframes.isNotEmpty()) {
            val offset = (positionUs.value - clip.startUs).coerceIn(0L, clip.durationUs)
            val alpha = clip.stateAt(positionUs.value).alpha
            execute(SetOverlayKeyframeCommand(clip.id, Keyframe(offset, draft.x, draft.y, draft.size, alpha)))
            return
        }
        execute(SetOverlayTransformCommand(draft.id, draft.x, draft.y, draft.size))
    }

    // ---- Keyframes ------------------------------------------------------

    /** Selected text/overlay and the playhead offset inside it, or null when outside. */
    private fun keyframeTarget(): Pair<Selection, Long>? {
        val sel = _state.value.selection ?: return null
        val project = current ?: return null
        val (start, end) = when (sel.lane) {
            Lane.TEXT -> project.textClips.firstOrNull { it.id == sel.id }?.let { it.startUs to it.endUs }
            Lane.OVERLAY -> project.overlayClips.firstOrNull { it.id == sel.id }?.let { it.startUs to it.endUs }
            else -> null
        } ?: return null
        val t = positionUs.value
        return if (t in start..end) sel to (t - start) else null
    }

    /** Saves (adds or replaces) the keyframe at the playhead. */
    fun saveKeyframe(x: Float, y: Float, scale: Float, alpha: Float) {
        closeTool()
        val (sel, offset) = keyframeTarget() ?: return message(R.string.msg_playhead_outside)
        val k = Keyframe(offset, x, y, scale, alpha)
        execute(if (sel.lane == Lane.TEXT) SetTextKeyframeCommand(sel.id, k) else SetOverlayKeyframeCommand(sel.id, k))
    }

    fun deleteKeyframeHere() {
        closeTool()
        val (sel, offset) = keyframeTarget() ?: return
        execute(if (sel.lane == Lane.TEXT) DeleteTextKeyframeCommand(sel.id, offset) else DeleteOverlayKeyframeCommand(sel.id, offset))
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

    /** Freeze frame at the playhead inside the selected clip. */
    private fun freezeFrame() {
        val id = _state.value.selectedClipId ?: return
        val project = current ?: return
        val at = TimelineRules.clipAt(project.mainTrack, positionUs.value)
        if (at == null || at.clip.id != id) {
            message(R.string.msg_playhead_outside)
            return
        }
        preview.pause()
        viewModelScope.launch {
            _state.update { it.copy(importing = true) }
            val frame = importer.grabFrame(at.clip, at.clip.sourceTimeAt(at.offsetUs))
            _state.update { it.copy(importing = false) }
            if (frame == null) {
                message(R.string.msg_freeze_failed)
                return@launch
            }
            val freezeId = ids.newId()
            if (execute(FreezeFrameCommand(id, positionUs.value, frame, freezeId, ids.newId()))) {
                select(Selection(Lane.MAIN, freezeId))
            }
        }
    }

    /** Replaces the selected clip's media, keeping its place and settings. */
    fun replaceClip(uri: Uri?) {
        uri ?: return
        val id = _state.value.selectedClipId ?: return
        viewModelScope.launch {
            _state.update { it.copy(importing = true) }
            val clip = importer.import(listOf(uri)).firstOrNull()
            _state.update { it.copy(importing = false) }
            if (clip == null) message(R.string.msg_import_failed) else execute(ReplaceClipCommand(id, clip.source))
        }
    }

    /** Live crop/rotate preview while the sheet is open (nothing is saved). */
    fun previewTransform(transform: ClipTransform) {
        val id = _state.value.selectedClipId ?: return
        val clip = current?.mainTrack?.clips?.firstOrNull { it.id == id } ?: return
        preview.previewClipStyle(clip.copy(transform = transform))
    }

    fun setClipTransform(transform: ClipTransform) {
        val id = _state.value.selectedClipId
        closeTool()
        execute(SetClipTransformCommand(id ?: return, transform))
    }

    fun setClipColor(look: ClipLook, color: ColorAdjust, applyToAll: Boolean) {
        val id = _state.value.selectedClipId
        closeTool()
        execute(SetClipColorCommand(if (applyToAll) null else id ?: return, look, color))
    }

    // ---- Voice-over -----------------------------------------------------

    /**
     * Starts recording from the microphone at the playhead. The video plays
     * MUTED meanwhile so the speaker is not recorded too.
     */
    fun startVoiceOver() {
        closeTool()
        if (_state.value.recording != null) return
        if (!recorder.start()) {
            message(R.string.msg_record_failed)
            return
        }
        select(null)
        _state.update { it.copy(recording = Recording(positionUs.value, System.currentTimeMillis())) }
        preview.muted = true
        preview.play()
    }

    /** Stops recording and puts the voice-over on the audio lane. */
    fun stopVoiceOver() {
        val recording = _state.value.recording ?: return
        preview.pause()
        preview.muted = false
        _state.update { it.copy(recording = null) }
        val file = recorder.stop()
        if (file == null) {
            message(R.string.msg_record_failed)
            return
        }
        viewModelScope.launch {
            val clip = importer.importAudio(Uri.fromFile(file), recording.startUs)
            if (clip == null) {
                message(R.string.msg_record_failed)
                return@launch
            }
            val number = (current?.audioClips?.count { it.source.uri.startsWith("file:") } ?: 0) + 1
            val named = clip.copy(name = context.getString(R.string.voice_clip_name, number))
            if (execute(AddAudioCommand(named))) select(Selection(Lane.AUDIO, named.id))
        }
    }

    /** Transition after the selected clip (into the next one), or after every clip. */
    fun setTransition(type: TransitionType, durationUs: Long, applyToAll: Boolean) {
        val id = _state.value.selectedClipId
        closeTool()
        val clips = current?.mainTrack?.clips ?: return
        if (!applyToAll && clips.lastOrNull()?.id == id) {
            message(R.string.msg_transition_last)
            return
        }
        execute(SetTransitionCommand(if (applyToAll) null else id ?: return, type, durationUs))
    }

    /** Voice effect for the selected video clip or audio clip. */
    fun setVoice(effect: VoiceEffect) {
        closeTool()
        val sel = _state.value.selection ?: return
        when (sel.lane) {
            Lane.MAIN -> {
                val clip = current?.mainTrack?.clips?.firstOrNull { it.id == sel.id } ?: return
                if (clip.source.type != MediaType.VIDEO) message(R.string.msg_voice_photo)
                else execute(SetClipVoiceCommand(sel.id, effect))
            }
            Lane.AUDIO -> execute(SetAudioVoiceCommand(sel.id, effect))
            else -> Unit
        }
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
            Lane.OVERLAY -> MoveOverlayCommand(selection.id, startUs)
            Lane.MAIN -> null
        }

    private fun trimLaneCommand(selection: Selection, startUs: Long, endUs: Long): TimelineCommand? =
        when (selection.lane) {
            Lane.TEXT -> SetTextTimingCommand(selection.id, startUs, endUs)
            Lane.AUDIO -> TrimAudioCommand(selection.id, startUs, endUs)
            Lane.OVERLAY -> TrimOverlayCommand(selection.id, startUs, endUs)
            Lane.MAIN -> null
        }

    private fun laneTiming(project: Project, selection: Selection): Pair<Long, Long>? = when (selection.lane) {
        Lane.TEXT -> project.textClips.firstOrNull { it.id == selection.id }?.let { it.startUs to it.endUs }
        Lane.AUDIO -> project.audioClips.firstOrNull { it.id == selection.id }?.let { it.startUs to it.endUs }
        Lane.OVERLAY -> project.overlayClips.firstOrNull { it.id == selection.id }?.let { it.startUs to it.endUs }
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

    /** Adds picked media at the end, or at the playhead when [atPlayhead]. */
    fun addClips(uris: List<Uri>, atPlayhead: Boolean = false) {
        if (uris.isEmpty()) return
        val index = if (atPlayhead) insertIndexAtPlayhead() else null
        viewModelScope.launch {
            _state.update { it.copy(importing = true) }
            val clips = importer.import(uris)
            _state.update { it.copy(importing = false) }
            if (clips.isEmpty()) message(R.string.msg_import_failed) else execute(AddClipsCommand(clips, index))
        }
    }

    /** Before the clip under the playhead if in its first half, otherwise after it. */
    private fun insertIndexAtPlayhead(): Int? {
        val track = current?.mainTrack ?: return null
        val at = TimelineRules.clipAt(track, positionUs.value) ?: return null
        return if (at.offsetUs < at.clip.durationUs / 2) at.index else at.index + 1
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
        project.audioClips.map { it.source.uri }.distinct().forEach { uri ->
            viewModelScope.launch { waveformLoader.request(uri) }
        }
        if (save) viewModelScope.launch { repository.save(project) }
    }

    private fun Project.contains(selection: Selection): Boolean = when (selection.lane) {
        Lane.MAIN -> mainTrack.clips.any { it.id == selection.id }
        Lane.TEXT -> textClips.any { it.id == selection.id }
        Lane.AUDIO -> audioClips.any { it.id == selection.id }
        Lane.OVERLAY -> overlayClips.any { it.id == selection.id }
    }

    private fun message(text: Int) {
        viewModelScope.launch { _events.send(EditorEvent.Message(text)) }
    }

    override fun onCleared() {
        recorder.stop()
        preview.release()
    }
}
