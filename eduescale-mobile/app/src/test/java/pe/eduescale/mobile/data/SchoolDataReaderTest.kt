package pe.eduescale.mobile.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.StringReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test

class SchoolDataReaderTest {
    @Test
    fun readsCompressedEduEscaleZip() {
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
    fun readsJsonWithMetadataAndRejectsMismatchedCount() {
        val validJson = """
            {
              "formato": "eduescale",
              "version": 1,
              "metadatos": {
                "generado_en": "2026-11-11T12:00:00Z",
                "cantidad_colegios": 1,
                "crs": "EPSG:4326",
                "aplicacion": "EduEscale Desktop",
                "version_aplicacion": "0.1.0"
              },
              "colegios": [{"nombre": "Con metadatos"}]
            }
        """.trimIndent()

        assertEquals(
            1,
            SchoolDataReader.read(
                ByteArrayInputStream(validJson.toByteArray(StandardCharsets.UTF_8)),
            ).totalCount,
        )

        val invalidJson = validJson.replace("\"cantidad_colegios\": 1", "\"cantidad_colegios\": 2")
        assertThrows(IllegalArgumentException::class.java) {
            SchoolDataReader.read(
                ByteArrayInputStream(invalidJson.toByteArray(StandardCharsets.UTF_8)),
            )
        }
    }

    @Test
    fun readsDesktopEncryptedFileAndPersistsOnlyDecryptedData() {
        val encryptedBytes = Base64.getDecoder().decode(ENCRYPTED_FIXTURE)
        val directory = Files.createTempDirectory("eduescale-protected").toFile()
        try {
            val storage = SchoolFileStorage(directory)
            val parsed = storage.save(
                ByteArrayInputStream(encryptedBytes),
                "fixture password",
            )
            assertEquals("Fixture cruzado", parsed.schools.single().nombre)
            assertEquals(
                "Fixture cruzado",
                storage.restore()?.schools?.single()?.nombre,
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun rejectsIncorrectPasswordForEncryptedFile() {
        val encryptedBytes = Base64.getDecoder().decode(ENCRYPTED_FIXTURE)

        assertThrows(IllegalArgumentException::class.java) {
            SchoolDataReader.read(
                ByteArrayInputStream(encryptedBytes),
                "incorrecta",
            )
        }
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
        assertEquals(1, parsed.schoolsWithoutCoordinates.size)
        assertEquals("Sin coordenadas", parsed.schoolsWithoutCoordinates.single().nombre)
        assertEquals("Colegio Ejemplo", parsed.schools.single().nombre)
        assertEquals("120", parsed.schools.single().alumnos)
        assertEquals("6", parsed.schools.single().secciones)
        assertEquals("publica", parsed.schools.single().gestion)
        assertEquals(listOf("secundaria"), parsed.schools.single().niveles)
        assertEquals(
            listOf(
                SchoolScheduleRow(
                    nivel = "secundaria",
                    aulas = "6",
                    alumnos = "120",
                    turno = "Mañana",
                ),
            ),
            parsed.schools.single().ofertaEducativa,
        )
        assertEquals(-73.253, parsed.schools.single().longitud!!, 0.000001)
        assertEquals(-3.749, parsed.schools.single().latitud!!, 0.000001)
    }

    @Test
    fun readsCetproAsAnEducationLevel() {
        val json = """
            {
              "formato": "eduescale",
              "version": 1,
              "colegios": [
                {
                  "nombre": "CETPRO Ejemplo",
                  "nivel": "cetpro",
                  "alumnos": 130,
                  "secciones": 7,
                  "turno": "Tarde y Noche",
                  "longitud": -76.5954,
                  "latitud": -7.0603
                }
              ]
            }
        """.trimIndent()

        val school = SchoolDataReader.read(StringReader(json)).schools.single()

        assertEquals(listOf("cetpro"), school.niveles)
        assertEquals(
            listOf(
                SchoolScheduleRow(
                    nivel = "cetpro",
                    aulas = "7",
                    alumnos = "130",
                    turno = "Tarde y Noche",
                ),
            ),
            school.ofertaEducativa,
        )
    }

    @Test
    fun rejectsFilesContainingInitialLevelRecords() {
        val json = """
            {
              "formato": "eduescale",
              "version": 1,
              "colegios": [
                {
                  "nombre": "Servicio Inicial",
                  "nivel": "inicial",
                  "longitud": -77.0,
                  "latitud": -12.0
                }
              ]
            }
        """.trimIndent()

        assertThrows(IllegalArgumentException::class.java) {
            SchoolDataReader.read(StringReader(json))
        }
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
                  "turno": "Mañana",
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
                  "turno": "Tarde",
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
        assertEquals(
            SchoolLevelStatistics(alumnos = "120", secciones = "6"),
            parsed.schools.single().estadisticasPorNivel["primaria"],
        )
        assertEquals(
            SchoolLevelStatistics(alumnos = "80", secciones = "4"),
            parsed.schools.single().estadisticasPorNivel["secundaria"],
        )
        assertEquals(
            listOf(
                SchoolScheduleRow(
                    nivel = "primaria",
                    aulas = "6",
                    alumnos = "120",
                    turno = "Tarde",
                ),
                SchoolScheduleRow(
                    nivel = "secundaria",
                    aulas = "4",
                    alumnos = "80",
                    turno = "Mañana",
                ),
            ),
            parsed.schools.single().ofertaEducativa,
        )
    }

    @Test
    fun rejectsUnsupportedFormatVersion() {
        val json = """{"formato":"eduescale","version":2,"colegios":[]}"""

        assertThrows(IllegalArgumentException::class.java) {
            SchoolDataReader.read(StringReader(json))
        }
    }

    @Test
    fun rejectsFilesWithoutTheEduEscaleFormatMarker() {
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
            val initialLevelJson =
                """{"formato":"eduescale","version":1,"colegios":[{"nombre":"Servicio Inicial","nivel":"inicial","longitud":-77.0,"latitud":-12.0}]}"""
            assertThrows(IllegalArgumentException::class.java) {
                storage.save(
                    ByteArrayInputStream(initialLevelJson.toByteArray(StandardCharsets.UTF_8)),
                )
            }

            assertEquals("Colegio guardado", storage.restore()?.schools?.single()?.nombre)
        } finally {
            directory.deleteRecursively()
        }
    }

    private companion object {
        const val ENCRYPTED_FIXTURE =
            "RURVU0NFTkMBAAknwAABAgMEBQYHCAkKCwwNDg8QERITFBUWFxgZGhuYA6/Incsj+5T3yjd3UCEMxCpihXliZ5aDhl5w5jItsDaa3T1CluRSZn7lU/CLi8TezbCNbUXEXO2Y+/8lNIpQSnlvlUuJpPayef2q7QH+SM6sf9i96IU4d5bow5wx2DtiBa3lHQGRixSCE0kG4+dlj9ZHO+wgIcQ7hyxNPPGH6elqOtMKTB9R4CEdQ1avdNIN5/+QuZdtqpXjTqTlUllfOH7VxqrGDngRK3gFs6Y4K5uglStDQaiXDqxtCGrrGEQBaS7xRT4ikwJD0NMbVBZYR0AUFQlh1AU="
    }
}
