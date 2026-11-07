package pe.eduescale.mobile.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test
import pe.eduescale.mobile.data.SchoolFile
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
    fun importFailureKeepsTheLastSuccessfullyLoadedFile() {
        val viewModel = EduEscaleViewModel()
        val file = SchoolFile(1, 0, listOf(school(1, "Colegio")))
        viewModel.setSchoolFile(file, "colegios.eduescale")

        viewModel.setImporting(true)
        viewModel.setImportError("El archivo no es válido.")

        assertSame(file, viewModel.state.value.schoolFile)
        assertEquals("El archivo no es válido.", viewModel.state.value.importError)
        assertFalse(viewModel.state.value.isImporting)
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
