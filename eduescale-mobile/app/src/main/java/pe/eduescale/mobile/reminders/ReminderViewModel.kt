package pe.eduescale.mobile.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pe.eduescale.mobile.activity.ReminderStore
import pe.eduescale.mobile.data.SchoolRecord
import pe.eduescale.mobile.activity.SchoolReminder
import pe.eduescale.mobile.data.activityKey

sealed interface ReminderEvent {
    data class Message(val text: String) : ReminderEvent
    data class Refresh(val schoolKey: String?) : ReminderEvent
    data object NotificationSettings : ReminderEvent
    data object HistoryExactAlarmSettings : ReminderEvent
}

sealed interface ReminderPrompt {
    data class PickDate(val school: SchoolRecord, val message: String) : ReminderPrompt
    data object NotificationPermission : ReminderPrompt
    data object ExactAlarmPermission : ReminderPrompt
}

@HiltViewModel
class ReminderViewModel @Inject constructor(
    private val repository: ReminderStore,
    private val alarms: ReminderAlarmPort,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val eventChannel = Channel<ReminderEvent>(Channel.UNLIMITED)
    val events = eventChannel.receiveAsFlow()
    private val mutablePrompt = MutableStateFlow<ReminderPrompt?>(
        when (savedState.get<String>(PROMPT_KEY)) {
            NOTIFICATION_PROMPT -> ReminderPrompt.NotificationPermission
            EXACT_ALARM_PROMPT -> ReminderPrompt.ExactAlarmPermission
            else -> null
        },
    )
    val prompt = mutablePrompt.asStateFlow()
    private val mutex = Mutex()
    private var pending: PendingReminder?
        get() = savedState[PENDING_KEY]
        set(value) { savedState[PENDING_KEY] = value }

    private fun setPrompt(prompt: ReminderPrompt?) {
        savedState[PROMPT_KEY] = when (prompt) {
            ReminderPrompt.NotificationPermission -> NOTIFICATION_PROMPT
            ReminderPrompt.ExactAlarmPermission -> EXACT_ALARM_PROMPT
            else -> null
        }
        mutablePrompt.value = prompt
    }

    fun createSchoolReminder(school: SchoolRecord, message: String) {
        val normalized = message.trim()
        if (normalized.isEmpty()) {
            emit(ReminderEvent.Message("Escribe qué debes recordar."))
        } else {
            pending = null
            setPrompt(ReminderPrompt.PickDate(school, normalized))
        }
    }

    fun dateSelected(school: SchoolRecord, message: String, scheduledAtMillis: Long) {
        setPrompt(null)
        pending = PendingReminder(
            school.activityKey(),
            school.nombre?.takeIf(String::isNotBlank) ?: "Colegio sin nombre",
            message,
            scheduledAtMillis,
        )
        when {
            !alarms.notificationPermissionGranted() -> setPrompt(ReminderPrompt.NotificationPermission)
            !alarms.notificationsEnabled() -> {
                pending = null
                emit(ReminderEvent.NotificationSettings)
            }
            else -> continuePending()
        }
    }

    fun notificationPermissionResult(granted: Boolean) {
        setPrompt(null)
        if (pending == null) return
        if (granted) continuePending() else {
            pending = null
            emit(ReminderEvent.NotificationSettings)
        }
    }

    fun exactAlarmPermissionResult() {
        setPrompt(null)
        if (alarms.canScheduleExactAlarms()) {
            restorePendingReminders()
            if (pending != null) continuePending()
        } else {
            pending = null
            emit(ReminderEvent.Message("No se concedió el permiso para programar alarmas exactas."))
        }
    }

    fun cancelPending() {
        pending = null
        setPrompt(null)
    }

    fun permissionRequestStarted() { setPrompt(null) }

    private fun continuePending() {
        val reminder = pending ?: return
        when {
            !alarms.notificationPermissionGranted() -> setPrompt(ReminderPrompt.NotificationPermission)
            !alarms.notificationsEnabled() -> {
                pending = null
                emit(ReminderEvent.NotificationSettings)
            }
            !alarms.canScheduleExactAlarms() -> setPrompt(ReminderPrompt.ExactAlarmPermission)
            else -> {
                pending = null
                viewModelScope.launch {
                    mutex.withLock { saveAndSchedule(reminder) }
                }
            }
        }
    }

    fun restorePendingReminders(showPermissionMessage: Boolean = false) {
        viewModelScope.launch {
            mutex.withLock {
                try {
                    val reminders = repository.pendingReminders()
                    if (reminders.isNotEmpty() &&
                        (!alarms.notificationPermissionGranted() || !alarms.notificationsEnabled())
                    ) {
                        if (showPermissionMessage) emit(ReminderEvent.NotificationSettings)
                    } else if (reminders.isNotEmpty() && !alarms.canScheduleExactAlarms()) {
                        if (showPermissionMessage) setPrompt(ReminderPrompt.ExactAlarmPermission)
                    } else {
                        reminders.forEach(alarms::schedule)
                    }
                } catch (error: IOException) {
                    message(error.message ?: "No se pudieron restaurar las alarmas.")
                } catch (error: SecurityException) {
                    message(error.message ?: "Android no permitió restaurar las alarmas.")
                }
            }
        }
    }

    private suspend fun saveAndSchedule(pending: PendingReminder) {
        try {
            val reminder = repository.addReminder(
                pending.schoolKey, pending.schoolName, pending.message, pending.scheduledAtMillis,
            )
            try {
                alarms.schedule(reminder)
            } catch (error: SecurityException) {
                cleanupFailedSchedule(reminder, error.message ?: "Android no permitió programar la alarma.")
                return
            } catch (error: IllegalStateException) {
                cleanupFailedSchedule(reminder, error.message ?: "No se pudo programar la alarma.")
                return
            } catch (error: IllegalArgumentException) {
                cleanupFailedSchedule(reminder, error.message ?: "No se pudo programar la alarma.")
                return
            }
            emit(ReminderEvent.Refresh(pending.schoolKey))
            message("Alarma programada.")
        } catch (error: IllegalArgumentException) {
            message(error.message ?: "No se pudo crear el recordatorio.")
        } catch (error: IOException) {
            message(error.message ?: "No se pudo guardar el recordatorio.")
        }
    }

    private suspend fun cleanupFailedSchedule(reminder: SchoolReminder, detail: String) {
        val cleanupError = try {
            repository.deleteReminder(reminder.id)
            null
        } catch (failure: IOException) {
            failure.message ?: "Error de lectura o escritura."
        }
        message(if (cleanupError == null) detail else "$detail No se pudo eliminar el registro: $cleanupError")
        emit(ReminderEvent.Refresh(reminder.schoolKey))
    }

    fun deleteSchoolReminder(school: SchoolRecord, reminderId: String) {
        viewModelScope.launch {
            mutex.withLock {
                try {
                    repository.cancelReminder(reminderId)
                    alarms.cancel(reminderId)
                    emit(ReminderEvent.Refresh(school.activityKey()))
                    message("Alarma cancelada y conservada en el historial.")
                } catch (error: IOException) {
                    message(error.message ?: "No se pudo cancelar el recordatorio.")
                } catch (error: SecurityException) {
                    emit(ReminderEvent.Refresh(school.activityKey()))
                    message(error.message ?: "Android no permitió cancelar la alarma.")
                }
            }
        }
    }

    fun editHistoryReminder(reminder: SchoolReminder, message: String, scheduledAtMillis: Long) {
        val normalized = message.trim()
        when {
            normalized.isEmpty() -> emit(ReminderEvent.Message("Escribe qué debes recordar."))
            scheduledAtMillis <= System.currentTimeMillis() ->
                emit(ReminderEvent.Message("Elige una fecha y hora futuras."))
            !alarms.notificationPermissionGranted() || !alarms.notificationsEnabled() ->
                emit(ReminderEvent.NotificationSettings)
            !alarms.canScheduleExactAlarms() -> emit(ReminderEvent.HistoryExactAlarmSettings)
            else -> viewModelScope.launch {
                mutex.withLock { updateReminder(reminder, normalized, scheduledAtMillis) }
            }
        }
    }

    private suspend fun updateReminder(original: SchoolReminder, message: String, scheduledAt: Long) {
        var updated: SchoolReminder? = null
        try {
            alarms.cancel(original.id)
            updated = repository.updateReminder(original.id, message, scheduledAt)
            alarms.schedule(updated)
            emit(ReminderEvent.Refresh(original.schoolKey))
            message("Alarma reactivada con la nueva fecha.")
        } catch (error: IllegalArgumentException) {
            editFailed(original, updated, error.message ?: "No se pudo actualizar la alarma.")
        } catch (error: IllegalStateException) {
            editFailed(original, updated, error.message ?: "No se pudo actualizar la alarma.")
        } catch (error: IOException) {
            editFailed(original, updated, error.message ?: "No se pudo guardar la alarma.")
        } catch (error: SecurityException) {
            editFailed(original, updated, error.message ?: "Android no permitió programar la alarma.")
        }
    }

    private suspend fun editFailed(original: SchoolReminder, updated: SchoolReminder?, text: String) {
        var restored = updated == null
        var rollbackError: String? = null
        if (updated != null) {
            try {
                repository.restoreReminder(original)
                restored = true
            } catch (error: IOException) {
                rollbackError = error.message ?: "Error de lectura o escritura."
            } catch (error: IllegalStateException) {
                rollbackError = error.message ?: "El registro anterior ya no está disponible."
            }
        }
        try {
            alarms.cancel(original.id)
            if (restored && original.status == SchoolReminder.STATUS_PENDING &&
                original.scheduledAtMillis > System.currentTimeMillis()
            ) alarms.schedule(original)
        } catch (error: SecurityException) {
            rollbackError = listOfNotNull(rollbackError, error.message ?: "No se pudo reprogramar la alarma anterior.")
                .joinToString(" ")
        }
        emit(ReminderEvent.Refresh(original.schoolKey))
        message(if (rollbackError == null) text else "$text No se pudo restaurar correctamente la alarma anterior: $rollbackError")
    }

    private fun message(text: String) = emit(ReminderEvent.Message(text))
    private fun emit(event: ReminderEvent) { eventChannel.trySend(event) }

    private companion object {
        const val PENDING_KEY = "pendingReminder"
        const val PROMPT_KEY = "reminderPrompt"
        const val NOTIFICATION_PROMPT = "notification"
        const val EXACT_ALARM_PROMPT = "exactAlarm"
    }
}
