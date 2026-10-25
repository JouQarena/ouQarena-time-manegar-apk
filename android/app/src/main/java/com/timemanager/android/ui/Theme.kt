package com.timemanager.android.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1A73E8), secondary = Color(0xFF34A853), tertiary = Color(0xFFF9AB00),
    background = Color(0xFFF8F9FA), surface = Color.White,
    error = Color(0xFFD93025)
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF8AB4F8), secondary = Color(0xFF81C995), tertiary = Color(0xFFFDE293),
    background = Color(0xFF202124), surface = Color(0xFF303134),
    error = Color(0xFFF28B82)
)

@Composable
fun TimeManagerTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
