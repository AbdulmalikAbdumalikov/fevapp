package uz.selerown.fevapp.feature.editor

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data class EditorRoute(val projectId: String)

fun NavController.navigateToEditor(projectId: String) = navigate(EditorRoute(projectId))

fun NavGraphBuilder.editorScreen(
    onBack: () -> Unit,
    onExport: (projectId: String) -> Unit,
) {
    composable<EditorRoute> {
        EditorScreen(onBack = onBack, onExport = onExport)
    }
}
