package com.techfix.app.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techfix.app.R
import com.techfix.app.core.designsystem.pressScale

/**
 * The "Sign in with Google" button, built to Google's Identity Services
 * branding guidelines rather than approximated with an app-styled outlined
 * button.
 *
 * Everything here that looks like a magic number is from that spec
 * (https://developers.google.com/identity/branding-guidelines):
 *
 * - Light theme: fill `#FFFFFF`, 1dp `#747775` stroke, text `#1F1F1F`.
 * - Dark theme: fill `#131314`, 1dp `#8E918F` stroke, text `#E3E3E3`.
 *   The dark fill is Google's own, which is why this button does not use the
 *   Fixora surface token — the brand asset owns its own colours in both
 *   themes and must not be re-tinted.
 * - Android padding: 12dp before the logo, 10dp between logo and text, 12dp
 *   after the text.
 * - Text at 14sp Medium, 20sp line height. The spec names Google Sans Medium,
 *   which is not redistributable, so this falls back to the platform default
 *   (Roboto Medium on Android) — the substitution Google's own older button
 *   assets used. It deliberately does **not** use Fixora's Inter: the button
 *   is Google's mark, not ours.
 * - The "G" is the unmodified official artwork (see `ic_google_g.xml`) at
 *   20dp, on the light/dark fill the spec requires.
 * - Corner radius is Google's rectangular variant (4dp) rather than Fixora's
 *   8dp button radius, for the same reason: on this one control the brand
 *   spec wins over the design system.
 *
 * The only Fixora behaviour layered on top is the design system's press
 * scale, which changes no colour, dimension or wording of the mark.
 */
@Composable
fun GoogleSignInButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    text: String = "Sign in with Google",
) {
    val dark = isSystemInDarkTheme()
    val container = if (dark) GoogleDarkFill else GoogleLightFill
    val content = if (dark) GoogleDarkText else GoogleLightText
    val stroke = if (dark) GoogleDarkStroke else GoogleLightStroke
    val interactionSource = remember { MutableInteractionSource() }

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = GoogleButtonHeight)
            .pressScale(interactionSource),
        interactionSource = interactionSource,
        shape = RoundedCornerShape(GoogleButtonRadius),
        border = BorderStroke(1.dp, stroke),
        elevation = null,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            // Google's spec has no disabled state, so a disabled button is
            // the same mark at reduced opacity rather than a recoloured one.
            disabledContainerColor = container.copy(alpha = 0.6f),
            disabledContentColor = content.copy(alpha = 0.6f),
        ),
        contentPadding = PaddingValues(0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(GoogleButtonHeight)
                .padding(start = 12.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = content,
                )
            } else {
                Image(
                    painter = painterResource(R.drawable.ic_google_g),
                    contentDescription = null,
                    modifier = Modifier
                        .size(20.dp)
                        .alpha(if (enabled) 1f else 0.6f),
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = text,
                color = content,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Default,
                maxLines = 1,
            )
        }
    }
}

// Straight from Google's branding guidelines — do not re-tint these to the
// Fixora palette.
private val GoogleLightFill = Color(0xFFFFFFFF)
private val GoogleLightStroke = Color(0xFF747775)
private val GoogleLightText = Color(0xFF1F1F1F)
private val GoogleDarkFill = Color(0xFF131314)
private val GoogleDarkStroke = Color(0xFF8E918F)
private val GoogleDarkText = Color(0xFFE3E3E3)

private val GoogleButtonHeight = 40.dp
private val GoogleButtonRadius = 4.dp
