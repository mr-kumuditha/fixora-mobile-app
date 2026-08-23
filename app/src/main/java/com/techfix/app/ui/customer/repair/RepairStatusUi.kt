package com.techfix.app.ui.customer.repair

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FactCheck
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.MoveToInbox
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.Troubleshoot
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.techfix.app.core.designsystem.FixoraSpacing
import com.techfix.app.core.designsystem.FixoraTheme
import com.techfix.app.domain.repair.RepairStatus

/**
 * Display label, one-line explanation, icon, and colour per repair status —
 * the same mapping used by the tracking timeline, the history list, and the
 * active-repair card on Home, so a status never reads differently depending
 * on which screen shows it. Same split as DeviceCategoryUi: the domain enum
 * stays free of presentation.
 */
val RepairStatus.label: String
    get() = when (this) {
        RepairStatus.SUBMITTED -> "Submitted"
        RepairStatus.CONFIRMED -> "Confirmed"
        RepairStatus.RECEIVED -> "Received"
        RepairStatus.DIAGNOSIS -> "Diagnosis"
        RepairStatus.APPROVED -> "Approved"
        RepairStatus.IN_PROGRESS -> "In Progress"
        RepairStatus.QUALITY_CHECK -> "Quality Check"
        RepairStatus.READY_FOR_PICKUP -> "Ready"
        RepairStatus.COMPLETED -> "Completed"
        RepairStatus.AWAITING_PARTS -> "Awaiting Parts"
        RepairStatus.CANCELLED -> "Cancelled"
    }

val RepairStatus.description: String
    get() = when (this) {
        RepairStatus.SUBMITTED -> "We've got your request and it's queued at your branch."
        RepairStatus.CONFIRMED -> "Your branch confirmed the booking and assigned a technician."
        RepairStatus.RECEIVED -> "Your device is checked in at the branch."
        RepairStatus.DIAGNOSIS -> "A technician is working out what's wrong."
        RepairStatus.APPROVED -> "The repair plan and cost are approved."
        RepairStatus.IN_PROGRESS -> "The repair is being carried out."
        RepairStatus.QUALITY_CHECK -> "We're testing the device before handing it back."
        RepairStatus.READY_FOR_PICKUP -> "Your device is ready to collect."
        RepairStatus.COMPLETED -> "Repair finished and device collected."
        RepairStatus.AWAITING_PARTS -> "Work is paused until a spare part arrives."
        RepairStatus.CANCELLED -> "This repair was cancelled."
    }

val RepairStatus.icon: ImageVector
    get() = when (this) {
        RepairStatus.SUBMITTED -> Icons.AutoMirrored.Rounded.Send
        RepairStatus.CONFIRMED -> Icons.Rounded.Verified
        RepairStatus.RECEIVED -> Icons.Rounded.MoveToInbox
        RepairStatus.DIAGNOSIS -> Icons.Rounded.Troubleshoot
        RepairStatus.APPROVED -> Icons.Rounded.ThumbUp
        RepairStatus.IN_PROGRESS -> Icons.Rounded.Build
        RepairStatus.QUALITY_CHECK -> Icons.AutoMirrored.Rounded.FactCheck
        RepairStatus.READY_FOR_PICKUP -> Icons.Rounded.Storefront
        RepairStatus.COMPLETED -> Icons.Rounded.CheckCircle
        RepairStatus.AWAITING_PARTS -> Icons.Rounded.Inventory2
        RepairStatus.CANCELLED -> Icons.Rounded.Cancel
    }

/**
 * Warning while the customer is waiting on us, primary while work is moving,
 * success once the device is ready, error when it's cancelled — the status
 * colours from the design system, used for nothing else.
 */
@Composable
fun RepairStatus.statusColor(): Color = when (this) {
    RepairStatus.SUBMITTED, RepairStatus.AWAITING_PARTS -> FixoraTheme.extendedColors.warning
    RepairStatus.READY_FOR_PICKUP, RepairStatus.COMPLETED -> FixoraTheme.extendedColors.success
    RepairStatus.CANCELLED -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.primary
}

@Composable
fun RepairStatus.onStatusColor(): Color = when (this) {
    RepairStatus.SUBMITTED, RepairStatus.AWAITING_PARTS -> FixoraTheme.extendedColors.onWarning
    RepairStatus.READY_FOR_PICKUP, RepairStatus.COMPLETED -> FixoraTheme.extendedColors.onSuccess
    RepairStatus.CANCELLED -> MaterialTheme.colorScheme.onError
    else -> MaterialTheme.colorScheme.onPrimary
}

/**
 * The reusable status chip from the architecture doc's component list. The
 * container colour is animated rather than swapped, so a live status change
 * arriving from the Firestore listener reads as a change instead of a flicker.
 */
@Composable
fun RepairStatusChip(
    status: RepairStatus,
    modifier: Modifier = Modifier,
    showIcon: Boolean = true,
) {
    val container by animateColorAsState(
        targetValue = status.statusColor(),
        animationSpec = tween(durationMillis = 250),
        label = "statusChipContainer",
    )
    val content by animateColorAsState(
        targetValue = status.onStatusColor(),
        animationSpec = tween(durationMillis = 250),
        label = "statusChipContent",
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(FixoraSpacing.md))
            .background(container)
            .padding(horizontal = FixoraSpacing.sm, vertical = FixoraSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.xs),
    ) {
        if (showIcon) {
            Icon(
                imageVector = status.icon,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = status.label,
            style = MaterialTheme.typography.labelMedium,
            color = content,
        )
    }
}
