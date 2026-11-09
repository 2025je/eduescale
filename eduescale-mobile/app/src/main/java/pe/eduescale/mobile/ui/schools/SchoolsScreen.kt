package pe.eduescale.mobile.ui.schools

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
import pe.eduescale.mobile.ui.files.*
import pe.eduescale.mobile.ui.history.*
import pe.eduescale.mobile.ui.map.*
import pe.eduescale.mobile.ui.theme.*
import pe.eduescale.mobile.ui.EduEscaleUiState
import pe.eduescale.mobile.ui.EduEscaleViewModel
import pe.eduescale.mobile.ui.OfflineMapAreaUi
import pe.eduescale.mobile.ui.OfflineMapBounds

@Composable
internal fun SchoolsScreen(
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

@Composable
internal fun SchoolListCard(
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

internal fun formatDistanceKm(distanceInMeters: Double): String =
    if (distanceInMeters < 50.0) {
        "<0,1 km"
    } else {
        String.format(Locale("es", "PE"), "%.1f km", distanceInMeters / 1000.0)
    }
