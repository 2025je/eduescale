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

    @Test
    fun managementCategoriesHaveDistinctMarkerColors() {
        assertEquals(
            SchoolMarkerStyle.PUBLIC_COLOR,
            SchoolMarkerStyle.color("publica", setOf("A1")),
        )
        assertEquals(
            SchoolMarkerStyle.PARISH_COLOR,
            SchoolMarkerStyle.color("publica", setOf("A4")),
        )
        assertEquals(
            SchoolMarkerStyle.MIXED_COLOR,
            SchoolMarkerStyle.color("publica", setOf("A1", "A4")),
        )
        assertEquals(
            SchoolMarkerStyle.PRIVATE_COLOR,
            SchoolMarkerStyle.color("privada", emptySet()),
        )
    }

    @Test
    fun levelGlyphsIncludeEveryOfferedLevelInStableOrder() {
        assertEquals(
            "I · P · S · BA · EE · SP · SA · ST · C",
            SchoolMarkerStyle.levelGlyphs(
                listOf(
                    "cetpro",
                    "superior_tecnologica",
                    "superior_artistica",
                    "superior_pedagogica",
                    "educacion_especial",
                    "basica_alternativa",
                    "secundaria",
                    "primaria",
                    "inicial",
                ),
            ),
        )
    }
}
