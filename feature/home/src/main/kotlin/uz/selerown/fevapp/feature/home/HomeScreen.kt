package uz.selerown.fevapp.feature.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.selerown.fevapp.core.designsystem.theme.FevDimens
import uz.selerown.fevapp.core.designsystem.theme.FevTheme
import uz.selerown.fevapp.core.model.ProjectSummary

@Composable
internal fun HomeScreen(
    onOpenProject: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val importing by viewModel.importing.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val failedText = stringResource(R.string.home_import_failed)
    val nextName = stringResource(R.string.home_default_project_name, (projects?.size ?: 0) + 1)

    // Android Photo Picker: videos + photos, multi-select, returns selection order.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        viewModel.createProject(uris, nextName)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is HomeEvent.OpenProject -> onOpenProject(event.projectId)
                HomeEvent.ImportFailed -> snackbar.showSnackbar(failedText)
            }
        }
    }

    var menuFor by remember { mutableStateOf<ProjectSummary?>(null) }
    var renameFor by remember { mutableStateOf<ProjectSummary?>(null) }
    var deleteFor by remember { mutableStateOf<ProjectSummary?>(null) }
    var renameText by rememberSaveable { mutableStateOf("") }

    Scaffold(
        containerColor = FevTheme.colors.background,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                contentPadding = PaddingValues(FevDimens.ScreenPadding),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        stringResource(R.string.home_title),
                        style = MaterialTheme.typography.headlineLarge,
                        color = FevTheme.colors.textPrimary,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    NewProjectCard(
                        enabled = !importing,
                        onClick = {
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                        },
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        stringResource(R.string.home_recent),
                        style = MaterialTheme.typography.titleMedium,
                        color = FevTheme.colors.textSecondary,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                val list = projects.orEmpty()
                if (projects != null && list.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            stringResource(R.string.home_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = FevTheme.colors.textSecondary,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        )
                    }
                }
                items(list, key = { it.id }) { project ->
                    Box {
                        ProjectCard(
                            project = project,
                            optionsLabel = stringResource(R.string.home_project_options),
                            onOpen = { onOpenProject(project.id) },
                            onOptions = { menuFor = project },
                        )
                        if (menuFor?.id == project.id) {
                            val duplicateName = stringResource(R.string.home_copy_name, project.name)
                            ProjectMenu(
                                onDismiss = { menuFor = null },
                                onRename = {
                                    menuFor = null
                                    renameText = project.name
                                    renameFor = project
                                },
                                onDuplicate = {
                                    menuFor = null
                                    viewModel.duplicate(project.id, duplicateName)
                                },
                                onDelete = {
                                    menuFor = null
                                    deleteFor = project
                                },
                            )
                        }
                    }
                }
            }

            if (importing) {
                ImportingOverlay()
            }
        }
    }

    renameFor?.let { project ->
        RenameDialog(
            text = renameText,
            onTextChange = { renameText = it },
            onDismiss = { renameFor = null },
            onConfirm = {
                viewModel.rename(project.id, renameText)
                renameFor = null
            },
        )
    }
    deleteFor?.let { project ->
        DeleteDialog(
            projectName = project.name,
            onDismiss = { deleteFor = null },
            onConfirm = {
                viewModel.delete(project.id)
                deleteFor = null
            },
        )
    }
}

@Composable
private fun ImportingOverlay() {
    Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = FevTheme.colors.accent)
            Text(
                stringResource(R.string.home_importing),
                color = FevTheme.colors.textPrimary,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}
