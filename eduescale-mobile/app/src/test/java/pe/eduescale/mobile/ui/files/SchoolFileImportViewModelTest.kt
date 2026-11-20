package pe.eduescale.mobile.ui.files

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import pe.eduescale.mobile.data.ImportedSchoolFile
import pe.eduescale.mobile.data.SchoolFile
import pe.eduescale.mobile.data.SchoolFileImportStore
import pe.eduescale.mobile.data.EduEscalePasswordRequiredException

@OptIn(ExperimentalCoroutinesApi::class)
class SchoolFileImportViewModelTest {
    @Test
    fun restorationIsPerformedOnlyOnceAndDeliversTheFile() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val store = FakeStore()
            val viewModel = SchoolFileImportViewModel(store)
            viewModel.restore()
            viewModel.restore()
            advanceUntilIdle()

            assertEquals(1, store.restoreCount)
            assertEquals(
                SchoolFileImportEvent.Loaded(store.file, false),
                viewModel.events.first(),
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun restorationFailureIsVisible() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val store = FakeStore()
            store.failRestore = true
            val viewModel = SchoolFileImportViewModel(store)
            viewModel.restore()
            advanceUntilIdle()

            assertEquals("Error de lectura", viewModel.state.value.error)
            assertFalse(viewModel.state.value.isImporting)
        } finally {
            Dispatchers.resetMain()
        }

        @Test
        fun passwordRequestSurvivesUntilHandledAndImportCanResume() = runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val store = FakeStore().apply { requirePassword = true }
                val viewModel = SchoolFileImportViewModel(store)
                viewModel.importFile("content://school")
                advanceUntilIdle()
                assertEquals("content://school", viewModel.state.value.passwordRequiredUri)
                viewModel.clearPasswordRequest()
                viewModel.importFile("content://school", "password")
                advanceUntilIdle()
                assertEquals(null, viewModel.state.value.passwordRequiredUri)
                assertEquals(SchoolFileImportEvent.Loaded(store.file, true), viewModel.events.first())
            } finally {
                Dispatchers.resetMain()
            }
        }
    }

    private class FakeStore : SchoolFileImportStore {
        val file = ImportedSchoolFile(SchoolFile(0, 0, emptyList()), "datos.eduescale")
        var restoreCount = 0
        var failRestore = false
        var requirePassword = false

        override suspend fun importFile(uri: String, password: String?): ImportedSchoolFile {
            if (requirePassword && password == null) throw EduEscalePasswordRequiredException()
            return file
        }

        override suspend fun restore(): ImportedSchoolFile {
            restoreCount++
            if (failRestore) throw IOException("Error de lectura")
            return file
        }
    }
}
