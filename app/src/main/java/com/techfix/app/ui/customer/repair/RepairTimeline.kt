package com.techfix.app.ui.customer.repair

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.techfix.app.core.designsystem.FixoraSpacing
import com.techfix.app.core.designsystem.FixoraTheme
import com.techfix.app.domain.repair.RepairStatus

/** Where one stage sits relative to the repair's current status. */
private enum class StageState { DONE, CURRENT, UPCOMING }

/**
 * The nine-stage repair timeline.
 *
 * When the Firestore listener pushes a new status, the stage does not snap:
 * the connecting line fills downward and the dot colours cross-fade
 * (design system, Motion — "animate the chip colour and the connecting
 * line", everything under 300ms). Driving both off a single animated
 * progress value keeps the line and the dots in step with each other.
 *
 * AWAITING_PARTS is not a stage of its own — it holds the timeline at
 * In Progress and surfaces as a hold note on that row instead.
 */
@Composable
fun RepairTimeline(
    status: RepairStatus,
    modifier: Modifier = Modifier,
) {
    val stages = RepairStatus.timeline
    val currentIndex = status.timelineIndex

    val progress by animateFloatAsState(
        targetValue = currentIndex.coerceAtLeast(0).toFloat(),
        animationSpec = tween(durationMillis = 280),
        label = "timelineProgress",
    )

    Column(modifier = modifier.fillMaxWidth()) {
        stages.forEachIndexed { index, stage ->
            val state = when {
                currentIndex < 0 -> StageState.UPCOMING
                index < currentIndex -> StageState.DONE
                index == currentIndex -> StageState.CURRENT
                else -> StageState.UPCOMING
            }
            TimelineRow(
                stage = stage,
                state = state,
                // Fraction of the connector below this row that is filled.
                // Fed by the same animated progress as the dots, so the line
                // travels down as the status advances.
                connectorFill = (progress - index).coerceIn(0f, 1f),
                showConnector = index != stages.lastIndex,
                // The hold only makes sense on the row the work is paused at.
                onHold = state == StageState.CURRENT && status == RepairStatus.AWAITING_PARTS,
            )
        }
    }
}

@Composable
private fun TimelineRow(
    stage: RepairStatus,
    state: StageState,
    connectorFill: Float,
    showConnector: Boolean,
    onHold: Boolean,
) {
    val doneColor = MaterialTheme.colorScheme.primary
    val currentColor = if (onHold) {
        FixoraTheme.extendedColors.warning
    } else {
        stage.statusColor()
    }
    val idleColor = FixoraTheme.extendedColors.border

    val dotColor by animateColorAsState(
        targetValue = when (state) {
            StageState.DONE -> doneColor
            StageState.CURRENT -> currentColor
            StageState.UPCOMING -> MaterialTheme.colorScheme.surfaceVariant
        },
        animationSpec = tween(durationMillis = 250),
        label = "timelineDot_${stage.name}",
    )
    val dotContentColor by animateColorAsState(
        targetValue = when (state) {
            StageState.DONE -> MaterialTheme.colorScheme.onPrimary
            StageState.CURRENT -> if (onHold) FixoraTheme.extendedColors.onWarning else stage.onStatusColor()
            StageState.UPCOMING -> FixoraTheme.extendedColors.textSecondary
        },
        animationSpec = tween(durationMillis = 250),
        label = "timelineDotContent_${stage.name}",
    )
    val labelColor by animateColorAsState(
        targetValue = when (state) {
            StageState.UPCOMING -> FixoraTheme.extendedColors.textSecondary
            else -> MaterialTheme.colorScheme.onSurface
        },
        animationSpec = tween(durationMillis = 250),
        label = "timelineLabel_${stage.name}",
    )
    // The active stage sits slightly larger than the rest, and grows into
    // that size when the status reaches it.
    val dotScale by animateFloatAsState(
        targetValue = if (state == StageState.CURRENT) 1f else 0.85f,
        animationSpec = tween(durationMillis = 250),
        label = "timelineDotScale_${stage.name}",
    )

    Row(modifier = Modifier.fillMaxWidth()) {
        // ------------------------------------------------------- left rail
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .scale(dotScale)
                    .clip(CircleShape)
                    .background(dotColor)
                    .then(
                        if (state == StageState.UPCOMING) {
                            Modifier.border(1.dp, idleColor, CircleShape)
                        } else {
                            Modifier
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (state == StageState.DONE) Icons.Rounded.Check else stage.icon,
                    contentDescription = null,
                    tint = dotContentColor,
                    modifier = Modifier.size(18.dp),
                )
            }

            if (showConnector) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(if (state == StageState.CURRENT) 44.dp else 28.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(idleColor),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight(connectorFill)
                            .width(2.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(doneColor),
                    )
                }
            }
        }

        // ---------------------------------------------------------- content
        Column(
            modifier = Modifier
                .padding(start = FixoraSpacing.md, bottom = FixoraSpacing.sm)
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stage.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (state == StageState.CURRENT) FontWeight.SemiBold else FontWeight.Medium,
                color = labelColor,
            )

            // Only the active stage explains itself — spelling out all nine
            // at once turns the timeline into a wall of text.
            AnimatedVisibility(
                visible = state == StageState.CURRENT,
                enter = fadeIn(tween(200)) + expandVertically(tween(200)),
                exit = fadeOut(tween(150)) + shrinkVertically(tween(150)),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(FixoraSpacing.xs)) {
                    Text(
                        text = if (onHold) RepairStatus.AWAITING_PARTS.description else stage.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = FixoraTheme.extendedColors.textSecondary,
                    )
                    if (onHold) {
                        RepairStatusChip(status = RepairStatus.AWAITING_PARTS)
                    }
                }
            }
        }
    }
}
