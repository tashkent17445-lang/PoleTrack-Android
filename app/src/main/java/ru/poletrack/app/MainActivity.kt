package ru.poletrack.app

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import org.maplibre.android.MapLibre
import ru.poletrack.app.data.PoleTrackDb
import ru.poletrack.app.location.LocationTracker
import ru.poletrack.app.ui.PoleTrackApp

class MainActivity : ComponentActivity() {

    private lateinit var db: PoleTrackDb
    private lateinit var locationTracker: LocationTracker

    private var currentLocation by mutableStateOf<Location?>(null)
    private var hasLocationPermission by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasLocationPermission = grants.values.any { it }
        if (hasLocationPermission) locationTracker.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)

        db = PoleTrackDb(applicationContext)
        locationTracker = LocationTracker(applicationContext) { location ->
            currentLocation = location
        }
        hasLocationPermission = checkLocationPermission()

        setContent {
            PoleTrackApp(
                db = db,
                currentLocation = currentLocation,
                hasLocationPermission = hasLocationPermission,
                requestLocationPermission = ::requestLocationPermission
            )
        }
    }

    override fun onStart() {
        super.onStart()
        if (checkLocationPermission()) {
            hasLocationPermission = true
            locationTracker.start()
        }
    }

    override fun onStop() {
        locationTracker.stop()
        super.onStop()
    }

    private fun requestLocationPermission() {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun checkLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }
}
