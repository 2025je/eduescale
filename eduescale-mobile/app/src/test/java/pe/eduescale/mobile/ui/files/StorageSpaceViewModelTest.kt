package pe.eduescale.mobile.ui.files

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Test
import pe.eduescale.mobile.data.StorageSpace

@OptIn(ExperimentalCoroutinesApi::class)
class StorageSpaceViewModelTest {
    @Test
    fun exposesAvailableSpaceAndDoesNotMaskErrors() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val storage = FakeStorage()
            val viewModel = StorageSpaceViewModel(storage)
            viewModel.refresh()
            advanceUntilIdle()
            assertEquals(123L, viewModel.state.value.availableBytes)
            storage.fail = true
            viewModel.refresh()
            advanceUntilIdle()
            assertEquals(null, viewModel.state.value.availableBytes)
            assertEquals("Error de almacenamiento", viewModel.state.value.error)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class FakeStorage : StorageSpace {
        var fail = false
        override suspend fun availableBytes(): Long {
            if (fail) throw IOException("Error de almacenamiento")
            return 123
        }
    }
}
