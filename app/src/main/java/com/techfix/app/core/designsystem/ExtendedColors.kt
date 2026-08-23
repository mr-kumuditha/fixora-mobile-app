package com.techfix.app.core.designsystem

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Material3's ColorScheme has no success/warning/accent/border slots. These
 * carry the design-system tokens that don't map onto it — used for repair
 * status chips, stock levels, key CTAs, and dividers.
 */
data class ExtendedColors(
    val accent: Color,
    val onAccent: Color,
    val success: Color,
    val onSuccess: Color,
    val warning: Color,
    val onWarning: Color,
    /**
     * Use these — not [accent], [success] or [warning] — whenever the color
     * is the ink rather than the fill. See Color.kt for why they differ.
     */
    val accentOnSurface: Color,
    val successOnSurface: Color,
    val warningOnSurface: Color,
    val border: Color,
    val textSecondary: Color,
)

val LocalExtendedColors = staticCompositionLocalOf {
    ExtendedColors(
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
}
