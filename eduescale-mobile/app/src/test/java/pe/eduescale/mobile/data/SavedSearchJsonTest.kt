package pe.eduescale.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class SavedSearchJsonTest {
    @Test
    fun encodesAndDecodesPortableSearchesWithNullCodes() {
        val searches = listOf(
            SavedSearch(
                name = "Lima centro",
                department = "Lima",
                province = "Lima",
                district = "Cercado de Lima",
                management = listOf("publica"),
                levels = listOf("primaria", "cetpro"),
            ),
        )

        val json = SavedSearchJson.encode(searches)

        assertEquals(searches, SavedSearchJson.decode(json))
        assertEquals(true, json.contains("\"department_code\":null"))
        assertEquals(true, json.contains("\"format\":\"eduescale.saved-searches\""))
    }

    @Test
    fun encodesAndDecodesAvailableCodesAndAppliesFilters() {
        val search = SavedSearch(
            name = "Secundaria privada",
            department = "Cusco",
            province = null,
            district = null,
            departmentCode = "08",
            provinceCode = "0801",
            management = listOf("privada"),
            levels = listOf("secundaria"),
        )

        val decoded = SavedSearchJson.decode(SavedSearchJson.encode(listOf(search))).single()

        assertEquals(search, decoded)
        assertEquals("Cusco", decoded.toSchoolFilters().departamento)
        assertEquals(setOf("privada"), decoded.toSchoolFilters().gestiones)
        assertEquals(setOf("secundaria"), decoded.toSchoolFilters().niveles)
        assertNull(decoded.districtCode)
    }

    @Test
    fun rejectsUnsupportedFormatVersionAndMissingFields() {
        assertInvalid(document(format = "eduescale"))
        assertInvalid(document(version = 2))
        assertInvalid("""{"format":"eduescale.saved-searches","version":1}""")
    }

    @Test
    fun rejectsInvalidNamesValuesAndUnexpectedFields() {
        assertInvalid(document(searches = """[${search(name = "")}]"""))
        assertInvalid(document(searches = """[${search(management = "[\"colegio\"]")}]"""))
        assertInvalid(document(searches = """[${search(extra = ",\"extra\":true")}]"""))
        assertInvalid(
            document(
                searches = """[${search(name = "Duplicada")},${search(name = "duplicada")}]""",
            ),
        )
    }

    private fun assertInvalid(json: String) {
        assertThrows(IllegalArgumentException::class.java) {
            SavedSearchJson.decode(json)
        }
    }

    private fun document(
        format: String = "eduescale.saved-searches",
        version: Int = 1,
        searches: String = "[]",
    ) = """{"format":"$format","version":$version,"searches":$searches}"""

    private fun search(
        name: String = "Preset",
        management: String = """["publica"]""",
        extra: String = "",
    ) = """{"name":"$name","department":null,"province":null,"district":null,"department_code":null,"province_code":null,"district_code":null,"management":$management,"levels":["primaria"]$extra}"""
}
