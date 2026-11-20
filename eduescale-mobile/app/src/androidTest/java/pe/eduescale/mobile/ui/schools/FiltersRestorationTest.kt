package pe.eduescale.mobile.ui.schools

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import pe.eduescale.mobile.data.SchoolFile
import pe.eduescale.mobile.data.SchoolFilters
import pe.eduescale.mobile.data.SchoolRecord
import pe.eduescale.mobile.ui.EduEscaleViewModel
import pe.eduescale.mobile.routing.RoadRoutingState

@OptIn(ExperimentalTestApi::class)
class FiltersRestorationTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun draftAndDialogSurviveRecreationUntilApplied() {
        val model = EduEscaleViewModel()
        val school = SchoolRecord(
            id = 1, departamento = "Cusco", provincia = "Cusco", distrito = "Wanchaq",
            nombre = "Colegio", direccion = null, localidad = null, cp = null, turno = null,
            alumnos = null, secciones = null, gestion = "publica", codigoLocal = "1",
            niveles = listOf("primaria"), longitud = -72.0, latitud = -13.0,
        )
        model.setSchoolFile(SchoolFile(1, 0, listOf(school)), "test")
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            val state by model.state.collectAsState()
            MaterialTheme {
                SchoolsScreen(
                    state = state,
                    roadState = RoadRoutingState(orderedSchools = state.visibleSchools),
                    onQueryChanged = model::setQuery,
                    onApplyFilters = model::setFilters,
                    onOpenFilters = model::openFilters,
                    onFilterDraftChanged = model::updateFilterDraft,
                    onDismissFilters = model::dismissFilters,
                    savedSearches = emptyList(),
                    savedSearchMessage = null,
                    savedSearchMessageIsError = false,
                    onApplySavedSearch = {},
                    onSaveSearch = {},
                    onDeleteSavedSearch = {},
                    onImportSavedSearches = {},
                    onExportSavedSearches = {},
                    onSchoolSelected = {},
                )
            }
        }
        compose.onNodeWithContentDescription("Abrir filtros avanzados").performClick()
        compose.onNodeWithText("Departamento: Todos").performClick()
        compose.onNodeWithText("Cusco").performClick()
        assertEquals(SchoolFilters(), model.state.value.filters)
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Filtros avanzados").assertIsDisplayed()
        compose.onNodeWithText("Departamento: Cusco").assertIsDisplayed()
        compose.onNodeWithText("Aplicar").performClick()
        assertEquals("Cusco", model.state.value.filters.departamento)
    }
}
