package com.tickideas.appstore.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1A73E8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E3FD),
    onPrimaryContainer = Color(0xFF001A41),
    secondary = Color(0xFF5F6368),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8EAED),
    surface = Color(0xFFFAFAFA),
    onSurface = Color(0xFF202124),
    onSurfaceVariant = Color(0xFF5F6368),
    background = Color.White,
    onBackground = Color(0xFF202124),
    error = Color(0xFFB00020),
    outline = Color(0xFFDADCE0),
)

@Composable
fun TickAppStoreTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        content = content,
    )
}
