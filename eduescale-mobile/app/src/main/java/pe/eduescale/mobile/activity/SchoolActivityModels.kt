package pe.eduescale.mobile.activity

import java.io.IOException

data class SchoolComment(
    val id: String,
    val schoolKey: String,
    val text: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long? = null,
)

data class SchoolReminder(
    val id: String,
    val schoolKey: String,
    val schoolName: String,
    val message: String,
    val scheduledAtMillis: Long,
    val status: String? = STATUS_PENDING,
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_TRIGGERED = "TRIGGERED"
        const val STATUS_CANCELLED = "CANCELLED"
        const val STATUS_EXPIRED = "EXPIRED"
        val VALID_STATUSES = setOf(STATUS_PENDING, STATUS_TRIGGERED, STATUS_CANCELLED, STATUS_EXPIRED)
    }
}

internal fun SchoolReminder.reactivatedWith(message: String, scheduledAtMillis: Long): SchoolReminder =
    copy(message = message, scheduledAtMillis = scheduledAtMillis, status = SchoolReminder.STATUS_PENDING)

enum class VisitStatus {
    NOT_VISITED,
    SCHEDULED,
    VISITED,
    PERMISSION_DENIED,
}

data class SchoolVisit(
    val id: String,
    val schoolKey: String,
    val schoolName: String,
    val status: VisitStatus,
    val visitDate: Long? = null,
    val scheduledDate: Long? = null,
    val permissionDeniedReason: String? = null,
    val observations: String? = null,
    val createdAtMillis: Long,
    val updatedAtMillis: Long? = null,
)

data class SchoolActivityBackup(
    val format: String = BACKUP_FORMAT,
    val version: Int = BACKUP_VERSION,
    val comments: List<SchoolComment> = emptyList(),
    val reminders: List<SchoolReminder> = emptyList(),
) {
    companion object {
        const val BACKUP_FORMAT = "eduescale-activity"
        const val BACKUP_VERSION = 1
    }
}

class ActivityStorageException(cause: Throwable) :
    IOException("No se pudo acceder al historial guardado.", cause)
