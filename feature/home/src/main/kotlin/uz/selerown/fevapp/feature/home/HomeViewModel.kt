package uz.selerown.fevapp.feature.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uz.selerown.fevapp.core.database.ProjectRepository
import uz.selerown.fevapp.core.model.ProjectSummary
import uz.selerown.fevapp.engine.render.MediaImporter
import javax.inject.Inject

/** One-off events for the Home UI. */
sealed interface HomeEvent {
    data class OpenProject(val projectId: String) : HomeEvent
    data object ImportFailed : HomeEvent
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: ProjectRepository,
    private val importer: MediaImporter,
) : ViewModel() {

    val projects: StateFlow<List<ProjectSummary>?> = repository.observeProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    private val _events = Channel<HomeEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** Creates a project from picked media (selection order kept) and opens it. */
    fun createProject(uris: List<Uri>, name: String) {
        if (uris.isEmpty() || _importing.value) return
        viewModelScope.launch {
            _importing.value = true
            try {
                val clips = importer.import(uris)
                if (clips.isEmpty()) {
                    _events.send(HomeEvent.ImportFailed)
                } else {
                    val project = repository.create(name, clips)
                    _events.send(HomeEvent.OpenProject(project.id))
                }
            } finally {
                _importing.value = false
            }
        }
    }

    fun rename(id: String, name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch { repository.rename(id, clean) }
    }

    fun duplicate(id: String, newName: String) {
        viewModelScope.launch { repository.duplicate(id, newName) }
    }

    fun delete(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }
}
