package pe.eduescale.mobile.map

import org.junit.Assert.assertEquals
import org.junit.Test

class SchoolMarkerStyleTest {
    @Test
    fun cetproUsesTheLargestMarkerRadius() {
        assertEquals(
            SchoolMarkerStyle.CETPRO_RADIUS,
            SchoolMarkerStyle.radius(listOf("cetpro")),
            0.0,
        )
    }

    @Test
    fun cetproTakesPriorityWhenAPlaceOffersMultipleLevels() {
        assertEquals(
            SchoolMarkerStyle.CETPRO_RADIUS,
            SchoolMarkerStyle.radius(listOf("primaria", "cetpro")),
            0.0,
        )
    }
}
