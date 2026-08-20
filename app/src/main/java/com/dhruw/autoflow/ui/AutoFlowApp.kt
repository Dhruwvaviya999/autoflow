package com.dhruw.autoflow.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dhruw.autoflow.ui.automationeditor.AutomationEditorScreen
import com.dhruw.autoflow.ui.automations.AutomationsScreen
import com.dhruw.autoflow.ui.history.HistoryScreen
import com.dhruw.autoflow.ui.home.HomeScreen
import com.dhruw.autoflow.ui.instagram.InstagramAnalyzerScreen
import com.dhruw.autoflow.ui.instagram.InstagramAnalyzerViewModel
import com.dhruw.autoflow.ui.instagram.InstagramResultsScreen
import com.dhruw.autoflow.ui.components.UiAutomationSessionHost
import com.dhruw.autoflow.ui.inspector.UiInspectorScreen
import com.dhruw.autoflow.ui.navigation.TopLevelDestination
import com.dhruw.autoflow.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

private const val EDITOR_ROUTE = "editor?automationId={automationId}"
private const val INSTAGRAM_ROUTE = "instagram"
private const val INSTAGRAM_RESULTS_ROUTE = "instagram/results"
private const val INSPECTOR_ROUTE = "inspector"

private fun editorRoute(automationId: String? = null): String =
    if (automationId == null) "editor" else "editor?automationId=$automationId"

@Composable
fun AutoFlowApp() {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val onEditorScreen = currentRoute?.startsWith("editor") == true ||
        currentRoute?.startsWith(INSTAGRAM_ROUTE) == true ||
        currentRoute == INSPECTOR_ROUTE

    val showMessage: (String) -> Unit = { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (!onEditorScreen) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                ) {
                    TopLevelDestination.entries.forEach { destination ->
                        val selected = currentRoute == destination.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) {
                                        destination.selectedIcon
                                    } else {
                                        destination.unselectedIcon
                                    },
                                    contentDescription = destination.label
                                )
                            },
                            label = { Text(destination.label) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            NavHost(
                navController = navController,
                startDestination = TopLevelDestination.HOME.route
            ) {
            composable(TopLevelDestination.HOME.route) {
                HomeScreen(
                    onCreateAutomation = { navController.navigate(editorRoute()) },
                    onOpenInstagramAnalyzer = { navController.navigate(INSTAGRAM_ROUTE) }
                )
            }
            composable(TopLevelDestination.AUTOMATIONS.route) {
                AutomationsScreen(
                    onCreateAutomation = { navController.navigate(editorRoute()) },
                    onEditAutomation = { id -> navController.navigate(editorRoute(id)) },
                    onShowMessage = showMessage
                )
            }
            composable(TopLevelDestination.HISTORY.route) {
                HistoryScreen()
            }
            composable(TopLevelDestination.SETTINGS.route) {
                SettingsScreen(
                    onOpenInspector = { navController.navigate(INSPECTOR_ROUTE) }
                )
            }
            composable(INSPECTOR_ROUTE) {
                UiInspectorScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = EDITOR_ROUTE,
                arguments = listOf(
                    navArgument("automationId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) {
                AutomationEditorScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(INSTAGRAM_ROUTE) {
                val vm: InstagramAnalyzerViewModel =
                    viewModel(factory = InstagramAnalyzerViewModel.Factory)
                InstagramAnalyzerScreen(
                    onBack = { navController.popBackStack() },
                    onViewResults = { navController.navigate(INSTAGRAM_RESULTS_ROUTE) },
                    viewModel = vm
                )
            }
            composable(INSTAGRAM_RESULTS_ROUTE) { entry ->
                // Share the analyzer destination's ViewModel so the result,
                // search, and export all act on the same analysis.
                val parentEntry = remember(entry) {
                    navController.getBackStackEntry(INSTAGRAM_ROUTE)
                }
                val vm: InstagramAnalyzerViewModel =
                    viewModel(parentEntry, factory = InstagramAnalyzerViewModel.Factory)
                InstagramResultsScreen(
                    onBack = { navController.popBackStack() },
                    viewModel = vm
                )
            }
            }

            // Live progress + confirmation for a running UI automation,
            // shown above whatever screen is open.
            UiAutomationSessionHost(
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}
