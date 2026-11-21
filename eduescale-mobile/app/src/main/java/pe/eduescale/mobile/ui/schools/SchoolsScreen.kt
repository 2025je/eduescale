package pe.eduescale.mobile.ui.schools

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale
import pe.eduescale.mobile.data.SchoolFilters
import pe.eduescale.mobile.data.SchoolRecord
import pe.eduescale.mobile.data.SavedSearch
import pe.eduescale.mobile.ui.EduEscaleUiState
import pe.eduescale.mobile.routing.RoadRoutingState
import pe.eduescale.mobile.ui.components.BrandHeader
import pe.eduescale.mobile.ui.components.EmptyState
import pe.eduescale.mobile.ui.files.RoadDownloadProgress

@Composable
internal fun SchoolsScreen(
    state: EduEscaleUiState,
    roadState: RoadRoutingState,
    onQueryChanged: (String) -> Unit,
    onApplyFilters: (SchoolFilters) -> Unit,
    onOpenFilters: () -> Unit,
    onFilterDraftChanged: (SchoolFilters) -> Unit,
    onDismissFilters: () -> Unit,
    savedSearches: List<SavedSearch>,
    savedSearchMessage: String?,
    savedSearchMessageIsError: Boolean,
    onApplySavedSearch: (SchoolFilters) -> Unit,
    onSaveSearch: (String) -> Unit,
    onDeleteSavedSearch: (SavedSearch) -> Unit,
    onImportSavedSearches: () -> Unit,
    onExportSavedSearches: () -> Unit,
    onSchoolSelected: (SchoolRecord) -> Unit,
) {
    var savedSearchesOpen by rememberSaveable { mutableStateOf(false) }
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
                onClick = onOpenFilters,
            )
        }
        OutlinedButton(
            onClick = { savedSearchesOpen = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Búsquedas guardadas (${savedSearches.size})")
        }
        savedSearchMessage?.let { message ->
            Text(
                message,
                modifier = Modifier.padding(vertical = 2.dp),
                color = if (savedSearchMessageIsError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
        ActiveFilterChips(state.filters, Modifier.padding(top = 4.dp))
        val schoolsWithoutCoordinates = state.visibleSchools.count { !it.hasCoordinates }
        val matchingRoadInputs = roadState.inputSchools == state.visibleSchools &&
            roadState.inputOrigin == state.userLocation
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
                        } else if (!roadState.available) {
                            " · ordenadas por línea recta (no por carretera)"
                        } else if (roadState.calculating || !matchingRoadInputs) {
                            " · calculando rutas"
                        } else if (roadState.error == null) {
                            " · ordenadas por carretera"
                        } else {
                            " · rutas no disponibles"
                        },
                    )
                }
            },
            modifier = Modifier.padding(vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        roadState.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if ((!roadState.available || roadState.downloading) && state.schoolFile != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (roadState.downloading) {
                        RoadDownloadProgress(roadState.downloadedBytes, roadState.totalBytes)
                    } else {
                        Text(
                            "No hay rutas descargadas. Las distancias se muestran en línea recta, " +
                                "no por carretera; descarga los datos en Archivos para obtener distancias por carretera.",
                        )
                    }
                }
            }
        }
        if (state.schoolFile == null) {
            EmptyState("Aún no hay colegios cargados", "Abre Archivos para importar un archivo .eduescale.")
        } else if (state.visibleSchools.isEmpty()) {
            EmptyState("No hay resultados", "Prueba con otro nombre o cambia los filtros.")
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(
                    if (matchingRoadInputs) {
                        roadState.orderedSchools
                    } else {
                        state.visibleSchools
                    },
                    key = SchoolRecord::id,
                ) { school ->
                    SchoolListCard(
                        school = school,
                        distance = if (matchingRoadInputs) {
                            roadState.distances[school.id]
                        } else null,
                        userLocationAvailable = state.userLocation != null,
                        roadsAvailable = roadState.available,
                        calculating = roadState.calculating || !matchingRoadInputs,
                        routingError = roadState.error != null,
                        onClick = { onSchoolSelected(school) },
                    )
                }
            }
        }
    }
    state.filterDraft?.let { draft ->
        SchoolFiltersDialog(
            filters = draft,
            options = state.filterOptions,
            onChange = onFilterDraftChanged,
            onDismiss = onDismissFilters,
            onApply = {
                onApplyFilters(it)
                onDismissFilters()
            },
        )
    }
    if (savedSearchesOpen) {
        SavedSearchesDialog(
            searches = savedSearches,
            canSave = state.schoolFile != null,
            canApply = state.schoolFile != null,
            message = savedSearchMessage,
            messageIsError = savedSearchMessageIsError,
            onDismiss = { savedSearchesOpen = false },
            onApply = {
                onApplySavedSearch(it)
                savedSearchesOpen = false
            },
            onSave = onSaveSearch,
            onDelete = onDeleteSavedSearch,
            onImport = onImportSavedSearches,
            onExport = onExportSavedSearches,
        )
    }
}

@Composable
internal fun SchoolListCard(
    school: SchoolRecord,
    distance: Double?,
    userLocationAvailable: Boolean,
    roadsAvailable: Boolean,
    calculating: Boolean,
    routingError: Boolean,
    onClick: () -> Unit,
) {
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
                    color = MaterialTheme.colorScheme.secondary,
                )
                !userLocationAvailable -> Text(
                    "Activa la ubicación para calcular la distancia",
                    style = MaterialTheme.typography.labelMedium,
                )
                !roadsAvailable && distance != null -> Text(
                    "${formatDistanceKm(distance)} en línea recta (no por carretera)",
                    style = MaterialTheme.typography.labelMedium,
                )
                !roadsAvailable -> Text(
                    "Descarga las carreteras en Archivos",
                    style = MaterialTheme.typography.labelMedium,
                )
                calculating -> Text("Calculando ruta…", style = MaterialTheme.typography.labelMedium)
                routingError -> Text("No se pudo calcular la ruta", style = MaterialTheme.typography.labelMedium)
                distance != null -> Text(
                    "${formatDistanceKm(distance)} por carretera",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                else -> Text("Ruta no disponible", style = MaterialTheme.typography.labelMedium)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ManagementBadge(school.gestion)
                school.niveles.forEach { level ->
                    Text(
                        schoolLevelLabel(level),
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

internal fun formatDistanceKm(distanceInMeters: Double): String =
    if (distanceInMeters < 50.0) {
        "<0,1 km"
    } else {
        String.format(Locale.forLanguageTag("es-PE"), "%.1f km", distanceInMeters / 1000.0)
    }
