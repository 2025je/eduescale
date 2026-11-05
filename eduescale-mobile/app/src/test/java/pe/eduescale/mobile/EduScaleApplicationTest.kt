package pe.eduescale.mobile

import java.nio.file.Files
import java.util.concurrent.TimeUnit
import okhttp3.CacheControl
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class EduScaleApplicationTest {
    @Test
    fun mapHttpClientServesCachedTilesWithoutNetwork() {
        val cacheDirectory = Files.createTempDirectory("eduescale-map-cache").toFile()
        val server = MockWebServer()
        server.start()
        val client = createMapHttpClient(cacheDirectory)

        try {
            server.enqueue(
                MockResponse()
                    .setHeader("Cache-Control", "public, max-age=604800")
                    .setBody("tile bytes"),
            )
            val request = Request.Builder()
                .url(server.url("/1/1/1.png"))
                .build()

            client.newCall(request).execute().use { response ->
                assertEquals(200, response.code)
                assertEquals("tile bytes", response.body?.string())
                assertNotNull(response.networkResponse)
                assertNull(response.cacheResponse)
            }
            assertEquals(
                "EduEscaleAndroid/1.0 (https://github.com/2025je/eduescale)",
                server.takeRequest().getHeader("User-Agent"),
            )

            val offlineRequest = request.newBuilder()
                .cacheControl(
                    CacheControl.Builder()
                        .onlyIfCached()
                        .maxStale(7, TimeUnit.DAYS)
                        .build(),
                )
                .build()
            client.newCall(offlineRequest).execute().use { response ->
                assertEquals(200, response.code)
                assertEquals("tile bytes", response.body?.string())
                assertNotNull(response.cacheResponse)
                assertNull(response.networkResponse)
            }
        } finally {
            client.cache?.close()
            server.shutdown()
            cacheDirectory.deleteRecursively()
        }
    }

    @Test
    fun mapTileCacheHasABoundedSize() {
        assertEquals(128L * 1024 * 1024, MAP_TILE_CACHE_SIZE_BYTES)
    }
}
