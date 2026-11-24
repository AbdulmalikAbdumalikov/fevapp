package uz.selerown.fevapp.feature.home

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** Type-safe navigation route of the Home screen. */
@Serializable
data object HomeRoute

fun NavGraphBuilder.homeScreen(onOpenProject: (projectId: String) -> Unit) {
    composable<HomeRoute> {
        HomeScreen(onOpenProject = onOpenProject)
    }
}
