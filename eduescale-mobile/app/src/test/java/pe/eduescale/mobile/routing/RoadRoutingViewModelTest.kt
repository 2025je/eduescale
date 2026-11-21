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
import pe.eduescale.mobile.data.distanceInMeters

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
    fun usesStraightLineDistancesWhenRoadDataIsNotDownloaded() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val routing = FakeRouting(initiallyAvailable = false) {
                error("Road matrix must not be used without downloaded road data")
            }
            val nearby = school(1)
            val farther = school(2).copy(longitud = -76.0)
            val unlocated = school(3).copy(latitud = null)
            val model = RoadRoutingViewModel(routing)
            model.updateInputs(listOf(farther, unlocated, nearby), origin)
            advanceUntilIdle()

            assertEquals(listOf(1, 2, 3), model.state.value.orderedSchools.map { it.id })
            assertEquals(0.0, model.state.value.distances[nearby.id])
            assertEquals(distanceInMeters(farther, origin), model.state.value.distances[farther.id])
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

    @Test
    fun changingSelectionDiscardsObsoleteRouteAndClearingRemovesIt() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val first = CompletableDeferred<RoadRoute>()
            val routing = FakeRouting(
                routeResult = { start, destination ->
                    if (destination.longitude == -77.0) {
                        withContext(NonCancellable) { first.await() }
                    } else {
                        RoadRoute(start, destination, listOf(start, destination), 200.0, 60.0)
                    }
                },
                result = { emptyList() },
            )
            val model = RoadRoutingViewModel(routing)
            val firstSchool = school(1)
            val secondSchool = school(2).copy(longitud = -76.0)
            model.updateSelectedRoute(firstSchool, origin)
            runCurrent()
            model.updateSelectedRoute(secondSchool, origin)
            runCurrent()
            first.complete(RoadRoute(origin, firstSchool.coordinates!!, listOf(origin, origin), 999.0, 1.0))
            advanceUntilIdle()
            assertEquals(2, model.state.value.routeSchoolId)
            assertEquals(200.0, model.state.value.route?.distanceMeters)
            model.updateSelectedRoute(null, origin)
            assertEquals(null, model.state.value.route)
            assertEquals(null, model.state.value.routeSchoolId)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun selectedRouteFailureIsReportedWithoutDrawingAPath() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val routing = FakeRouting(
                routeResult = { _, _ -> throw IOException("No se pudo leer la red vial.") },
                result = { emptyList() },
            )
            val model = RoadRoutingViewModel(routing)
            model.updateSelectedRoute(school(1), origin)
            advanceUntilIdle()
            assertEquals("No se pudo leer la red vial.", model.state.value.routeError)
            assertEquals(null, model.state.value.route)
            assertFalse(model.state.value.routeCalculating)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun routeKeepsItsOriginUntilAnotherSchoolIsSelected() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val model = RoadRoutingViewModel(FakeRouting { emptyList() })
            val first = school(1)
            val second = school(2).copy(longitud = -76.0)
            val updatedOrigin = SchoolCoordinates(-12.1, -77.1)
            model.updateSelectedRoute(first, null)
            model.updateSelectedRoute(first, origin)
            advanceUntilIdle()
            val firstRoute = model.state.value.route
            model.updateSelectedRoute(first, null)
            model.updateSelectedRoute(first, updatedOrigin)
            advanceUntilIdle()
            assertEquals(firstRoute, model.state.value.route)
            assertEquals(origin, model.state.value.routeOrigin)

            model.updateSelectedRoute(second, updatedOrigin)
            advanceUntilIdle()
            assertEquals(2, model.state.value.routeSchoolId)
            assertEquals(updatedOrigin, model.state.value.routeOrigin)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun changedOriginRetriesAnUnsuccessfulRoute() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val updatedOrigin = SchoolCoordinates(-12.1, -77.1)
            val model = RoadRoutingViewModel(FakeRouting(
                routeResult = { start, destination ->
                    if (start == origin) throw IOException("Sin ruta")
                    RoadRoute(start, destination, listOf(start, destination), 100.0, 60.0)
                },
                result = { emptyList() },
            ))
            model.updateSelectedRoute(school(1), origin)
            advanceUntilIdle()
            assertEquals("Sin ruta", model.state.value.routeError)
            model.updateSelectedRoute(school(1), updatedOrigin)
            advanceUntilIdle()
            assertEquals(updatedOrigin, model.state.value.route?.origin)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun deletingRoadDataCancelsCalculationsAndPreservesTheDisplayedRoute() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val pending = CompletableDeferred<List<Double?>>()
            val routing = FakeRouting { withContext(NonCancellable) { pending.await() } }
            val model = RoadRoutingViewModel(routing)
            model.updateSelectedRoute(school(1), origin)
            advanceUntilIdle()
            val displayedRoute = model.state.value.route
            model.updateInputs(listOf(school(1)), origin)
            runCurrent()
            model.delete()
            runCurrent()
            pending.complete(listOf(123.0))
            advanceUntilIdle()

            assertFalse(model.state.value.available)
            assertEquals(0L, model.state.value.storedBytes)
            assertEquals(mapOf(1 to 0.0), model.state.value.distances)
            assertEquals(displayedRoute, model.state.value.route)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun failedDeletionKeepsRoadDataAvailableAndReportsTheError() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val routing = FakeRouting(deleteResult = { throw IOException("No se pudo borrar") }) { emptyList() }
            val model = RoadRoutingViewModel(routing)
            model.delete()
            advanceUntilIdle()

            assertEquals(true, model.state.value.available)
            assertEquals(1024L, model.state.value.storedBytes)
            assertEquals("No se pudo borrar", model.state.value.error)
            assertFalse(model.state.value.deleting)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class FakeRouting(
        private val routeResult: suspend (SchoolCoordinates, SchoolCoordinates) -> RoadRoute =
            { origin, destination -> RoadRoute(origin, destination, listOf(origin, destination), 100.0, 60.0) },
        private val deleteResult: suspend () -> Unit = {},
        initiallyAvailable: Boolean = true,
        private val result: suspend (List<SchoolCoordinates>) -> List<Double?>,
    ) : RoadRoutingPort {
        private var available = initiallyAvailable
        override fun isAvailable() = available
        override fun storedBytes() = if (available) 1024L else 0L
        override suspend fun delete() {
            deleteResult()
            available = false
        }
        override suspend fun download(progress: (Long, Long) -> Unit) {
            throw IOException("Descarga fallida")
        }
        override suspend fun distances(origin: SchoolCoordinates, targets: List<SchoolCoordinates>) = result(targets)
        override suspend fun route(origin: SchoolCoordinates, destination: SchoolCoordinates): RoadRoute =
            routeResult(origin, destination)
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
