package pe.eduescale.mobile.data

import java.text.Normalizer
import java.util.Locale

data class SchoolFilters(
    val departamento: String? = null,
    val provincia: String? = null,
    val distrito: String? = null,
    val gestiones: Set<String> = emptySet(),
    val niveles: Set<String> = emptySet(),
)

fun filterSchools(
    schools: List<SchoolRecord>,
    nameQuery: String,
    filters: SchoolFilters,
): List<SchoolRecord> {
    val normalizedQuery = normalize(nameQuery.trim())
    return schools.filter { school ->
        (normalizedQuery.isEmpty() || normalize(school.nombre.orEmpty()).contains(normalizedQuery)) &&
            (filters.departamento == null || school.departamento == filters.departamento) &&
            (filters.provincia == null || school.provincia == filters.provincia) &&
            (filters.distrito == null || school.distrito == filters.distrito) &&
            (filters.gestiones.isEmpty() || school.gestion in filters.gestiones) &&
            (filters.niveles.isEmpty() || school.niveles.any(filters.niveles::contains))
    }
}

private fun normalize(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(ACCENT_MARKS, "")
        .lowercase(Locale.ROOT)

private val ACCENT_MARKS = "\\p{Mn}+".toRegex()
