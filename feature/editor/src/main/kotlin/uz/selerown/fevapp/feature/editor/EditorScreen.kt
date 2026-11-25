package uz.selerown.fevapp.feature.editor

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.selerown.fevapp.core.designsystem.theme.FevTheme
import uz.selerown.fevapp.feature.editor.timeline.TimelineView
import uz.selerown.fevapp.feature.editor.timeline.TimelineZoom

@Composable
internal fun EditorScreen(
    onBack: () -> Unit,
    onExport: (String) -> Unit,
    viewModel: EditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val positionUs by viewModel.positionUs.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val colorMatrix by viewModel.preview.colorMatrix.collectAsStateWithLifecycle()
    val waveforms by viewModel.waveforms.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    var fullscreen by rememberSaveable { mutableStateOf(false) }
    var pxPerSecond by rememberSaveable { mutableFloatStateOf(TimelineZoom.DEFAULT_DP_PER_SECOND) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        viewModel.addClips(uris)
    }
    val openPicker = {
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
    }
    // Music: the system file picker (persistable access, works offline).
    val musicPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        viewModel.addAudio(uri)
    }
    // Microphone permission is asked only when the user taps "Ovoz yozish".
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.startVoiceOver() else viewModel.closeTool()
    }
    val recordVoice = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            viewModel.startVoiceOver()
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    val videoSoundPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        viewModel.addAudio(uri)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is EditorEvent.Message -> snackbar.showSnackbar(context.getString(event.text))
                is EditorEvent.OpenExport -> onExport(event.projectId)
                EditorEvent.Close -> onBack()
            }
        }
    }

    BackHandler(enabled = state.recording != null) { viewModel.stopVoiceOver() }
    BackHandler(enabled = fullscreen) { fullscreen = false }
    BackHandler(enabled = !fullscreen && state.selection != null) { viewModel.select(null) }

    val project = state.project
    Box(
        Modifier
            .fillMaxSize()
            .background(FevTheme.colors.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        if (project == null) {
            CircularProgressIndicator(color = FevTheme.colors.accent, modifier = Modifier.align(Alignment.Center))
        } else if (fullscreen) {
            PreviewArea(
                player = viewModel.preview.player,
                project = project,
                positionUs = positionUs,
                colorMatrix = colorMatrix,
                selectedTextId = state.selection?.takeIf { it.lane == Lane.TEXT }?.id,
                textDraft = state.textDraft,
                onTextDrag = viewModel::onTextDrag,
                onTextDragEnd = viewModel::onTextDragEnd,
                isPlaying = isPlaying,
                fullscreen = true,
                onTogglePlay = viewModel::togglePlay,
                onToggleFullscreen = { fullscreen = false },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Column(Modifier.fillMaxSize()) {
                EditorTopBar(
                    canUndo = state.canUndo,
                    canRedo = state.canRedo,
                    onBack = onBack,
                    onUndo = viewModel::undo,
                    onRedo = viewModel::redo,
                    onExport = viewModel::export,
                )
                PreviewArea(
                    player = viewModel.preview.player,
                    project = project,
                    positionUs = positionUs,
                    colorMatrix = colorMatrix,
                    selectedTextId = state.selection?.takeIf { it.lane == Lane.TEXT }?.id,
                    textDraft = state.textDraft,
                    onTextDrag = viewModel::onTextDrag,
                    onTextDragEnd = viewModel::onTextDragEnd,
                    isPlaying = isPlaying,
                    fullscreen = false,
                    onTogglePlay = viewModel::togglePlay,
                    onToggleFullscreen = { fullscreen = true },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                PlaybackBar(
                    positionUs = positionUs,
                    durationUs = project.durationUs,
                    isPlaying = isPlaying,
                    onTogglePlay = viewModel::togglePlay,
                    onZoomIn = { pxPerSecond = TimelineZoom.clamp(pxPerSecond * 1.5f) },
                    onZoomOut = { pxPerSecond = TimelineZoom.clamp(pxPerSecond / 1.5f) },
                    onFullscreen = { fullscreen = true },
                )
                TimelineView(
                    project = project,
                    positionUs = positionUs,
                    selection = state.selection,
                    trimDraft = state.trimDraft,
                    laneDraft = state.laneDraft,
                    waveforms = waveforms,
                    dpPerSecond = pxPerSecond,
                    onZoom = { factor -> pxPerSecond = TimelineZoom.clamp(pxPerSecond * factor) },
                    onScrubStart = viewModel::onScrubStart,
                    onScrub = viewModel::seekTo,
                    onSelect = viewModel::select,
                    onTrimDrag = viewModel::onTrimDrag,
                    onTrimEnd = viewModel::onTrimEnd,
                    onMoveClip = viewModel::moveClip,
                    onLaneMove = viewModel::onLaneMove,
                    onLaneTrim = viewModel::onLaneTrim,
                    onLaneDragEnd = viewModel::onLaneDragEnd,
                    onAddClip = openPicker,
                    onAddText = { viewModel.select(null); viewModel.onTool(EditorTool.TEXT) },
                    onAddAudio = { viewModel.select(null); viewModel.onTool(EditorTool.AUDIO) },
                )
                val recording = state.recording
                if (recording != null) {
                    RecordingBar(recording, onStop = viewModel::stopVoiceOver)
                } else EditorToolbar(
                    lane = state.selection?.lane,
                    onDeselect = { viewModel.select(null) },
                    onTool = viewModel::onTool,
                )
            }
        }

        if (state.importing) {
            CircularProgressIndicator(color = FevTheme.colors.accent, modifier = Modifier.align(Alignment.Center))
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    state.openTool?.let { tool ->
        ToolSheet(
            tool = tool,
            project = project,
            selection = state.selection,
            onDismiss = viewModel::closeTool,
            onApplyFormat = viewModel::applyFormat,
            onSaveText = viewModel::saveText,
            onPickMusic = { musicPicker.launch(arrayOf("audio/*")) },
            onRecordVoice = recordVoice,
            onPickVideoSound = {
                videoSoundPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
            },
            onSetClipVolume = viewModel::setClipVolume,
            onSetAudioMix = viewModel::setAudioMix,
            onSetSpeed = viewModel::setClipSpeed,
            onPreviewColor = viewModel::previewColor,
            onSetColor = viewModel::setClipColor,
            onSetTransition = viewModel::setTransition,
        )
    }
}
