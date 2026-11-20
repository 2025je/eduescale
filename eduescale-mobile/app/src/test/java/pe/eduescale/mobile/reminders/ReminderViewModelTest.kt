package pe.eduescale.mobile.reminders

import java.io.IOException
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.eduescale.mobile.activity.ReminderStore
import pe.eduescale.mobile.data.SchoolRecord
import pe.eduescale.mobile.activity.SchoolReminder

@OptIn(ExperimentalCoroutinesApi::class)
class ReminderViewModelTest {
    @Test
    fun permissionResultSchedulesOnlyOnce() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val store = FakeStore()
            val alarms = FakeAlarms().apply { permission = false }
            val viewModel = ReminderViewModel(store, alarms, SavedStateHandle())
            val events = mutableListOf<ReminderEvent>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.events.collect { events.add(it) }
            }
            viewModel.dateSelected(school(), "mensaje", System.currentTimeMillis() + 100_000)
            assertEquals(ReminderPrompt.NotificationPermission, viewModel.prompt.value)
            viewModel.permissionRequestStarted()
            assertEquals(null, viewModel.prompt.value)
            alarms.permission = true
            viewModel.notificationPermissionResult(true)
            viewModel.notificationPermissionResult(true)
            advanceUntilIdle()
            assertEquals(1, store.added)
            assertEquals(1, alarms.scheduled.size)
            assertTrue(events.any { it is ReminderEvent.Refresh })
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun scheduleFailureRemovesCreatedRecordAndReportsCleanupFailure() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val store = FakeStore().apply { deleteError = true }
            val alarms = FakeAlarms().apply { failSchedule = true }
            val viewModel = ReminderViewModel(store, alarms, SavedStateHandle())
            val events = mutableListOf<ReminderEvent>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.events.collect { events.add(it) }
            }
            viewModel.dateSelected(school(), "mensaje", System.currentTimeMillis() + 100_000)
            advanceUntilIdle()
            assertEquals(1, store.deleted)
            assertTrue(events.filterIsInstance<ReminderEvent.Message>().single().text.contains("No se pudo eliminar el registro"))
            assertTrue(events.any { it is ReminderEvent.Refresh })
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun failedEditRestoresPreviousRecordAndAlarm() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val store = FakeStore()
            val alarms = FakeAlarms().apply { failNextSchedule = true }
            val viewModel = ReminderViewModel(store, alarms, SavedStateHandle())
            val original = store.record
            viewModel.editHistoryReminder(original, "nuevo", System.currentTimeMillis() + 200_000)
            advanceUntilIdle()
            assertEquals(original, store.record)
            assertEquals(listOf(original), alarms.scheduled)
        } finally {
            Dispatchers.resetMain()
        }

    }

    @Test
    fun pendingReminderSurvivesProcessRecreationDuringPermissionRequests() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val store = FakeStore()
            val alarms = FakeAlarms().apply { permission = false; exactPermission = false }
            val initialState = SavedStateHandle()
            val original = ReminderViewModel(store, alarms, initialState)
            original.dateSelected(school(), "mensaje", System.currentTimeMillis() + 100_000)
            val dialogState = copyState(initialState)
            val dialogRestored = ReminderViewModel(store, alarms, dialogState)
            assertEquals(ReminderPrompt.NotificationPermission, dialogRestored.prompt.value)

            dialogRestored.permissionRequestStarted()
            val waitingState = copyState(dialogState)
            val waiting = ReminderViewModel(store, alarms, waitingState)
            assertEquals(null, waiting.prompt.value)
            assertEquals(0, store.added)
            alarms.permission = true
            waiting.notificationPermissionResult(true)
            assertEquals(ReminderPrompt.ExactAlarmPermission, waiting.prompt.value)
            val exactState = copyState(waitingState)
            val exactDialog = ReminderViewModel(store, alarms, exactState)
            assertEquals(ReminderPrompt.ExactAlarmPermission, exactDialog.prompt.value)
            exactDialog.permissionRequestStarted()
            val settingsReturn = ReminderViewModel(store, alarms, copyState(exactState))
            alarms.exactPermission = true
            settingsReturn.exactAlarmPermissionResult()
            settingsReturn.notificationPermissionResult(true)
            advanceUntilIdle()
            assertEquals(1, store.added)
            assertEquals(1, alarms.scheduled.count { it.message == "mensaje" })
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun cancellingRestoredPendingReminderPreventsScheduling() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val store = FakeStore()
            val alarms = FakeAlarms().apply { permission = false }
            val state = SavedStateHandle()
            ReminderViewModel(store, alarms, state)
                .dateSelected(school(), "mensaje", System.currentTimeMillis() + 100_000)
            val restoredState = copyState(state)
            ReminderViewModel(store, alarms, restoredState).cancelPending()
            alarms.permission = true
            ReminderViewModel(store, alarms, copyState(restoredState)).notificationPermissionResult(true)
            advanceUntilIdle()
            assertEquals(0, store.added)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun returningFromExactAlarmSettingsRechecksNotificationPermission() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val store = FakeStore()
            val alarms = FakeAlarms().apply { exactPermission = false }
            val model = ReminderViewModel(store, alarms, SavedStateHandle())
            model.dateSelected(school(), "mensaje", System.currentTimeMillis() + 100_000)
            model.permissionRequestStarted()
            alarms.permission = false
            alarms.exactPermission = true
            model.exactAlarmPermissionResult()
            advanceUntilIdle()
            assertEquals(ReminderPrompt.NotificationPermission, model.prompt.value)
            assertEquals(0, store.added)
            assertTrue(alarms.scheduled.isEmpty())
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun copyState(state: SavedStateHandle): SavedStateHandle =
        SavedStateHandle(state.keys().associateWith { state.get<Any?>(it) })

    private class FakeAlarms : ReminderAlarmPort {
        var permission = true
        var exactPermission = true
        var failSchedule = false
        var failNextSchedule = false
        val scheduled = mutableListOf<SchoolReminder>()
        override fun notificationPermissionGranted() = permission
        override fun notificationsEnabled() = true
        override fun canScheduleExactAlarms() = exactPermission
        override fun schedule(reminder: SchoolReminder) {
            if (failSchedule || failNextSchedule) {
                failNextSchedule = false
                throw SecurityException("sin permiso")
            }
            scheduled.add(reminder)
        }
        override fun cancel(reminderId: String) = Unit
    }

    private class FakeStore : ReminderStore {
        var record = SchoolReminder("id", "key", "nombre", "anterior", System.currentTimeMillis() + 100_000)
        var added = 0
        var deleted = 0
        var deleteError = false
        override suspend fun pendingReminders() = listOf(record)
        override suspend fun cancelReminder(reminderId: String) = Unit
        override suspend fun addReminder(schoolKey: String, schoolName: String, message: String, scheduledAtMillis: Long): SchoolReminder {
            added++
            record = SchoolReminder("id", schoolKey, schoolName, message, scheduledAtMillis)
            return record
        }
        override suspend fun updateReminder(reminderId: String, message: String, scheduledAtMillis: Long): SchoolReminder {
            record = record.copy(message = message, scheduledAtMillis = scheduledAtMillis)
            return record
        }
        override suspend fun restoreReminder(reminder: SchoolReminder) { record = reminder }
        override suspend fun deleteReminder(reminderId: String) {
            deleted++
            if (deleteError) throw IOException("falló")
        }
    }

    private fun school() = SchoolRecord(
        id = 1, departamento = "Lima", provincia = "Lima", distrito = "Lima",
        nombre = "Colegio", direccion = null, localidad = null, cp = null, turno = null,
        alumnos = null, secciones = null, gestion = "publica", codigoLocal = "1",
        niveles = listOf("primaria"), longitud = -77.0, latitud = -12.0,
    )
}
