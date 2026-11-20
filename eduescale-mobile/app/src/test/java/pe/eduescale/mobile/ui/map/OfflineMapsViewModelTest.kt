package pe.eduescale.mobile.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.eduescale.mobile.map.OfflineMapRegion
import pe.eduescale.mobile.map.OfflineMapStatus
import pe.eduescale.mobile.map.OfflineMapStore
import pe.eduescale.mobile.map.OfflineMapBounds

class OfflineMapsViewModelTest {
    @Test
    fun refusesSecondDownloadWhileRegionIsBeingCreated() {
        val store = FakeStore()
        val viewModel = OfflineMapsViewModel(store)
        viewModel.setStyleReady(true)
        viewModel.downloadVisibleMapArea(bounds, 1, 2)
        viewModel.downloadVisibleMapArea(bounds, 1, 2)

        assertEquals(1, store.creations)
        assertTrue(viewModel.state.value.isCreatingOfflineMapArea)
        assertTrue(viewModel.state.value.offlineMapMessageIsError)
        store.finishCreate()
        assertFalse(viewModel.state.value.isCreatingOfflineMapArea)
        assertTrue(viewModel.state.value.offlineMapAreas.single().isDownloading)
    }

    @Test
    fun rejectsOversizedRegionsAndReportsDeleteFailure() {
        val store = FakeStore()
        val viewModel = OfflineMapsViewModel(store)
        viewModel.setStyleReady(true)
        viewModel.downloadVisibleMapArea(bounds, 1, 2)
        store.finishCreate()
        store.deleteFailure = true
        store.status(1, OfflineMapStatus(3001, 10, 0, 10, true, true))

        assertEquals(listOf(1L), store.deleted)
        assertFalse(viewModel.state.value.offlineMapAreas.single().isDownloading)
        assertTrue(viewModel.state.value.offlineMapMessage!!.contains("no se pudo eliminar"))
        store.deleteFailure = false
        store.status(1, OfflineMapStatus(3001, 10, 0, 10, true, true))
        assertTrue(viewModel.state.value.offlineMapAreas.isEmpty())
    }

    @Test
    fun validatesBoundsAndTracksCompletion() {
        val store = FakeStore()
        val viewModel = OfflineMapsViewModel(store)
        viewModel.setStyleReady(true)
        viewModel.downloadVisibleMapArea(bounds.copy(west = -200.0), 1, 2)
        assertEquals(0, store.creations)
        viewModel.downloadVisibleMapArea(bounds, 1, 2)
        store.finishCreate()
        store.status(1, OfflineMapStatus(5, 100, 10, 10, true, true))
        val area = viewModel.state.value.offlineMapAreas.single()
        assertTrue(area.isComplete)
        assertFalse(area.isDownloading)
        assertEquals(5L, area.completedTiles)
    }

    @Test
    fun sizeLimitIsInclusive() {
        val store = FakeStore()
        val viewModel = OfflineMapsViewModel(store)
        viewModel.setStyleReady(true)
        viewModel.downloadVisibleMapArea(bounds, 1, 2)
        store.finishCreate()
        store.status(1, OfflineMapStatus(3000, 50_000_000, 1, 2, true, true))
        assertTrue(store.deleted.isEmpty())
        store.status(1, OfflineMapStatus(3000, 50_000_001, 1, 2, true, true))
        assertEquals(listOf(1L), store.deleted)
    }

    private class FakeStore : OfflineMapStore {
        var creations = 0
        var deleteFailure = false
        val deleted = mutableListOf<Long>()
        lateinit var status: (Long, OfflineMapStatus) -> Unit
        private lateinit var pending: (Result<OfflineMapRegion>) -> Unit
        private lateinit var region: OfflineMapRegion
        override fun listen(onStatus: (Long, OfflineMapStatus) -> Unit, onError: (String) -> Unit) {
            status = onStatus
        }
        override fun list(callback: (Result<List<OfflineMapRegion>>) -> Unit) = callback(Result.success(emptyList()))
        override fun create(bounds: OfflineMapBounds, region: OfflineMapRegion, callback: (Result<OfflineMapRegion>) -> Unit) {
            creations++
            this.region = region.copy(id = 1)
            pending = callback
        }
        fun finishCreate() = pending(Result.success(region))
        override fun setActive(id: Long, active: Boolean, callback: (Result<Unit>) -> Unit) = callback(Result.success(Unit))
        override fun delete(id: Long, callback: (Result<Unit>) -> Unit) {
            deleted.add(id)
            callback(if (deleteFailure) Result.failure(IllegalStateException("falló")) else Result.success(Unit))
        }
        override fun close() = Unit
    }

    private companion object {
        val bounds = OfflineMapBounds(-77.0, -12.1, -76.9, -12.0, 5.0)
    }
}
