package pe.eduescale.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineTileEstimatorTest {
    @Test
    fun countsVectorAndReliefTilesAcrossSelectedZooms() {
        assertEquals(
            10L,
            OfflineTileEstimator.estimate(
                west = -180.0,
                south = -85.05112878,
                east = 180.0,
                north = 85.05112878,
                minZoom = 0,
                maxZoom = 1,
            ),
        )
    }

    @Test
    fun estimatesOnlyTheSelectedRegionAndZooms() {
        val count = OfflineTileEstimator.estimate(
            west = -77.1,
            south = -12.1,
            east = -77.0,
            north = -12.0,
            minZoom = 12,
            maxZoom = 14,
        )

        assertTrue(count > 0)
        assertTrue(count < OfflineTileEstimator.MAX_TILE_COUNT)
    }
}
