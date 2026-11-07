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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FolderOpen
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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import pe.eduescale.mobile.data.SchoolFilters
import pe.eduescale.mobile.data.SchoolRecord
import pe.eduescale.mobile.ui.EduEscaleUiState
import pe.eduescale.mobile.ui.EduEscaleViewModel

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
    onPickFile: () -> Unit,
    onLocate: () -> Unit,
    onApplyFilters: (SchoolFilters) -> Unit,
    onSchoolSelected: (SchoolRecord) -> Unit,
    onDirections: (SchoolRecord) -> Unit,
    onShare: (SchoolRecord) -> Unit,
    onClearSelection: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route ?: "map"
    LaunchedEffect(currentRoute, state.selectedSchool) {
        onMapTouchEnabled(currentRoute == "map" && state.selectedSchool == null)
    }
    val destinations = listOf(
        Destination("map", "Mapa", Icons.Default.Map),
        Destination("schools", "Colegios", Icons.Default.List),
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
                        onGoToFiles = {
                            navController.navigate("files") {
                                launchSingleTop = true
                            }
                        },
                        onLocate = onLocate,
                        onDirections = onDirections,
                        onShare = onShare,
                        onClearSelection = onClearSelection,
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
    onGoToFiles: () -> Unit,
    onLocate: () -> Unit,
    onDirections: (SchoolRecord) -> Unit,
    onShare: (SchoolRecord) -> Unit,
    onClearSelection: () -> Unit,
) {
    var legendExpanded by rememberSaveable { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp, vertical = 10.dp),
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
                        "${state.visibleSchools.size} colegios en el mapa",
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

        Text(
            "© OpenStreetMap contributors",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    SchoolDetailsSheet(
        school = state.selectedSchool,
        onDismiss = onClearSelection,
        onDirections = onDirections,
        onShare = onShare,
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
        Text(
            if (state.schoolFile == null) {
                "Importa un archivo desde la sección Archivos."
            } else {
                "${state.visibleSchools.size} instituciones"
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
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.visibleSchools, key = SchoolRecord::id) { school ->
                    SchoolListCard(school, onClick = { onSchoolSelected(school) })
                }
            }
        }
    }
    if (filtersOpen && state.schoolFile != null) {
        SchoolFiltersDialog(
            schools = state.schoolFile.schools,
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
private fun FilesScreen(
    file: SchoolFile?,
    fileName: String?,
    importing: Boolean,
    error: String?,
    onPickFile: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
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
                    StatisticLine("Instituciones en el archivo", file.totalCount.toString())
                    StatisticLine("Con ubicación disponible", file.schools.size.toString())
                    StatisticLine("Sin coordenadas utilizables", file.withoutCoordinates.toString())
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
    }
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
private fun SchoolListCard(school: SchoolRecord, onClick: () -> Unit) {
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SchoolDetailsSheet(
    school: SchoolRecord?,
    onDismiss: () -> Unit,
    onDirections: (SchoolRecord) -> Unit,
    onShare: (SchoolRecord) -> Unit,
) {
    if (school == null) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val scope = rememberCoroutineScope()
    LaunchedEffect(school.id) { sheetState.show() }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
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
