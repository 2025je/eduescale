package pe.eduescale.mobile.data

data class SchoolCoordinates(
    val latitude: Double,
    val longitude: Double,
)

fun sortSchoolsByDistance(
    schools: List<SchoolRecord>,
    origin: SchoolCoordinates?,
): List<SchoolRecord> {
    if (origin == null) {
        return schools
    }

    return schools.sortedBy { school ->
        distanceInMeters(school, origin) ?: Double.POSITIVE_INFINITY
    }
}

fun distanceInMeters(school: SchoolRecord, origin: SchoolCoordinates): Double? {
    val coordinates = school.coordinates ?: return null
    val startLatitude = origin.latitude
    val startLongitude = origin.longitude
    val endLatitude = coordinates.latitude
    val endLongitude = coordinates.longitude
    val latitudeDifference = Math.toRadians(endLatitude - startLatitude)
    val longitudeDifference = Math.toRadians(endLongitude - startLongitude)
    val haversine = kotlin.math.sin(latitudeDifference / 2).let { sinLatitude ->
        sinLatitude * sinLatitude +
            kotlin.math.cos(Math.toRadians(startLatitude)) *
            kotlin.math.cos(Math.toRadians(endLatitude)) *
            kotlin.math.sin(longitudeDifference / 2).let { sinLongitude ->
                sinLongitude * sinLongitude
            }
    }
    return 2 * EARTH_RADIUS_METERS * kotlin.math.asin(kotlin.math.sqrt(haversine))
}

private const val EARTH_RADIUS_METERS = 6_371_000.0
