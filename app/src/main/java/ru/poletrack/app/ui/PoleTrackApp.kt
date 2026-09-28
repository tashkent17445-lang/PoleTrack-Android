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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.rememberScrollState
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import ru.poletrack.app.data.SearchHit
import ru.poletrack.app.search.AddressResult
import ru.poletrack.app.search.AddressSearch
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
    var viewedRoute by remember { mutableStateOf<Route?>(null) }
    var points by remember { mutableStateOf(activeRoute?.let { db.getPoints(it.id) }.orEmpty()) }

    var showNewRoute by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showPoints by remember { mutableStateOf(false) }
    var editingPoint by remember { mutableStateOf<RoutePoint?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    var searchQuery by remember { mutableStateOf("") }
    var searchRequest by remember { mutableStateOf(0) }
    var localResults by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    var addressResults by remember { mutableStateOf<List<AddressResult>>(emptyList()) }
    var loadingAddresses by remember { mutableStateOf(false) }
    var showSearchResults by remember { mutableStateOf(false) }
    var mapFocus by remember { mutableStateOf<MapFocus?>(null) }

    val displayedRoute = viewedRoute ?: activeRoute

    fun reloadDisplayed() {
        activeRoute = db.getActiveRoute()
        val route = viewedRoute ?: activeRoute
        points = route?.let { db.getPoints(it.id) }.orEmpty()
    }

    fun openRoute(route: Route) {
        activeRoute = db.getActiveRoute()
        viewedRoute = if (route.isActive && route.id == activeRoute?.id) null else route
        points = db.getPoints(route.id)
        mapFocus = null
        showPoints = false
    }

    LaunchedEffect(searchRequest) {
        if (searchRequest == 0) return@LaunchedEffect
        val query = searchQuery.trim()
        if (query.isBlank()) return@LaunchedEffect

        localResults = db.searchPoints(query)
        addressResults = emptyList()
        loadingAddresses = true
        showSearchResults = true
        addressResults = AddressSearch.search(query)
        loadingAddresses = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Header(route = displayedRoute, onHistory = { showHistory = true })

        SearchBar(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            onSearch = {
                if (searchQuery.isNotBlank()) searchRequest += 1
            }
        )

        if (!hasLocationPermission) {
            PermissionCard(onRequest = requestLocationPermission)
        }

        MapPanel(
            points = points,
            currentLocation = currentLocation,
            routeKey = displayedRoute?.id,
            focus = mapFocus,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        when {
            viewedRoute != null -> {
                RouteViewPanel(
                    route = viewedRoute!!,
                    points = points,
                    showPoints = showPoints,
                    onShowPoints = { showPoints = !showPoints },
                    onEditPoint = { editingPoint = it },
                    onClose = {
                        viewedRoute = null
                        mapFocus = null
                        points = activeRoute?.let { db.getPoints(it.id) }.orEmpty()
                        showPoints = false
                    }
                )
            }

            activeRoute == null -> {
                IdlePanel(
                    currentLocation = currentLocation,
                    onNewRoute = { showNewRoute = true }
                )
            }

            else -> {
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
                            reloadDisplayed()
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
                            viewedRoute = null
                            reloadDisplayed()
                            mapFocus = null
                            message = "Линия сохранена"
                        }
                    },
                    onShowPoints = { showPoints = !showPoints },
                    showPoints = showPoints,
                    onEditPoint = { editingPoint = it }
                )
            }
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
                    viewedRoute = null
                    reloadDisplayed()
                }
            }
        )
    }

    editingPoint?.let { point ->
        PoleDialog(
            point = point,
            onDismiss = { editingPoint = null },
            onSave = { note, tag, boxNumber, extra ->
                db.updatePoint(point.id, note, tag, boxNumber, extra)
                editingPoint = null
                reloadDisplayed()
            }
        )
    }

    if (showHistory) {
        HistoryDialog(
            db = db,
            onDismiss = { showHistory = false },
            onOpen = { route ->
                showHistory = false
                openRoute(route)
            }
        )
    }

    if (showSearchResults) {
        SearchResultsDialog(
            localResults = localResults,
            addressResults = addressResults,
            loadingAddresses = loadingAddresses,
            onDismiss = { showSearchResults = false },
            onOpenPoint = { hit ->
                db.getRoute(hit.point.routeId)?.let { route ->
                    openRoute(route)
                    mapFocus = MapFocus(
                        latitude = hit.point.latitude,
                        longitude = hit.point.longitude,
                        label = if (hit.point.boxNumber.isNotBlank()) {
                            "Ящик №${hit.point.boxNumber}"
                        } else {
                            "Опора ${hit.point.sequence}"
                        }
                    )
                }
                showSearchResults = false
            },
            onOpenAddress = { address ->
                mapFocus = MapFocus(
                    latitude = address.latitude,
                    longitude = address.longitude,
                    label = address.displayName
                )
                showSearchResults = false
            }
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
            .padding(horizontal = 16.dp, vertical = 8.dp),
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
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Адрес, № ящика, метка…") },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        Button(
            onClick = onSearch,
            enabled = query.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = PoleGreen)
        ) {
            Text("Найти")
        }
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
            if (currentLocation == null) "Ищу GPS…" else "GPS найден. Можно начинать трассу.",
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
        MetricsRow(geometry, cable, remaining)

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

        PointsToggleAndList(poles, showPoints, onShowPoints, onEditPoint)
    }
}

@Composable
private fun RouteViewPanel(
    route: Route,
    points: List<RoutePoint>,
    showPoints: Boolean,
    onShowPoints: () -> Unit,
    onEditPoint: (RoutePoint) -> Unit,
    onClose: () -> Unit
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
        Text("Просмотр сохранённой линии", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        MetricsRow(geometry, cable, remaining)
        Spacer(Modifier.height(8.dp))
        Text("Опор: ${poles.size}", style = MaterialTheme.typography.bodySmall)

        PointsToggleAndList(poles, showPoints, onShowPoints, onEditPoint)

        OutlinedButton(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Закрыть просмотр")
        }
    }
}

@Composable
private fun MetricsRow(
    geometry: Double,
    cable: Double,
    remaining: Double
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Metric("Трасса", "${geometry.roundToInt()} м", Modifier.weight(1f))
        Metric("Кабель", "~${cable.roundToInt()} м", Modifier.weight(1f))
        Metric("Остаток", "~${remaining.roundToInt()} м", Modifier.weight(1f))
    }
}

@Composable
private fun PointsToggleAndList(
    poles: List<RoutePoint>,
    showPoints: Boolean,
    onShowPoints: () -> Unit,
    onEditPoint: (RoutePoint) -> Unit
) {
    TextButton(
        onClick = onShowPoints,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(if (showPoints) "Скрыть опоры" else "Опоры и заметки (${poles.size})")
    }

    if (showPoints) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
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
                Text("Опор пока нет", style = MaterialTheme.typography.bodySmall)
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
            if (point.boxNumber.isNotBlank()) "Ящик №${point.boxNumber}" else "Опора $number",
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(0.4f)
        )
        Column(modifier = Modifier.weight(0.6f)) {
            if (point.boxNumber.isNotBlank()) {
                Text("Опора $number", style = MaterialTheme.typography.labelSmall)
            }
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
