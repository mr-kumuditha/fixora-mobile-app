package com.techfix.app.core.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * The design system's button press feedback: a subtle scale-down while the
 * button is held, standard Material3 easing, well inside the 300ms ceiling.
 *
 * Applied through `graphicsLayer`, so it scales the drawn button without
 * re-measuring the layout around it — a button in a Column doesn't shove its
 * neighbours around when pressed.
 *
 * Pass the same [InteractionSource] the button itself was given, otherwise
 * the press is never observed.
 */
@Composable
fun Modifier.pressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.96f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "pressScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
