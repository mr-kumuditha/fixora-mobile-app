package com.techfix.app.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** 8dp grid. Use these instead of ad-hoc padding values. */
object FixoraSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
}

/** 12dp cards, 8dp inputs/buttons, 20dp bottom sheets and dialogs. */
object FixoraRadius {
    val input = 8.dp
    val card = 12.dp
    val sheet = 20.dp
}

val FixoraShapes = Shapes(
    extraSmall = RoundedCornerShape(FixoraRadius.input),
    small = RoundedCornerShape(FixoraRadius.input),
    medium = RoundedCornerShape(FixoraRadius.card),
    large = RoundedCornerShape(FixoraRadius.sheet),
    extraLarge = RoundedCornerShape(FixoraRadius.sheet),
)
