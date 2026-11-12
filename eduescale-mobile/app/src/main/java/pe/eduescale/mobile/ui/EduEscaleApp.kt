package pe.eduescale.mobile.ui

import android.content.Context
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import android.os.StatFs
import androidx.compose.ui.platform.LocalContext
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
import pe.eduescale.mobile.data.SavedSearchJson
import pe.eduescale.mobile.data.SavedSearch
import pe.eduescale.mobile.ui.files.FilesScreen
import pe.eduescale.mobile.ui.history.ActivityHistoryScreen
import pe.eduescale.mobile.ui.map.MapScreen
import pe.eduescale.mobile.ui.schools.SchoolsScreen
import pe.eduescale.mobile.ui.theme.DARK_THEME_PREFERENCE_KEY
import pe.eduescale.mobile.ui.theme.THEME_MODE_PREFERENCE_KEY
import pe.eduescale.mobile.ui.theme.THEME_PREFERENCES_NAME
import pe.eduescale.mobile.ui.theme.ThemeMode
import pe.eduescale.mobile.ui.theme.EduEscaleTheme

@Composable
fun EduEscaleApp(
    viewModel: EduEscaleViewModel,
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
    onPickReminderDateTime: (Long, (Long) -> Unit) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val themePreferences = remember(context) {
        context.getSharedPreferences(THEME_PREFERENCES_NAME, Context.MODE_PRIVATE)
    }
    val systemDarkTheme = isSystemInDarkTheme()
    var themeMode by remember(themePreferences) {
        mutableStateOf(
            themePreferences.getString(THEME_MODE_PREFERENCE_KEY, null)?.let(ThemeMode::fromPreference)
                ?: if (themePreferences.contains(DARK_THEME_PREFERENCE_KEY)) {
                    if (themePreferences.getBoolean(DARK_THEME_PREFERENCE_KEY, false)) {
                        ThemeMode.DARK
                    } else {
                        ThemeMode.LIGHT
                    }
                } else {
                    ThemeMode.SYSTEM
                },
        )
    }
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
    LaunchedEffect(themeMode) {
        themePreferences.edit()
            .putString(THEME_MODE_PREFERENCE_KEY, themeMode.preferenceValue)
            .remove(DARK_THEME_PREFERENCE_KEY)
            .apply()
    }
    LaunchedEffect(state.savedSearches) {
        themePreferences.edit()
            .putString("saved_searches", SavedSearchJson.encode(state.savedSearches))
            .apply()
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
                        savedSearches = state.savedSearches,
                        savedSearchMessage = state.savedSearchMessage,
                        savedSearchMessageIsError = state.savedSearchMessageIsError,
                        onApplySavedSearch = { onApplyFilters(it) },
                        onSaveSearch = { name ->
                            try {
                                val search = SavedSearch.from(name, state.filters)
                                val updated = state.savedSearches
                                    .filterNot { it.name.equals(search.name, ignoreCase = true) } + search
                                SavedSearchJson.encode(updated)
                                viewModel.setSavedSearches(updated)
                                viewModel.setSavedSearchMessage("Búsqueda guardada.")
                            } catch (error: IllegalArgumentException) {
                                viewModel.setSavedSearchMessage(
                                    error.message ?: "No se pudo guardar la búsqueda.",
                                    isError = true,
                                )
                            }
                        },
                        onDeleteSavedSearch = { search ->
                            viewModel.setSavedSearches(state.savedSearches - search)
                        },
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
                    FilesScreen(
                        file = state.schoolFile,
                        fileName = state.fileName,
                        importing = state.isImporting,
                        error = state.importError,
                        onPickFile = onPickFile,
                        onExportActivityBackup = onExportActivityBackup,
                        onImportActivityBackup = onImportActivityBackup,
                        offlineMapAreas = state.offlineMapAreas,
                        deviceAvailableBytes = StatFs(context.filesDir.path).availableBytes,
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
                composable("settings") {
                    SettingsScreen(
                        themeMode = themeMode,
                        onThemeModeChanged = { themeMode = it },
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
