package pe.eduescale.mobile.routing

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import pe.eduescale.mobile.data.SchoolCoordinates
import pe.eduescale.mobile.data.SchoolRecord

@OptIn(ExperimentalCoroutinesApi::class)
class RoadRoutingViewModelTest {
    private val origin = SchoolCoordinates(-12.0, -77.0)

    @Test
    fun ordersReachableSchoolsBeforeUnreachableAndUnlocatedSchools() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val routing = FakeRouting { listOf(2000.0, 1000.0, null) }
            val model = RoadRoutingViewModel(routing)
            val schools = listOf(school(1), school(2), school(3), school(4).copy(latitud = null))
            model.updateInputs(schools, origin)
            advanceUntilIdle()

            assertEquals(listOf(2, 1, 3, 4), model.state.value.orderedSchools.map { it.id })
            assertEquals(mapOf(1 to 2000.0, 2 to 1000.0, 3 to null), model.state.value.distances)
            assertFalse(model.state.value.calculating)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun changingInputsDoesNotPublishACancelledResult() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val first = CompletableDeferred<List<Double?>>()
            val routing = FakeRouting { targets ->
                if (targets.size == 1) withContext(NonCancellable) { first.await() } else listOf(2.0, 1.0)
            }
            val model = RoadRoutingViewModel(routing)
            model.updateInputs(listOf(school(1)), origin)
            runCurrent()
            model.updateInputs(listOf(school(2), school(3)), origin)
            runCurrent()
            first.complete(listOf(999.0))
            advanceUntilIdle()

            assertEquals(listOf(3, 2), model.state.value.orderedSchools.map { it.id })
            assertEquals(mapOf(2 to 2.0, 3 to 1.0), model.state.value.distances)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun incompleteResultsAndDownloadErrorsAreReported() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val routing = FakeRouting { emptyList() }
            val model = RoadRoutingViewModel(routing)
            model.updateInputs(listOf(school(1)), origin)
            advanceUntilIdle()
            assertEquals("El resultado de rutas está incompleto.", model.state.value.error)
            assertFalse(model.state.value.calculating)

            model.download()
            advanceUntilIdle()
            assertEquals("Descarga fallida", model.state.value.error)
            assertFalse(model.state.value.downloading)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class FakeRouting(
        private val result: suspend (List<SchoolCoordinates>) -> List<Double?>,
    ) : RoadRoutingPort {
        override fun isAvailable() = true
        override suspend fun download(progress: (Long, Long) -> Unit) {
            throw IOException("Descarga fallida")
        }
        override suspend fun distances(origin: SchoolCoordinates, targets: List<SchoolCoordinates>) = result(targets)
    }

    private fun school(id: Int) = SchoolRecord(
        id = id,
        departamento = null,
        provincia = null,
        distrito = null,
        nombre = "Colegio $id",
        direccion = null,
        localidad = null,
        cp = null,
        turno = null,
        alumnos = null,
        secciones = null,
        gestion = null,
        codigoLocal = null,
        niveles = emptyList(),
        longitud = -77.0,
        latitud = -12.0,
    )
}
