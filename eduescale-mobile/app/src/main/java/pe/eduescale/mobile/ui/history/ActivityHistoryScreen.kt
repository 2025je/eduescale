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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pe.eduescale.mobile.activity.SchoolComment
import pe.eduescale.mobile.activity.SchoolReminder
import pe.eduescale.mobile.activity.SchoolActivityUiState
import pe.eduescale.mobile.reminders.ReminderDateTimePicker
import pe.eduescale.mobile.ui.components.BrandHeader
import pe.eduescale.mobile.ui.components.EmptyState

@Composable
internal fun ActivityHistoryScreen(
    activityState: SchoolActivityUiState,
    onQueryChanged: (String) -> Unit,
    onFilterChanged: (ActivityHistoryFilter) -> Unit,
    onRefresh: () -> Unit,
    onEditComment: (SchoolComment, String) -> Unit,
    onEditReminder: (SchoolReminder, String, Long) -> Unit,
    onReminderMessage: (String) -> Unit,
) {
    val query = activityState.historyQuery
    val selectedFilter = activityState.historyFilter
    var commentBeingEditedId by rememberSaveable { mutableStateOf<String?>(null) }
    var commentEditText by rememberSaveable { mutableStateOf("") }
    var reminderBeingEditedId by rememberSaveable { mutableStateOf<String?>(null) }
    var reminderEditText by rememberSaveable { mutableStateOf("") }
    var reminderEditScheduledAt by rememberSaveable { mutableStateOf(0L) }
    var pickingReminderDate by rememberSaveable { mutableStateOf(false) }
    val reminderBeingEdited = activityState.activityHistoryReminders
        .firstOrNull { it.id == reminderBeingEditedId }
    val commentBeingEdited = activityState.activityHistoryComments
        .firstOrNull { it.id == commentBeingEditedId }
    val entries = activityState.historyEntries

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
            onValueChange = onQueryChanged,
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
                    onClick = { onFilterChanged(filter) },
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
        if (activityState.isLoadingActivityHistory) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        activityState.activityHistoryError?.let { error ->
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
                            commentBeingEditedId = comment.id
                            commentEditText = comment.text
                        }
                        entry.reminder?.let { reminder ->
                            reminderBeingEditedId = reminder.id
                            reminderEditText = reminder.message
                            reminderEditScheduledAt = reminder.scheduledAtMillis
                        }
                    }
                }
            }
        } else if (activityState.isLoadingActivityHistory) {
            EmptyState("Cargando historial", "Consultando comentarios y alarmas guardados.")
        } else if (activityState.activityHistoryError == null) {
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
            onDismissRequest = { commentBeingEditedId = null },
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
                        commentBeingEditedId = null
                    },
                ) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { commentBeingEditedId = null }) { Text("Cancelar") }
            },
        )
    }
    reminderBeingEdited?.let { reminder ->
        AlertDialog(
            onDismissRequest = {
                reminderBeingEditedId = null
                pickingReminderDate = false
            },
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
                        onClick = { pickingReminderDate = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Fecha y hora: ${formatActivityDate(reminderEditScheduledAt)}")
                    }
                    Text(
                        "Al guardar, la alarma quedará pendiente con la nueva fecha.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (pickingReminderDate) {
                        ReminderDateTimePicker(
                            initialScheduledAtMillis = reminderEditScheduledAt,
                            showMessage = onReminderMessage,
                            onSelected = { selectedAt ->
                                reminderEditScheduledAt = selectedAt
                                pickingReminderDate = false
                            },
                            onCancelled = { pickingReminderDate = false },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = reminderEditText.isNotBlank() &&
                        reminderEditScheduledAt > System.currentTimeMillis(),
                    onClick = {
                        onEditReminder(reminder, reminderEditText, reminderEditScheduledAt)
                        reminderBeingEditedId = null
                    },
                ) { Text("Guardar y reactivar") }
            },
            dismissButton = {
                TextButton(onClick = { reminderBeingEditedId = null }) { Text("Cancelar") }
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
