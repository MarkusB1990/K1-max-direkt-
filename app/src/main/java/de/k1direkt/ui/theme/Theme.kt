package de.k1direkt.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val K1Orange = Color(0xFFFF7A1A)
val K1Green = Color(0xFF3DDC84)
val K1Blue = Color(0xFF5B9BFF)
val K1Purple = Color(0xFFB388FF)

private val Dark = darkColorScheme(
    primary = K1Orange,
    onPrimary = Color(0xFF1F0E00),
    primaryContainer = Color(0xFF4A2508),
    onPrimaryContainer = Color(0xFFFFDCC6),
    secondary = K1Blue,
    onSecondary = Color(0xFF00214D),
    secondaryContainer = Color(0xFF1C2C46),
    onSecondaryContainer = Color(0xFFD6E3FF),
    tertiary = K1Purple,
    background = Color(0xFF0E1014),
    onBackground = Color(0xFFE8EAEF),
    surface = Color(0xFF0E1014),
    onSurface = Color(0xFFE8EAEF),
    surfaceVariant = Color(0xFF1F232B),
    onSurfaceVariant = Color(0xFFA9AFBA),
    surfaceContainerLowest = Color(0xFF0A0B0E),
    surfaceContainerLow = Color(0xFF14171C),
    surfaceContainer = Color(0xFF181B21),
    surfaceContainerHigh = Color(0xFF1F232A),
    surfaceContainerHighest = Color(0xFF282C34),
    outline = Color(0xFF3A3F49),
    outlineVariant = Color(0xFF2A2E36),
    error = Color(0xFFFF6B6B),
)

private val Light = lightColorScheme(
    primary = Color(0xFFE0650E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDCC6),
    onPrimaryContainer = Color(0xFF331200),
    secondary = Color(0xFF2F5FBF),
    secondaryContainer = Color(0xFFDCE6FF),
    tertiary = Color(0xFF7A4FD6),
    background = Color(0xFFF6F7F9),
    surface = Color(0xFFF6F7F9),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEEF0F4),
    surfaceContainerHighest = Color(0xFFE4E7EC),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val Base = Typography()
private val AppTypography = Base.copy(
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
)

@Composable
fun K1Theme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        shapes = AppShapes,
        typography = AppTypography,
        content = content,
    )
}
