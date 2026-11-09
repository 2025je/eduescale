package pe.eduescale.mobile.navigation

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import pe.eduescale.mobile.data.SchoolRecord

/** Opens driving directions and shares a school's location. */
class SchoolNavigator(private val context: Context) {
    fun showNavigationOptions(school: SchoolRecord) {
        if (!school.hasCoordinates) {
            showMissingCoordinatesMessage()
            return
        }
        AlertDialog.Builder(context)
            .setTitle("Cómo llegar en auto")
            .setItems(arrayOf("Google Maps", "Waze")) { _, choice ->
                val coordinates = school.coordinates ?: return@setItems
                val url = when (choice) {
                    0 -> SchoolNavigationLinks.googleMapsDirections(coordinates)
                    1 -> SchoolNavigationLinks.wazeDirections(coordinates)
                    else -> return@setItems
                }
                openNavigationUrl(url)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    fun shareSchoolLocation(school: SchoolRecord) {
        val coordinates = school.coordinates ?: run {
            showMissingCoordinatesMessage()
            return
        }
        val schoolName = school.nombre?.takeIf(String::isNotBlank) ?: "Ubicación del colegio"
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, schoolName)
            putExtra(
                Intent.EXTRA_TEXT,
                "$schoolName\n${SchoolNavigationLinks.googleMapsLocation(coordinates)}",
            )
        }
        context.startActivity(Intent.createChooser(sendIntent, "Compartir ubicación"))
    }

    private fun openNavigationUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        try {
            context.startActivity(intent)
        } catch (error: ActivityNotFoundException) {
            Toast.makeText(
                context,
                "No se encontró una aplicación o navegador para abrir la ruta.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    private fun showMissingCoordinatesMessage() {
        Toast.makeText(
            context,
            "Este colegio no tiene coordenadas disponibles.",
            Toast.LENGTH_LONG,
        ).show()
    }
}
