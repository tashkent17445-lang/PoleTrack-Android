package ru.poletrack.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.poletrack.app.data.PoleTrackDb
import ru.poletrack.app.data.RoutePoint
import ru.poletrack.app.util.RouteMath
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun NewRouteDialog(
    onDismiss: () -> Unit,
    onCreate: (String, Int, Double, Double, Double) -> Unit
) {
    val defaultName = remember {
        SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date())
    }

    var name by remember { mutableStateOf("Линия $defaultName") }
    var spool by remember { mutableStateOf("1000") }
    var sag by remember { mutableStateOf("3") }
    var startReserve by remember { mutableStateOf("10") }
    var endReserve by remember { mutableStateOf("15") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новая линия") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = spool,
                    onValueChange = { spool = it.filter(Char::isDigit) },
                    label = { Text("Остаток бухты, м") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = sag,
                    onValueChange = { sag = it },
                    label = { Text("Провис / запас, %") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = startReserve,
                        onValueChange = { startReserve = it },
                        label = { Text("Старт, м") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = endReserve,
                        onValueChange = { endReserve = it },
                        label = { Text("Финиш, м") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onCreate(
                        name.ifBlank { "Линия" },
                        spool.toIntOrNull() ?: 1000,
                        sag.toDoubleOrNull() ?: 3.0,
                        startReserve.toDoubleOrNull() ?: 10.0,
                        endReserve.toDoubleOrNull() ?: 15.0
                    )
                }
            ) {
                Text("Начать")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
internal fun PoleDialog(
    point: RoutePoint,
    onDismiss: () -> Unit,
    onSave: (String, String, Double) -> Unit
) {
    var note by remember(point.id) { mutableStateOf(point.note) }
    var tag by remember(point.id) { mutableStateOf(point.tag) }
    var extra by remember(point.id) {
        mutableStateOf(
            if (point.extraMeters == 0.0) "" else point.extraMeters.toString()
        )
    }

    val presets = listOf(
        "⚠️ Может упасть",
        "🔧 Сварка / муфта",
        "🌳 Ветки",
        "🚧 Переход дороги",
        "🪜 Нужна вышка",
        "📦 Запас кабеля"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Комментарий к опоре") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Быстрая метка",
                    style = MaterialTheme.typography.labelLarge
                )

                presets.forEach { preset ->
                    OutlinedButton(
                        onClick = { tag = preset },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(preset)
                    }
                }

                OutlinedTextField(
                    value = tag,
                    onValueChange = { tag = it },
                    label = { Text("Метка") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Комментарий") },
                    placeholder = {
                        Text("Например: может упасть, тут сварка…")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )

                OutlinedTextField(
                    value = extra,
                    onValueChange = { extra = it },
                    label = { Text("Доп. запас кабеля, м") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        note.trim(),
                        tag.trim(),
                        extra.toDoubleOrNull() ?: 0.0
                    )
                }
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Позже") }
        }
    )
}

@Composable
internal fun HistoryDialog(
    db: PoleTrackDb,
    onDismiss: () -> Unit
) {
    val routes = remember { db.getRoutes() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("История линий") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                routes.forEach { route ->
                    val points = db.getPoints(route.id)
                    val geometry = RouteMath.geometryMeters(points)
                    val cable = RouteMath.estimatedCableMeters(route, points)

                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                route.name,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Трасса ${geometry.roundToInt()} м · кабель ~${cable.roundToInt()} м"
                            )
                            Text(
                                if (route.isActive) "В работе" else "Завершена",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                if (routes.isEmpty()) {
                    Text("История пока пустая")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        }
    )
}
