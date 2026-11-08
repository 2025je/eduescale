package pe.eduescale.mobile.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.StringReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test

class SchoolDataReaderTest {
    @Test
    fun readsCompressedEduScaleZip() {
        val json = """{"formato":"eduescale","version":1,"colegios":[{"nombre":"Colegio ZIP","longitud":-77.0,"latitud":-12.0}]}"""
        val archiveBytes = ByteArrayOutputStream()
        ZipOutputStream(archiveBytes).use { archive ->
            archive.putNextEntry(ZipEntry("colegios.json"))
            archive.write(json.toByteArray(StandardCharsets.UTF_8))
            archive.closeEntry()
        }

        val parsed = SchoolDataReader.read(ByteArrayInputStream(archiveBytes.toByteArray()))

        assertEquals(1, parsed.totalCount)
        assertEquals("Colegio ZIP", parsed.schools.single().nombre)
    }

    @Test
    fun readsPlainJsonFromInputStream() {
        val json = """{"formato":"eduescale","version":1,"colegios":[]}"""

        val parsed = SchoolDataReader.read(
            ByteArrayInputStream(json.toByteArray(StandardCharsets.UTF_8)),
        )

        assertEquals(0, parsed.totalCount)
        assertEquals(emptyList<SchoolRecord>(), parsed.schools)
    }

    @Test
    fun readsVersionOneAndSkipsSchoolsWithoutUsableCoordinates() {
        val json = """
            {
              "formato": "eduescale",
              "version": 1,
              "colegios": [
                {
                  "departamento": "Loreto",
                  "provincia": "Maynas",
                  "distrito": "Iquitos",
                  "nombre": "Colegio Ejemplo",
                  "direccion": "Av. Principal",
                  "localidad": "Iquitos",
                  "cp": "Centro Poblado",
                  "turno": "Mañana",
                  "alumnos": 120,
                  "secciones": 6,
                  "gestion": "publica",
                  "nivel": "secundaria",
                  "longitud": -73.253,
                  "latitud": -3.749
                },
                {
                  "nombre": "Sin coordenadas",
                  "longitud": null,
                  "latitud": null
                }
              ]
            }
        """.trimIndent()

        val parsed = SchoolDataReader.read(StringReader(json))

        assertEquals(2, parsed.totalCount)
        assertEquals(1, parsed.withoutCoordinates)
        assertEquals(1, parsed.schools.size)
        assertEquals("Colegio Ejemplo", parsed.schools.single().nombre)
        assertEquals("120", parsed.schools.single().alumnos)
        assertEquals("6", parsed.schools.single().secciones)
        assertEquals("publica", parsed.schools.single().gestion)
        assertEquals(listOf("secundaria"), parsed.schools.single().niveles)
        assertEquals(-73.253, parsed.schools.single().longitud, 0.000001)
        assertEquals(-3.749, parsed.schools.single().latitud, 0.000001)
    }

    @Test
    fun combinesLevelsAndTotalsForRecordsFromTheSameLocal() {
        val json = """
            {
              "formato": "eduescale",
              "version": 1,
              "colegios": [
                {
                  "codigo_local": "000123",
                  "departamento": "Loreto",
                  "provincia": "Maynas",
                  "distrito": "Iquitos",
                  "nombre": "Colegio Ejemplo",
                  "gestion": "publica",
                  "nivel": "secundaria",
                  "alumnos": 80,
                  "secciones": 4,
                  "longitud": -73.253,
                  "latitud": -3.749
                },
                {
                  "codigo_local": "000123",
                  "departamento": "Loreto",
                  "provincia": "Maynas",
                  "distrito": "Iquitos",
                  "nombre": "Colegio Ejemplo",
                  "gestion": "publica",
                  "nivel": "primaria",
                  "alumnos": 120,
                  "secciones": 6,
                  "longitud": -73.253,
                  "latitud": -3.749
                },
                {
                  "codigo_local": "000456",
                  "nombre": "Sin coordenadas",
                  "longitud": null,
                  "latitud": null
                }
              ]
            }
        """.trimIndent()

        val parsed = SchoolDataReader.read(StringReader(json))

        assertEquals(3, parsed.totalCount)
        assertEquals(1, parsed.withoutCoordinates)
        assertEquals(1, parsed.schools.size)
        assertEquals(listOf("primaria", "secundaria"), parsed.schools.single().niveles)
        assertEquals("200", parsed.schools.single().alumnos)
        assertEquals("10", parsed.schools.single().secciones)
    }

    @Test
    fun rejectsUnsupportedFormatVersion() {
        val json = """{"formato":"eduescale","version":2,"colegios":[]}"""

        assertThrows(IllegalArgumentException::class.java) {
            SchoolDataReader.read(StringReader(json))
        }
    }

    @Test
    fun rejectsFilesWithoutTheEduScaleFormatMarker() {
        val json = """{"formato":"otro","version":1,"colegios":[]}"""

        assertThrows(IllegalArgumentException::class.java) {
            SchoolDataReader.read(StringReader(json))
        }
    }

    @Test
    fun storedFileCanBeRestoredAfterTheAppIsRecreated() {
        val directory = Files.createTempDirectory("eduescale-store").toFile()
        try {
            val storage = SchoolFileStorage(directory)
            val json =
                """{"formato":"eduescale","version":1,"colegios":[{"nombre":"Colegio guardado","longitud":-77.0,"latitud":-12.0}]}"""
            storage.save(ByteArrayInputStream(json.toByteArray(StandardCharsets.UTF_8)))

            val restored = SchoolFileStorage(directory).restore()

            assertNotNull(restored)
            assertEquals("Colegio guardado", restored?.schools?.single()?.nombre)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun invalidImportDoesNotReplaceThePreviouslyStoredFile() {
        val directory = Files.createTempDirectory("eduescale-store").toFile()
        try {
            val storage = SchoolFileStorage(directory)
            val json =
                """{"formato":"eduescale","version":1,"colegios":[{"nombre":"Colegio guardado","longitud":-77.0,"latitud":-12.0}]}"""
            storage.save(ByteArrayInputStream(json.toByteArray(StandardCharsets.UTF_8)))

            assertThrows(IllegalArgumentException::class.java) {
                storage.save(ByteArrayInputStream("""{"formato":"otro"}""".toByteArray()))
            }

            assertEquals("Colegio guardado", storage.restore()?.schools?.single()?.nombre)
        } finally {
            directory.deleteRecursively()
        }
    }
}
