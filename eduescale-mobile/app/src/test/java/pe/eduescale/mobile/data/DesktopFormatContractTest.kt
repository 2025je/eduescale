package pe.eduescale.mobile.data

import com.google.gson.JsonParser
import java.io.ByteArrayInputStream
import java.io.StringReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopFormatContractTest {
    private fun fixturePath(name: String) = listOf(
        Paths.get("..", "..", "test-fixtures", name),
        Paths.get("..", "test-fixtures", name),
    ).firstOrNull(Files::isRegularFile) ?: error("No se encontró el fixture $name.")

    @Test
    fun readsDesktopVersionTwoWithInitialAndPublicConvention() {
        val bytes = Base64.getDecoder().decode(
            String(Files.readAllBytes(fixturePath("desktop-contract-v2.base64")), StandardCharsets.US_ASCII).trim(),
        )
        val file = SchoolDataReader.read(ByteArrayInputStream(bytes))
        assertEquals(2, file.formatVersion)
        val result = filterSchools(
            file.schools, "",
            SchoolFilters(gestiones = setOf("publica_convenio"), niveles = setOf("inicial")),
        )
        assertEquals("Colegio contrato v2", result.single().nombre)
    }

    @Test
    fun readsCurrentDesktopExportsInAllContainers() {
        for (format in listOf("json", "zip", "encrypted")) {
            val name = "desktop-contract-$format.base64"
            val path = fixturePath(name)
            val bytes = Base64.getDecoder().decode(
                String(Files.readAllBytes(path), StandardCharsets.US_ASCII).trim(),
            )
            val password = if (format == "encrypted") "contraseña segura" else null
            val parsed = SchoolDataReader.read(ByteArrayInputStream(bytes), password)

            assertEquals(format, 2, parsed.totalCount)
            assertEquals(format, 1, parsed.withoutCoordinates)
            assertEquals(format, 1, parsed.formatVersion)
            assertTrue(format, !parsed.applicationVersion.isNullOrBlank())
            val school = parsed.schools.single()
            assertEquals(format, "Colegio contrato", school.nombre)
            assertEquals(format, "000123", school.codigoLocal)
            assertEquals(format, "Loreto", school.departamento)
            assertEquals(format, "Maynas", school.provincia)
            assertEquals(format, "Iquitos", school.distrito)
            assertEquals(format, "120", school.alumnos)
            assertEquals(format, "6", school.secciones)
            assertEquals(format, "Mañana", school.turno)
            assertEquals(format, listOf("secundaria"), school.niveles)
            assertEquals(format, -73.253, school.longitud!!, 0.000001)
            assertEquals(format, -3.749, school.latitud!!, 0.000001)
            assertEquals(
                format,
                "Sin coordenadas",
                parsed.schoolsWithoutCoordinates.single().nombre,
            )
        }
    }

    @Test
    fun acceptsAndRejectsSharedVersionOneCases() {
        val cases = JsonParser.parseString(
            String(
                Files.readAllBytes(fixturePath("eduescale-v1-cases.json")),
                StandardCharsets.UTF_8,
            ),
        ).asJsonArray
        for (case in cases) {
            val entry = case.asJsonObject
            val document = entry.get("document").toString()
            if (entry.get("valid").asBoolean) {
                assertEquals(
                    entry.get("name").asString,
                    1,
                    SchoolDataReader.read(StringReader(document)).totalCount,
                )
            } else {
                assertThrows(IllegalArgumentException::class.java) {
                    SchoolDataReader.read(StringReader(document))
                }
            }

        }
    }

    @Test
    fun rejectsTamperedDesktopEncryptedExport() {
        val bytes = Base64.getDecoder().decode(
            String(
                Files.readAllBytes(fixturePath("desktop-contract-encrypted.base64")),
                StandardCharsets.US_ASCII,
            ).trim(),
        )
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()

        assertThrows(IllegalArgumentException::class.java) {
            SchoolDataReader.read(ByteArrayInputStream(bytes), "contraseña segura")
        }
    }
}
