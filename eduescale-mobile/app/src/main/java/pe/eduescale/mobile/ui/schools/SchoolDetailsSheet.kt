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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun SchoolDetailsSheet(
    school: SchoolRecord?,
    userLocation: SchoolCoordinates?,
    onSheetShown: (SchoolRecord, Float) -> Unit,
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
    var sheetTopOnScreen by remember(school.id) { mutableStateOf<Float?>(null) }
    LaunchedEffect(school.id) {
        sheetState.show()
        sheetTopOnScreen?.let { top -> onSheetShown(school, top) }
    }
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
                .onGloballyPositioned { coordinates ->
                    sheetTopOnScreen = coordinates.positionOnScreen().y
                }
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
                        Text(
                            when {
                                !school.hasCoordinates ->
                                    "Distancia no disponible · sin coordenadas"
                                userLocation == null ->
                                    "Activa la ubicación para calcular la distancia"
                                else -> distanceInMeters(school, userLocation)?.let { distance ->
                                    "${formatDistanceKm(distance)} desde tu ubicación"
                                } ?: "Distancia no disponible"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (school.hasCoordinates && userLocation != null) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
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
internal fun ManagementBadge(gestion: String?) {
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
internal fun StatisticCard(label: String, value: String, modifier: Modifier = Modifier) {
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
internal fun DetailRow(label: String, value: String?) {
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
internal fun StatisticLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

internal fun managementLabel(value: String): String = when (value) {
    "publica" -> "Pública"
    "privada" -> "Privada"
    else -> value.replaceFirstChar(Char::uppercase)
}
