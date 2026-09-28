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

@Suppress("DEPRECATION")
@Composable
fun MapPanel(
    points: List<RoutePoint>,
    currentLocation: Location?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mapView = remember(context) { MapView(context).also { it.onCreate(null) } }
    val firstCameraTarget = remember { mutableMapOf<String, Boolean>() }

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
                            .add(linePoints.toTypedArray())
                            .color(Color.rgb(0, 122, 86))
                            .width(5f)
                    )
                }

                points.forEach { point ->
                    val title = when (point.type) {
                        PointType.START -> "Старт"
                        PointType.FINISH -> "Финиш"
                        PointType.POLE -> "Опора ${point.sequence}"
                    }
                    val snippet = listOf(point.tag, point.note)
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
                    if (firstCameraTarget.putIfAbsent("done", true) == null && points.isEmpty()) {
                        map.cameraPosition = CameraPosition.Builder()
                            .target(LatLng(location.latitude, location.longitude))
                            .zoom(16.5)
                            .build()
                    }
                }

                if (points.isNotEmpty() && firstCameraTarget.putIfAbsent("route", true) == null) {
                    val last = points.last()
                    map.cameraPosition = CameraPosition.Builder()
                        .target(LatLng(last.latitude, last.longitude))
                        .zoom(16.5)
                        .build()
                }
            }
        }
    )
}
