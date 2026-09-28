package ru.poletrack.app.util

import android.location.Location
import ru.poletrack.app.data.Route
import ru.poletrack.app.data.RoutePoint
import kotlin.math.max

object RouteMath {
    fun geometryMeters(points: List<RoutePoint>): Double {
        if (points.size < 2) return 0.0
        return points.zipWithNext().sumOf { (a, b) ->
            val out = FloatArray(1)
            Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, out)
            out[0].toDouble()
        }
    }

    fun estimatedCableMeters(route: Route, points: List<RoutePoint>): Double {
        val geometry = geometryMeters(points)
        val sag = geometry * route.sagPercent / 100.0
        val pointExtras = points.sumOf { it.extraMeters }
        return geometry + sag + route.startReserveMeters + route.endReserveMeters + pointExtras
    }

    fun remainingSpoolMeters(route: Route, points: List<RoutePoint>): Double =
        max(0.0, route.spoolStartMeters - estimatedCableMeters(route, points))

    fun distanceClass(geometryMeters: Double): String = when {
        geometryMeters <= 400.0 -> "Обычная линия"
        geometryMeters <= 600.0 -> "Длинная линия"
        else -> "Проверить расчёт"
    }
}
