package com.captasoluciones.signage.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.captasoluciones.signage.data.local.LogEntry
import com.captasoluciones.signage.player.PlayerUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Status panel: deviceId, pairingCode, last sync time, item count, last error, app
 * version, plus the rotating in-memory log of the last 200 events.
 */
@Composable
fun StatusScreen(state: PlayerUiState, logEntries: List<LogEntry>) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }

    Row(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.weight(1f).padding(end = 24.dp)) {
            StatusRow("ID de dispositivo", state.deviceId)
            StatusRow("Código de emparejamiento", state.pairingCode)
            StatusRow("Vinculado", if (state.linked) "Sí" else "No")
            StatusRow(
                "Última sincronización",
                if (state.lastSyncTime > 0) dateFormat.format(Date(state.lastSyncTime)) else "Nunca"
            )
            StatusRow("Items cargados", state.itemCount.toString())
            StatusRow("Último error", state.lastError.ifBlank { "Ninguno" })
            StatusRow("Versión de la app", state.appVersion)
        }

        Column(modifier = Modifier.weight(1.4f)) {
            Text(
                text = "Registro de eventos (últimos ${logEntries.size} de 200)",
                color = Color.White,
                fontSize = 16.sp
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF16161C))
            ) {
                items(logEntries.asReversed()) { entry ->
                    LogRow(entry, dateFormat)
                }
            }
        }
    }
}

@Composable
private fun LogRow(entry: LogEntry, dateFormat: SimpleDateFormat) {
    val time = remember(entry.timestampMs) { dateFormat.format(Date(entry.timestampMs)) }
    Text(
        text = "$time  ${entry.message}",
        color = Color(0xFFB0B0B0),
        fontSize = 12.sp,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

@Composable
private fun StatusRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(label, color = Color.Gray, fontSize = 12.sp)
        Text(value, color = Color.White, fontSize = 18.sp)
    }
}
