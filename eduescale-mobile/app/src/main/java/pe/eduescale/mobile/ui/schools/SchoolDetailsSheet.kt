package pe.eduescale.mobile.ui.schools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import pe.eduescale.mobile.data.SchoolComment
import pe.eduescale.mobile.data.SchoolCoordinates
import pe.eduescale.mobile.data.SchoolRecord
import pe.eduescale.mobile.data.SchoolReminder
import pe.eduescale.mobile.data.SchoolScheduleRow
import pe.eduescale.mobile.data.distanceInMeters
import pe.eduescale.mobile.ui.history.formatActivityDate
import pe.eduescale.mobile.ui.history.isReminderPending
import pe.eduescale.mobile.ui.history.reminderStatusLabel

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
                            label = { Text(schoolLevelLabel(level)) },
                        )
                    }
                }
            } else {
                Text(
                    "Niveles educativos no informados",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "Oferta educativa",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            SchoolOfferTable(school.ofertaEducativa)
            HorizontalDivider()
            Text("Información del local", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            DetailRow("Dirección", school.direccion)
            DetailRow("Localidad", school.localidad)
            DetailRow("Centro poblado", school.cp)
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
    val colorScheme = MaterialTheme.colorScheme
    val (label, background, foreground) = when (gestion) {
        "publica" -> Triple(
            "Gestión pública",
            colorScheme.primaryContainer,
            colorScheme.onPrimaryContainer,
        )
        "privada" -> Triple(
            "Gestión privada",
            colorScheme.secondaryContainer,
            colorScheme.onSecondaryContainer,
        )
        null, "" -> Triple(
            "Gestión desconocida",
            colorScheme.surfaceVariant,
            colorScheme.onSurfaceVariant,
        )
        else -> Triple(
            gestion,
            colorScheme.surfaceVariant,
            colorScheme.onSurfaceVariant,
        )
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
private fun SchoolOfferTable(rows: List<SchoolScheduleRow>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                )
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SchoolOfferCell("Nivel", Modifier.weight(1.2f), header = true)
            SchoolOfferCell("Aulas", Modifier.weight(0.8f), header = true)
            SchoolOfferCell("Alumnos", Modifier.weight(0.9f), header = true)
            SchoolOfferCell("Turno", Modifier.weight(1.1f), header = true)
        }
        rows.forEachIndexed { index, row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SchoolOfferCell(
                    row.nivel?.let(::schoolLevelLabel) ?: "—",
                    Modifier.weight(1.2f),
                )
                SchoolOfferCell(row.aulas ?: "—", Modifier.weight(0.8f))
                SchoolOfferCell(row.alumnos ?: "—", Modifier.weight(0.9f))
                SchoolOfferCell(row.turno ?: "—", Modifier.weight(1.1f))
            }
            if (index < rows.lastIndex) HorizontalDivider()
        }
    }
}

@Composable
private fun SchoolOfferCell(value: String, modifier: Modifier, header: Boolean = false) {
    Text(
        text = value,
        modifier = modifier.padding(end = 4.dp),
        style = if (header) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
        fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
        color = if (header) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurface
        },
    )
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

internal fun schoolLevelLabel(value: String): String =
    if (value.equals("cetpro", ignoreCase = true)) {
        "CETPRO"
    } else {
        value.replaceFirstChar(Char::uppercase)
    }
