package pe.eduescale.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import pe.eduescale.mobile.data.SchoolFile
import pe.eduescale.mobile.data.SchoolComment
import pe.eduescale.mobile.data.SchoolCoordinates
import pe.eduescale.mobile.data.SchoolFilters
import pe.eduescale.mobile.data.SchoolReminder
import pe.eduescale.mobile.data.SchoolRecord
import pe.eduescale.mobile.data.distanceInMeters
import pe.eduescale.mobile.data.sortSchoolsByDistance
import pe.eduescale.mobile.data.OfflineTileEstimator
import pe.eduescale.mobile.data.activityKey
import pe.eduescale.mobile.ui.EduEscaleUiState
import pe.eduescale.mobile.ui.EduEscaleViewModel
import pe.eduescale.mobile.ui.OfflineMapAreaUi
import pe.eduescale.mobile.ui.OfflineMapBounds
import java.util.Locale
import kotlin.math.roundToInt

private val EduGreen = Color(0xFF176B50)
private val EduOrange = Color(0xFFD97706)
private val EduGray = Color(0xFF7B8794)

@Composable
fun EduEscaleTheme(content: @Composable () -> Unit) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val colors = if (dark) {
        darkColorScheme(
            primary = Color(0xFF70D5AB),
            secondary = Color(0xFFFFB86B),
            surface = Color(0xFF151A18),
            background = Color(0xFF101412),
        )
    } else {
        lightColorScheme(
            primary = EduGreen,
            secondary = EduOrange,
            surface = Color(0xFFFFFEFC),
            background = Color(0xFFF4F7F4),
        )
    }
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
fun EduEscaleApp(
    viewModel: EduEscaleViewModel,
    onMapTouchEnabled: (Boolean) -> Unit,
    onMapTopOverlayBottomChanged: (Float) -> Unit,
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

private data class Destination(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

@Composable
private fun MapScreen(
    state: EduEscaleUiState,
    onMapTopOverlayBottomChanged: (Float) -> Unit,
    onMapDownloadControlBoundsChanged: (Float, Float, Float, Float) -> Unit,
    onGoToFiles: () -> Unit,
    onLocate: () -> Unit,
    onDirections: (SchoolRecord) -> Unit,
    onShare: (SchoolRecord) -> Unit,
    onClearSelection: () -> Unit,
    onAddComment: (SchoolRecord, String) -> Unit,
    onDeleteComment: (SchoolRecord, String) -> Unit,
    onEditComment: (SchoolRecord, String, String) -> Unit,
    onSearchComments: (SchoolRecord, String) -> Unit,
    onAddReminder: (SchoolRecord, String) -> Unit,
    onDeleteReminder: (SchoolRecord, String) -> Unit,
    onDownloadVisibleArea: (OfflineMapBounds, Int, Int) -> Unit,
    onToggleOfflineMapArea: (Long, Boolean) -> Unit,
) {
    var legendExpanded by rememberSaveable { mutableStateOf(false) }
    var showOfflineMapDialog by rememberSaveable { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .onGloballyPositioned { coordinates ->
                    onMapTopOverlayBottomChanged(
                        coordinates.positionInRoot().y + coordinates.size.height,
                    )
                },
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BrandHeader()
            if (state.schoolFile == null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onGoToFiles),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null)
                        Text("Importa un archivo .eduescale en Archivos para comenzar.")
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 2.dp,
                ) {
                    Text(
                        "${state.visibleSchools.count(SchoolRecord::hasCoordinates)} colegios en el mapa",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            Card(
                modifier = Modifier
                    .align(Alignment.Start)
                    .clickable { legendExpanded = !legendExpanded },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Leyenda", style = MaterialTheme.typography.labelLarge)
                        Icon(
                            if (legendExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (legendExpanded) "Contraer leyenda" else "Expandir leyenda",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    if (legendExpanded) {
                        LegendRow(EduGreen, "Pública")
                        LegendRow(EduOrange, "Privada")
                        LegendRow(EduGray, "Gestión desconocida")
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        Text(
                            "Tamaño: inicial · primaria · secundaria",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = 12.dp)
                .onGloballyPositioned { coordinates ->
                    val position = coordinates.positionInRoot()
                    onMapDownloadControlBoundsChanged(
                        position.x,
                        position.y,
                        position.x + coordinates.size.width,
                        position.y + coordinates.size.height,
                    )
                },
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val activeDownload = state.offlineMapAreas.firstOrNull(OfflineMapAreaUi::isDownloading)
            if (state.isCreatingOfflineMapArea) {
                Card(shape = RoundedCornerShape(14.dp)) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("Iniciando descarga del área…", fontWeight = FontWeight.SemiBold)
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            } else if (activeDownload != null) {
                val progress = if (activeDownload.estimatedTiles > 0) {
                    (activeDownload.completedTiles.toFloat() /
                        activeDownload.estimatedTiles).coerceIn(0f, 1f)
                } else {
                    0f
                }
                Card(shape = RoundedCornerShape(14.dp)) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("Descargando ${activeDownload.name}", fontWeight = FontWeight.SemiBold)
                        Text(
                            "${activeDownload.completedTiles} / " +
                                "${activeDownload.estimatedTiles} teselas · " +
                                formatMapBytes(activeDownload.completedBytes),
                            style = MaterialTheme.typography.labelSmall,
                        )
                        LinearProgressIndicator(
                            progress = progress,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        TextButton(
                            onClick = {
                                onToggleOfflineMapArea(activeDownload.id, false)
                            },
                            modifier = Modifier.align(Alignment.End),
                        ) {
                            Text("Pausar")
                        }
                    }
                }
            }
            Button(
                onClick = { showOfflineMapDialog = true },
                enabled = state.visibleMapBounds != null &&
                    !state.isCreatingOfflineMapArea &&
                    state.offlineMapAreas.none(OfflineMapAreaUi::isDownloading),
            ) {
                Icon(Icons.Default.FileDownload, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(
                    if (state.isCreatingOfflineMapArea) {
                        "Iniciando descarga…"
                    } else if (activeDownload != null) {
                        "Descarga en curso"
                    } else {
                        "Descargar área visible"
                    },
                )
            }
            Text(
                "OpenFreeMap © OpenMapTiles · Data from OpenStreetMap",
                modifier = Modifier.padding(start = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!state.isCreatingOfflineMapArea && activeDownload == null) {
                state.offlineMapMessage?.let { message ->
                Text(
                    message,
                    modifier = Modifier.padding(start = 4.dp, end = 8.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (state.offlineMapMessageIsError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                }
            }
        }

        if (state.locationServicesEnabled == false) {
            ExtendedFloatingActionButton(
                onClick = onLocate,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                icon = { Icon(Icons.Default.MyLocation, contentDescription = null) },
                text = { Text("Activar ubicación") },
            )
        } else {
            FloatingActionButton(
                onClick = onLocate,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "Centrar mi ubicación")
            }
        }
    }

    if (showOfflineMapDialog) {
        state.visibleMapBounds?.let { bounds ->
            OfflineMapDownloadDialog(
                bounds = bounds,
                onDismiss = { showOfflineMapDialog = false },
                onDownload = { minZoom, maxZoom ->
                    onDownloadVisibleArea(bounds, minZoom, maxZoom)
                    showOfflineMapDialog = false
                },
            )
        }
    }

    SchoolDetailsSheet(
        school = state.selectedSchool,
        onDismiss = onClearSelection,
        onDirections = onDirections,
        onShare = onShare,
        comments = state.schoolComments,
        reminders = state.schoolReminders,
        commentSearchQuery = state.commentSearchQuery,
        onAddComment = { text ->
            state.selectedSchool?.let { school -> onAddComment(school, text) }
        },
        onDeleteComment = { commentId ->
            state.selectedSchool?.let { school -> onDeleteComment(school, commentId) }
        },
        onEditComment = { commentId, text ->
            state.selectedSchool?.let { school ->
                onEditComment(school, commentId, text)
            }
        },
        onSearchComments = { query ->
            state.selectedSchool?.let { school -> onSearchComments(school, query) }
        },
        onAddReminder = { message ->
            state.selectedSchool?.let { school -> onAddReminder(school, message) }
        },
        onDeleteReminder = { reminderId ->
            state.selectedSchool?.let { school -> onDeleteReminder(school, reminderId) }
        },
    )
}

@Composable
private fun OfflineMapDownloadDialog(
    bounds: OfflineMapBounds,
    onDismiss: () -> Unit,
    onDownload: (Int, Int) -> Unit,
) {
    val initialZoom = bounds.cameraZoom.toInt().coerceIn(0, OfflineTileEstimator.MAX_ZOOM)
    var zoomRange by remember(bounds) {
        mutableStateOf(
            maxOf(initialZoom - 2, 0).toFloat()..
                minOf(initialZoom + 2, OfflineTileEstimator.MAX_ZOOM).toFloat(),
        )
    }
    val minZoom = zoomRange.start.roundToInt()
    val maxZoom = zoomRange.endInclusive.roundToInt()
    val estimatedTiles = OfflineTileEstimator.estimate(
        west = bounds.west,
        south = bounds.south,
        east = bounds.east,
        north = bounds.north,
        minZoom = minZoom,
        maxZoom = maxZoom,
    )
    val withinTileLimit = estimatedTiles <= OfflineTileEstimator.MAX_TILE_COUNT

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Descargar área visible") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Elige los niveles de zoom que estarán disponibles sin conexión.")
                Text("Zoom $minZoom–$maxZoom")
                RangeSlider(
                    value = zoomRange,
                    onValueChange = { zoomRange = it },
                    valueRange = 0f..OfflineTileEstimator.MAX_ZOOM.toFloat(),
                    steps = OfflineTileEstimator.MAX_ZOOM - 1,
                )
                Text(
                    "$estimatedTiles / ${OfflineTileEstimator.MAX_TILE_COUNT} teselas estimadas",
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Cada área debe respetar el límite de OpenFreeMap: 3.000 teselas o 50 MB.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!withinTileLimit) {
                    Text(
                        "Reduce el área o el rango de zoom para continuar.",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onDownload(minZoom, maxZoom) },
                enabled = withinTileLimit,
            ) {
                Text("Descargar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
    )
}

@Composable
private fun SchoolsScreen(
    state: EduEscaleUiState,
    onQueryChanged: (String) -> Unit,
    onApplyFilters: (SchoolFilters) -> Unit,
    onSchoolSelected: (SchoolRecord) -> Unit,
) {
    var filtersOpen by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp),
    ) {
        BrandHeader(modifier = Modifier.padding(top = 12.dp, bottom = 10.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChanged,
                modifier = Modifier.weight(1f),
                enabled = state.schoolFile != null,
                singleLine = true,
                placeholder = { Text("Buscar por nombre") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = RoundedCornerShape(16.dp),
            )
            FilterButton(
                count = activeFilterCount(state.filters),
                enabled = state.schoolFile != null,
                onClick = { filtersOpen = true },
            )
        }
        ActiveFilterChips(state.filters, Modifier.padding(top = 4.dp))
        val schoolsWithoutCoordinates = state.visibleSchools.count { !it.hasCoordinates }
        Text(
            if (state.schoolFile == null) {
                "Importa un archivo desde la sección Archivos."
            } else {
                buildString {
                    append("${state.visibleSchools.size} instituciones")
                    if (schoolsWithoutCoordinates > 0) {
                        append(" · $schoolsWithoutCoordinates sin ubicación")
                    }
                    append(
                        if (state.userLocation == null) {
                            " · activa la ubicación en Mapa"
                        } else {
                            " · ordenadas por cercanía"
                        },
                    )
                }
            },
            modifier = Modifier.padding(vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.schoolFile == null) {
            EmptyState("Aún no hay colegios cargados", "Abre Archivos para importar un archivo .eduescale.")
        } else if (state.visibleSchools.isEmpty()) {
            EmptyState("No hay resultados", "Prueba con otro nombre o cambia los filtros.")
        } else {
            val schoolsByDistance = remember(state.visibleSchools, state.userLocation) {
                sortSchoolsByDistance(state.visibleSchools, state.userLocation)
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(schoolsByDistance, key = SchoolRecord::id) { school ->
                    SchoolListCard(
                        school = school,
                        userLocation = state.userLocation,
                        onClick = { onSchoolSelected(school) },
                    )
                }
            }
        }
    }
    if (filtersOpen && state.schoolFile != null) {
        SchoolFiltersDialog(
            schools = state.schoolFile.schools + state.schoolFile.schoolsWithoutCoordinates,
            filters = state.filters,
            onDismiss = { filtersOpen = false },
            onApply = {
                onApplyFilters(it)
                filtersOpen = false
            },
        )
    }
}

private enum class ActivityHistoryFilter {
    ALL,
    COMMENTS,
    PENDING_REMINDERS,
    NON_PENDING_REMINDERS,
}

private data class ActivityHistoryEntry(
    val id: String,
    val type: String,
    val status: String,
    val schoolName: String,
    val message: String,
    val date: String,
    val timestamp: Long,
    val isReminder: Boolean,
    val isPending: Boolean,
    val comment: SchoolComment? = null,
    val reminder: SchoolReminder? = null,
)

@Composable
private fun ActivityHistoryScreen(
    state: EduEscaleUiState,
    onRefresh: () -> Unit,
    onEditComment: (SchoolComment, String) -> Unit,
    onEditReminder: (SchoolReminder, String, Long) -> Unit,
    onPickReminderDateTime: (Long, (Long) -> Unit) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable {
        mutableStateOf(ActivityHistoryFilter.ALL)
    }
    var commentBeingEdited by remember { mutableStateOf<SchoolComment?>(null) }
    var commentEditText by remember { mutableStateOf("") }
    var reminderBeingEdited by remember { mutableStateOf<SchoolReminder?>(null) }
    var reminderEditText by remember { mutableStateOf("") }
    var reminderEditScheduledAt by remember { mutableStateOf(0L) }
    val schoolNames = remember(state.schoolFile) {
        state.schoolFile?.schools
            ?.associate { school ->
                school.activityKey() to (
                    school.nombre?.takeIf(String::isNotBlank) ?: "Colegio sin nombre"
                    )
            }
            .orEmpty()
    }
    val entries = remember(
        state.activityHistoryComments,
        state.activityHistoryReminders,
        schoolNames,
        query,
        selectedFilter,
    ) {
        val now = System.currentTimeMillis()
        buildList {
            if (
                selectedFilter == ActivityHistoryFilter.ALL ||
                selectedFilter == ActivityHistoryFilter.COMMENTS
            ) {
                state.activityHistoryComments.forEach { comment ->
                    val updatedAt = comment.updatedAtMillis
                    add(
                        ActivityHistoryEntry(
                            id = "comment-${comment.id}",
                            type = "Comentario",
                            status = if (updatedAt == null) "Guardado" else "Editado",
                            schoolName = schoolNames[comment.schoolKey]
                                ?: "Colegio no disponible",
                            message = comment.text,
                            date = buildString {
                                append("Creado: ${formatActivityDate(comment.createdAtMillis)}")
                                updatedAt?.let {
                                    append(" · Editado: ${formatActivityDate(it)}")
                                }
                            },
                            timestamp = updatedAt ?: comment.createdAtMillis,
                            isReminder = false,
                            isPending = false,
                            comment = comment,
                        ),
                    )
                }
            }
            if (
                selectedFilter != ActivityHistoryFilter.COMMENTS
            ) {
                state.activityHistoryReminders.forEach { reminder ->
                    val isPending = isReminderPending(reminder, now)
                    if (
                        selectedFilter == ActivityHistoryFilter.PENDING_REMINDERS &&
                        !isPending
                    ) {
                        return@forEach
                    }
                    if (
                        selectedFilter == ActivityHistoryFilter.NON_PENDING_REMINDERS &&
                        isPending
                    ) {
                        return@forEach
                    }
                    add(
                        ActivityHistoryEntry(
                            id = "reminder-${reminder.id}",
                            type = "Alarma",
                            status = reminderStatusLabel(reminder, now),
                            schoolName = reminder.schoolName,
                            message = reminder.message,
                            date = formatActivityDate(reminder.scheduledAtMillis),
                            timestamp = reminder.scheduledAtMillis,
                            isReminder = true,
                            isPending = isPending,
                            reminder = reminder,
                        ),
                    )
                }
            }
        }.filter { entry ->
            query.isBlank() ||
                entry.schoolName.contains(query.trim(), ignoreCase = true) ||
                entry.message.contains(query.trim(), ignoreCase = true)
        }.sortedByDescending(ActivityHistoryEntry::timestamp)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp),
    ) {
        BrandHeader(modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
        Text("Historial", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            singleLine = true,
            placeholder = { Text("Buscar colegio, comentario o alarma") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            shape = RoundedCornerShape(16.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ActivityHistoryFilter.entries.forEach { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { selectedFilter = filter },
                    label = {
                        Text(
                            when (filter) {
                                ActivityHistoryFilter.ALL -> "Todo"
                                ActivityHistoryFilter.COMMENTS -> "Comentarios"
                                ActivityHistoryFilter.PENDING_REMINDERS -> "Alarmas pendientes"
                                ActivityHistoryFilter.NON_PENDING_REMINDERS -> "Alarmas no pendientes"
                            },
                        )
                    },
                )
            }
        }
        if (state.isLoadingActivityHistory) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        state.activityHistoryError?.let { error ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(error, color = MaterialTheme.colorScheme.onErrorContainer)
                    TextButton(onClick = onRefresh) { Text("Reintentar") }
                }
            }
        }
        if (entries.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(entries, key = ActivityHistoryEntry::id) { entry ->
                    ActivityHistoryCard(entry) {
                        entry.comment?.let { comment ->
                            commentBeingEdited = comment
                            commentEditText = comment.text
                        }
                        entry.reminder?.let { reminder ->
                            reminderBeingEdited = reminder
                            reminderEditText = reminder.message
                            reminderEditScheduledAt = reminder.scheduledAtMillis
                        }
                    }
                }
            }
        } else if (state.isLoadingActivityHistory) {
            EmptyState("Cargando historial", "Consultando comentarios y alarmas guardados.")
        } else if (state.activityHistoryError == null) {
            EmptyState(
                title = if (query.isBlank()) "Aún no hay actividad" else "Sin resultados",
                message = if (query.isBlank()) {
                    "Los comentarios y las alarmas de tus colegios aparecerán aquí."
                } else {
                    "Prueba con otro texto o cambia el filtro."
                },
            )
        }
    }
    commentBeingEdited?.let { comment ->
        AlertDialog(
            onDismissRequest = { commentBeingEdited = null },
            title = { Text("Editar comentario") },
            text = {
                OutlinedTextField(
                    value = commentEditText,
                    onValueChange = { commentEditText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Comentario") },
                    minLines = 3,
                    maxLines = 6,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = commentEditText.isNotBlank(),
                    onClick = {
                        onEditComment(comment, commentEditText)
                        commentBeingEdited = null
                    },
                ) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { commentBeingEdited = null }) { Text("Cancelar") }
            },
        )
    }
    reminderBeingEdited?.let { reminder ->
        AlertDialog(
            onDismissRequest = { reminderBeingEdited = null },
            title = { Text("Editar alarma") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = reminderEditText,
                        onValueChange = { reminderEditText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Recordatorio") },
                        minLines = 2,
                        maxLines = 4,
                    )
                    OutlinedButton(
                        onClick = {
                            onPickReminderDateTime(reminderEditScheduledAt) { selectedAt ->
                                reminderEditScheduledAt = selectedAt
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Fecha y hora: ${formatActivityDate(reminderEditScheduledAt)}")
                    }
                    Text(
                        "Al guardar, la alarma quedará pendiente con la nueva fecha.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = reminderEditText.isNotBlank() &&
                        reminderEditScheduledAt > System.currentTimeMillis(),
                    onClick = {
                        onEditReminder(reminder, reminderEditText, reminderEditScheduledAt)
                        reminderBeingEdited = null
                    },
                ) { Text("Guardar y reactivar") }
            },
            dismissButton = {
                TextButton(onClick = { reminderBeingEdited = null }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun ActivityHistoryCard(entry: ActivityHistoryEntry, onEdit: () -> Unit) {
    Card(shape = RoundedCornerShape(18.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                if (entry.isReminder) Icons.Default.Alarm else Icons.Default.Comment,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 2.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        entry.schoolName,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (entry.isPending) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    ) {
                        Text(
                            entry.status,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                Text(
                    "${entry.type}: ${entry.message}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    entry.date,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Editar ${entry.type.lowercase()}",
                )
            }
        }
    }
}

@Composable
private fun FilesScreen(
    file: SchoolFile?,
    fileName: String?,
    importing: Boolean,
    error: String?,
    onPickFile: () -> Unit,
    onExportActivityBackup: () -> Unit,
    onImportActivityBackup: () -> Unit,
    offlineMapAreas: List<OfflineMapAreaUi>,
    offlineMapMessage: String?,
    offlineMapMessageIsError: Boolean,
    onToggleOfflineMapArea: (Long, Boolean) -> Unit,
    onDeleteOfflineMapArea: (Long) -> Unit,
) {
    var pendingOfflineAreaDeletion by remember { mutableStateOf<OfflineMapAreaUi?>(null) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BrandHeader(modifier = Modifier.padding(top = 4.dp))
        Text("Archivo de datos", style = MaterialTheme.typography.headlineSmall)
        if (file == null) {
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        Icons.Default.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(42.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text("Importa tu archivo .eduescale", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Se admiten los archivos EduScale versionados y ZIP de versiones anteriores.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Archivo actual", style = MaterialTheme.typography.labelLarge)
                    Text(
                        fileName ?: "Archivo EduScale",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    StatisticLine(
                        "Registros en el archivo (antes de agrupar)",
                        file.totalCount.toString(),
                    )
                    StatisticLine(
                        "Locales agrupados con ubicación",
                        file.schools.size.toString(),
                    )
                    StatisticLine(
                        "Registros sin coordenadas utilizables",
                        file.withoutCoordinates.toString(),
                    )
                    Text(
                        if (importing) "Importando archivo…" else "Archivo listo para explorar",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
        if (error != null) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
            ) {
                Text(
                    error,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
        Button(
            onClick = onPickFile,
            enabled = !importing,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        ) {
            Icon(Icons.Default.FolderOpen, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (file == null) "Seleccionar archivo" else "Reemplazar archivo")
        }
        HorizontalDivider()
        Text("Mapas sin conexión", style = MaterialTheme.typography.titleMedium)
        Text(
            "Descarga el área visible desde Mapa. Los mapas guardados se pueden reanudar o eliminar aquí.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        offlineMapMessage?.let { message ->
            Text(
                message,
                color = if (offlineMapMessageIsError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        if (offlineMapAreas.isEmpty()) {
            Text(
                "Aún no hay áreas descargadas.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            offlineMapAreas.forEach { area ->
                OfflineMapAreaCard(
                    area = area,
                    onToggle = { onToggleOfflineMapArea(area.id, !area.isDownloading) },
                    onDelete = { pendingOfflineAreaDeletion = area },
                )
            }
        }
        HorizontalDivider()
        Text("Copia local", style = MaterialTheme.typography.titleMedium)
        Text(
            "Guarda comentarios y recordatorios en un archivo para conservarlos al cambiar de dispositivo.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onExportActivityBackup,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        ) {
            Icon(Icons.Default.Share, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Exportar copia")
        }
        OutlinedButton(
            onClick = onImportActivityBackup,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        ) {
            Icon(Icons.Default.FolderOpen, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Restaurar copia")
        }
        Text(
            "Restaurar combina la copia con los datos actuales y omite registros duplicados.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    pendingOfflineAreaDeletion?.let { area ->
        AlertDialog(
            onDismissRequest = { pendingOfflineAreaDeletion = null },
            title = { Text("Eliminar mapa sin conexión") },
            text = { Text("¿Quieres eliminar “${area.name}” del dispositivo?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteOfflineMapArea(area.id)
                        pendingOfflineAreaDeletion = null
                    },
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingOfflineAreaDeletion = null }) {
                    Text("Cancelar")
                }
            },
        )
    }
}

@Composable
private fun OfflineMapAreaCard(
    area: OfflineMapAreaUi,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(shape = RoundedCornerShape(16.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(area.name, fontWeight = FontWeight.SemiBold)
            Text(
                "Zoom ${area.minZoom}–${area.maxZoom} · ${area.completedTiles} / " +
                    "${area.estimatedTiles} teselas · ${formatMapBytes(area.completedBytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(
                progress = if (area.estimatedTiles > 0) {
                    (area.completedTiles.toFloat() / area.estimatedTiles).coerceIn(0f, 1f)
                } else {
                    0f
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                if (!area.isComplete) {
                    TextButton(onClick = onToggle) {
                        Text(if (area.isDownloading) "Pausar" else "Reanudar")
                    }
                } else {
                    Text(
                        "Disponible sin conexión",
                        modifier = Modifier
                            .weight(1f)
                            .align(Alignment.CenterVertically),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                TextButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Eliminar")
                }
            }
        }
    }
}

private fun formatMapBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${bytes / (1024 * 1024)} MB"
}

@Composable
private fun BrandHeader(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            modifier = Modifier.size(42.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.School,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Column {
            Text("EduEscale", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Instituciones educativas del Perú",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FilterButton(count: Int, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.height(56.dp),
    ) {
        Icon(Icons.Default.FilterList, contentDescription = null)
        if (count > 0) {
            Spacer(Modifier.width(4.dp))
            Text(count.toString())
        }
    }
}

@Composable
private fun ActiveFilterChips(filters: SchoolFilters, modifier: Modifier = Modifier) {
    val labels = buildList {
        filters.departamento?.let { add(it) }
        filters.provincia?.let { add(it) }
        filters.distrito?.let { add(it) }
        filters.gestiones.forEach { add(managementLabel(it)) }
        filters.niveles.forEach { add(it.replaceFirstChar(Char::uppercase)) }
    }
    if (labels.isNotEmpty()) {
        androidx.compose.foundation.lazy.LazyRow(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(labels) { label ->
                AssistChip(onClick = {}, label = { Text(label) })
            }
        }
    }
}

@Composable
private fun LegendRow(color: Color, label: String) {
    Row(
        modifier = Modifier.padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun SchoolListCard(
    school: SchoolRecord,
    userLocation: SchoolCoordinates?,
    onClick: () -> Unit,
) {
    val distance = userLocation?.let { distanceInMeters(school, it) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                school.nombre?.takeIf(String::isNotBlank) ?: "Institución sin nombre",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(school.distrito, school.provincia, school.departamento)
                    .filter(String::isNotBlank)
                    .joinToString(" · ")
                    .ifBlank { "Ubicación no informada" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when {
                !school.hasCoordinates -> Text(
                    "Sin ubicación · toca para ver detalles",
                    style = MaterialTheme.typography.labelMedium,
                    color = EduOrange,
                )
                distance != null -> Text(
                    formatDistanceKm(distance),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ManagementBadge(school.gestion)
                school.niveles.forEach { level ->
                    Text(
                        level.replaceFirstChar(Char::uppercase),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (school.hasCoordinates) {
                    Icon(
                        Icons.Default.TurnRight,
                        contentDescription = "Mostrar en el mapa",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

private fun formatDistanceKm(distanceInMeters: Double): String =
    if (distanceInMeters < 50.0) {
        "<0,1 km"
    } else {
        String.format(Locale("es", "PE"), "%.1f km", distanceInMeters / 1000.0)
    }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SchoolDetailsSheet(
    school: SchoolRecord?,
    onDismiss: () -> Unit,
    onDirections: (SchoolRecord) -> Unit,
    onShare: (SchoolRecord) -> Unit,
    comments: List<SchoolComment>,
    reminders: List<SchoolReminder>,
    commentSearchQuery: String,
    onAddComment: (String) -> Unit,
    onDeleteComment: (String) -> Unit,
    onEditComment: (String, String) -> Unit,
    onSearchComments: (String) -> Unit,
    onAddReminder: (String) -> Unit,
    onDeleteReminder: (String) -> Unit,
) {
    if (school == null) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val scope = rememberCoroutineScope()
    var commentText by rememberSaveable(school.id) { mutableStateOf("") }
    var commentQuery by rememberSaveable(school.id) { mutableStateOf(commentSearchQuery) }
    var reminderText by rememberSaveable(school.id) { mutableStateOf("") }
    var commentBeingEdited by remember(school.id) { mutableStateOf<SchoolComment?>(null) }
    var commentEditText by rememberSaveable(school.id) { mutableStateOf("") }
    var commentPendingDeletion by remember(school.id) { mutableStateOf<SchoolComment?>(null) }
    var reminderPendingDeletion by remember(school.id) { mutableStateOf<SchoolReminder?>(null) }
    LaunchedEffect(school.id) { sheetState.show() }
    LaunchedEffect(school.id, commentSearchQuery) {
        if (commentQuery != commentSearchQuery) {
            commentQuery = commentSearchQuery
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            school.nombre?.takeIf(String::isNotBlank) ?: "Colegio sin nombre",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            listOfNotNull(school.distrito, school.provincia, school.departamento)
                                .filter(String::isNotBlank)
                                .joinToString(" · ")
                                .ifBlank { "Ubicación no informada" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(
                    onClick = {
                        scope.launch {
                            if (sheetState.currentValue == SheetValue.Expanded) {
                                sheetState.partialExpand()
                            } else {
                                sheetState.expand()
                            }
                            }
                        },
                ) {
                    Icon(
                        if (sheetState.currentValue == SheetValue.Expanded) {
                            Icons.Default.ExpandMore
                        } else {
                            Icons.Default.ExpandLess
                        },
                        contentDescription = "Minimizar o expandir ficha",
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar ficha")
                }
            }
            ManagementBadge(school.gestion)
            if (school.niveles.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    school.niveles.forEach { level ->
                        AssistChip(
                            onClick = {},
                            label = { Text(level.replaceFirstChar(Char::uppercase)) },
                        )
                    }
                }
            } else {
                Text(
                    "Niveles educativos no informados",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatisticCard("Alumnos", school.alumnos ?: "—", Modifier.weight(1f))
                StatisticCard("Secciones", school.secciones ?: "—", Modifier.weight(1f))
            }
            HorizontalDivider()
            Text("Información del local", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            DetailRow("Dirección", school.direccion)
            DetailRow("Localidad", school.localidad)
            DetailRow("Centro poblado", school.cp)
            DetailRow("Turno", school.turno)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { onDirections(school) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Default.TurnRight, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Cómo llegar")
                }
                OutlinedButton(
                    onClick = { onShare(school) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Compartir")
                }
            }
            HorizontalDivider()
            Text(
                "Comentarios",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedTextField(
                value = commentText,
                onValueChange = { commentText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Añadir un comentario") },
                minLines = 2,
                maxLines = 4,
            )
            Button(
                onClick = {
                    onAddComment(commentText)
                    commentText = ""
                },
                enabled = commentText.isNotBlank(),
                modifier = Modifier.align(Alignment.End),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Guardar comentario")
            }
            OutlinedTextField(
                value = commentQuery,
                onValueChange = { query ->
                    commentQuery = query
                    onSearchComments(query)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Buscar en el historial") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
            )
            if (comments.isEmpty()) {
                Text(
                    if (commentQuery.isBlank()) {
                        "Todavía no hay comentarios para este colegio."
                    } else {
                        "No hay comentarios que coincidan con la búsqueda."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                comments.forEach { comment ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(comment.text, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                buildString {
                                    append("Creado: ${formatActivityDate(comment.createdAtMillis)}")
                                    comment.updatedAtMillis?.let {
                                        append(" · Editado: ${formatActivityDate(it)}")
                                    }
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(
                            onClick = {
                                commentBeingEdited = comment
                                commentEditText = comment.text
                            },
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Editar comentario")
                        }
                        IconButton(onClick = { commentPendingDeletion = comment }) {
                            Icon(Icons.Default.Delete, contentDescription = "Eliminar comentario")
                        }
                    }
                }
            }
            HorizontalDivider()
            Text(
                "Recordatorios",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedTextField(
                value = reminderText,
                onValueChange = { reminderText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("¿Qué debes recordar?") },
                singleLine = true,
            )
            OutlinedButton(
                onClick = {
                    onAddReminder(reminderText)
                    reminderText = ""
                },
                enabled = reminderText.isNotBlank(),
                modifier = Modifier.align(Alignment.End),
            ) {
                Icon(Icons.Default.Alarm, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Programar alarma")
            }
            if (reminders.isEmpty()) {
                Text(
                    "No hay alarmas programadas para este colegio.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                reminders.forEach { reminder ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(reminder.message, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${reminderStatusLabel(reminder)} · " +
                                    formatActivityDate(reminder.scheduledAtMillis),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (isReminderPending(reminder)) {
                            IconButton(onClick = { reminderPendingDeletion = reminder }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Cancelar recordatorio",
                                )
                            }
                        }
                    }
                }
            }

            commentBeingEdited?.let { comment ->
                AlertDialog(
                    onDismissRequest = { commentBeingEdited = null },
                    title = { Text("Editar comentario") },
                    text = {
                        OutlinedTextField(
                            value = commentEditText,
                            onValueChange = { commentEditText = it },
                            label = { Text("Comentario") },
                            minLines = 2,
                            maxLines = 5,
                        )
                    },
                    confirmButton = {
                        TextButton(
                            enabled = commentEditText.isNotBlank(),
                            onClick = {
                                onEditComment(comment.id, commentEditText)
                                commentBeingEdited = null
                            },
                        ) {
                            Text("Guardar")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { commentBeingEdited = null }) {
                            Text("Cancelar")
                        }
                    },
                )
            }

            commentPendingDeletion?.let { comment ->
                AlertDialog(
                    onDismissRequest = { commentPendingDeletion = null },
                    title = { Text("Eliminar comentario") },
                    text = { Text("Esta acción no se puede deshacer. ¿Quieres eliminar este comentario?") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                onDeleteComment(comment.id)
                                commentPendingDeletion = null
                            },
                        ) {
                            Text("Eliminar")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { commentPendingDeletion = null }) {
                            Text("Cancelar")
                        }
                    },
                )
            }

            reminderPendingDeletion?.let { reminder ->
                AlertDialog(
                    onDismissRequest = { reminderPendingDeletion = null },
                    title = { Text("Cancelar recordatorio") },
                    text = {
                        Text(
                            "¿Quieres cancelar la alarma de “${reminder.message}”? " +
                                "Se conservará en el historial.",
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                onDeleteReminder(reminder.id)
                                reminderPendingDeletion = null
                            },
                        ) {
                            Text("Cancelar y conservar")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { reminderPendingDeletion = null }) {
                            Text("Volver")
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ManagementBadge(gestion: String?) {
    val (label, background, foreground) = when (gestion) {
        "publica" -> Triple("Gestión pública", Color(0xFFE7F4EC), EduGreen)
        "privada" -> Triple("Gestión privada", Color(0xFFFFF1DB), Color(0xFF9A5B00))
        null, "" -> Triple("Gestión desconocida", Color(0xFFECEFF1), EduGray)
        else -> Triple(gestion, Color(0xFFECEFF1), EduGray)
    }
    Surface(shape = RoundedCornerShape(50), color = background) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = foreground,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun StatisticCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(14.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            label,
            modifier = Modifier.width(112.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value?.takeIf(String::isNotBlank) ?: "No informado",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun SchoolFiltersDialog(
    schools: List<SchoolRecord>,
    filters: SchoolFilters,
    onDismiss: () -> Unit,
    onApply: (SchoolFilters) -> Unit,
) {
    var department by remember(filters) { mutableStateOf(filters.departamento) }
    var province by remember(filters) { mutableStateOf(filters.provincia) }
    var district by remember(filters) { mutableStateOf(filters.distrito) }
    var management by remember(filters) { mutableStateOf(filters.gestiones) }
    var levels by remember(filters) { mutableStateOf(filters.niveles) }
    val departments = schools.mapNotNull(SchoolRecord::departamento).distinct().sorted()
    val provinces = schools.asSequence()
        .filter { department == null || it.departamento == department }
        .mapNotNull(SchoolRecord::provincia)
        .distinct()
        .sorted()
        .toList()
    val districts = schools.asSequence()
        .filter { department == null || it.departamento == department }
        .filter { province == null || it.provincia == province }
        .mapNotNull(SchoolRecord::distrito)
        .distinct()
        .sorted()
        .toList()
    val allManagement = schools.mapNotNull(SchoolRecord::gestion).distinct().sorted()
    val allLevels = schools.flatMap(SchoolRecord::niveles).distinct().sorted()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filtros avanzados") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterDropdown("Departamento", department, departments) {
                        department = it
                        province = null
                        district = null
                    }
                    FilterDropdown("Provincia", province, provinces) {
                        province = it
                        district = null
                    }
                    FilterDropdown("Distrito", district, districts) { district = it }
                }
                item {
                    Text("Gestión", style = MaterialTheme.typography.titleSmall)
                    allManagement.forEach { option ->
                        FilterCheckbox(
                            label = managementLabel(option),
                            checked = option in management,
                            onCheckedChange = {
                                management = if (it) management + option else management - option
                            },
                        )
                    }
                }
                item {
                    Text("Nivel educativo", style = MaterialTheme.typography.titleSmall)
                    allLevels.forEach { option ->
                        FilterCheckbox(
                            label = option.replaceFirstChar(Char::uppercase),
                            checked = option in levels,
                            onCheckedChange = {
                                levels = if (it) levels + option else levels - option
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onApply(
                        SchoolFilters(
                            departamento = department,
                            provincia = province,
                            distrito = district,
                            gestiones = management,
                            niveles = levels,
                        ),
                    )
                },
            ) { Text("Aplicar") }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = {
                        onApply(SchoolFilters())
                    },
                ) { Text("Limpiar") }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )
}

@Composable
private fun FilterDropdown(
    label: String,
    selected: String?,
    options: List<String>,
    onSelected: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp),
        ) {
            Text(
                "$label: ${selected ?: "Todos"}",
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(Icons.Default.ExpandMore, contentDescription = null)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.height(300.dp),
        ) {
            DropdownMenuItem(
                text = { Text("Todos") },
                onClick = {
                    onSelected(null)
                    expanded = false
                },
            )
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun FilterCheckbox(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label)
    }
}

@Composable
private fun EmptyState(title: String, message: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 44.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StatisticLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

private fun activeFilterCount(filters: SchoolFilters): Int =
    listOf(filters.departamento, filters.provincia, filters.distrito).count { it != null } +
        filters.gestiones.size + filters.niveles.size

private fun managementLabel(value: String): String = when (value) {
    "publica" -> "Pública"
    "privada" -> "Privada"
    else -> value.replaceFirstChar(Char::uppercase)
}

private fun formatActivityDate(timestamp: Long): String =
    java.text.DateFormat.getDateTimeInstance(
        java.text.DateFormat.SHORT,
        java.text.DateFormat.SHORT,
    ).format(java.util.Date(timestamp))

internal fun isReminderPending(
    reminder: SchoolReminder,
    nowMillis: Long = System.currentTimeMillis(),
): Boolean =
    reminder.status == SchoolReminder.STATUS_PENDING &&
        reminder.scheduledAtMillis > nowMillis

private fun reminderStatusLabel(
    reminder: SchoolReminder,
    nowMillis: Long = System.currentTimeMillis(),
): String = when {
    isReminderPending(reminder, nowMillis) -> "Pendiente"
    reminder.status == SchoolReminder.STATUS_TRIGGERED -> "Disparada"
    reminder.status == SchoolReminder.STATUS_CANCELLED -> "Cancelada"
    else -> "Vencida"
}
