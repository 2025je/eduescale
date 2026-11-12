package pe.eduescale.mobile.data

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

data class SavedSearch(
    val name: String,
    val department: String?,
    val province: String?,
    val district: String?,
    val departmentCode: String? = null,
    val provinceCode: String? = null,
    val districtCode: String? = null,
    val management: List<String>,
    val levels: List<String>,
) {
    fun toSchoolFilters() = SchoolFilters(
        departamento = department,
        provincia = province,
        distrito = district,
        gestiones = management.toSet(),
        niveles = levels.toSet(),
    )

    companion object {
        fun from(name: String, filters: SchoolFilters) = SavedSearch(
            name = name.trim(),
            department = filters.departamento,
            province = filters.provincia,
            district = filters.distrito,
            management = filters.gestiones.sorted(),
            levels = filters.niveles.sorted(),
        )
    }
}

object SavedSearchJson {
    private const val FORMAT = "eduescale.saved-searches"
    private const val VERSION = 1
    private val fields = setOf(
        "name",
        "department",
        "province",
        "district",
        "department_code",
        "province_code",
        "district_code",
        "management",
        "levels",
    )
    private val managementValues = setOf("publica", "privada")
    private val levelValues = setOf("primaria", "secundaria", "cetpro")

    fun encode(searches: List<SavedSearch>): String {
        validateSearches(searches)
        val root = JsonObject().apply {
            addProperty("format", FORMAT)
            addProperty("version", VERSION)
            add("searches", JsonArray().apply { searches.forEach { add(it.toJson()) } })
        }
        return root.toString()
    }

    fun decode(json: String): List<SavedSearch> {
        val root = try {
            JsonParser.parseString(json).asJsonObject
        } catch (_: Exception) {
            throw IllegalArgumentException("El archivo no contiene un objeto JSON válido.")
        }
        if (root.keySet() != setOf("format", "version", "searches")) {
            invalid("El documento debe contener format, version y searches.")
        }
        if (root.string("format") != FORMAT) invalid("El formato del archivo no es compatible.")
        val version = root.get("version")
        if (version == null || !version.isJsonPrimitive || !version.asJsonPrimitive.isNumber ||
            version.asInt != VERSION || version.asString != VERSION.toString()
        ) {
            invalid("La versión del archivo no es compatible.")
        }
        val searchArray = root.get("searches")
        if (searchArray == null || !searchArray.isJsonArray) {
            invalid("El campo searches debe ser una lista.")
        }
        val searches = searchArray.asJsonArray.mapIndexed { index, value ->
            if (!value.isJsonObject || value.asJsonObject.keySet() != fields) {
                invalid("La búsqueda ${index + 1} tiene campos ausentes o no admitidos.")
            }
            val item = value.asJsonObject
            SavedSearch(
                name = item.string("name"),
                department = item.nullableString("department"),
                province = item.nullableString("province"),
                district = item.nullableString("district"),
                departmentCode = item.nullableString("department_code"),
                provinceCode = item.nullableString("province_code"),
                districtCode = item.nullableString("district_code"),
                management = item.stringList("management", managementValues),
                levels = item.stringList("levels", levelValues),
            )
        }
        validateSearches(searches)
        return searches
    }

    private fun SavedSearch.toJson() = JsonObject().apply {
        addProperty("name", name)
        addNullable("department", department)
        addNullable("province", province)
        addNullable("district", district)
        addNullable("department_code", departmentCode)
        addNullable("province_code", provinceCode)
        addNullable("district_code", districtCode)
        add("management", management.toJsonArray())
        add("levels", levels.toJsonArray())
    }

    private fun validateSearches(searches: List<SavedSearch>) {
        val names = mutableSetOf<String>()
        searches.forEachIndexed { index, search ->
            if (search.name.isBlank() || search.name != search.name.trim() ||
                search.name.length > 80
            ) {
                invalid("El nombre de la búsqueda ${index + 1} debe tener entre 1 y 80 caracteres.")
            }
            if (!names.add(search.name.lowercase())) {
                invalid("Los nombres de las búsquedas deben ser únicos.")
            }
            if (search.management.any { it !in managementValues } ||
                search.management.distinct().size != search.management.size
            ) {
                invalid("La búsqueda “${search.name}” contiene gestiones no válidas.")
            }
            if (search.levels.any { it !in levelValues } ||
                search.levels.distinct().size != search.levels.size
            ) {
                invalid("La búsqueda “${search.name}” contiene niveles no válidos.")
            }
        }
    }

    private fun JsonObject.string(key: String): String {
        val value = get(key)
        if (value == null || !value.isJsonPrimitive || !value.asJsonPrimitive.isString) {
            invalid("El campo $key debe ser texto.")
        }
        return value.asString
    }

    private fun JsonObject.nullableString(key: String): String? {
        val value = get(key) ?: invalid("Falta el campo $key.")
        if (value.isJsonNull) return null
        if (!value.isJsonPrimitive || !value.asJsonPrimitive.isString) {
            invalid("El campo $key debe ser texto o null.")
        }
        return value.asString
    }

    private fun JsonObject.stringList(key: String, allowed: Set<String>): List<String> {
        val value = get(key)
        if (value == null || !value.isJsonArray) invalid("El campo $key debe ser una lista.")
        return value.asJsonArray.map {
            if (!it.isJsonPrimitive || !it.asJsonPrimitive.isString || it.asString !in allowed) {
                invalid("El campo $key contiene un valor no admitido.")
            }
            it.asString
        }
    }

    private fun JsonObject.addNullable(key: String, value: String?) {
        if (value == null) add(key, com.google.gson.JsonNull.INSTANCE) else addProperty(key, value)
    }

    private fun List<String>.toJsonArray() = JsonArray().also { array ->
        forEach { array.add(it) }
    }

    private fun invalid(message: String): Nothing = throw IllegalArgumentException(message)
}
