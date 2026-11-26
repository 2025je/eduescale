package pe.eduescale.mobile.activity

import androidx.lifecycle.SavedStateHandle
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import pe.eduescale.mobile.data.SchoolRecord
import pe.eduescale.mobile.data.SchoolFile
import pe.eduescale.mobile.data.ImportedSchoolFile
import pe.eduescale.mobile.data.activityKey
import pe.eduescale.mobile.ui.EduEscaleViewModel
import pe.eduescale.mobile.ui.files.SchoolFileImportEvent
import pe.eduescale.mobile.ui.history.ActivityHistoryFilter
import pe.eduescale.mobile.ui.map.SchoolMapCoordinator
import pe.eduescale.mobile.ui.map.SchoolMapPort

@OptIn(ExperimentalCoroutinesApi::class)
class SchoolActivityViewModelTest {
    @Test
    fun selectionDoesNotAcceptAnOlderSchoolsResult() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val first = school(1)
            val second = school(2)
            val gate = CompletableDeferred<Unit>()
            val store = FakeStore()
            store.loadComments = { key, _ ->
                if (key == first.activityKey()) {
                    withContext(NonCancellable) { gate.await() }
                }
                listOf(SchoolComment(key, key, key, 1))
            }
            val viewModel = SchoolActivityViewModel(store)
            viewModel.selectSchool(first)
            runCurrent()
            viewModel.selectSchool(second)
            runCurrent()
            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(second.activityKey(), viewModel.state.value.selectedSchoolKey)
            assertEquals(listOf(second.activityKey()), viewModel.state.value.schoolComments.map { it.schoolKey })
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun commentsAreReloadedUsingTheCurrentSearch() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val school = school(1)
            val store = FakeStore()
            val viewModel = SchoolActivityViewModel(store)
            viewModel.selectSchool(school)
            advanceUntilIdle()
            viewModel.searchComments(school, "nota")
            advanceUntilIdle()
            viewModel.addComment(school, "nota nueva")
            advanceUntilIdle()

            assertEquals("nota", store.lastQuery)
            assertEquals(listOf("nota nueva"), viewModel.state.value.schoolComments.map { it.text })
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun historyFailureIsExposedAndLoadingEnds() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val store = FakeStore()
            store.historyError = ActivityStorageException(IOException("database failure"))
            val viewModel = SchoolActivityViewModel(store)
            viewModel.refreshActivityHistory()
            advanceUntilIdle()

            assertEquals("No se pudo acceder al historial guardado.", viewModel.state.value.activityHistoryError)
            assertFalse(viewModel.state.value.isLoadingActivityHistory)
        } finally {
            Dispatchers.resetMain()
        }

    }

    @Test
    fun historyUsesSchoolNamesAndFiltersInTheViewModel() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val school = school(1)
            val store = FakeStore()
            store.comments.add(SchoolComment("comment", school.activityKey(), "Visitar", 1))
            store.reminders.add(
                SchoolReminder("alarm", school.activityKey(), "Otro colegio", "Llamar", Long.MAX_VALUE),
            )
            val viewModel = SchoolActivityViewModel(store)
            viewModel.setHistorySchools(SchoolFile(1, 0, listOf(school), formatVersion = 1))
            viewModel.refreshActivityHistory()
            advanceUntilIdle()

            assertEquals(listOf("alarm", "comment"), viewModel.state.value.historyEntries.map {
                it.reminder?.id ?: it.comment?.id
            })
            viewModel.setHistoryQuery(" COLEGIO 1 ")
            assertEquals(listOf("comment"), viewModel.state.value.historyEntries.map { it.comment?.id })
            viewModel.setHistoryQuery("")
            viewModel.setHistoryFilter(ActivityHistoryFilter.PENDING_REMINDERS)
            assertEquals(listOf("alarm"), viewModel.state.value.historyEntries.map { it.reminder?.id })
            viewModel.setHistoryFilter(ActivityHistoryFilter.NON_PENDING_REMINDERS)
            assertEquals(emptyList<String>(), viewModel.state.value.historyEntries.map { it.id })
        } finally {
            Dispatchers.resetMain()
        }

    }

    @Test
    fun historySearchAndFilterAreRestoredFromSavedState() {
        val savedState = SavedStateHandle()
        val first = SchoolActivityViewModel(FakeStore(), savedState)
        first.setHistoryQuery("Lima")
        first.setHistoryFilter(ActivityHistoryFilter.COMMENTS)
        val restored = SchoolActivityViewModel(FakeStore(), savedState)
        assertEquals("Lima", restored.state.value.historyQuery)
        assertEquals(ActivityHistoryFilter.COMMENTS, restored.state.value.historyFilter)
    }

    @Test
    fun mapCoordinatorUpdatesOnlyForMapChangesAndFocusesUserImports() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val school = school(1)
            val file = SchoolFile(1, 0, listOf(school), formatVersion = 1)
            val schools = EduEscaleViewModel(StandardTestDispatcher(testScheduler))
            val activities = SchoolActivityViewModel(FakeStore())
            val map = FakeMap()
            val coordinator = SchoolMapCoordinator(schools, activities, map)
            coordinator.fileLoaded(SchoolFileImportEvent.Loaded(ImportedSchoolFile(file, "file"), true))
            coordinator.synchronize(schools.state.value)
            coordinator.synchronize(schools.state.value.copy(locationServicesEnabled = true))
            assertEquals(1, map.updates)
            assertEquals(1, map.fileFocuses)
            coordinator.selectSchool(school, focusMap = true)
            coordinator.synchronize(schools.state.value)
            assertEquals(school.activityKey(), activities.state.value.selectedSchoolKey)
            assertEquals(school, map.focusedSchool)
            assertEquals(2, map.updates)
            coordinator.clearSelection()
            coordinator.synchronize(schools.state.value)
            assertEquals(null, activities.state.value.selectedSchoolKey)
            assertEquals(1, map.clears)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class FakeMap : SchoolMapPort {
        var updates = 0
        var fileFocuses = 0
        var clears = 0
        var focusedSchool: SchoolRecord? = null
        override fun onSchoolFileLoaded(file: SchoolFile?, schools: List<SchoolRecord>) = Unit
        override fun updateMapData() { updates++ }
        override fun focusOnNextFile() { fileFocuses++ }
        override fun focusSelectedSchoolFromList(school: SchoolRecord) { focusedSchool = school }
        override fun clearSelectionState() { clears++ }
    }

    private class FakeStore : SchoolActivityStore {
        val comments = mutableListOf<SchoolComment>()
        val reminders = mutableListOf<SchoolReminder>()
        var lastQuery = ""
        var historyError: IOException? = null
        var loadComments: suspend (String, String) -> List<SchoolComment> = { key, query ->
            lastQuery = query
            comments.filter { it.schoolKey == key && it.text.contains(query) }
        }

        override suspend fun commentsFor(schoolKey: String, query: String) = loadComments(schoolKey, query)
        override suspend fun addComment(schoolKey: String, text: String): List<SchoolComment> {
            comments.add(SchoolComment("id", schoolKey, text, 1))
            return comments
        }
        override suspend fun deleteComment(schoolKey: String, commentId: String) {
            comments.removeAll { it.schoolKey == schoolKey && it.id == commentId }
        }
        override suspend fun updateComment(schoolKey: String, commentId: String, text: String) = Unit
        override suspend fun remindersFor(schoolKey: String): List<SchoolReminder> = emptyList()
        override suspend fun allComments(): List<SchoolComment> {
            historyError?.let { throw it }
            return comments
        }
        override suspend fun allReminders(): List<SchoolReminder> = reminders
        override suspend fun expirePastReminders(nowMillis: Long) = Unit
    }

    private fun school(id: Int) = SchoolRecord(
        id = id,
        departamento = "Lima",
        provincia = "Lima",
        distrito = "Lima",
        nombre = "Colegio $id",
        direccion = null,
        localidad = null,
        cp = null,
        turno = null,
        alumnos = null,
        secciones = null,
        gestion = "publica",
        codigoLocal = id.toString(),
        niveles = listOf("primaria"),
        longitud = -77.0,
        latitud = -12.0,
    )
}
