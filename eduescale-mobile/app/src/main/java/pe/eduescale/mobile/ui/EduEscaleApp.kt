package pe.eduescale.mobile.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import pe.eduescale.mobile.activity.SchoolComment
import pe.eduescale.mobile.data.SchoolFilters
import pe.eduescale.mobile.data.SchoolRecord
import pe.eduescale.mobile.activity.SchoolReminder
import pe.eduescale.mobile.activity.SchoolActivityViewModel
import pe.eduescale.mobile.map.OfflineMapBounds
import pe.eduescale.mobile.reminders.ReminderPrompt
import pe.eduescale.mobile.reminders.ReminderViewModel
import pe.eduescale.mobile.routing.RoadRoutingViewModel
import pe.eduescale.mobile.ui.files.FilesScreen
import pe.eduescale.mobile.ui.files.SchoolFileImportViewModel
import pe.eduescale.mobile.ui.files.StorageSpaceViewModel
import pe.eduescale.mobile.ui.history.ActivityHistoryScreen
import pe.eduescale.mobile.ui.map.MapScreen
import pe.eduescale.mobile.ui.map.OfflineMapsViewModel
import pe.eduescale.mobile.ui.schools.SchoolsScreen
import pe.eduescale.mobile.ui.schools.SavedSearchViewModel
import pe.eduescale.mobile.ui.theme.ThemeViewModel
import pe.eduescale.mobile.ui.theme.EduEscaleTheme

@Composable
fun EduEscaleApp(
    viewModel: EduEscaleViewModel,
    activityViewModel: SchoolActivityViewModel,
    savedSearchViewModel: SavedSearchViewModel,
    offlineMapsViewModel: OfflineMapsViewModel,
    schoolFileImportViewModel: SchoolFileImportViewModel,
    storageSpaceViewModel: StorageSpaceViewModel,
    themeViewModel: ThemeViewModel,
    reminderViewModel: ReminderViewModel,
    roadRoutingViewModel: RoadRoutingViewModel,
    onReminderMessage: (String) -> Unit,
    onMapTouchEnabled: (Boolean) -> Unit,
    onThemeChanged: (Boolean) -> Unit,
    onMapTopOverlayBottomChanged: (Float) -> Unit,
    onSchoolDetailsSheetShown: (SchoolRecord, Float) -> Unit,
    onMapDownloadControlBoundsChanged: (Float, Float, Float, Float) -> Unit,
    onPickFile: () -> Unit,
    onImportSavedSearches: () -> Unit,
    onExportSavedSearches: () -> Unit,
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
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activityState by activityViewModel.state.collectAsStateWithLifecycle()
    val savedSearchState by savedSearchViewModel.state.collectAsStateWithLifecycle()
    val offlineMapsState by offlineMapsViewModel.state.collectAsStateWithLifecycle()
    val schoolFileImportState by schoolFileImportViewModel.state.collectAsStateWithLifecycle()
    val storageState by storageSpaceViewModel.state.collectAsStateWithLifecycle()
    val themeMode by themeViewModel.mode.collectAsStateWithLifecycle()
    val reminderPrompt by reminderViewModel.prompt.collectAsStateWithLifecycle()
    val roadState by roadRoutingViewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.visibleSchools, state.userLocation) {
        roadRoutingViewModel.updateInputs(state.visibleSchools, state.userLocation)
    }
    val systemDarkTheme = isSystemInDarkTheme()
    val darkTheme = themeMode.isDark(systemDarkTheme)
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
        Destination("schools", "Colegios", Icons.AutoMirrored.Filled.List),
        Destination("history", "Historial", Icons.Default.History),
        Destination("files", "Archivos", Icons.Default.FolderOpen),
        Destination("settings", "Ajustes", Icons.Default.Settings),
    )

    LaunchedEffect(darkTheme) {
        onThemeChanged(darkTheme)
    }
    EduEscaleTheme(darkTheme = darkTheme) {
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
                        roadState = roadState,
                        activityState = activityState,
                        offlineMapsState = offlineMapsState,
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
                        reminderDatePrompt = reminderPrompt as? ReminderPrompt.PickDate,
                        onReminderDateSelected = reminderViewModel::dateSelected,
                        onReminderDateCancelled = reminderViewModel::cancelPending,
                        onReminderMessage = onReminderMessage,
                        onDeleteReminder = onDeleteReminder,
                        onDownloadVisibleArea = onDownloadVisibleArea,
                        onToggleOfflineMapArea = onToggleOfflineMapArea,
                    )
                }
                composable("schools") {
                    SchoolsScreen(
                        state = state,
                        roadState = roadState,
                        onQueryChanged = viewModel::setQuery,
                        onApplyFilters = onApplyFilters,
                        onOpenFilters = viewModel::openFilters,
                        onFilterDraftChanged = viewModel::updateFilterDraft,
                        onDismissFilters = viewModel::dismissFilters,
                        savedSearches = savedSearchState.searches,
                        savedSearchMessage = savedSearchState.message,
                        savedSearchMessageIsError = savedSearchState.messageIsError,
                        onApplySavedSearch = { onApplyFilters(it) },
                        onSaveSearch = { name -> savedSearchViewModel.saveSearch(name, state.filters) },
                        onDeleteSavedSearch = savedSearchViewModel::deleteSearch,
                        onImportSavedSearches = onImportSavedSearches,
                        onExportSavedSearches = onExportSavedSearches,
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
                    LaunchedEffect(offlineMapsState.offlineMapAreas.sumOf(OfflineMapAreaUi::completedBytes)) {
                        storageSpaceViewModel.refresh()
                    }
                    FilesScreen(
                        roadState = roadState,
                        onDownloadRoadData = roadRoutingViewModel::download,
                        file = state.schoolFile,
                        fileName = state.fileName,
                        importing = schoolFileImportState.isImporting,
                        error = schoolFileImportState.error,
                        onPickFile = onPickFile,
                        onExportActivityBackup = onExportActivityBackup,
                        onImportActivityBackup = onImportActivityBackup,
                        offlineMapAreas = offlineMapsState.offlineMapAreas,
                        deviceAvailableBytes = storageState.availableBytes,
                        storageError = storageState.error,
                        offlineMapMessage = offlineMapsState.offlineMapMessage,
                        offlineMapMessageIsError = offlineMapsState.offlineMapMessageIsError,
                        onToggleOfflineMapArea = onToggleOfflineMapArea,
                        onDeleteOfflineMapArea = onDeleteOfflineMapArea,
                    )
                }
                composable("history") {
                    ActivityHistoryScreen(
                        activityState = activityState,
                        onQueryChanged = activityViewModel::setHistoryQuery,
                        onFilterChanged = activityViewModel::setHistoryFilter,
                        onRefresh = onRefreshActivityHistory,
                        onEditComment = onEditHistoryComment,
                        onEditReminder = onEditHistoryReminder,
                        onReminderMessage = onReminderMessage,
                    )
                }
                composable("settings") {
                    SettingsScreen(
                        themeMode = themeMode,
                        onThemeModeChanged = themeViewModel::setMode,
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
