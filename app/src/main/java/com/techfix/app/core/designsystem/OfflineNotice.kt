package com.techfix.app.core.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * Shown when a screen is rendering data that came out of the Room cache
 * rather than the network. Deliberately a notice and not an error state: the
 * content below it is real, just not fresh, and saying so is more honest than
 * either hiding it or replacing a working screen with a retry button.
 *
 * Uses the warning token rather than error for the same reason.
 */
@Composable
fun OfflineNotice(
    visible: Boolean,
    modifier: Modifier = Modifier,
    message: String = "Offline — showing your saved catalog.",
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)) + expandVertically(tween(200)),
        exit = fadeOut(tween(200)) + shrinkVertically(tween(200)),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(FixoraShapes.small)
                .background(FixoraTheme.extendedColors.warning.copy(alpha = 0.16f))
                .padding(horizontal = FixoraSpacing.sm, vertical = FixoraSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.sm),
        ) {
            Icon(
                Icons.Rounded.CloudOff,
                contentDescription = null,
                tint = FixoraTheme.extendedColors.warning,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
