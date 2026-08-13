package com.shutterly.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E6E5C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFE9DC),
    onPrimaryContainer = Color(0xFF0B2E22),
    secondary = Color(0xFF5B8A72),
    background = Color(0xFFF7F5F0),
    surface = Color(0xFFFDFBF7),
    surfaceVariant = Color(0xFFEDEAE2),
    error = Color(0xFFC0392B)
)

@Composable
fun ShutterlyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightColors, content = content)
}
