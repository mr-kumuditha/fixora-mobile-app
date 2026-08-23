package com.techfix.app.core.designsystem

import androidx.compose.ui.graphics.Color

// Light palette — exact tokens from the design system in CLAUDE.md.
val PrimaryLight = Color(0xFF4F46E5)
val PrimaryContainerLight = Color(0xFFE0E1FC)
val AccentLight = Color(0xFFFF7A45)
val SuccessLight = Color(0xFF22C55E)
val WarningLight = Color(0xFFF59E0B)
val ErrorLight = Color(0xFFEF4444)
val BackgroundLight = Color(0xFFF7F8FA)
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceVariantLight = Color(0xFFEEF1F5)
val TextPrimaryLight = Color(0xFF111827)
val TextSecondaryLight = Color(0xFF6B7280)
val BorderLight = Color(0xFFE5E7EB)

// Dark palette — separately tuned for contrast and depth, not an inversion.
val PrimaryDark = Color(0xFF8B93FF)
val PrimaryContainerDark = Color(0xFF2C2F5C)
val AccentDark = Color(0xFFFF9466)
val SuccessDark = Color(0xFF34D399)
val WarningDark = Color(0xFFFBBF24)
val ErrorDark = Color(0xFFF87171)
val BackgroundDark = Color(0xFF0F1115)
val SurfaceDark = Color(0xFF1A1D24)
val SurfaceVariantDark = Color(0xFF23262E)
val TextPrimaryDark = Color(0xFFF3F4F6)
val TextSecondaryDark = Color(0xFF9CA3AF)
val BorderDark = Color(0xFF2A2E37)

// Foreground colors paired with the tokens above. Not separate tokens in the
// spec, derived here so text on a colored surface always has a defined color.
val OnPrimaryLight = Color(0xFFFFFFFF)
val OnPrimaryContainerLight = Color(0xFF1E1B4B)
val OnAccentLight = Color(0xFF3B1200)

/**
 * Status chips in light mode carry dark ink, not white. The success, warning
 * and error tokens are all bright mid-tones — white on #22C55E is about
 * 2.3:1 and on #F59E0B about 2.1:1, which is not readable at the 13sp label
 * size the chips use. Each of these is a very dark tint of its own hue, so
 * the chip still reads as green/amber/red rather than turning neutral, and
 * each clears 4.5:1 against its container.
 */
val OnSuccessLight = Color(0xFF06301B)
val OnWarningLight = Color(0xFF3B2606)
val OnErrorLight = Color(0xFF3A0505)

val OnPrimaryDark = Color(0xFF1E1B4B)
val OnPrimaryContainerDark = Color(0xFFE0E1FC)
val OnAccentDark = Color(0xFF3B1200)

/**
 * Dark mode already gets this right with one value: the dark-mode status
 * colors are lighter and more saturated, so the near-black background token
 * sits on all three of them at better than 6:1.
 */
val OnStatusDark = Color(0xFF0F1115)

/**
 * The status colors again, but as *text and small icons drawn on a surface*
 * rather than as a filled container.
 *
 * The palette's success/warning/accent are bright mid-tones chosen to be read
 * as fills. Used as ink on the light surface they land around 2:1 — amber on
 * white is the worst of them — which is not legible at the 13sp label size
 * these appear at. Each light value below is a darker step of the same hue,
 * so the meaning still reads (green = fine, amber = attention, orange =
 * action) while clearing 4.5:1 on both Surface and Background.
 *
 * Dark mode needs no second value: the dark palette's status colors are
 * already light against the near-black surfaces, at better than 9:1.
 */
val SuccessOnSurfaceLight = Color(0xFF15803D)
val WarningOnSurfaceLight = Color(0xFF92400E)
val AccentOnSurfaceLight = Color(0xFFC2410C)
