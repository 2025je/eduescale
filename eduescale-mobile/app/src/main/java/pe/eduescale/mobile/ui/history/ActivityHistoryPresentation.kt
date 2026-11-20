package pe.eduescale.mobile.ui.history

import pe.eduescale.mobile.activity.SchoolComment
import pe.eduescale.mobile.activity.SchoolReminder

enum class ActivityHistoryFilter {
    ALL,
    COMMENTS,
    PENDING_REMINDERS,
    NON_PENDING_REMINDERS,
}

data class ActivityHistoryEntry(
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

internal fun buildActivityHistory(
    comments: List<SchoolComment>,
    reminders: List<SchoolReminder>,
    schoolNames: Map<String, String>,
    query: String,
    filter: ActivityHistoryFilter,
    now: Long,
): List<ActivityHistoryEntry> = buildList {
    if (filter == ActivityHistoryFilter.ALL || filter == ActivityHistoryFilter.COMMENTS) {
        comments.forEach { comment ->
            val updatedAt = comment.updatedAtMillis
            add(
                ActivityHistoryEntry(
                    id = "comment-${comment.id}",
                    type = "Comentario",
                    status = if (updatedAt == null) "Guardado" else "Editado",
                    schoolName = schoolNames[comment.schoolKey] ?: "Colegio no disponible",
                    message = comment.text,
                    date = buildString {
                        append("Creado: ${formatActivityDate(comment.createdAtMillis)}")
                        updatedAt?.let { append(" · Editado: ${formatActivityDate(it)}") }
                    },
                    timestamp = updatedAt ?: comment.createdAtMillis,
                    isReminder = false,
                    isPending = false,
                    comment = comment,
                ),
            )
        }
    }
    if (filter != ActivityHistoryFilter.COMMENTS) {
        reminders.forEach { reminder ->
            val pending = isReminderPending(reminder, now)
            if (filter == ActivityHistoryFilter.PENDING_REMINDERS && !pending) return@forEach
            if (filter == ActivityHistoryFilter.NON_PENDING_REMINDERS && pending) return@forEach
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
                    isPending = pending,
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

internal fun formatActivityDate(timestamp: Long): String =
    java.text.DateFormat.getDateTimeInstance(
        java.text.DateFormat.SHORT,
        java.text.DateFormat.SHORT,
    ).format(java.util.Date(timestamp))

internal fun isReminderPending(
    reminder: SchoolReminder,
    nowMillis: Long = System.currentTimeMillis(),
): Boolean =
    reminder.status == SchoolReminder.STATUS_PENDING && reminder.scheduledAtMillis > nowMillis

internal fun reminderStatusLabel(
    reminder: SchoolReminder,
    nowMillis: Long = System.currentTimeMillis(),
): String = when {
    isReminderPending(reminder, nowMillis) -> "Pendiente"
    reminder.status == SchoolReminder.STATUS_TRIGGERED -> "Disparada"
    reminder.status == SchoolReminder.STATUS_CANCELLED -> "Cancelada"
    else -> "Vencida"
}
