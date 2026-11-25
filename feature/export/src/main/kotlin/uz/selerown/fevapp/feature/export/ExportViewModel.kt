package uz.selerown.fevapp.feature.export

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import uz.selerown.fevapp.engine.render.ExportSettings
import javax.inject.Inject

data class ExportUiState(
    val settings: ExportSettings = ExportSettings(),
    val phase: ExportPhase = ExportPhase.Idle,
)

/**
 * Export screen state. The export itself runs in [ExportManager] so it keeps
 * going when the user leaves the screen or the app.
 */
@HiltViewModel
class ExportViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val manager: ExportManager,
) : ViewModel() {

    private val projectId = savedStateHandle.toRoute<ExportRoute>().projectId
    private val settings = MutableStateFlow(ExportSettings())

    val state: StateFlow<ExportUiState> = combine(settings, manager.state) { s, job ->
        // Show the running/finished export only if it belongs to this project.
        ExportUiState(s, if (job.projectId == projectId) job.phase else ExportPhase.Idle)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ExportUiState())

    init {
        // Opening the screen again shows the settings, not a finished/failed
        // old result (a running export is kept and shown).
        if (manager.state.value.phase !is ExportPhase.Running) manager.reset()
    }

    fun setResolution(value: Int) = settings.update { it.copy(resolution = value) }

    fun setFrameRate(value: Int) = settings.update { it.copy(frameRate = value) }

    fun start() = manager.start(projectId, settings.value)

    fun cancel() = manager.cancel()

    fun reset() = manager.reset()
}
