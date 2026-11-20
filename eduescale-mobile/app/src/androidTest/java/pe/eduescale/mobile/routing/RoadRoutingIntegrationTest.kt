package pe.eduescale.mobile.routing

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import pe.eduescale.mobile.data.SchoolCoordinates

@RunWith(AndroidJUnit4::class)
class RoadRoutingIntegrationTest {
    @Test
    fun routesAgainstInstalledGraphAndIsolatesOutOfCoverageTargets() = runBlocking {
        val routing = RoadRouting(ApplicationProvider.getApplicationContext(), OkHttpClient())
        assumeTrue("Requires the Peru routing graph installed in app storage", routing.isAvailable())
        val distances = routing.distances(
            SchoolCoordinates(-12.0464, -77.0428),
            listOf(
                SchoolCoordinates(-12.121, -77.029),
                SchoolCoordinates(-13.5319, -71.9675),
                SchoolCoordinates(-0.1, -30.0),
                SchoolCoordinates(-25.0, -80.0),
            ),
        )
        assertTrue(distances[0]!! in 10_000.0..15_000.0)
        assertTrue(distances[1]!! in 900_000.0..1_300_000.0)
        assertNull(distances[2])
        assertNull(distances[3])
    }
}
