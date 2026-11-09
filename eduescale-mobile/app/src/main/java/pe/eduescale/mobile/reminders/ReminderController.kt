package pe.eduescale.mobile.reminders

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteException
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import java.io.IOException
import kotlinx.coroutines.launch
import pe.eduescale.mobile.activity.SchoolActivityController
import pe.eduescale.mobile.data.SchoolActivityRepository
import pe.eduescale.mobile.data.SchoolRecord
import pe.eduescale.mobile.data.SchoolReminder
import pe.eduescale.mobile.data.activityKey
import pe.eduescale.mobile.ui.EduEscaleViewModel

/** Reminder scheduling flows: permissions, alarms, persistence and history edits. */
class ReminderController(
    private val activity: ComponentActivity,
    private val viewModel: EduEscaleViewModel,
    private val repositoryProvider: () -> SchoolActivityRepository,
    private val activityProvider: () -> SchoolActivityController,
    private val showReminderMessage: (String) -> Unit,
) {
    private val schoolActivityRepository get() = repositoryProvider()
    private val activityController get() = activityProvider()
    private var pendingReminder: PendingReminder? = null

    private val requestNotificationPermission = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val reminder = pendingReminder ?: return@registerForActivityResult
        if (granted) {
            requestExactAlarmPermission(reminder)
        } else {
            pendingReminder = null
            showNotificationSettings()
        }
    }

    private val requestExactAlarmSettings = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        val reminder = pendingReminder ?: return@registerForActivityResult
        if (SchoolReminderAlarms.canScheduleExactAlarms(activity)) {
            restorePendingReminders()
            saveAndScheduleReminder(reminder)
        } else {
            pendingReminder = null
            showReminderMessage("No se concedió el permiso para programar alarmas exactas.")
        }
    }

    fun restorePendingReminders(showPermissionMessage: Boolean = false) {
        activity.lifecycleScope.launch {
            try {
                val reminders = schoolActivityRepository.pendingReminders()
                if (
                    reminders.isNotEmpty() &&
                    !NotificationManagerCompat.from(activity).areNotificationsEnabled()
                ) {
                    if (showPermissionMessage) {
                        showNotificationSettings()
                    }
                    return@launch
                }
                if (
                    reminders.isNotEmpty() &&
                    !SchoolReminderAlarms.canScheduleExactAlarms(activity)
                ) {
                    if (showPermissionMessage) {
                        showExactAlarmSettings()
                    }
                    return@launch
                }
                reminders.forEach { reminder ->
                    SchoolReminderAlarms.schedule(activity, reminder)
                }
            } catch (error: IOException) {
                showReminderMessage(error.message ?: "No se pudieron restaurar las alarmas.")
            } catch (error: SQLiteException) {
                showReminderMessage(error.message ?: "No se pudieron leer los recordatorios guardados.")
            } catch (error: SecurityException) {
                showReminderMessage(error.message ?: "Android no permitió restaurar las alarmas.")
            }
        }
    }

    fun editHistoryReminder(
        reminder: SchoolReminder,
        message: String,
        scheduledAtMillis: Long,
    ) {
        val normalizedMessage = message.trim()
        if (normalizedMessage.isEmpty()) {
            showReminderMessage("Escribe qué debes recordar.")
            return
        }
        if (scheduledAtMillis <= System.currentTimeMillis()) {
            showReminderMessage("Elige una fecha y hora futuras.")
            return
        }
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            showNotificationSettings()
            return
        }
        if (!NotificationManagerCompat.from(activity).areNotificationsEnabled()) {
            showNotificationSettings()
            return
        }
        if (!SchoolReminderAlarms.canScheduleExactAlarms(activity)) {
            showHistoryExactAlarmSettings()
            return
        }

        activity.lifecycleScope.launch {
            var updatedReminder: SchoolReminder? = null
            try {
                SchoolReminderAlarms.cancel(activity, reminder.id)
                updatedReminder = schoolActivityRepository.updateReminder(
                    reminderId = reminder.id,
                    message = normalizedMessage,
                    scheduledAtMillis = scheduledAtMillis,
                )
                SchoolReminderAlarms.createNotificationChannel(activity)
                SchoolReminderAlarms.schedule(activity, updatedReminder)
            } catch (error: IllegalArgumentException) {
                showHistoryReminderEditFailure(
                    reminder,
                    updatedReminder,
                    error.message ?: "No se pudo actualizar la alarma.",
                )
                return@launch
            } catch (error: IllegalStateException) {
                showHistoryReminderEditFailure(
                    reminder,
                    updatedReminder,
                    error.message ?: "No se pudo actualizar la alarma.",
                )
                return@launch
            } catch (error: IOException) {
                showHistoryReminderEditFailure(
                    reminder,
                    updatedReminder,
                    error.message ?: "No se pudo guardar la alarma.",
                )
                return@launch
            } catch (error: SQLiteException) {
                showHistoryReminderEditFailure(
                    reminder,
                    updatedReminder,
                    error.message ?: "No se pudo guardar la alarma.",
                )
                return@launch
            } catch (error: SecurityException) {
                showHistoryReminderEditFailure(
                    reminder,
                    updatedReminder,
                    error.message ?: "Android no permitió programar la alarma.",
                )
                return@launch
            }

            activityController.refreshActivityHistory()
            viewModel.state.value.selectedSchool
                ?.takeIf { it.activityKey() == reminder.schoolKey }
                ?.let { activityController.refreshSchoolActivity(it, viewModel.state.value.commentSearchQuery) }
            showReminderMessage("Alarma reactivada con la nueva fecha.")
        }
    }

    private suspend fun showHistoryReminderEditFailure(
        originalReminder: SchoolReminder,
        updatedReminder: SchoolReminder?,
        message: String,
    ) {
        val rollbackError = rollbackHistoryReminderEdit(originalReminder, updatedReminder)
        activityController.refreshActivityHistory()
        showReminderMessage(
            if (rollbackError == null) {
                message
            } else {
                "$message No se pudo restaurar correctamente la alarma anterior: $rollbackError"
            },
        )
    }

    private suspend fun rollbackHistoryReminderEdit(
        originalReminder: SchoolReminder,
        updatedReminder: SchoolReminder?,
    ): String? {
        var oldRecordRestored = updatedReminder == null
        var rollbackError: String? = null
        if (updatedReminder != null) {
            try {
                schoolActivityRepository.restoreReminder(originalReminder)
                oldRecordRestored = true
            } catch (error: IOException) {
                rollbackError = error.message ?: "Error de lectura o escritura."
            } catch (error: SQLiteException) {
                rollbackError = error.message ?: "Error de base de datos."
            } catch (error: IllegalStateException) {
                rollbackError = error.message ?: "El registro anterior ya no está disponible."
            }
        }
        SchoolReminderAlarms.cancel(activity, originalReminder.id)
        if (
            oldRecordRestored &&
            originalReminder.status == SchoolReminder.STATUS_PENDING &&
            originalReminder.scheduledAtMillis > System.currentTimeMillis()
        ) {
            try {
                SchoolReminderAlarms.schedule(activity, originalReminder)
            } catch (error: SecurityException) {
                rollbackError = error.message ?: "No se pudo reprogramar la alarma anterior."
            }
        }
        return rollbackError
    }

    private fun showHistoryExactAlarmSettings() {
        ReminderDialogs.showExactAlarmSettings(
            context = activity,
            message = "EduEscale necesita acceso a “Alarmas y recordatorios” para reactivar esta alarma.",
            openSettings = { activity.startActivity(it) },
            showMessage = showReminderMessage,
        )
    }

    fun createSchoolReminder(school: SchoolRecord, message: String) {
        val normalizedMessage = message.trim()
        if (normalizedMessage.isEmpty()) {
            showReminderMessage("Escribe qué debes recordar.")
            return
        }

        pickReminderDateTime(System.currentTimeMillis()) { scheduledAt ->
            prepareReminder(
                PendingReminder(
                    schoolKey = school.activityKey(),
                    schoolName = school.nombre?.takeIf(String::isNotBlank)
                        ?: "Colegio sin nombre",
                    message = normalizedMessage,
                    scheduledAtMillis = scheduledAt,
                ),
            )
        }
    }

    fun pickReminderDateTime(
        initialScheduledAtMillis: Long,
        onSelected: (Long) -> Unit,
    ) {
        ReminderDialogs.pickDateTime(
            context = activity,
            initialScheduledAtMillis = initialScheduledAtMillis,
            showMessage = showReminderMessage,
            onSelected = onSelected,
        )
    }

    private fun prepareReminder(reminder: PendingReminder) {
        pendingReminder = reminder
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            AlertDialog.Builder(activity)
                .setTitle("Permitir notificaciones")
                .setMessage(
                    "EduEscale necesita mostrar una notificación cuando llegue la hora del recordatorio.",
                )
                .setPositiveButton("Continuar") { _, _ ->
                    requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                .setNegativeButton("Ahora no") { _, _ -> pendingReminder = null }
                .show()
            return
        }
        if (!NotificationManagerCompat.from(activity).areNotificationsEnabled()) {
            pendingReminder = null
            showNotificationSettings()
            return
        }
        requestExactAlarmPermission(reminder)
    }

    private fun requestExactAlarmPermission(reminder: PendingReminder) {
        if (!NotificationManagerCompat.from(activity).areNotificationsEnabled()) {
            pendingReminder = null
            showNotificationSettings()
            return
        }
        if (!SchoolReminderAlarms.canScheduleExactAlarms(activity)) {
            showExactAlarmSettings()
            return
        }
        saveAndScheduleReminder(reminder)
    }

    private fun saveAndScheduleReminder(pending: PendingReminder) {
        if (!NotificationManagerCompat.from(activity).areNotificationsEnabled()) {
            pendingReminder = null
            showNotificationSettings()
            return
        }
        activity.lifecycleScope.launch {
            val reminder = try {
                schoolActivityRepository.addReminder(
                    schoolKey = pending.schoolKey,
                    schoolName = pending.schoolName,
                    message = pending.message,
                    scheduledAtMillis = pending.scheduledAtMillis,
                )
            } catch (error: IllegalArgumentException) {
                pendingReminder = null
                showReminderMessage(error.message ?: "No se pudo crear el recordatorio.")
                return@launch
            } catch (error: IOException) {
                pendingReminder = null
                showReminderMessage(error.message ?: "No se pudo guardar el recordatorio.")
                return@launch
            } catch (error: SQLiteException) {
                pendingReminder = null
                showReminderMessage(error.message ?: "No se pudo guardar el recordatorio.")
                return@launch
            }

            try {
                SchoolReminderAlarms.createNotificationChannel(activity)
                SchoolReminderAlarms.schedule(activity, reminder)
            } catch (error: SecurityException) {
                try {
                    schoolActivityRepository.deleteReminder(reminder.id)
                } catch (cleanupError: IOException) {
                    error.addSuppressed(cleanupError)
                } catch (cleanupError: SQLiteException) {
                    error.addSuppressed(cleanupError)
                }
                pendingReminder = null
                showReminderMessage(error.message ?: "Android no permitió programar la alarma.")
                return@launch
            }

            pendingReminder = null
            viewModel.state.value.selectedSchool
                ?.takeIf { it.activityKey() == pending.schoolKey }
                ?.let { activityController.refreshSchoolActivity(it, viewModel.state.value.commentSearchQuery) }
            showReminderMessage("Alarma programada.")
        }
    }

    fun deleteSchoolReminder(school: SchoolRecord, reminderId: String) {
        activity.lifecycleScope.launch {
            try {
                schoolActivityRepository.cancelReminder(reminderId)
                SchoolReminderAlarms.cancel(activity, reminderId)
                viewModel.state.value.selectedSchool
                    ?.takeIf { it.activityKey() == school.activityKey() }
                    ?.let { activityController.refreshSchoolActivity(it, viewModel.state.value.commentSearchQuery) }
                showReminderMessage("Alarma cancelada y conservada en el historial.")
            } catch (error: IOException) {
                showReminderMessage(error.message ?: "No se pudo cancelar el recordatorio.")
            } catch (error: SQLiteException) {
                showReminderMessage(error.message ?: "No se pudo cancelar el recordatorio.")
            }
        }
    }

    private fun showNotificationSettings() {
        ReminderDialogs.showNotificationSettings(activity, showReminderMessage)
    }

    private fun showExactAlarmSettings() {
        ReminderDialogs.showExactAlarmSettings(
            context = activity,
            message = "EduEscale necesita acceso a “Alarmas y recordatorios” para avisarte a la hora elegida, incluso si la app está cerrada.",
            openSettings = { requestExactAlarmSettings.launch(it) },
            showMessage = showReminderMessage,
            onCancelled = { pendingReminder = null },
        )
    }
}
