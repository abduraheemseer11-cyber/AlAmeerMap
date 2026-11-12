package com.alameer.map.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF0E7C86),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBDEFF2),
    onPrimaryContainer = Color(0xFF002022),
    secondary = Color(0xFFE8731A),
    onSecondary = Color.White,
    tertiary = Color(0xFF1F4E79),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF5FD3DC),
    onPrimary = Color(0xFF00363A),
    primaryContainer = Color(0xFF004F55),
    onPrimaryContainer = Color(0xFFBDEFF2),
    secondary = Color(0xFFFFB27A),
    onSecondary = Color(0xFF4A2400),
    tertiary = Color(0xFF9CC9F5),
)

@Composable
fun AlAmeerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        content = content,
    )
}
