package com.captasoluciones.signage.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SignageDarkColors = darkColorScheme(
    primary = Color(0xFF3D5AFE),
    onPrimary = Color.White,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF121212),
    onSurface = Color.White,
    secondary = Color(0xFF00E5A0),
    error = Color(0xFFFF5252)
)

@Composable
fun SignagePlayerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SignageDarkColors,
        typography = Typography(),
        content = content
    )
}
