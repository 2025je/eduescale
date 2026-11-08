package pe.eduescale.mobile.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.eduescale.mobile.data.SchoolReminder
import pe.eduescale.mobile.data.reactivatedWith

class ReminderHistoryTest {
    @Test
    fun onlyFuturePendingAlarmsArePending() {
        assertTrue(isReminderPending(reminder(SchoolReminder.STATUS_PENDING, 200L), 100L))
        assertFalse(isReminderPending(reminder(SchoolReminder.STATUS_PENDING, 100L), 100L))
        assertFalse(isReminderPending(reminder(SchoolReminder.STATUS_PENDING, 50L), 100L))
        assertFalse(isReminderPending(reminder(SchoolReminder.STATUS_TRIGGERED, 200L), 100L))
        assertFalse(isReminderPending(reminder(SchoolReminder.STATUS_CANCELLED, 200L), 100L))
        assertFalse(isReminderPending(reminder(SchoolReminder.STATUS_EXPIRED, 200L), 100L))
    }

    @Test
    fun editingAnyAlarmReactivatesItWithTheNewDetails() {
        listOf(
            SchoolReminder.STATUS_PENDING,
            SchoolReminder.STATUS_TRIGGERED,
            SchoolReminder.STATUS_CANCELLED,
            SchoolReminder.STATUS_EXPIRED,
            null,
        ).forEach { oldStatus ->
            val updated = reminder(oldStatus, 50L).reactivatedWith("Nuevo mensaje", 300L)

            assertEquals(SchoolReminder.STATUS_PENDING, updated.status)
            assertEquals("Nuevo mensaje", updated.message)
            assertEquals(300L, updated.scheduledAtMillis)
        }
    }

    private fun reminder(status: String?, scheduledAtMillis: Long) = SchoolReminder(
        id = "reminder",
        schoolKey = "school",
        schoolName = "Colegio",
        message = "Llamar",
        scheduledAtMillis = scheduledAtMillis,
        status = status,
    )
}
