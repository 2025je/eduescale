package pe.eduescale.mobile.activity

interface ReminderStore {
    suspend fun pendingReminders(): List<SchoolReminder>
    suspend fun cancelReminder(reminderId: String)
    suspend fun addReminder(
        schoolKey: String,
        schoolName: String,
        message: String,
        scheduledAtMillis: Long,
    ): SchoolReminder
    suspend fun updateReminder(reminderId: String, message: String, scheduledAtMillis: Long): SchoolReminder
    suspend fun restoreReminder(reminder: SchoolReminder)
    suspend fun deleteReminder(reminderId: String)
}
