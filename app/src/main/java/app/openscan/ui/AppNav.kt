package app.openscan.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.openscan.ui.capture.CaptureScreen
import app.openscan.ui.components.BusyOverlay
import app.openscan.ui.document.DocumentScreen
import app.openscan.ui.editor.EditorScreen
import app.openscan.ui.library.LibraryScreen
import app.openscan.ui.settings.SettingsScreen
import kotlinx.coroutines.flow.filterNotNull

@Composable
fun AppNav() {
    val viewModel: MainViewModel = viewModel()
    val navController = rememberNavController()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        snapshotFlow { viewModel.message }
            .filterNotNull()
            .collect { text ->
                snackbar.showSnackbar(text)
                viewModel.consumeMessage()
            }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = "library") {
            composable("library") {
                LibraryScreen(
                    viewModel = viewModel,
                    onOpenDocument = { id -> navController.navigate("document/$id") },
                    onScan = { navController.navigate("capture") },
                    onSettings = { navController.navigate("settings") }
                )
            }

            composable(
                route = "capture?docId={docId}",
                arguments = listOf(
                    navArgument("docId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { entry ->
                val initialDocId = entry.arguments?.getString("docId")
                CaptureScreen(
                    viewModel = viewModel,
                    initialDocId = initialDocId,
                    onCancel = { navController.popBackStack() },
                    onDone = { id ->
                        navController.navigate("editor/$id") {
                            popUpTo("library")
                        }
                    }
                )
            }

            composable(
                route = "editor/{docId}",
                arguments = listOf(navArgument("docId") { type = NavType.StringType })
            ) { entry ->
                val docId = entry.arguments?.getString("docId").orEmpty()
                EditorScreen(
                    viewModel = viewModel,
                    docId = docId,
                    onBack = { navController.popBackStack() },
                    onAddPage = { navController.navigate("capture?docId=$docId") }
                )
            }

            composable(
                route = "document/{docId}",
                arguments = listOf(navArgument("docId") { type = NavType.StringType })
            ) { entry ->
                val docId = entry.arguments?.getString("docId").orEmpty()
                DocumentScreen(
                    viewModel = viewModel,
                    docId = docId,
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate("editor/$docId") },
                    onMerged = { mergedId ->
                        navController.navigate("document/$mergedId") {
                            popUpTo(entry.destination.id) { inclusive = true }
                        }
                    }
                )
            }

            composable("settings") {
                SettingsScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }
        }

        BusyOverlay(text = viewModel.busy)

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}
