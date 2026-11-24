package uz.selerown.fevapp.feature.export

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data class ExportRoute(val projectId: String)

fun NavController.navigateToExport(projectId: String) = navigate(ExportRoute(projectId))

fun NavGraphBuilder.exportScreen(onBack: () -> Unit) {
    composable<ExportRoute> {
        ExportScreen(onBack = onBack)
    }
}
