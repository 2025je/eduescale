package pe.eduescale.mobile.ui.files

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import pe.eduescale.mobile.data.SchoolFile
import pe.eduescale.mobile.ui.OfflineMapAreaUi
import pe.eduescale.mobile.ui.components.BrandHeader
import pe.eduescale.mobile.ui.schools.StatisticLine

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
    deviceAvailableBytes: Long,
    offlineMapMessage: String?,
    offlineMapMessageIsError: Boolean,
    onToggleOfflineMapArea: (Long, Boolean) -> Unit,
    onDeleteOfflineMapArea: (Long) -> Unit,
) {
    var pendingOfflineAreaDeletion by remember { mutableStateOf<OfflineMapAreaUi?>(null) }
    val context = LocalContext.current
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
                        "Se admiten los archivos EduEscale versionados y ZIP de versiones anteriores.",
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
                        fileName ?: "Archivo EduEscale",
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
        StatisticLine(
            "Total descargado en mapas sin conexión",
            android.text.format.Formatter.formatFileSize(
                context,
                offlineMapAreas.sumOf(OfflineMapAreaUi::completedBytes),
            ),
        )
        StatisticLine(
            "Espacio disponible en el almacenamiento interno",
            android.text.format.Formatter.formatFileSize(context, deviceAvailableBytes),
        )
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
                progress = {
                    if (area.estimatedTiles > 0) {
                        (area.completedTiles.toFloat() / area.estimatedTiles).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
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
