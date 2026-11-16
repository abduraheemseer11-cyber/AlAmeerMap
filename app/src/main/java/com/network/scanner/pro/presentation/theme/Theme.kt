package com.network.scanner.pro.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Cyan,
    onPrimary = DeepBlue,
    secondary = Teal,
    onSecondary = DeepBlue,
    tertiary = WarningOrange,
    background = DeepBlue,
    onBackground = Color(0xFFE6F1FF),
    surface = NavyBlue,
    onSurface = Color(0xFFE6F1FF),
    surfaceVariant = SurfaceBlue,
    onSurfaceVariant = MutedText,
    error = DangerRed,
    outline = Color(0xFF3B5A8C)
)

private val LightColors = lightColorScheme(
    primary = CyanDark,
    onPrimary = Color.White,
    secondary = TealDark,
    onSecondary = Color.White,
    tertiary = Color(0xFFE65100),
    background = LightBackground,
    onBackground = DeepBlue,
    surface = Color.White,
    onSurface = DeepBlue,
    surfaceVariant = Color(0xFFE3EDF7),
    onSurfaceVariant = Color(0xFF4A6080),
    error = Color(0xFFB3261E),
    outline = Color(0xFF8FA6C4)
)

/**
 * App theme. Follows the system light/dark setting. Right-to-left layout is
 * automatic: the manifest declares supportsRtl and Arabic strings live in
 * values-ar, so an Arabic device gets a mirrored UI without any extra code.
 */
@Composable
fun ScannerProTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content
    )
}
