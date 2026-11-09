package pe.eduescale.mobile.navigation

import android.net.Uri
import pe.eduescale.mobile.data.SchoolCoordinates

internal object SchoolNavigationLinks {
    fun googleMapsLocation(coordinates: SchoolCoordinates): String =
        "https://www.google.com/maps/search/?api=1&query=" +
            Uri.encode("${coordinates.latitude},${coordinates.longitude}")

    fun googleMapsDirections(coordinates: SchoolCoordinates): String =
        Uri.parse("https://www.google.com/maps/dir/").buildUpon()
            .appendQueryParameter("api", "1")
            .appendQueryParameter(
                "destination",
                "${coordinates.latitude},${coordinates.longitude}",
            )
            .appendQueryParameter("travelmode", "driving")
            .build()
            .toString()

    fun wazeDirections(coordinates: SchoolCoordinates): String =
        Uri.parse("https://waze.com/ul").buildUpon()
            .appendQueryParameter("ll", "${coordinates.latitude},${coordinates.longitude}")
            .appendQueryParameter("navigate", "yes")
            .build()
            .toString()
}
