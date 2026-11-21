package de.k1direkt.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val K1Orange = Color(0xFFFF7A1A)

private val Dark = darkColorScheme(
    primary = K1Orange,
    onPrimary = Color(0xFF1A0C00),
    primaryContainer = Color(0xFF5A2A00),
    onPrimaryContainer = Color(0xFFFFDBC7),
    secondary = Color(0xFF8FB4FF),
    background = Color(0xFF14161A),
    surface = Color(0xFF14161A),
    surfaceVariant = Color(0xFF242830),
    surfaceContainer = Color(0xFF1C1F25),
    surfaceContainerHigh = Color(0xFF242830),
    onSurfaceVariant = Color(0xFFC3C7CF),
)

private val Light = lightColorScheme(
    primary = Color(0xFFD95F06),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBC7),
    onPrimaryContainer = Color(0xFF331200),
    secondary = Color(0xFF3B5BA9),
)

@Composable
fun K1Theme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
