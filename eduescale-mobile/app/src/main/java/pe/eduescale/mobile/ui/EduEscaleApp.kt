package pe.eduescale.mobile.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import pe.eduescale.mobile.data.SchoolComment
import pe.eduescale.mobile.data.SchoolFilters
import pe.eduescale.mobile.data.SchoolRecord
import pe.eduescale.mobile.data.SchoolReminder
import pe.eduescale.mobile.ui.files.FilesScreen
import pe.eduescale.mobile.ui.history.ActivityHistoryScreen
import pe.eduescale.mobile.ui.map.MapScreen
import pe.eduescale.mobile.ui.schools.SchoolsScreen
import pe.eduescale.mobile.ui.theme.EduEscaleTheme

@Composable
fun EduEscaleApp(
    viewModel: EduEscaleViewModel,
    onMapTouchEnabled: (Boolean) -> Unit,
    onMapTopOverlayBottomChanged: (Float) -> Unit,
    onSchoolDetailsSheetShown: (SchoolRecord, Float) -> Unit,
    onMapDownloadControlBoundsChanged: (Float, Float, Float, Float) -> Unit,
    onPickFile: () -> Unit,
    onLocate: () -> Unit,
    onApplyFilters: (SchoolFilters) -> Unit,
    onSchoolSelected: (SchoolRecord) -> Unit,
    onDirections: (SchoolRecord) -> Unit,
    onShare: (SchoolRecord) -> Unit,
    onClearSelection: () -> Unit,
    onAddComment: (SchoolRecord, String) -> Unit,
    onDeleteComment: (SchoolRecord, String) -> Unit,
    onEditComment: (SchoolRecord, String, String) -> Unit,
    onSearchComments: (SchoolRecord, String) -> Unit,
    onAddReminder: (SchoolRecord, String) -> Unit,
    onDeleteReminder: (SchoolRecord, String) -> Unit,
    onExportActivityBackup: () -> Unit,
    onImportActivityBackup: () -> Unit,
    onDownloadVisibleArea: (OfflineMapBounds, Int, Int) -> Unit,
    onToggleOfflineMapArea: (Long, Boolean) -> Unit,
    onDeleteOfflineMapArea: (Long) -> Unit,
    onRefreshActivityHistory: () -> Unit,
    onEditHistoryComment: (SchoolComment, String) -> Unit,
    onEditHistoryReminder: (SchoolReminder, String, Long) -> Unit,
    onPickReminderDateTime: (Long, (Long) -> Unit) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route ?: "map"
    LaunchedEffect(currentRoute, state.selectedSchool) {
        onMapTouchEnabled(currentRoute == "map" && state.selectedSchool == null)
    }
    LaunchedEffect(currentRoute) {
        if (currentRoute == "history") {
            onRefreshActivityHistory()
        }
    }
    val destinations = listOf(
        Destination("map", "Mapa", Icons.Default.Map),
        Destination("schools", "Colegios", Icons.Default.List),
        Destination("history", "Historial", Icons.Default.History),
        Destination("files", "Archivos", Icons.Default.FolderOpen),
    )

    EduEscaleTheme {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                NavigationBar(
                    windowInsets = WindowInsets.navigationBars,
                    containerColor = MaterialTheme.colorScheme.surface,
                ) {
                    destinations.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
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
                                Icon(destination.icon, contentDescription = null)
                            },
                            label = { Text(destination.label) },
                        )
                    }
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = "map",
                modifier = Modifier.padding(padding),
            ) {
                composable("map") {
                    MapScreen(
                        state = state,
                        onMapTopOverlayBottomChanged = onMapTopOverlayBottomChanged,
                        onSchoolDetailsSheetShown = onSchoolDetailsSheetShown,
                        onMapDownloadControlBoundsChanged =
                            onMapDownloadControlBoundsChanged,
                        onGoToFiles = {
                            navController.navigate("files") {
                                launchSingleTop = true
                            }
                        },
                        onLocate = onLocate,
                        onDirections = onDirections,
                        onShare = onShare,
                        onClearSelection = onClearSelection,
                        onAddComment = onAddComment,
                        onDeleteComment = onDeleteComment,
                        onEditComment = onEditComment,
                        onSearchComments = onSearchComments,
                        onAddReminder = onAddReminder,
                        onDeleteReminder = onDeleteReminder,
                        onDownloadVisibleArea = onDownloadVisibleArea,
                        onToggleOfflineMapArea = onToggleOfflineMapArea,
                    )
                }
                composable("schools") {
                    SchoolsScreen(
                        state = state,
                        onQueryChanged = viewModel::setQuery,
                        onApplyFilters = onApplyFilters,
                        onSchoolSelected = { school ->
                            onSchoolSelected(school)
                            navController.navigate("map") {
                                popUpTo("map") { inclusive = false }
                                launchSingleTop = true
                            }
                        }
                    )
                }
                composable("files") {
                    FilesScreen(
                        file = state.schoolFile,
                        fileName = state.fileName,
                        importing = state.isImporting,
                        error = state.importError,
                        onPickFile = onPickFile,
                        onExportActivityBackup = onExportActivityBackup,
                        onImportActivityBackup = onImportActivityBackup,
                        offlineMapAreas = state.offlineMapAreas,
                        offlineMapMessage = state.offlineMapMessage,
                        offlineMapMessageIsError = state.offlineMapMessageIsError,
                        onToggleOfflineMapArea = onToggleOfflineMapArea,
                        onDeleteOfflineMapArea = onDeleteOfflineMapArea,
                    )
                }
                composable("history") {
                    ActivityHistoryScreen(
                        state = state,
                        onRefresh = onRefreshActivityHistory,
                        onEditComment = onEditHistoryComment,
                        onEditReminder = onEditHistoryReminder,
                        onPickReminderDateTime = onPickReminderDateTime,
                    )
                }
            }
        }
    }
}

internal data class Destination(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)
