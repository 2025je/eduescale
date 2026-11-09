package pe.eduescale.mobile.ui.files

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
import androidx.compose.ui.layout.positionOnScreen
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
import java.util.Locale
import kotlin.math.roundToInt
import pe.eduescale.mobile.ui.components.*
import pe.eduescale.mobile.ui.history.*
import pe.eduescale.mobile.ui.map.*
import pe.eduescale.mobile.ui.schools.*
import pe.eduescale.mobile.ui.theme.*
import pe.eduescale.mobile.ui.EduEscaleUiState
import pe.eduescale.mobile.ui.EduEscaleViewModel
import pe.eduescale.mobile.ui.OfflineMapAreaUi
import pe.eduescale.mobile.ui.OfflineMapBounds

@Composable
internal fun FilesScreen(
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
internal fun OfflineMapAreaCard(
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

internal fun formatMapBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${bytes / (1024 * 1024)} MB"
}
