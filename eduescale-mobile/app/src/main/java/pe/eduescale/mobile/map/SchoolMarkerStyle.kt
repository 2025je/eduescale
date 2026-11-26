package pe.eduescale.mobile.map

internal object SchoolMarkerStyle {
    const val PUBLIC_COLOR = "#176B50"
    const val PARISH_COLOR = "#6B4E9B"
    const val MIXED_COLOR = "#167A8B"
    const val PRIVATE_COLOR = "#D97706"
    const val UNKNOWN_COLOR = "#7B8794"
    const val PRIMARY_RADIUS = 8.0
    const val SECONDARY_RADIUS = 10.0
    const val CETPRO_RADIUS = 12.0
    const val UNKNOWN_RADIUS = 7.0

    fun color(gestion: String?, dependencies: Set<String>): String {
        if (gestion == "privada") return PRIVATE_COLOR
        if (gestion != "publica") return UNKNOWN_COLOR

        val hasDirect = dependencies.any { it in DIRECT_MANAGEMENT_CODES }
        val hasParish = "A4" in dependencies
        return when {
            hasDirect && hasParish -> MIXED_COLOR
            hasParish -> PARISH_COLOR
            hasDirect -> PUBLIC_COLOR
            else -> UNKNOWN_COLOR
        }
    }

    fun levelGlyphs(niveles: List<String>): String =
        LEVEL_GLYPHS.entries
            .filter { (level, _) -> level in niveles }
            .joinToString(" · ") { (_, glyph) -> glyph }

    fun radius(niveles: List<String>): Double =
        when {
            "cetpro" in niveles -> CETPRO_RADIUS
            "secundaria" in niveles -> SECONDARY_RADIUS
            "primaria" in niveles -> PRIMARY_RADIUS
            else -> UNKNOWN_RADIUS
        }

    private val DIRECT_MANAGEMENT_CODES = setOf("A1", "A2", "A3")
    private val LEVEL_GLYPHS = linkedMapOf(
        "inicial" to "I",
        "primaria" to "P",
        "secundaria" to "S",
        "basica_alternativa" to "BA",
        "educacion_especial" to "EE",
        "superior_pedagogica" to "SP",
        "superior_artistica" to "SA",
        "superior_tecnologica" to "ST",
        "cetpro" to "C",
    )
}
