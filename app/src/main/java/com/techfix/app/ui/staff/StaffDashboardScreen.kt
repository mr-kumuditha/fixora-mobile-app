package com.techfix.app.ui.staff

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.PendingActions
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Timelapse
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.techfix.app.core.designsystem.FixoraSpacing
import com.techfix.app.core.designsystem.FixoraRadius
import com.techfix.app.core.designsystem.FixoraTheme
import com.techfix.app.core.navigation.UserRole
import com.techfix.app.domain.operations.BranchPerformance
import com.techfix.app.ui.customer.repair.formatPrice
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Staff Dashboard — counts and entry points, shared by all three staff roles.
 *
 * What differs per role is which counts and which entry points are on it, all
 * driven by [StaffContext]: a Technician gets "Assigned to me" and no
 * appointment-queue card, a Branch Manager or Admin gets the queue.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffDashboardScreen(
    staffContext: StaffContext,
    uiState: StaffDashboardUiState,
    branchName: String?,
    onRetry: () -> Unit,
    onOpenQueue: (StaffQueueTab) -> Unit,
    onOpenInventory: () -> Unit,
    onSignOut: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Overview") },
                actions = {
                    IconButton(onClick = onSignOut) {
                        Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = "Sign out")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(FixoraSpacing.md),
            verticalArrangement = Arrangement.spacedBy(FixoraSpacing.md),
        ) {
            DashboardHeader(staffContext, branchName)
            RoleCard(staffContext, branchName)

            Crossfade(
                targetState = when {
                    uiState.isLoading -> DashboardPane.LOADING
                    uiState.errorMessage != null -> DashboardPane.ERROR
                    else -> DashboardPane.CONTENT
                },
                animationSpec = tween(220),
                label = "dashboardPane",
            ) { pane ->
                when (pane) {
                    DashboardPane.LOADING -> DashboardSkeleton()
                    DashboardPane.ERROR -> DashboardError(
                        message = uiState.errorMessage ?: "Something went wrong.",
                        onRetry = onRetry,
                    )
                    DashboardPane.CONTENT -> DashboardContent(staffContext, uiState)
                }
            }

            if (staffContext.canAssign) {
                EntryCard(
                    title = "Appointment queue",
                    description = "Confirm a branch and assign a technician to new requests.",
                    icon = Icons.Rounded.PendingActions,
                    onClick = { onOpenQueue(StaffQueueTab.NEW) },
                )
            }
            EntryCard(
                title = if (staffContext.seesOnlyOwnRepairs) "My repairs" else "Repairs in progress",
                description = "Move a repair on to its next stage.",
                icon = Icons.Rounded.Build,
                onClick = { onOpenQueue(StaffQueueTab.ACTIVE) },
            )
            EntryCard(
                title = if (staffContext.canManageInventory) "Inventory management" else "Technicians & spare parts",
                description = if (staffContext.canManageInventory) {
                    "Manage parts, thresholds and secure stock adjustments."
                } else {
                    "See the roster and current stock levels."
                },
                icon = Icons.Rounded.Inventory2,
                onClick = onOpenInventory,
            )
        }
    }
}

@Composable
private fun DashboardHeader(staffContext: StaffContext, branchName: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(FixoraSpacing.xs)) {
        Text(
            text = when (staffContext.role) {
                UserRole.ADMIN -> "FIXORA ADMIN"
                UserRole.BRANCH_MANAGER -> "FIXORA BRANCH OPERATIONS"
                UserRole.TECHNICIAN -> "FIXORA TECHNICIAN"
                UserRole.CUSTOMER -> "FIXORA"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = when (staffContext.role) {
                UserRole.ADMIN -> "Business at a glance"
                UserRole.BRANCH_MANAGER -> branchName ?: "Your branch"
                UserRole.TECHNICIAN -> "Your assigned work"
                UserRole.CUSTOMER -> "Overview"
            },
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date()),
            style = MaterialTheme.typography.bodySmall,
            color = FixoraTheme.extendedColors.textSecondary,
        )
    }
}

@Composable
private fun DashboardContent(staffContext: StaffContext, uiState: StaffDashboardUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(FixoraSpacing.md)) {
        DashboardCounts(staffContext, uiState)
        if (staffContext.role == UserRole.ADMIN) {
            RevenueCard(uiState.recordedRevenue)
        }
        OperationalSummary(staffContext, uiState)
        if (uiState.branchPerformance.isNotEmpty()) {
            BranchPerformanceCard(uiState.branchPerformance, staffContext.role == UserRole.ADMIN)
        }
        if (uiState.recentRepairs.isNotEmpty()) RecentActivityCard(uiState.recentRepairs)
    }
}

@Composable
private fun RecentActivityCard(repairs: List<com.techfix.app.domain.repair.RepairRequest>) {
    Column(verticalArrangement = Arrangement.spacedBy(FixoraSpacing.sm)) {
        Text("Recent repair activity", style = MaterialTheme.typography.titleMedium)
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.fillMaxWidth().padding(FixoraSpacing.md), verticalArrangement = Arrangement.spacedBy(FixoraSpacing.sm)) {
                repairs.forEach { repair ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.sm)) {
                        Box(Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Build, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(com.techfix.app.ui.customer.repair.repairReference(repair.id), style = MaterialTheme.typography.titleSmall)
                            Text("${repair.deviceDetails.brand} ${repair.deviceDetails.model}".trim(), style = MaterialTheme.typography.bodySmall, color = FixoraTheme.extendedColors.textSecondary)
                        }
                        Text(repair.status.name.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercaseChar), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

private enum class DashboardPane { LOADING, ERROR, CONTENT }

@Composable
private fun RoleCard(staffContext: StaffContext, branchName: String?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FixoraSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Storefront, contentDescription = null, modifier = Modifier.size(24.dp))
            Column {
                Text(staffContext.roleLabel, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = branchName
                        ?: if (staffContext.seesAllBranches) "All branches" else "Branch not set",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun DashboardCounts(staffContext: StaffContext, uiState: StaffDashboardUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(FixoraSpacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.sm)) {
            CountTile(
                label = if (staffContext.role == UserRole.TECHNICIAN) "Assigned to me" else "Total repairs",
                value = if (staffContext.role == UserRole.TECHNICIAN) uiState.assignedToMeCount else uiState.totalCount,
                tint = FixoraTheme.extendedColors.warning,
                icon = if (staffContext.role == UserRole.TECHNICIAN) Icons.Rounded.Person else Icons.Rounded.Build,
                modifier = Modifier.weight(1f),
            )
            CountTile(
                label = if (staffContext.role == UserRole.TECHNICIAN) "In progress" else "Pending",
                value = if (staffContext.role == UserRole.TECHNICIAN) uiState.activeCount else uiState.newCount,
                tint = MaterialTheme.colorScheme.primary,
                icon = Icons.Rounded.Timelapse,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.sm)) {
            CountTile(
                label = if (staffContext.role == UserRole.TECHNICIAN) "Ready" else "Active repairs",
                value = if (staffContext.role == UserRole.TECHNICIAN) uiState.readyCount else uiState.activeCount,
                tint = FixoraTheme.extendedColors.success,
                icon = Icons.Rounded.Storefront,
                modifier = Modifier.weight(1f),
            )
            if (staffContext.role != UserRole.TECHNICIAN) {
                CountTile(
                    label = "Completed",
                    value = uiState.completedCount,
                    tint = FixoraTheme.extendedColors.accent,
                    icon = Icons.Rounded.CheckCircle,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Box(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RevenueCard(amount: Double) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(FixoraSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Payments, null, tint = MaterialTheme.colorScheme.primary) }
            Column(modifier = Modifier.weight(1f)) {
                Text("Recorded demo revenue", style = MaterialTheme.typography.labelMedium)
                Text(formatPrice(amount), style = MaterialTheme.typography.headlineSmall)
                Text("Successful payment receipts", style = MaterialTheme.typography.bodySmall, color = FixoraTheme.extendedColors.textSecondary)
            }
            Icon(Icons.Rounded.Assessment, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun OperationalSummary(staffContext: StaffContext, state: StaffDashboardUiState) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.fillMaxWidth().padding(FixoraSpacing.md), verticalArrangement = Arrangement.spacedBy(FixoraSpacing.sm)) {
            Text("Operations", style = MaterialTheme.typography.titleMedium)
            if (staffContext.role == UserRole.ADMIN) {
                SummaryRow(Icons.Rounded.Groups, "Customers", state.customerCount.toString())
            }
            SummaryRow(Icons.Rounded.Person, "Available technicians", state.availableTechnicianCount.toString())
            SummaryRow(Icons.Rounded.WarningAmber, "Out of stock items", state.outOfStockCount.toString())
            SummaryRow(Icons.Rounded.Storefront, "Ready for pickup", state.readyCount.toString())
        }
    }
}

@Composable
private fun SummaryRow(icon: ImageVector, label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.sm)) {
        Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun BranchPerformanceCard(rows: List<BranchPerformance>, showRevenue: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(FixoraSpacing.sm)) {
        Text("Branch performance", style = MaterialTheme.typography.titleMedium)
        rows.forEach { row ->
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.fillMaxWidth().padding(FixoraSpacing.md), verticalArrangement = Arrangement.spacedBy(FixoraSpacing.xs)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(row.branch.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        Text("${row.openRepairs} open", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Text("${row.totalRepairs} repairs · ${row.availableTechnicians} technicians available", style = MaterialTheme.typography.bodySmall, color = FixoraTheme.extendedColors.textSecondary)
                    if (showRevenue) Text("Recorded revenue ${formatPrice(row.recordedRevenue)}", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun CountTile(
    label: String,
    value: Int,
    tint: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FixoraSpacing.md),
            verticalArrangement = Arrangement.spacedBy(FixoraSpacing.xs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.sm)) {
                Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                }
                Text(value.toString(), style = MaterialTheme.typography.headlineMedium, color = tint)
            }
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = FixoraTheme.extendedColors.textSecondary,
            )
        }
    }
}

@Composable
private fun EntryCard(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FixoraSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(FixoraRadius.card)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = FixoraTheme.extendedColors.textSecondary,
                )
            }
            Icon(
                Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = FixoraTheme.extendedColors.textSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun DashboardSkeleton() {
    val transition = rememberInfiniteTransition(label = "dashboardSkeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dashboardSkeletonAlpha",
    )
    Column(verticalArrangement = Arrangement.spacedBy(FixoraSpacing.sm)) {
        repeat(2) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .alpha(alpha)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
    }
}

@Composable
private fun DashboardError(message: String, onRetry: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FixoraSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(FixoraSpacing.sm),
        ) {
            Icon(
                Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp),
            )
            Text("Couldn't load the queue", style = MaterialTheme.typography.titleSmall)
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = FixoraTheme.extendedColors.textSecondary,
            )
            OutlinedButton(onClick = onRetry) { Text("Retry") }
        }
    }
}
