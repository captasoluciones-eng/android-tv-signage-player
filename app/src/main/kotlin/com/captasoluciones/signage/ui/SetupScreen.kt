package com.captasoluciones.signage.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.captasoluciones.signage.data.local.LogEntry
import com.captasoluciones.signage.player.PlayerUiState

/**
 * On-device configuration screen. Reachable by holding DPAD_CENTER/OK for 5s or
 * pressing MENU (see MainActivity.dispatchKeyEvent), and auto-opened whenever the
 * base URL is empty. Contains two tabs: the editable settings form, and the
 * read-only status panel (StatusScreen).
 *
 * Standard Compose Material3 focusable components are used instead of
 * androidx.tv:tv-material - see README "Architecture rationale" for why.
 */
@Composable
fun SetupScreen(
    state: PlayerUiState,
    logEntries: List<LogEntry>,
    onSave: (baseUrl: String, deviceKey: String, deviceName: String, pollMinutes: Int, muteVideo: Boolean) -> Unit,
    onClose: () -> Unit
) {
    var tab by remember { mutableStateOf(0) }
    var baseUrl by remember(state.baseUrl) { mutableStateOf(state.baseUrl) }
    var deviceKey by remember(state.deviceKey) { mutableStateOf(state.deviceKey) }
    var deviceName by remember(state.deviceName) { mutableStateOf(state.deviceName) }
    var pollMinutesText by remember(state.pollMinutes) { mutableStateOf(state.pollMinutes.toString()) }
    var muteVideo by remember(state.muteVideo) { mutableStateOf(state.muteVideo) }

    val firstFieldFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        // The focused field's node may not be placed yet on the first composition;
        // requestFocus() throws IllegalStateException in that case. This is the only
        // way to reach the settings screen on a kiosk box with no pointer input, so a
        // crash here would strand the device -- swallow and let the user just press
        // D-pad down to reach the field manually instead.
        runCatching { firstFieldFocus.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0B0F))
            .padding(32.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "Configuración del reproductor",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(20.dp))

            Row {
                TabButton("Configuración", selected = tab == 0) { tab = 0 }
                Spacer(Modifier.width(16.dp))
                TabButton("Estado", selected = tab == 1) { tab = 1 }
            }
            Spacer(Modifier.height(28.dp))

            if (tab == 0) {
                Column(modifier = Modifier.widthIn(max = 720.dp)) {
                    LabeledField(
                        label = "URL base del servidor (ej. https://ejemplo.com/playlist)",
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        focusRequester = firstFieldFocus
                    )
                    Spacer(Modifier.height(14.dp))
                    LabeledField(
                        label = "Llave del dispositivo (deviceKey, solo tras vincular en el panel)",
                        value = deviceKey,
                        onValueChange = { deviceKey = it }
                    )
                    Spacer(Modifier.height(14.dp))
                    LabeledField(
                        label = "Nombre del dispositivo",
                        value = deviceName,
                        onValueChange = { deviceName = it }
                    )
                    Spacer(Modifier.height(14.dp))
                    LabeledField(
                        label = "Minutos entre sincronizaciones",
                        value = pollMinutesText,
                        onValueChange = { input -> pollMinutesText = input.filter { it.isDigit() } }
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Silenciar video", color = Color.White, modifier = Modifier.weight(1f))
                        Switch(checked = muteVideo, onCheckedChange = { muteVideo = it })
                    }
                    Spacer(Modifier.height(28.dp))
                    Row {
                        Button(onClick = {
                            val minutes = pollMinutesText.toIntOrNull()?.coerceIn(1, 1440) ?: 5
                            onSave(baseUrl.trim(), deviceKey.trim(), deviceName.trim(), minutes, muteVideo)
                        }) {
                            Text("Guardar")
                        }
                        Spacer(Modifier.width(16.dp))
                        if (state.baseUrl.isNotBlank()) {
                            OutlinedButton(onClick = onClose) {
                                Text("Cerrar")
                            }
                        }
                    }
                }
            } else {
                StatusScreen(state = state, logEntries = logEntries)
            }
        }
    }
}

@Composable
private fun TabButton(text: String, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) Color(0xFF3D5AFE) else Color(0xFF2A2A32)
        )
    ) {
        Text(text)
    }
}

@Composable
private fun LabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    focusRequester: FocusRequester? = null
) {
    Column {
        Text(label, color = Color.LightGray, fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
        val fieldModifier = if (focusRequester != null) {
            Modifier.fillMaxWidth().focusRequester(focusRequester)
        } else {
            Modifier.fillMaxWidth()
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            modifier = fieldModifier
        )
    }
}
