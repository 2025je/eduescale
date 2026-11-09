package pe.eduescale.mobile.map

internal object SchoolMarkerStyle {
    const val PUBLIC_COLOR = "#176B50"
    const val PRIVATE_COLOR = "#D97706"
    const val UNKNOWN_COLOR = "#7B8794"
    const val INITIAL_RADIUS = 6.0
    const val PRIMARY_RADIUS = 8.0
    const val SECONDARY_RADIUS = 10.0
    const val UNKNOWN_RADIUS = 7.0

    fun color(gestion: String?): String =
        when (gestion) {
            "publica" -> PUBLIC_COLOR
            "privada" -> PRIVATE_COLOR
            else -> UNKNOWN_COLOR
        }

    fun radius(niveles: List<String>): Double =
        when {
            "secundaria" in niveles -> SECONDARY_RADIUS
            "primaria" in niveles -> PRIMARY_RADIUS
            "inicial" in niveles -> INITIAL_RADIUS
            else -> UNKNOWN_RADIUS
        }
}
