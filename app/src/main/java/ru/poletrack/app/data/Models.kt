package ru.poletrack.app.data

enum class PointType {
    START,
    POLE,
    FINISH
}

data class Route(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val finishedAt: Long?,
    val spoolStartMeters: Int,
    val sagPercent: Double,
    val startReserveMeters: Double,
    val endReserveMeters: Double
) {
    val isActive: Boolean get() = finishedAt == null
}

data class RoutePoint(
    val id: Long,
    val routeId: Long,
    val sequence: Int,
    val type: PointType,
    val latitude: Double,
    val longitude: Double,
    val createdAt: Long,
    val note: String,
    val tag: String,
    val extraMeters: Double
)
