package com.captasoluciones.signage.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.captasoluciones.signage.player.PlayerUiState

/**
 * Shown only when there is no content to loop yet (never simply "black") - either the
 * device has never received a successful playlist, or the currently held playlist has
 * no playable items. Keeps the pairing code visible until the device is linked, and
 * surfaces the last error so a technician can diagnose connectivity issues on-site.
 */
@Composable
fun WaitingScreen(state: PlayerUiState) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101014)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Esperando contenido...", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Dispositivo: ${state.deviceName.ifBlank { state.deviceId.take(8) }}",
                color = Color.Gray,
                fontSize = 18.sp
            )

            if (state.pairingCode.isNotBlank() && !state.linked) {
                Spacer(Modifier.height(28.dp))
                Text("Código de emparejamiento", color = Color.White, fontSize = 18.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = state.pairingCode,
                    color = Color(0xFF00E5A0),
                    fontSize = 60.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (state.lastError.isNotBlank()) {
                Spacer(Modifier.height(28.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("Reintentando conexión...", color = Color(0xFFFFC400), fontSize = 16.sp)
                    Text(state.lastError, color = Color(0xFF9E9E9E), fontSize = 13.sp)
                }
            }
        }
    }
}
