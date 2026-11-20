package pe.eduescale.mobile.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pe.eduescale.mobile.data.SchoolFile
import pe.eduescale.mobile.data.SchoolFilters
import pe.eduescale.mobile.data.SchoolRecord

@OptIn(ExperimentalCoroutinesApi::class)
class EduEscaleViewModelTest {
    @Test
    fun searchesImportedRecordsIgnoringAccents() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val viewModel = EduEscaleViewModel(StandardTestDispatcher(testScheduler))
            val file = SchoolFile(
                totalCount = 2,
                withoutCoordinates = 0,
                schools = listOf(
                    school(1, "Institución Educativa San Martín"),
                    school(2, "Colegio Central"),
                ),
            )

            viewModel.setSchoolFile(file, "colegios.eduescale")
            viewModel.setQuery("san martin")
            advanceUntilIdle()

            assertEquals(listOf(1), viewModel.state.value.visibleSchools.map(SchoolRecord::id))
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun keepsSchoolsWithoutCoordinatesVisibleInTheSchoolsList() {
        val viewModel = EduEscaleViewModel()
        val locatedSchool = school(1, "Con coordenadas")
        val schoolWithoutCoordinates = school(2, "Sin coordenadas").copy(
            longitud = null,
            latitud = null,
        )
        val file = SchoolFile(
            totalCount = 2,
            withoutCoordinates = 1,
            schools = listOf(locatedSchool),
            schoolsWithoutCoordinates = listOf(schoolWithoutCoordinates),
        )

        viewModel.setSchoolFile(file, "colegios.eduescale")

        assertEquals(
            listOf(1, 2),
            viewModel.state.value.visibleSchools.map(SchoolRecord::id),
        )
    }

    @Test
    fun tracksLocationServicesAvailability() {
        val viewModel = EduEscaleViewModel()

        assertNull(viewModel.state.value.locationServicesEnabled)

        viewModel.setLocationServicesEnabled(false)
        assertEquals(false, viewModel.state.value.locationServicesEnabled)

        viewModel.setLocationServicesEnabled(true)
        assertEquals(true, viewModel.state.value.locationServicesEnabled)
    }

    @Test
    fun sortsOnlyTheListAndUpdatesItWhenLocationOrFiltersChange() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val viewModel = EduEscaleViewModel(StandardTestDispatcher(testScheduler))
            val far = school(1, "Lejos").copy(latitud = -13.0)
            val near = school(2, "Cerca")
            val unlocated = school(3, "Sin coordenadas").copy(latitud = null, longitud = null)
            viewModel.setSchoolFile(SchoolFile(3, 1, listOf(far, near), listOf(unlocated)), "test")
            viewModel.setUserLocation(-12.0, -77.0)
            advanceUntilIdle()
            assertEquals(listOf(1, 2, 3), viewModel.state.value.visibleSchools.map { it.id })
            assertEquals(listOf(2, 1, 3), viewModel.state.value.schoolsByDistance.map { it.id })

            viewModel.setQuery("lejos")
            advanceUntilIdle()
            assertEquals(listOf(1), viewModel.state.value.schoolsByDistance.map { it.id })
            viewModel.setQuery("")
            viewModel.setUserLocation(-13.0, -77.0)
            advanceUntilIdle()
            assertEquals(listOf(1, 2, 3), viewModel.state.value.schoolsByDistance.map { it.id })
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun filterDraftIsSeparateFromAppliedFiltersAndProvidesDependentOptions() {
        val viewModel = EduEscaleViewModel()
        val lima = school(1, "Lima").copy(provincia = "Lima", distrito = "Miraflores")
        val cusco = school(2, "Cusco").copy(departamento = "Cusco", provincia = "Cusco", distrito = "Wanchaq")
        viewModel.setSchoolFile(SchoolFile(2, 0, listOf(lima, cusco)), "test")
        viewModel.openFilters()
        viewModel.updateFilterDraft(SchoolFilters(departamento = "Cusco"))

        assertEquals(SchoolFilters(), viewModel.state.value.filters)
        assertEquals(listOf("Cusco"), viewModel.state.value.filterOptions.provinces)
        assertEquals(listOf("Wanchaq"), viewModel.state.value.filterOptions.districts)
        viewModel.updateFilterDraft(SchoolFilters(departamento = "Cusco", provincia = "Cusco"))
        assertEquals(listOf("Wanchaq"), viewModel.state.value.filterOptions.districts)
        viewModel.dismissFilters()
        viewModel.openFilters()
        assertEquals(SchoolFilters(), viewModel.state.value.filterDraft)
    }

    private fun school(id: Int, name: String) = SchoolRecord(
        id = id,
        departamento = "Lima",
        provincia = "Lima",
        distrito = "Lima",
        nombre = name,
        direccion = null,
        localidad = null,
        cp = null,
        turno = null,
        alumnos = null,
        secciones = null,
        gestion = "publica",
        codigoLocal = null,
        niveles = listOf("primaria"),
        longitud = -77.0,
        latitud = -12.0,
    )
}
