package pe.eduescale.mobile.ui.schools

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.eduescale.mobile.data.SavedSearch
import pe.eduescale.mobile.data.SavedSearchJson
import pe.eduescale.mobile.data.SchoolFilters
import pe.eduescale.mobile.data.SavedSearchFile

@OptIn(ExperimentalCoroutinesApi::class)
class SavedSearchViewModelTest {
    @Test
    fun savingSameNameReplacesThePreviousSearch() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val store = FakeStore()
            val viewModel = SavedSearchViewModel(store, FakeFile())
            viewModel.saveSearch("Lima", SchoolFilters(departamento = "Lima"))
            viewModel.saveSearch("lima", SchoolFilters(departamento = "Cusco"))
            advanceUntilIdle()

            assertEquals(1, store.searches.size)
            assertEquals("Cusco", viewModel.state.value.searches.single().department)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun failureDoesNotReplaceDisplayedSearches() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val store = FakeStore()
            val viewModel = SavedSearchViewModel(store, FakeFile())
            viewModel.saveSearch("Primera", SchoolFilters())
            advanceUntilIdle()
            store.failSave = true
            viewModel.saveSearch("Segunda", SchoolFilters())
            advanceUntilIdle()

            assertEquals(listOf("Primera"), viewModel.state.value.searches.map { it.name })
            assertTrue(viewModel.state.value.messageIsError)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun invalidSavedDataIsReportedWithoutOverwritingIt() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val store = FakeStore()
            store.failLoad = true
            val viewModel = SavedSearchViewModel(store, FakeFile())
            advanceUntilIdle()

            assertTrue(viewModel.state.value.messageIsError)
            assertEquals(0, store.saveCount)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun failedFileImportPreservesSavedSearches() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val store = FakeStore()
            val file = FakeFile().apply { failRead = true }
            val viewModel = SavedSearchViewModel(store, file)
            viewModel.saveSearch("Primera", SchoolFilters())
            advanceUntilIdle()
            viewModel.importFile("content://searches")
            advanceUntilIdle()

            assertEquals(listOf("Primera"), store.searches.map { it.name })
            assertEquals(listOf("Primera"), viewModel.state.value.searches.map { it.name })
            assertTrue(viewModel.state.value.messageIsError)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class FakeFile : SavedSearchFile {
        var failRead = false
        override suspend fun read(uri: String): List<SavedSearch> {
            if (failRead) throw IOException("Error de lectura")
            return emptyList()
        }
        override suspend fun write(uri: String, searches: List<SavedSearch>) = Unit
    }

    private class FakeStore : SavedSearchStore {
        var searches: List<SavedSearch> = emptyList()
        var failLoad = false
        var failSave = false
        var saveCount = 0

        override suspend fun load(): List<SavedSearch> {
            if (failLoad) throw IllegalArgumentException("Datos inválidos")
            return searches
        }

        override suspend fun save(searches: List<SavedSearch>) {
            if (failSave) throw IOException("Error de escritura")
            SavedSearchJson.encode(searches)
            saveCount++
            this.searches = searches
        }
    }
}
