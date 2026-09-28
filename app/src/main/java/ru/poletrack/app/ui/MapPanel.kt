package ru.poletrack.app.ui

import android.graphics.Color
import android.location.Location
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import ru.poletrack.app.data.PointType
import ru.poletrack.app.data.RoutePoint

private const val MAP_STYLE = "https://tiles.openfreemap.org/styles/liberty"

data class MapFocus(
    val latitude: Double,
    val longitude: Double,
    val label: String,
    val token: Long = System.nanoTime()
)

@Suppress("DEPRECATION")
@Composable
fun MapPanel(
    points: List<RoutePoint>,
    currentLocation: Location?,
    routeKey: Long?,
    focus: MapFocus?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mapView = remember(context) { MapView(context).also { it.onCreate(null) } }
    val cameraState = remember { mutableMapOf<String, Any?>() }

    DisposableEffect(mapView) {
        mapView.onStart()
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = {
            mapView.apply {
                getMapAsync { map ->
                    map.setStyle(MAP_STYLE) {
                        map.uiSettings.isCompassEnabled = true
                        map.uiSettings.isAttributionEnabled = true
                        map.uiSettings.isLogoEnabled = true
                    }
                }
            }
        },
        update = { view ->
            view.getMapAsync { map ->
                if (map.style == null) return@getMapAsync
                map.clear()

                val linePoints = points.map { LatLng(it.latitude, it.longitude) }
                if (linePoints.size >= 2) {
                    map.addPolyline(
                        PolylineOptions()
                            .add(*linePoints.toTypedArray())
                            .color(Color.rgb(0, 122, 86))
                            .width(5f)
                    )
                }

                points.forEach { point ->
                    val title = when (point.type) {
                        PointType.START -> "Старт"
                        PointType.FINISH -> "Финиш"
                        PointType.POLE -> {
                            if (point.boxNumber.isNotBlank()) {
                                "Ящик №${point.boxNumber}"
                            } else {
                                "Опора ${point.sequence}"
                            }
                        }
                    }
                    val poleInfo = if (point.type == PointType.POLE) "Опора ${point.sequence}" else ""
                    val snippet = listOf(poleInfo, point.tag, point.note)
                        .filter { it.isNotBlank() }
                        .joinToString(" · ")
                    map.addMarker(
                        MarkerOptions()
                            .position(LatLng(point.latitude, point.longitude))
                            .title(title)
                            .snippet(snippet)
                    )
                }

                currentLocation?.let { location ->
                    map.addMarker(
                        MarkerOptions()
                            .position(LatLng(location.latitude, location.longitude))
                            .title("Вы здесь")
                    )
                }

                focus?.let { target ->
                    map.addMarker(
                        MarkerOptions()
                            .position(LatLng(target.latitude, target.longitude))
                            .title(target.label)
                    )
                    if (cameraState["focusToken"] != target.token) {
                        cameraState["focusToken"] = target.token
                        map.cameraPosition = CameraPosition.Builder()
                            .target(LatLng(target.latitude, target.longitude))
                            .zoom(17.5)
                            .build()
                    }
                }

                if (focus == null && routeKey != null && cameraState["routeKey"] != routeKey) {
                    cameraState["routeKey"] = routeKey
                    points.lastOrNull()?.let { last ->
                        map.cameraPosition = CameraPosition.Builder()
                            .target(LatLng(last.latitude, last.longitude))
                            .zoom(16.5)
                            .build()
                    }
                }

                if (
                    focus == null &&
                    routeKey == null &&
                    points.isEmpty() &&
                    cameraState["initialLocation"] != true
                ) {
                    currentLocation?.let { location ->
                        cameraState["initialLocation"] = true
                        map.cameraPosition = CameraPosition.Builder()
                            .target(LatLng(location.latitude, location.longitude))
                            .zoom(16.5)
                            .build()
                    }
                }
            }
        }
    )
}
