package pe.eduescale.mobile.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import pe.eduescale.mobile.data.OfflineTileEstimator
import pe.eduescale.mobile.data.SchoolRecord
import pe.eduescale.mobile.ui.EduEscaleUiState
import pe.eduescale.mobile.ui.OfflineMapAreaUi
import pe.eduescale.mobile.ui.OfflineMapBounds
import pe.eduescale.mobile.ui.components.BrandHeader
import pe.eduescale.mobile.ui.components.LegendRow
import pe.eduescale.mobile.ui.files.formatMapBytes
import pe.eduescale.mobile.ui.schools.SchoolDetailsSheet
import pe.eduescale.mobile.ui.theme.EduGray
import pe.eduescale.mobile.ui.theme.EduGreen
import pe.eduescale.mobile.ui.theme.EduOrange

@Composable
internal fun MapScreen(
    state: EduEscaleUiState,
    onMapTopOverlayBottomChanged: (Float) -> Unit,
    onSchoolDetailsSheetShown: (SchoolRecord, Float) -> Unit,
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
                    .clickable(
                        role = Role.Button,
                        onClick = { legendExpanded = !legendExpanded },
                    ),
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
                            "Tamaño: primaria · secundaria · CETPRO",
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
                            progress = { progress },
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!state.isCreatingOfflineMapArea && activeDownload == null) {
                IconButton(
                    onClick = { showOfflineMapDialog = true },
                    enabled = state.visibleMapBounds != null,
                ) {
                    Icon(
                        Icons.Default.FileDownload,
                        contentDescription = "Descargar el área visible sin conexión",
                    )
                }
                }
                Text(
                "OpenFreeMap © OpenMapTiles · Data from OpenStreetMap",
                modifier = Modifier.padding(start = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
        userLocation = state.userLocation,
        onSheetShown = onSchoolDetailsSheetShown,
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
internal fun OfflineMapDownloadDialog(
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
