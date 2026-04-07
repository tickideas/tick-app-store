package com.tickideas.appstore.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

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

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    onPrimary = Color(0xFF00296B),
    primaryContainer = Color(0xFF003A94),
    onPrimaryContainer = Color(0xFFD3E3FD),
    secondary = Color(0xFFC4C7C5),
    onSecondary = Color(0xFF2D312F),
    secondaryContainer = Color(0xFF3B3F3D),
    surface = Color(0xFF1F1F1F),
    onSurface = Color(0xFFE3E3E3),
    onSurfaceVariant = Color(0xFF9AA0A6),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE3E3E3),
    error = Color(0xFFCF6679),
    outline = Color(0xFF444746),
)

@Composable
fun TickAppStoreTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // Material You dynamic colors on Android 12+
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
