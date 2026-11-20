package pe.eduescale.mobile.reminders

import java.io.Serializable

internal data class PendingReminder(
    val schoolKey: String,
    val schoolName: String,
    val message: String,
    val scheduledAtMillis: Long,
) : Serializable
