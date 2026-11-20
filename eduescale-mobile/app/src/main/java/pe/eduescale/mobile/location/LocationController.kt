package pe.eduescale.mobile.location

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat

/**
 * Owns location permission, provider state and the MapLibre location component.
 * Must be created while the activity is being constructed so the permission launcher is registered in time.
 */
internal class LocationController(
    private val activity: ComponentActivity,
    private val display: LocationDisplay,
    private val onLocationServicesChanged: (Boolean) -> Unit,
    private val onLocationChanged: (Double, Double) -> Unit,
) {
    private val locationManager by lazy { activity.getSystemService(LocationManager::class.java) }
    private var providerReceiverRegistered = false

    private var currentLocationRequest: CancellationSignal? = null

    private val providerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            refreshLocationServices()
        }
    }

    private val requestLocationPermission = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        if (permissions.values.any { it }) {
            showMyLocation()
        } else {
            Toast.makeText(
                activity,
                "Permite el acceso a la ubicación para mostrarla en el mapa.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    fun onStart() {
        val filter = IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION).apply {
            addAction(LocationManager.MODE_CHANGED_ACTION)
        }
        ContextCompat.registerReceiver(
            activity,
            providerReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        providerReceiverRegistered = true
    }

    fun onPause() {
        cancelCurrentLocationRequest()
    }

    fun onStop() {
        if (providerReceiverRegistered) {
            activity.unregisterReceiver(providerReceiver)
            providerReceiverRegistered = false
        }
    }

    fun showMyLocation() {
        if (!hasLocationPermission()) {
            requestLocationPermission.launch(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                ),
            )
            return
        }
        if (!isLocationServicesEnabled()) {
            openLocationSettings()
            return
        }
        enableMyLocation()
    }

    fun refreshLocationServices() {
        val enabled = isLocationServicesEnabled()
        onLocationServicesChanged(enabled)
        if (enabled) {
            if (hasLocationPermission()) {
                enableMyLocation(trackCamera = false)
            }
        } else {
            cancelCurrentLocationRequest()
            display.disable()
        }
    }

    private fun isLocationServicesEnabled(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager.isLocationEnabled
        } else {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }

    private fun openLocationSettings() {
        try {
            activity.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        } catch (error: ActivityNotFoundException) {
            Toast.makeText(
                activity,
                "No se pudo abrir la configuración de ubicación.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    private fun enableMyLocation(trackCamera: Boolean = true) {
        if (!hasLocationPermission()) {
            return
        }
        try {
            if (!display.enable()) return
            updateCurrentLocation()
            if (trackCamera) {
                display.trackCamera()
            }
        } catch (error: SecurityException) {
            Toast.makeText(
                activity,
                "Android no permitió acceder a la ubicación.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    private fun updateCurrentLocation() {
        if (!hasLocationPermission()) {
            return
        }
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
        ).filter(locationManager::isProviderEnabled)
        providers.mapNotNull(locationManager::getLastKnownLocation)
            .maxByOrNull(Location::getTime)
            ?.let { location ->
                onLocationChanged(location.latitude, location.longitude)
            }
        cancelCurrentLocationRequest()
        if (providers.isNotEmpty()) {
            val cancellation = CancellationSignal()
            currentLocationRequest = cancellation
            providers.forEach { provider ->
                LocationManagerCompat.getCurrentLocation(
                    locationManager,
                    provider,
                    cancellation,
                    ContextCompat.getMainExecutor(activity),
                ) { location ->
                    if (location != null && !cancellation.isCanceled) {
                        onLocationChanged(location.latitude, location.longitude)
                        cancellation.cancel()
                    }
                }
            }
        }
    }

    private fun cancelCurrentLocationRequest() {
        currentLocationRequest?.cancel()
        currentLocationRequest = null
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
}
