package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun MyApplicationTheme(
    accentColor: Color = Color(0xFF007AFF),
    themeMode: String = "Normal",
    content: @Composable () -> Unit,
) {
    val isDark = when (themeMode) {
        "Light" -> false
        "OLED", "AMOLED" -> true
        else -> true // Dark is default
    }

    val colorScheme = if (isDark) {
        val backgroundCanvas = if (themeMode in listOf("OLED", "AMOLED")) {
            Color(0xFF000000) // Pure OLED pitch black
        } else {
            Color(0xFF0F172A) // Beautiful dark slate navy
        }

        val surfaceCanvas = if (themeMode in listOf("OLED", "AMOLED")) {
            Color(0xFF121212)
        } else {
            Color(0xFF1E293B)
        }

        darkColorScheme(
            primary = accentColor,
            secondary = accentColor.copy(alpha = 0.7f),
            tertiary = Color(0xFFFF9500),
            background = backgroundCanvas,
            surface = surfaceCanvas,
            onPrimary = Color.White,
            onSecondary = Color.White,
            onBackground = Color.White,
            onSurface = Color.White
        )
    } else {
        lightColorScheme(
            primary = accentColor,
            secondary = accentColor.copy(alpha = 0.7f),
            tertiary = Color(0xFFFF9500),
            background = Color(0xFFF8FAFC),
            surface = Color.White,
            onPrimary = Color.White,
            onSecondary = Color.White,
            onBackground = Color(0xFF0F172A),
            onSurface = Color(0xFF0F172A)
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
