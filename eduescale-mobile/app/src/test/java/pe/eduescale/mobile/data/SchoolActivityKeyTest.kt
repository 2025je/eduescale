package pe.eduescale.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SchoolActivityKeyTest {
    @Test
    fun keyDoesNotDependOnRecordPosition() {
        val school = school()

        assertEquals(school.activityKey(), school.copy(id = 90).activityKey())
        assertNotEquals(school.activityKey(), school.copy(nombre = "Otro colegio").activityKey())
    }

    @Test
    fun localCodeKeepsTheKeyStableWhenTheSchoolNameChanges() {
        val school = school().copy(codigoLocal = "000123")

        assertEquals(
            school.activityKey(),
            school.copy(id = 91, nombre = "Nombre actualizado", gestion = "privada")
                .activityKey(),
        )
    }

    private fun school(id: Int = 1) = SchoolRecord(
        id = id,
        departamento = "Lima",
        provincia = "Lima",
        distrito = "Lima",
        nombre = "Colegio Ejemplo",
        direccion = "Av. Principal",
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
