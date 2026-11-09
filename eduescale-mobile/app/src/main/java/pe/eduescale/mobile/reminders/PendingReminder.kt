package pe.eduescale.mobile.reminders

internal data class PendingReminder(
    val schoolKey: String,
    val schoolName: String,
    val message: String,
    val scheduledAtMillis: Long,
)
