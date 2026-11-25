package uz.selerown.fevapp.feature.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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
import javax.inject.Singleton

sealed interface ExportPhase {
    data object Idle : ExportPhase
    data class Running(val progress: Int) : ExportPhase
    data class Done(val uri: Uri) : ExportPhase
    data object Failed : ExportPhase
}

/** The one export that may run at a time, and which project it belongs to. */
data class ExportJobState(
    val projectId: String? = null,
    val phase: ExportPhase = ExportPhase.Idle,
)

/**
 * Runs exports OUTSIDE any screen so they survive leaving the app: the work
 * lives in an app-wide scope and [ExportService] keeps the process in the
 * foreground (with a progress notification) while it runs.
 */
@Singleton
class ExportManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ProjectRepository,
    private val engine: ExportEngine,
    private val gallery: GallerySaver,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    private val _state = MutableStateFlow(ExportJobState())
    val state: StateFlow<ExportJobState> = _state.asStateFlow()

    fun start(projectId: String, settings: ExportSettings) {
        if (job?.isActive == true) return
        _state.value = ExportJobState(projectId, ExportPhase.Running(0))
        ContextCompat.startForegroundService(context, Intent(context, ExportService::class.java))
        job = scope.launch {
            try {
                val project = repository.get(projectId) ?: error("Project not found")
                val file = engine.export(project, settings) { progress ->
                    _state.update { s -> if (s.phase is ExportPhase.Running) s.copy(phase = ExportPhase.Running(progress)) else s }
                }
                val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val uri = gallery.saveVideo(file, "Fevapp_$stamp.mp4")
                file.delete()
                _state.value = ExportJobState(projectId, ExportPhase.Done(uri))
            } catch (e: CancellationException) {
                _state.value = ExportJobState(projectId, ExportPhase.Idle)
                throw e
            } catch (e: Exception) {
                _state.value = ExportJobState(projectId, ExportPhase.Failed)
            }
        }
    }

    fun cancel() {
        job?.cancel()
        _state.update { it.copy(phase = ExportPhase.Idle) }
    }

    /** Forget a finished/failed result (back to the settings screen). */
    fun reset() {
        if (job?.isActive == true) return
        _state.value = ExportJobState()
    }
}
