package ru.poletrack.app.ui

import android.location.Location
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.poletrack.app.data.PointType
import ru.poletrack.app.data.PoleTrackDb
import ru.poletrack.app.data.Route
import ru.poletrack.app.data.RoutePoint
import ru.poletrack.app.util.RouteMath
import kotlin.math.roundToInt

private val PoleGreen = Color(0xFF007A56)
private val SoftGreen = Color(0xFFE6F4EF)
private val Background = Color(0xFFF7F9F8)

@Composable
fun PoleTrackApp(
    db: PoleTrackDb,
    currentLocation: Location?,
    hasLocationPermission: Boolean,
    requestLocationPermission: () -> Unit
) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = Background) {
            AppContent(db, currentLocation, hasLocationPermission, requestLocationPermission)
        }
    }
}

@Composable
private fun AppContent(
    db: PoleTrackDb,
    currentLocation: Location?,
    hasLocationPermission: Boolean,
    requestLocationPermission: () -> Unit
) {
    var activeRoute by remember { mutableStateOf(db.getActiveRoute()) }
    var points by remember { mutableStateOf(activeRoute?.let { db.getPoints(it.id) }.orEmpty()) }
    var showNewRoute by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showPoints by remember { mutableStateOf(false) }
    var editingPoint by remember { mutableStateOf<RoutePoint?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    fun reload() {
        activeRoute = db.getActiveRoute()
        points = activeRoute?.let { db.getPoints(it.id) }.orEmpty()
    }

    LaunchedEffect(activeRoute?.id) {
        points = activeRoute?.let { db.getPoints(it.id) }.orEmpty()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Header(route = activeRoute, onHistory = { showHistory = true })

        if (!hasLocationPermission) {
            PermissionCard(onRequest = requestLocationPermission)
        }

        MapPanel(
            points = points,
            currentLocation = currentLocation,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        if (activeRoute == null) {
            IdlePanel(
                currentLocation = currentLocation,
                onNewRoute = { showNewRoute = true }
            )
        } else {
            ActiveRoutePanel(
                route = activeRoute!!,
                points = points,
                currentLocation = currentLocation,
                onAddPole = {
                    val loc = currentLocation
                    if (loc == null) {
                        message = "Нет GPS-координат. Подожди несколько секунд."
                    } else {
                        val point = db.addPoint(
                            activeRoute!!.id,
                            PointType.POLE,
                            loc.latitude,
                            loc.longitude
                        )
                        reload()
                        editingPoint = point
                    }
                },
                onFinish = {
                    val loc = currentLocation
                    if (loc == null) {
                        message = "Нет GPS-координат. Подожди несколько секунд."
                    } else {
                        db.addPoint(
                            activeRoute!!.id,
                            PointType.FINISH,
                            loc.latitude,
                            loc.longitude
                        )
                        db.finishRoute(activeRoute!!.id)
                        reload()
                        message = "Линия сохранена"
                    }
                },
                onShowPoints = { showPoints = !showPoints },
                showPoints = showPoints,
                onEditPoint = { editingPoint = it }
            )
        }
    }

    if (showNewRoute) {
        NewRouteDialog(
            onDismiss = { showNewRoute = false },
            onCreate = { name, spool, sag, startReserve, endReserve ->
                val loc = currentLocation
                if (loc == null) {
                    message = "Для старта линии сначала нужен GPS"
                } else {
                    val route = db.createRoute(
                        name,
                        spool,
                        sag,
                        startReserve,
                        endReserve
                    )
                    db.addPoint(
                        route.id,
                        PointType.START,
                        loc.latitude,
                        loc.longitude
                    )
                    showNewRoute = false
                    reload()
                }
            }
        )
    }

    editingPoint?.let { point ->
        PoleDialog(
            point = point,
            onDismiss = { editingPoint = null },
            onSave = { note, tag, extra ->
                db.updatePoint(point.id, note, tag, extra)
                editingPoint = null
                reload()
            }
        )
    }

    if (showHistory) {
        HistoryDialog(
            db = db,
            onDismiss = { showHistory = false }
        )
    }

    message?.let { text ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { message = null },
            confirmButton = {
                TextButton(onClick = { message = null }) { Text("ОК") }
            },
            title = { Text("PoleTrack") },
            text = { Text(text) }
        )
    }
}

@Composable
private fun Header(route: Route?, onHistory: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "PoleTrack",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                route?.name ?: "Полевой помощник монтажника",
                style = MaterialTheme.typography.bodySmall
            )
        }
        TextButton(onClick = onHistory) { Text("История") }
    }
}

@Composable
private fun PermissionCard(onRequest: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3CD))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Нужен доступ к геолокации", modifier = Modifier.weight(1f))
            Button(onClick = onRequest) { Text("Разрешить") }
        }
    }
}

@Composable
private fun IdlePanel(
    currentLocation: Location?,
    onNewRoute: () -> Unit
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            if (currentLocation == null) {
                "Ищу GPS…"
            } else {
                "GPS найден. Можно начинать трассу."
            },
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onNewRoute,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = PoleGreen),
            enabled = currentLocation != null
        ) {
            Text("Новая линия")
        }
    }
}

@Composable
private fun ActiveRoutePanel(
    route: Route,
    points: List<RoutePoint>,
    currentLocation: Location?,
    onAddPole: () -> Unit,
    onFinish: () -> Unit,
    onShowPoints: () -> Unit,
    showPoints: Boolean,
    onEditPoint: (RoutePoint) -> Unit
) {
    val geometry = RouteMath.geometryMeters(points)
    val cable = RouteMath.estimatedCableMeters(route, points)
    val remaining = RouteMath.remainingSpoolMeters(route, points)
    val poles = points.filter { it.type == PointType.POLE }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(14.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Metric("Трасса", "${geometry.roundToInt()} м", Modifier.weight(1f))
            Metric("Кабель", "~${cable.roundToInt()} м", Modifier.weight(1f))
            Metric("Остаток", "~${remaining.roundToInt()} м", Modifier.weight(1f))
        }

        Spacer(Modifier.height(8.dp))

        Text(
            "${RouteMath.distanceClass(geometry)} · опор: ${poles.size} · GPS ${if (currentLocation == null) "нет" else "есть"}",
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onAddPole,
                modifier = Modifier.weight(1f),
                enabled = currentLocation != null,
                colors = ButtonDefaults.buttonColors(containerColor = PoleGreen)
            ) {
                Text("＋ Опора")
            }
            OutlinedButton(
                onClick = onFinish,
                modifier = Modifier.weight(1f),
                enabled = currentLocation != null
            ) {
                Text("Финиш")
            }
        }

        TextButton(
            onClick = onShowPoints,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (showPoints) {
                    "Скрыть опоры"
                } else {
                    "Опоры и заметки (${poles.size})"
                }
            )
        }

        if (showPoints) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                poles.forEachIndexed { index, point ->
                    PoleRow(
                        number = index + 1,
                        point = point,
                        onClick = { onEditPoint(point) }
                    )
                }
                if (poles.isEmpty()) {
                    Text(
                        "Опор пока нет",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun Metric(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(SoftGreen, RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(value, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PoleRow(
    number: Int,
    point: RoutePoint,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Опора $number",
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(0.35f)
        )
        Column(modifier = Modifier.weight(0.65f)) {
            if (point.tag.isNotBlank()) {
                Text(
                    point.tag,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }
            Text(
                point.note.ifBlank { "Без комментария" },
                style = MaterialTheme.typography.bodySmall
            )
            if (point.extraMeters > 0) {
                Text(
                    "Запас +${point.extraMeters.roundToInt()} м",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
