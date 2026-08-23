package com.techfix.app.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val FixoraLightScheme = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = OnPrimaryLight,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,
    secondary = AccentLight,
    onSecondary = OnAccentLight,
    background = BackgroundLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = TextSecondaryLight,
    outline = BorderLight,
    outlineVariant = BorderLight,
    error = ErrorLight,
    onError = OnErrorLight,
)

private val FixoraDarkScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = AccentDark,
    onSecondary = OnAccentDark,
    background = BackgroundDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = BorderDark,
    outlineVariant = BorderDark,
    error = ErrorDark,
    onError = OnStatusDark,
)

private val LightExtendedColors = ExtendedColors(
    accent = AccentLight,
    onAccent = OnAccentLight,
    success = SuccessLight,
    onSuccess = OnSuccessLight,
    warning = WarningLight,
    onWarning = OnWarningLight,
    accentOnSurface = AccentOnSurfaceLight,
    successOnSurface = SuccessOnSurfaceLight,
    warningOnSurface = WarningOnSurfaceLight,
    border = BorderLight,
    textSecondary = TextSecondaryLight,
)

private val DarkExtendedColors = ExtendedColors(
    accent = AccentDark,
    onAccent = OnAccentDark,
    success = SuccessDark,
    onSuccess = OnStatusDark,
    warning = WarningDark,
    onWarning = OnStatusDark,
    // Dark mode reuses the fill colors as ink — they already read as light
    // text on the dark surfaces.
    accentOnSurface = AccentDark,
    successOnSurface = SuccessDark,
    warningOnSurface = WarningDark,
    border = BorderDark,
    textSecondary = TextSecondaryDark,
)

object FixoraTheme {
    val extendedColors: ExtendedColors
        @Composable
        get() = LocalExtendedColors.current
}

@Composable
fun FixoraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) FixoraDarkScheme else FixoraLightScheme
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors

    CompositionLocalProvider(LocalExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = FixoraTypography,
            shapes = FixoraShapes,
            content = content,
        )
    }
}
