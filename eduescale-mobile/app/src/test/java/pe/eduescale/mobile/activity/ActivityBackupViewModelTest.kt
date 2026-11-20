package pe.eduescale.mobile.activity

import java.io.IOException
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
import pe.eduescale.mobile.data.ActivityBackupFile

@OptIn(ExperimentalCoroutinesApi::class)
class ActivityBackupViewModelTest {
    @Test
    fun importPublishesRestoredOnlyAfterPersisting() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val store = FakeStore()
            val file = FakeFile()
            val viewModel = ActivityBackupViewModel(store, file)
            val events = mutableListOf<ActivityBackupEvent>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.events.collect { events.add(it) }
            }
            viewModel.import("content://backup")
            advanceUntilIdle()
            assertEquals(file.backup, store.imported)
            assertEquals(ActivityBackupEvent.Restored, events.first())
            assertTrue(events.last() is ActivityBackupEvent.Message)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun failedReadDoesNotImportOrPublishRestored() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val store = FakeStore()
            val file = FakeFile().apply { failRead = true }
            val viewModel = ActivityBackupViewModel(store, file)
            val events = mutableListOf<ActivityBackupEvent>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.events.collect { events.add(it) }
            }
            viewModel.import("content://backup")
            advanceUntilIdle()
            assertEquals(null, store.imported)
            assertEquals(listOf(ActivityBackupEvent.Message("lectura fallida")), events)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class FakeStore : ActivityBackupStore {
        var imported: SchoolActivityBackup? = null
        override suspend fun exportBackup() = SchoolActivityBackup()
        override suspend fun importBackup(backup: SchoolActivityBackup) { imported = backup }
    }

    private class FakeFile : ActivityBackupFile {
        val backup = SchoolActivityBackup()
        var failRead = false
        override suspend fun read(uri: String): SchoolActivityBackup {
            if (failRead) throw IOException("lectura fallida")
            return backup
        }
        override suspend fun write(uri: String, backup: SchoolActivityBackup) = Unit
    }
}
