package pe.eduescale.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SchoolFiltersTest {
    private val schools = listOf(
        school(
            id = 1,
            nombre = "Institución Educativa San Martín",
            departamento = "Lima",
            provincia = "Lima",
            distrito = "Miraflores",
            gestion = "publica",
            niveles = listOf("primaria", "secundaria"),
        ),
        school(
            id = 2,
            nombre = "San Martín Inicial",
            departamento = "Lima",
            provincia = "Huarochirí",
            distrito = "Matucana",
            gestion = "privada",
            niveles = listOf("inicial"),
        ),
        school(
            id = 3,
            nombre = "Colegio Central",
            departamento = "Cusco",
            provincia = "Cusco",
            distrito = "Cusco",
            gestion = "publica",
            niveles = listOf("secundaria"),
        ),
    )

    @Test
    fun searchesNameIgnoringCaseAndAccents() {
        assertEquals(
            listOf(schools[0], schools[1]),
            filterSchools(schools, "san martin", SchoolFilters()),
        )
    }

    @Test
    fun combinesLocationManagementAndLevelFilters() {
        val filters = SchoolFilters(
            departamento = "Lima",
            provincia = "Lima",
            distrito = "Miraflores",
            gestiones = setOf("publica"),
            niveles = setOf("primaria"),
        )

        assertEquals(
            listOf(schools[0]),
            filterSchools(schools, "", filters),
        )
    }

    @Test
    fun levelFilterMatchesAnyLevelAtTheSchool() {
        assertEquals(
            listOf(schools[0], schools[2]),
            filterSchools(
                schools,
                "",
                SchoolFilters(niveles = setOf("secundaria")),
            ),
        )
    }

    @Test
    fun sortsSchoolsFromNearestToFarthestAndKeepsOriginalOrderWithoutLocation() {
        val origin = SchoolCoordinates(latitude = -12.0, longitude = -77.0)
        val nearest = schools[0].copy(latitud = -12.001, longitud = -77.001)
        val middle = schools[2].copy(latitud = -12.1, longitud = -77.0)
        val farthest = schools[1].copy(latitud = -13.0, longitud = -77.0)
        val input = listOf(farthest, nearest, middle)

        assertEquals(
            listOf(nearest, middle, farthest),
            sortSchoolsByDistance(input, origin),
        )
        val nearestDistance = distanceInMeters(nearest, origin)
        assertTrue(nearestDistance != null && nearestDistance in 100.0..200.0)
        assertEquals(input, sortSchoolsByDistance(input, null))
    }

    @Test
    fun sortsSchoolsWithoutCoordinatesAfterLocatedSchools() {
        val nearby = schools[0]
        val withoutCoordinates = schools[1].copy(longitud = null, latitud = null)

        assertEquals(
            listOf(nearby, withoutCoordinates),
            sortSchoolsByDistance(
                listOf(withoutCoordinates, nearby),
                SchoolCoordinates(latitude = -12.0, longitude = -77.0),
            ),
        )
        assertEquals(null, distanceInMeters(withoutCoordinates, SchoolCoordinates(-12.0, -77.0)))
    }

    private fun school(
        id: Int,
        nombre: String,
        departamento: String,
        provincia: String,
        distrito: String,
        gestion: String,
        niveles: List<String>,
    ) = SchoolRecord(
        id = id,
        departamento = departamento,
        provincia = provincia,
        distrito = distrito,
        nombre = nombre,
        direccion = null,
        localidad = null,
        cp = null,
        turno = null,
        alumnos = null,
        secciones = null,
        gestion = gestion,
        codigoLocal = null,
        niveles = niveles,
        longitud = -77.0,
        latitud = -12.0,
    )
}
