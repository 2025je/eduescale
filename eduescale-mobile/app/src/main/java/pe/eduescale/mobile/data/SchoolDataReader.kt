package pe.eduescale.mobile.data

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.IOException
import java.io.Reader
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream

data class SchoolRecord(
    val id: Int,
    val departamento: String?,
    val provincia: String?,
    val distrito: String?,
    val nombre: String?,
    val direccion: String?,
    val localidad: String?,
    val cp: String?,
    val turno: String?,
    val alumnos: String?,
    val secciones: String?,
    val gestion: String?,
    val codigoLocal: String?,
    val niveles: List<String>,
    val longitud: Double?,
    val latitud: Double?,
) {
    val coordinates: SchoolCoordinates?
        get() {
            val longitude = longitud ?: return null
            val latitude = latitud ?: return null
            return SchoolCoordinates(latitude, longitude)
        }

    val hasCoordinates: Boolean
        get() = coordinates != null
}

data class SchoolFile(
    // Both counts refer to source rows, before grouping by local.
    val totalCount: Int,
    val withoutCoordinates: Int,
    val schools: List<SchoolRecord>,
    val schoolsWithoutCoordinates: List<SchoolRecord> = emptyList(),
)

private data class ParsedSchools(
    val totalCount: Int,
    val withoutCoordinates: Int,
    val schools: List<SchoolRecord>,
)

private data class SchoolGroupKey(
    val codigoLocal: String?,
    val departamento: String?,
    val provincia: String?,
    val distrito: String?,
    val nombre: String?,
    val gestion: String?,
    val recordId: Int?,
)

object SchoolDataReader {
    private const val EXPECTED_FORMAT = "eduescale"
    private const val SUPPORTED_VERSION = 1
    private const val MAX_MERCATOR_LATITUDE = 85.05112878
    private const val ZIP_ENTRY_NAME = "colegios.json"

    @Throws(IOException::class, IllegalArgumentException::class)
    fun read(source: InputStream): SchoolFile {
        val input = BufferedInputStream(source)
        input.mark(ZIP_SIGNATURE.size)
        val signature = ByteArray(ZIP_SIGNATURE.size)
        var bytesRead = 0
        while (bytesRead < signature.size) {
            val count = input.read(signature, bytesRead, signature.size - bytesRead)
            if (count < 0) break
            bytesRead += count
        }
        input.reset()

        return if (bytesRead == signature.size && signature.contentEquals(ZIP_SIGNATURE)) {
            readZip(input)
        } else {
            InputStreamReader(input, StandardCharsets.UTF_8).use { reader -> read(reader) }
        }
    }

    private fun readZip(source: InputStream): SchoolFile =
        ZipInputStream(source, StandardCharsets.UTF_8).use { archive ->
            val entry = archive.nextEntry
                ?: throw IllegalArgumentException("El archivo ZIP está vacío.")
            if (entry.isDirectory || entry.name != ZIP_ENTRY_NAME) {
                throw IllegalArgumentException(
                    "El ZIP no contiene el archivo $ZIP_ENTRY_NAME esperado.",
                )
            }

            val parsed = read(InputStreamReader(archive, StandardCharsets.UTF_8))
            archive.closeEntry()
            if (archive.nextEntry != null) {
                throw IllegalArgumentException(
                    "El ZIP debe contener únicamente $ZIP_ENTRY_NAME.",
                )
            }
            parsed
        }

    @Throws(IOException::class, IllegalArgumentException::class)
    fun read(source: Reader): SchoolFile {
        val reader = JsonReader(source).apply { isLenient = false }
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            throw IllegalArgumentException("El archivo no contiene un objeto JSON.")
        }

        var format: String? = null
        var version: Int? = null
        var parsedSchools: ParsedSchools? = null

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "formato" -> format = readText(reader)
                "version" -> version = readText(reader)?.toIntOrNull()
                "colegios" -> parsedSchools = readSchools(reader)

                else -> reader.skipValue()
            }
        }
        reader.endObject()

        if (reader.peek() != JsonToken.END_DOCUMENT) {
            throw IllegalArgumentException("El archivo contiene datos después del JSON.")
        }
        if (format != EXPECTED_FORMAT) {
            throw IllegalArgumentException("El archivo no tiene formato EduScale.")
        }
        if (version != SUPPORTED_VERSION) {
            throw IllegalArgumentException(
                "Versión no compatible: ${version ?: "desconocida"}.",
            )
        }
        val result = parsedSchools
            ?: throw IllegalArgumentException("El archivo no contiene la lista colegios.")
        val groupedSchools = groupSchools(result.schools)
        return SchoolFile(
            totalCount = result.totalCount,
            withoutCoordinates = result.withoutCoordinates,
            schools = groupedSchools.filter(SchoolRecord::hasCoordinates),
            schoolsWithoutCoordinates = groupedSchools.filterNot(SchoolRecord::hasCoordinates),
        )
    }

    private fun readSchools(reader: JsonReader): ParsedSchools {
        if (reader.peek() != JsonToken.BEGIN_ARRAY) {
            throw IllegalArgumentException("El campo colegios debe ser una lista.")
        }

        val schools = mutableListOf<SchoolRecord>()
        var count = 0
        var withoutCoordinates = 0
        reader.beginArray()
        while (reader.hasNext()) {
            if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                throw IllegalArgumentException("Cada colegio debe ser un objeto JSON.")
            }
            val school = readSchool(reader, count)
            count += 1
            if (!school.hasCoordinates) {
                withoutCoordinates += 1
            }
            schools += school
        }
        reader.endArray()
        return ParsedSchools(count, withoutCoordinates, schools)
    }

    private fun readSchool(reader: JsonReader, id: Int): SchoolRecord {
        var departamento: String? = null
        var provincia: String? = null
        var distrito: String? = null
        var nombre: String? = null
        var direccion: String? = null
        var localidad: String? = null
        var cp: String? = null
        var turno: String? = null
        var alumnos: String? = null
        var secciones: String? = null
        var gestion: String? = null
        var codigoLocal: String? = null
        val niveles = mutableListOf<String>()
        var longitud: Double? = null
        var latitud: Double? = null

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "departamento" -> departamento = readText(reader)
                "provincia" -> provincia = readText(reader)
                "distrito" -> distrito = readText(reader)
                "nombre" -> nombre = readText(reader)
                "direccion" -> direccion = readText(reader)
                "localidad" -> localidad = readText(reader)
                "cp" -> cp = readText(reader)
                "turno" -> turno = readText(reader)
                "alumnos" -> alumnos = readText(reader)
                "secciones" -> secciones = readText(reader)
                "gestion" -> gestion = readText(reader)
                "codigo_local" -> codigoLocal = readText(reader)
                "nivel" -> readText(reader)?.takeIf(String::isNotBlank)?.let(niveles::add)
                "niveles" -> niveles += readLevels(reader)
                "longitud" -> longitud = readDouble(reader)
                "latitud" -> latitud = readDouble(reader)
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        val parsedLongitude = longitud
        val parsedLatitude = latitud
        val coordinates = if (
            parsedLongitude != null &&
            parsedLatitude != null &&
            parsedLongitude.isFinite() &&
            parsedLatitude.isFinite() &&
            parsedLongitude in -180.0..180.0 &&
            parsedLatitude in -MAX_MERCATOR_LATITUDE..MAX_MERCATOR_LATITUDE
        ) {
            SchoolCoordinates(latitude = parsedLatitude, longitude = parsedLongitude)
        } else {
            null
        }

        return SchoolRecord(
            id = id,
            departamento = departamento,
            provincia = provincia,
            distrito = distrito,
            nombre = nombre,
            direccion = direccion,
            localidad = localidad,
            cp = cp,
            turno = turno,
            alumnos = alumnos,
            secciones = secciones,
            gestion = gestion,
            codigoLocal = codigoLocal,
            niveles = niveles.distinct(),
            longitud = coordinates?.longitude,
            latitud = coordinates?.latitude,
        )
    }

    private fun groupSchools(schools: List<SchoolRecord>): List<SchoolRecord> {
        val groups = linkedMapOf<SchoolGroupKey, MutableList<SchoolRecord>>()
        schools.forEach { school ->
            val localCode = school.codigoLocal?.takeIf(String::isNotBlank)
            val key = SchoolGroupKey(
                codigoLocal = localCode,
                departamento = school.departamento,
                provincia = school.provincia,
                distrito = school.distrito,
                nombre = school.nombre,
                gestion = school.gestion,
                recordId = if (localCode == null) school.id else null,
            )
            groups.getOrPut(key) { mutableListOf() }.add(school)
        }
        return groups.values.map(::mergeSchoolGroup)
    }

    private fun mergeSchoolGroup(group: List<SchoolRecord>): SchoolRecord {
        val first = group.first()
        if (group.size == 1) return first

        return first.copy(
            turno = group.mapNotNull { it.turno?.takeIf(String::isNotBlank) }
                .distinct()
                .joinToString(" / ")
                .takeIf(String::isNotEmpty),
            alumnos = sumValues(group.map { it.alumnos }),
            secciones = sumValues(group.map { it.secciones }),
            niveles = group.flatMap { it.niveles }
                .distinct()
                .sortedBy { levelOrder.indexOf(it).let { index -> if (index < 0) Int.MAX_VALUE else index } },
            longitud = group.firstOrNull(SchoolRecord::hasCoordinates)?.longitud,
            latitud = group.firstOrNull(SchoolRecord::hasCoordinates)?.latitud,
        )
    }

    private fun sumValues(values: List<String?>): String? {
        val numbers = values.mapNotNull { it?.toLongOrNull() }
        return numbers.takeIf(List<Long>::isNotEmpty)
            ?.sum()
            ?.toString()
            ?: values.firstOrNull { !it.isNullOrBlank() }
    }

    private fun readLevels(reader: JsonReader): List<String> {
        if (reader.peek() != JsonToken.BEGIN_ARRAY) {
            return listOfNotNull(readText(reader)?.takeIf(String::isNotBlank))
        }

        val levels = mutableListOf<String>()
        reader.beginArray()
        while (reader.hasNext()) {
            readText(reader)?.takeIf(String::isNotBlank)?.let(levels::add)
        }
        reader.endArray()
        return levels
    }

    private fun readText(reader: JsonReader): String? =
        when (reader.peek()) {
            JsonToken.STRING, JsonToken.NUMBER -> reader.nextString()
            JsonToken.BOOLEAN -> reader.nextBoolean().toString()
            JsonToken.NULL -> {
                reader.nextNull()
                null
            }

            else -> {
                reader.skipValue()
                null
            }
        }

    private fun readDouble(reader: JsonReader): Double? =
        when (reader.peek()) {
            JsonToken.NUMBER -> reader.nextDouble()
            JsonToken.STRING -> reader.nextString().toDoubleOrNull()
            JsonToken.NULL -> {
                reader.nextNull()
                null
            }

            else -> {
                reader.skipValue()
                null
            }
        }

    private val levelOrder = listOf("inicial", "primaria", "secundaria")
    private val ZIP_SIGNATURE = byteArrayOf(0x50, 0x4b, 0x03, 0x04)
}
