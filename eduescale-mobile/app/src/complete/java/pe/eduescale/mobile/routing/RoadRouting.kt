package pe.eduescale.mobile.routing

import android.content.Context
import com.valhalla.valhalla.Valhalla
import com.valhalla.valhalla.ValhallaException
import com.valhalla.valhalla.config.ValhallaConfigFactory
import com.valhalla.config.models.ValhallaConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import pe.eduescale.mobile.data.SchoolCoordinates

@Singleton
class RoadRouting @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val http: OkHttpClient,
) : RoadRoutingPort {
    override val supportsRoadRouting = true

    private val directory = File(context.filesDir, "routing")
    private val graph = File(directory, "peru.tar")
    private val temporaryGraph = File(directory, "peru.tar.part")
    private val mutex = Mutex()

    override fun isAvailable(): Boolean = graph.isFile && graph.length() > 0

    override fun storedBytes(): Long =
        listOf(graph, temporaryGraph).sumOf { if (it.isFile) it.length() else 0 }

    override suspend fun delete(): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            for (file in listOf(temporaryGraph, graph)) {
                if (file.exists() && !file.delete()) {
                    throw IOException("No se pudieron eliminar las carreteras del dispositivo.")
                }
            }
        }
    }

    override suspend fun download(progress: (Long, Long) -> Unit): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!directory.exists() && !directory.mkdirs()) throw IOException("No se pudo crear el directorio de rutas.")
            val checksum = request("$BASE_URL/peru.tar.sha256").use { response ->
                val text = response.body?.string() ?: throw IOException("El checksum del grafo está vacío.")
                val value = text.trim().split(Regex("\\s+")).first()
                if (!value.matches(Regex("[0-9a-fA-F]{64}"))) throw IOException("El checksum del grafo no es válido.")
                value.lowercase()
            }
            val temporary = temporaryGraph
            try {
                request("$BASE_URL/peru.tar").use { response ->
                    val body = response.body ?: throw IOException("La descarga del grafo está vacía.")
                    val total = body.contentLength()
                    val digest = MessageDigest.getInstance("SHA-256")
                    var downloaded = 0L
                    body.byteStream().use { input ->
                        temporary.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                                digest.update(buffer, 0, count)
                                downloaded += count
                                progress(downloaded, total)
                            }
                            output.fd.sync()
                        }
                    }
                    if (downloaded == 0L || (total >= 0 && downloaded != total)) {
                        throw IOException("La descarga del grafo está incompleta.")
                    }
                    val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
                    if (actual != checksum) throw IOException("El grafo descargado no coincide con su checksum.")
                }
                if (!temporary.renameTo(graph)) throw IOException("No se pudo instalar el grafo descargado.")
            } finally {
                if (temporary.exists() && !temporary.delete()) {
                    android.util.Log.w("RoadRouting", "No se pudo eliminar la descarga temporal.")
                }
            }
        }
    }

    override suspend fun distances(origin: SchoolCoordinates, targets: List<SchoolCoordinates>): List<Double?> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                if (!isAvailable()) throw IOException("Descarga los datos de carreteras en Archivos.")
                try {
                    Valhalla(context, routingConfig()).use { engine ->
                        targets.chunked(30).flatMap { batch ->
                            currentCoroutineContext().ensureActive()
                            matrixDistances(engine, origin, batch)
                        }
                    }
                } catch (error: ValhallaException) {
                    throw IOException(error.message ?: "No se pudieron calcular las distancias por carretera.", error)
                }
            }
        }

    override suspend fun route(origin: SchoolCoordinates, destination: SchoolCoordinates): RoadRoute =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                if (!isAvailable()) throw IOException("Descarga los datos de carreteras en Archivos.")
                val request = JSONObject()
                    .put("locations", JSONArray().put(origin.toJson()).put(destination.toJson()))
                    .put("costing", "auto")
                    .put("format", "osrm")
                    .put("shape_format", "geojson")
                    .put("overview", "full")
                try {
                    Valhalla(context, routingConfig()).use { engine ->
                        parseRoute(engine.routeRaw(request.toString()), origin, destination)
                    }
                } catch (error: ValhallaException) {
                    throw IOException(error.message ?: "No se pudo calcular la ruta en automóvil.", error)
                }
            }
        }

    private fun routingConfig(): ValhallaConfig {
        val defaults = ValhallaConfigFactory.usingTileExtract(graph.absolutePath)
        val limits = defaults.serviceLimits ?: throw IOException("Falta la configuración de límites de rutas.")
        val auto = limits.auto ?: throw IOException("Falta la configuración de rutas en automóvil.")
        return defaults.copy(serviceLimits = limits.copy(
            auto = auto.copy(maxDistance = 5_000_000, maxMatrixDistance = 5_000_000),
        ))
    }

    private fun parseRoute(response: String, origin: SchoolCoordinates, destination: SchoolCoordinates): RoadRoute {
        val result = JSONObject(response).getJSONArray("routes").getJSONObject(0)
        val geometry = result.getJSONObject("geometry")
        if (geometry.getString("type") != "LineString") throw IOException("La geometría de la ruta no es válida.")
        val coordinates = geometry.getJSONArray("coordinates")
        val points = (0 until coordinates.length()).map { index ->
            val point = coordinates.getJSONArray(index)
            val longitude = point.getDouble(0)
            val latitude = point.getDouble(1)
            if (!latitude.isFinite() || latitude !in -90.0..90.0 ||
                !longitude.isFinite() || longitude !in -180.0..180.0
            ) throw IOException("La geometría de la ruta no es válida.")
            SchoolCoordinates(latitude, longitude)
        }
        val distance = result.getDouble("distance")
        val duration = result.getDouble("duration")
        if (points.size < 2 || !distance.isFinite() || distance < 0 ||
            !duration.isFinite() || duration < 0
        ) throw IOException("El resultado de la ruta no es válido.")
        return RoadRoute(origin, destination, points, distance, duration)
    }

    private suspend fun matrixDistances(
        engine: Valhalla,
        origin: SchoolCoordinates,
        targets: List<SchoolCoordinates>,
    ): List<Double?> {
        currentCoroutineContext().ensureActive()
        try {
            return queryMatrix(engine, origin, targets)
        } catch (error: ValhallaException.Internal) {
            // The library currently exposes the error code only in this formatted message.
            val isLocationError = error.message?.startsWith("ValhallaError(code=170,") == true ||
                error.message?.startsWith("ValhallaError(code=171,") == true ||
                error.message?.startsWith("ValhallaError(code=154,") == true
            if (!isLocationError) throw error
            if (targets.size == 1) {
                queryMatrix(engine, origin, listOf(origin))
                return listOf(null)
            }
            val middle = targets.size / 2
            return matrixDistances(engine, origin, targets.take(middle)) +
                matrixDistances(engine, origin, targets.drop(middle))
        }
    }

    private fun queryMatrix(
        engine: Valhalla,
        origin: SchoolCoordinates,
        targets: List<SchoolCoordinates>,
    ): List<Double?> {
        val request = JSONObject()
            .put("sources", JSONArray().put(origin.toJson()))
            .put("targets", JSONArray().apply { targets.forEach { put(it.toJson()) } })
            .put("costing", "auto")
            .put("units", "kilometers")
        val rows = JSONObject(engine.matrixRaw(request.toString())).getJSONArray("sources_to_targets")
        if (rows.length() != 1) throw IOException("El resultado de rutas está incompleto.")
        val row = rows.getJSONArray(0)
        if (row.length() != targets.size) throw IOException("El resultado de rutas está incompleto.")
        return (0 until row.length()).map { index ->
            val entry = row.getJSONObject(index)
            if (entry.getInt("from_index") != 0 || entry.getInt("to_index") != index || !entry.has("distance")) {
                throw IOException("El resultado de rutas no es válido.")
            }
            if (entry.isNull("distance")) null else {
                val distance = entry.getDouble("distance")
                if (!distance.isFinite() || distance < 0) throw IOException("La distancia de ruta no es válida.")
                distance * 1000.0
            }
        }
    }

    private fun request(url: String): okhttp3.Response {
        val response = http.newCall(Request.Builder().url(url).header("Cache-Control", "no-store").build()).execute()
        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            throw IOException("No se pudieron descargar las carreteras (HTTP $code).")
        }
        return response
    }

    private fun SchoolCoordinates.toJson(): JSONObject =
        JSONObject().put("lat", latitude).put("lon", longitude)

    private companion object {
        const val BASE_URL = "https://github.com/2025je/eduescale/releases/download/vial-peru"
    }
}
