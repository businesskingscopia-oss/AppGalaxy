package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val AppGalaxyLightColorScheme = lightColorScheme(
    primary = GalaxyBlue,
    onPrimary = GalaxyWhite,
    primaryContainer = GalaxyBlueContainer,
    onPrimaryContainer = GalaxyOnBlueContainer,
    secondary = GalaxySlate700,
    onSecondary = GalaxyWhite,
    secondaryContainer = ColorTokens.LightSurfaceVariant,
    onSecondaryContainer = GalaxySlate900,
    tertiary = CyanAccent,
    onTertiary = GalaxyWhite,
    background = GalaxyLightBg,
    onBackground = GalaxySlate900,
    surface = GalaxyWhite,
    onSurface = GalaxySlate900,
    surfaceVariant = ColorTokens.LightSurfaceVariant,
    onSurfaceVariant = GalaxySlate500,
    surfaceContainerLowest = GalaxyWhite,
    surfaceContainerLow = GalaxyLightBg,
    surfaceContainer = ColorTokens.LightSurfaceContainer,
    surfaceContainerHigh = ColorTokens.LightSurfaceContainerHigh,
    outline = GalaxyBorderLight,
    outlineVariant = GalaxyBorderLight,
    error = RoseError,
    onError = GalaxyWhite,
    errorContainer = RoseContainer,
    onErrorContainer = GalaxySlate900
)

private val AppGalaxyDarkColorScheme = darkColorScheme(
    primary = GalaxyBlueLight,
    onPrimary = GalaxyDarkBg,
    primaryContainer = GalaxyBlueDark,
    onPrimaryContainer = GalaxyWhite,
    secondary = GalaxySlate400,
    onSecondary = GalaxyDarkBg,
    secondaryContainer = GalaxySlate800,
    onSecondaryContainer = GalaxyWhite,
    tertiary = CyanAccent,
    onTertiary = GalaxyDarkBg,
    background = GalaxyDarkBg,
    onBackground = GalaxyWhite,
    surface = GalaxyDarkSurface,
    onSurface = GalaxyWhite,
    surfaceVariant = GalaxySlate800,
    onSurfaceVariant = GalaxySlate400,
    surfaceContainerLowest = GalaxyDarkBg,
    surfaceContainerLow = GalaxyDarkSurface,
    surfaceContainer = GalaxySlate800,
    surfaceContainerHigh = GalaxySlate700,
    outline = GalaxyBorderDark,
    outlineVariant = GalaxyBorderDark,
    error = RoseError,
    onError = GalaxyWhite,
    errorContainer = ColorTokens.DarkErrorContainer,
    onErrorContainer = GalaxyWhite
)

private object ColorTokens {
    val LightSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFF1F5F9)
    val LightSurfaceContainer = androidx.compose.ui.graphics.Color(0xFFF8FAFC)
    val LightSurfaceContainerHigh = androidx.compose.ui.graphics.Color(0xFFEFF6FF)
    val DarkErrorContainer = androidx.compose.ui.graphics.Color(0xFF7F1D1D)
}

val AppGalaxyShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun AppGalaxyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) AppGalaxyDarkColorScheme else AppGalaxyLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppGalaxyTypography,
        shapes = AppGalaxyShapes,
        content = content
    )
}
