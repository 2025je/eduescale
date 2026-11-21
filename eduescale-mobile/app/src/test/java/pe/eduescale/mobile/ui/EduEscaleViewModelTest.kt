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
    fun disablingLocationClearsTheOriginUsedForRoutes() {
        val viewModel = EduEscaleViewModel()
        viewModel.setLocationServicesEnabled(true)
        viewModel.setUserLocation(-12.0, -77.0)
        viewModel.setLocationServicesEnabled(false)
        assertNull(viewModel.state.value.userLocation)
        viewModel.setLocationServicesEnabled(true)
        assertNull(viewModel.state.value.userLocation)
    }

    @Test
    fun closingDetailsAndFilteringKeepRouteDestinationUntilAnotherSchoolIsSelected() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val viewModel = EduEscaleViewModel(StandardTestDispatcher(testScheduler))
            val first = school(1, "Primero")
            val second = school(2, "Segundo")
            viewModel.setSchoolFile(SchoolFile(2, 0, listOf(first, second)), "test")
            viewModel.selectSchool(first)
            viewModel.selectSchool(null)
            assertNull(viewModel.state.value.selectedSchool)
            assertEquals(first, viewModel.state.value.routeSchool)

            viewModel.setQuery("Segundo")
            advanceUntilIdle()
            assertEquals(first, viewModel.state.value.routeSchool)
            viewModel.selectSchool(second)
            assertEquals(second, viewModel.state.value.routeSchool)
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
