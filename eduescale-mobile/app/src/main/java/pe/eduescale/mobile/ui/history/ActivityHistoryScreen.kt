package pe.eduescale.mobile.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pe.eduescale.mobile.data.SchoolComment
import pe.eduescale.mobile.data.SchoolReminder
import pe.eduescale.mobile.data.activityKey
import pe.eduescale.mobile.ui.EduEscaleUiState
import pe.eduescale.mobile.ui.components.BrandHeader
import pe.eduescale.mobile.ui.components.EmptyState

internal enum class ActivityHistoryFilter {
    ALL,
    COMMENTS,
    PENDING_REMINDERS,
    NON_PENDING_REMINDERS,
}

internal data class ActivityHistoryEntry(
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
internal fun ActivityHistoryScreen(
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
internal fun ActivityHistoryCard(entry: ActivityHistoryEntry, onEdit: () -> Unit) {
    Card(shape = RoundedCornerShape(18.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                if (entry.isReminder) Icons.Default.Alarm else Icons.AutoMirrored.Filled.Comment,
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

internal fun formatActivityDate(timestamp: Long): String =
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

internal fun reminderStatusLabel(
    reminder: SchoolReminder,
    nowMillis: Long = System.currentTimeMillis(),
): String = when {
    isReminderPending(reminder, nowMillis) -> "Pendiente"
    reminder.status == SchoolReminder.STATUS_TRIGGERED -> "Disparada"
    reminder.status == SchoolReminder.STATUS_CANCELLED -> "Cancelada"
    else -> "Vencida"
}
