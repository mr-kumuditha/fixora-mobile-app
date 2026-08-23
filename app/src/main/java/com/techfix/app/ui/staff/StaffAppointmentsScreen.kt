package com.techfix.app.ui.staff

import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.techfix.app.core.designsystem.FixoraSpacing
import com.techfix.app.core.designsystem.FixoraTheme
import com.techfix.app.domain.repair.RepairRequest
import com.techfix.app.ui.customer.catalog.icon
import com.techfix.app.ui.customer.catalog.label
import com.techfix.app.ui.customer.repair.RepairStatusChip
import com.techfix.app.ui.customer.repair.formatDate
import com.techfix.app.ui.customer.repair.repairReference

/**
 * Appointment Queue for every staff role, with the two slices the brief asks
 * for on one screen: **New** is the queue of SUBMITTED requests waiting to be
 * confirmed, **Active** is everything already moving.
 *
 * A Technician doesn't get the New tab — confirming a booking is a manager
 * action — and their Active tab is already narrowed to their own repairs by
 * the ViewModel, so the same screen serves all three roles.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffAppointmentsScreen(
    staffContext: StaffContext,
    uiState: StaffAppointmentsUiState,
    onTabSelected: (StaffQueueTab) -> Unit,
    onQueryChanged: (String) -> Unit,
    onRetry: () -> Unit,
    onRequestClick: (RepairRequest) -> Unit,
    onBack: () -> Unit,
) {
    val tabs = if (staffContext.canAssign) {
        listOf(StaffQueueTab.NEW, StaffQueueTab.ACTIVE, StaffQueueTab.COMPLETED, StaffQueueTab.CANCELLED)
    } else {
        listOf(StaffQueueTab.ACTIVE, StaffQueueTab.COMPLETED)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (staffContext.canAssign) "Repairs" else "My repairs") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
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
                .padding(padding),
        ) {
            if (tabs.size > 1) {
                TabRow(
                    selectedTabIndex = tabs.indexOf(uiState.tab).coerceAtLeast(0),
                    containerColor = MaterialTheme.colorScheme.background,
                ) {
                    tabs.forEach { tab ->
                        Tab(
                            selected = uiState.tab == tab,
                            onClick = { onTabSelected(tab) },
                            text = {
                                val count = when (tab) {
                                    StaffQueueTab.NEW -> uiState.newRequests.size
                                    StaffQueueTab.ACTIVE -> uiState.activeRequests.size
                                    StaffQueueTab.COMPLETED -> uiState.completedRequests.size
                                    StaffQueueTab.CANCELLED -> uiState.cancelledRequests.size
                                }
                                Text(
                                    when (tab) {
                                        StaffQueueTab.NEW -> "New ($count)"
                                        StaffQueueTab.ACTIVE -> "Active ($count)"
                                        StaffQueueTab.COMPLETED -> "Done ($count)"
                                        StaffQueueTab.CANCELLED -> "Cancelled ($count)"
                                    }
                                )
                            },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = uiState.query,
                onValueChange = onQueryChanged,
                modifier = Modifier.fillMaxWidth().padding(horizontal = FixoraSpacing.md, vertical = FixoraSpacing.sm),
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                placeholder = { Text("Search repairs") },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
            )

            QueueSummaryCard(staffContext = staffContext, tab = uiState.tab, count = uiState.visibleRequests.size)

            Crossfade(
                targetState = when {
                    uiState.isLoading -> QueuePane.LOADING
                    uiState.errorMessage != null -> QueuePane.ERROR
                    uiState.isEmpty -> QueuePane.EMPTY
                    else -> QueuePane.CONTENT
                },
                animationSpec = tween(220),
                label = "queuePane",
                modifier = Modifier.fillMaxSize(),
            ) { pane ->
                when (pane) {
                    QueuePane.LOADING -> QueueSkeleton()
                    QueuePane.ERROR -> QueueError(
                        message = uiState.errorMessage ?: "Something went wrong.",
                        onRetry = onRetry,
                    )
                    QueuePane.EMPTY -> QueueEmpty(uiState.tab, staffContext)
                    QueuePane.CONTENT -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(FixoraSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(FixoraSpacing.sm),
                    ) {
                        items(uiState.visibleRequests, key = { it.id }) { request ->
                            AnimatedVisibility(
                                visible = true,
                                enter = fadeIn(tween(180)) + slideInVertically(tween(180), initialOffsetY = { it / 8 }),
                            ) {
                                QueueCard(
                                    request = request,
                                    serviceName = uiState.serviceNames[request.serviceId],
                                    branchName = uiState.branchNames[request.branchId],
                                    technicianName = request.technicianId?.let { uiState.technicianNames[it] },
                                    onClick = { onRequestClick(request) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueSummaryCard(staffContext: StaffContext, tab: StaffQueueTab, count: Int) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = FixoraSpacing.md, vertical = FixoraSpacing.sm),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(FixoraSpacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.md)) {
            Icon(Icons.Rounded.Inbox, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(when (tab) {
                    StaffQueueTab.NEW -> "Incoming requests"
                    StaffQueueTab.ACTIVE -> if (staffContext.seesOnlyOwnRepairs) "Your active repairs" else "Active repair queue"
                    StaffQueueTab.COMPLETED -> "Completed repairs"
                    StaffQueueTab.CANCELLED -> "Cancelled repairs"
                }, style = MaterialTheme.typography.titleSmall)
                Text(if (tab == StaffQueueTab.NEW) "Review, assign and confirm new bookings." else "Open a repair for its full operational detail.", style = MaterialTheme.typography.bodySmall, color = FixoraTheme.extendedColors.textSecondary)
            }
            Text(count.toString(), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

private enum class QueuePane { LOADING, ERROR, EMPTY, CONTENT }

@Composable
private fun QueueCard(
    request: RepairRequest,
    serviceName: String?,
    branchName: String?,
    technicianName: String?,
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
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(FixoraSpacing.sm))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    request.deviceDetails.category.icon,
                    contentDescription = null,
                    tint = FixoraTheme.extendedColors.textSecondary,
                    modifier = Modifier.size(24.dp),
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "${request.deviceDetails.brand} ${request.deviceDetails.model}".trim()
                        .ifBlank { request.deviceDetails.category.label },
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = serviceName ?: request.deviceDetails.category.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = FixoraTheme.extendedColors.textSecondary,
                    maxLines = 1,
                )
                Text(
                    text = listOfNotNull(
                        repairReference(request.id),
                        branchName,
                        technicianName ?: "Unassigned",
                        formatDate(request.createdAt),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = FixoraTheme.extendedColors.textSecondary,
                )
                RepairStatusChip(
                    status = request.status,
                    modifier = Modifier.padding(top = FixoraSpacing.xs),
                )
            }
        }
    }
}

@Composable
private fun QueueSkeleton() {
    val transition = rememberInfiniteTransition(label = "queueSkeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "queueSkeletonAlpha",
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(FixoraSpacing.md),
        verticalArrangement = Arrangement.spacedBy(FixoraSpacing.sm),
    ) {
        repeat(5) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .alpha(alpha)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
    }
}

@Composable
private fun QueueEmpty(tab: StaffQueueTab, staffContext: StaffContext) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(FixoraSpacing.lg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Rounded.Inbox,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = FixoraTheme.extendedColors.textSecondary,
        )
        Text(
            when (tab) {
                StaffQueueTab.NEW -> "No new requests"
                StaffQueueTab.ACTIVE -> if (staffContext.seesOnlyOwnRepairs) {
                    "Nothing assigned to you"
                } else {
                    "Nothing in progress"
                }
                StaffQueueTab.COMPLETED -> "No completed repairs"
                StaffQueueTab.CANCELLED -> "No cancelled repairs"
            },
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = FixoraSpacing.sm),
        )
        Text(
            when (tab) {
                StaffQueueTab.NEW -> "New bookings land here as customers submit them."
                StaffQueueTab.ACTIVE -> "Repairs show up here once they've been confirmed."
                StaffQueueTab.COMPLETED -> "Successfully completed repairs will appear here."
                StaffQueueTab.CANCELLED -> "Cancelled requests will appear here."
            },
            style = MaterialTheme.typography.bodySmall,
            color = FixoraTheme.extendedColors.textSecondary,
        )
    }
}

@Composable
private fun QueueError(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(FixoraSpacing.lg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Rounded.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.error,
        )
        Text(
            "Couldn't load the queue",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = FixoraSpacing.sm),
        )
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = FixoraTheme.extendedColors.textSecondary,
        )
        OutlinedButton(onClick = onRetry, modifier = Modifier.padding(top = FixoraSpacing.md)) {
            Text("Retry")
        }
    }
}
