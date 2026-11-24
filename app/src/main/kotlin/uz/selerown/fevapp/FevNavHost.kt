package uz.selerown.fevapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import uz.selerown.fevapp.core.designsystem.theme.FevTheme
import uz.selerown.fevapp.feature.editor.editorScreen
import uz.selerown.fevapp.feature.editor.navigateToEditor
import uz.selerown.fevapp.feature.export.exportScreen
import uz.selerown.fevapp.feature.export.navigateToExport
import uz.selerown.fevapp.feature.home.HomeRoute
import uz.selerown.fevapp.feature.home.homeScreen

/** App navigation: Home → Editor → Export. Features only know their own routes. */
@Composable
fun FevNavHost() {
    val nav = rememberNavController()
    NavHost(
        navController = nav,
        startDestination = HomeRoute,
        modifier = Modifier.fillMaxSize().background(FevTheme.colors.background),
    ) {
        homeScreen(onOpenProject = { id -> nav.navigateToEditor(id) })
        editorScreen(
            onBack = { nav.popBackStack() },
            onExport = { id -> nav.navigateToExport(id) },
        )
        exportScreen(onBack = { nav.popBackStack() })
    }
}
