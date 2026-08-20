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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.dhruw.autoflow.ui.history.ExecutionDetailScreen
import com.dhruw.autoflow.ui.history.HistoryScreen
import com.dhruw.autoflow.ui.home.HomeScreen
import com.dhruw.autoflow.ui.instagram.InstagramAnalyzerScreen
import com.dhruw.autoflow.ui.instagram.InstagramAnalyzerViewModel
import com.dhruw.autoflow.ui.instagram.InstagramResultsScreen
import com.dhruw.autoflow.ui.components.UiAutomationSessionHost
import com.dhruw.autoflow.ui.data.DataManagementScreen
import com.dhruw.autoflow.ui.diagnostics.DiagnosticsScreen
import com.dhruw.autoflow.ui.privacy.PrivacyCenterScreen
import com.dhruw.autoflow.ui.templates.TemplatesScreen
import com.dhruw.autoflow.ui.inspector.UiInspectorScreen
import com.dhruw.autoflow.ui.navigation.TopLevelDestination
import com.dhruw.autoflow.ui.onboarding.OnboardingScreen
import com.dhruw.autoflow.ui.onboarding.OnboardingState
import com.dhruw.autoflow.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

private const val EDITOR_ROUTE = "editor?automationId={automationId}"
private const val INSTAGRAM_ROUTE = "instagram"
private const val INSTAGRAM_RESULTS_ROUTE = "instagram/results"
private const val INSPECTOR_ROUTE = "inspector"
private const val EXECUTION_ROUTE = "execution/{executionId}"
private const val TEMPLATES_ROUTE = "templates"
private const val PRIVACY_ROUTE = "privacy"
private const val DATA_ROUTE = "data"
private const val DIAGNOSTICS_ROUTE = "diagnostics"

private fun editorRoute(automationId: String? = null): String =
    if (automationId == null) "editor" else "editor?automationId=$automationId"

private fun executionRoute(executionId: String): String = "execution/$executionId"

@Composable
fun AutoFlowApp() {
    val context = LocalContext.current
    val onboarding = remember { OnboardingState(context) }
    var onboardingDone by rememberSaveable { mutableStateOf(onboarding.isComplete()) }

    if (!onboardingDone) {
        OnboardingScreen(
            onDone = {
                onboarding.markComplete()
                onboardingDone = true
            }
        )
        return
    }

    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    // Full-screen destinations carry their own app bar and hide the bottom nav.
    val onEditorScreen = currentRoute?.startsWith("editor") == true ||
        currentRoute?.startsWith(INSTAGRAM_ROUTE) == true ||
        currentRoute?.startsWith("execution/") == true ||
        currentRoute == INSPECTOR_ROUTE ||
        currentRoute == TEMPLATES_ROUTE ||
        currentRoute == PRIVACY_ROUTE ||
        currentRoute == DATA_ROUTE ||
        currentRoute == DIAGNOSTICS_ROUTE

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
                    onShowMessage = showMessage,
                    onOpenPermissions = {
                        navController.navigate(TopLevelDestination.SETTINGS.route) {
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(TopLevelDestination.HISTORY.route) {
                HistoryScreen(
                    onOpenExecution = { id -> navController.navigate(executionRoute(id)) }
                )
            }
            composable(
                route = EXECUTION_ROUTE,
                arguments = listOf(navArgument("executionId") { type = NavType.StringType })
            ) { entry ->
                ExecutionDetailScreen(
                    executionId = entry.arguments?.getString("executionId").orEmpty(),
                    onBack = { navController.popBackStack() }
                )
            }
            composable(TopLevelDestination.SETTINGS.route) {
                SettingsScreen(
                    onOpenInspector = { navController.navigate(INSPECTOR_ROUTE) },
                    onOpenTemplates = { navController.navigate(TEMPLATES_ROUTE) },
                    onOpenPrivacy = { navController.navigate(PRIVACY_ROUTE) },
                    onOpenData = { navController.navigate(DATA_ROUTE) },
                    onOpenDiagnostics = { navController.navigate(DIAGNOSTICS_ROUTE) }
                )
            }
            composable(INSPECTOR_ROUTE) {
                UiInspectorScreen(onBack = { navController.popBackStack() })
            }
            composable(TEMPLATES_ROUTE) {
                TemplatesScreen(
                    onBack = { navController.popBackStack() },
                    onTemplateCreated = { id ->
                        navController.popBackStack()
                        navController.navigate(editorRoute(id))
                    }
                )
            }
            composable(PRIVACY_ROUTE) {
                PrivacyCenterScreen(onBack = { navController.popBackStack() })
            }
            composable(DATA_ROUTE) {
                DataManagementScreen(
                    onBack = { navController.popBackStack() },
                    onShowMessage = showMessage
                )
            }
            composable(DIAGNOSTICS_ROUTE) {
                DiagnosticsScreen(onBack = { navController.popBackStack() })
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
