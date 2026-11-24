package uz.selerown.fevapp.feature.export

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uz.selerown.fevapp.core.database.ProjectRepository
import uz.selerown.fevapp.engine.render.ExportEngine
import uz.selerown.fevapp.engine.render.ExportSettings
import uz.selerown.fevapp.engine.render.GallerySaver
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

sealed interface ExportPhase {
    data object Idle : ExportPhase
    data class Running(val progress: Int) : ExportPhase
    data class Done(val uri: Uri) : ExportPhase
    data object Failed : ExportPhase
}

data class ExportUiState(
    val settings: ExportSettings = ExportSettings(),
    val phase: ExportPhase = ExportPhase.Idle,
)

@HiltViewModel
class ExportViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ProjectRepository,
    private val engine: ExportEngine,
    private val gallery: GallerySaver,
) : ViewModel() {

    private val projectId = savedStateHandle.toRoute<ExportRoute>().projectId
    private var job: Job? = null

    private val _state = MutableStateFlow(ExportUiState())
    val state: StateFlow<ExportUiState> = _state.asStateFlow()

    fun setResolution(value: Int) = _state.update { it.copy(settings = it.settings.copy(resolution = value)) }

    fun setFrameRate(value: Int) = _state.update { it.copy(settings = it.settings.copy(frameRate = value)) }

    fun start() {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            _state.update { it.copy(phase = ExportPhase.Running(0)) }
            try {
                val project = repository.get(projectId) ?: error("Project not found")
                val file = engine.export(project, _state.value.settings) { progress ->
                    _state.update { s -> if (s.phase is ExportPhase.Running) s.copy(phase = ExportPhase.Running(progress)) else s }
                }
                val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val uri = gallery.saveVideo(file, "Fevapp_$stamp.mp4")
                file.delete()
                _state.update { it.copy(phase = ExportPhase.Done(uri)) }
            } catch (e: CancellationException) {
                _state.update { it.copy(phase = ExportPhase.Idle) }
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(phase = ExportPhase.Failed) }
            }
        }
    }

    fun cancel() {
        job?.cancel()
        _state.update { it.copy(phase = ExportPhase.Idle) }
    }

    fun reset() = _state.update { it.copy(phase = ExportPhase.Idle) }
}
