package com.techfix.app.ui.customer.repair

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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.techfix.app.core.designsystem.FixoraSpacing
import com.techfix.app.core.designsystem.FixoraTheme
import com.techfix.app.domain.repair.RepairRequest
import com.techfix.app.domain.repair.RepairStatus
import com.techfix.app.ui.customer.catalog.label

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepairTrackingScreen(
    uiState: RepairTrackingUiState,
    onRetry: () -> Unit,
    onPayNow: (String) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Repair Tracking") },
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
        // Crossfade rather than a hard cut, per the design system's
        // loading-to-content rule.
        Crossfade(
            targetState = when {
                uiState.isLoading -> TrackingPane.LOADING
                uiState.errorMessage != null && uiState.request == null -> TrackingPane.ERROR
                uiState.request != null -> TrackingPane.CONTENT
                else -> TrackingPane.ERROR
            },
            animationSpec = tween(220),
            label = "trackingPane",
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) { pane ->
            when (pane) {
                TrackingPane.LOADING -> TrackingSkeleton()
                TrackingPane.ERROR -> TrackingError(
                    message = uiState.errorMessage ?: "This repair couldn't be found.",
                    onRetry = onRetry,
                )
                TrackingPane.CONTENT -> uiState.request?.let { request ->
                    TrackingContent(
                        request = request,
                        serviceName = uiState.serviceName,
                        branchName = uiState.branchName,
                        onPayNow = { onPayNow(request.id) },
                    )
                }
            }
        }
    }
}

private enum class TrackingPane { LOADING, ERROR, CONTENT }

@Composable
private fun TrackingContent(
    request: RepairRequest,
    serviceName: String?,
    branchName: String?,
    onPayNow: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(FixoraSpacing.md),
        verticalArrangement = Arrangement.spacedBy(FixoraSpacing.md),
    ) {
        SummaryCard(request, serviceName, branchName)

        // The one action this screen offers, and only once the device is
        // actually ready. Paying is also what moves the repair to COMPLETED,
        // so the card disappears on its own once it has been paid.
        if (request.status == RepairStatus.READY_FOR_PICKUP) {
            PayNowCard(onPayNow)
        }

        if (request.status == RepairStatus.CANCELLED) {
            CancelledCard()
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(modifier = Modifier.padding(FixoraSpacing.md)) {
                    Text("Progress", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Updates live as your branch moves the repair along.",
                        style = MaterialTheme.typography.bodySmall,
                        color = FixoraTheme.extendedColors.textSecondary,
                        modifier = Modifier.padding(bottom = FixoraSpacing.md),
                    )
                    RepairTimeline(status = request.status)
                }
            }
        }

        if (request.imageUrls.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(modifier = Modifier.padding(vertical = FixoraSpacing.md)) {
                    Text(
                        "Photos you sent",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = FixoraSpacing.md),
                    )
                    LazyRow(
                        contentPadding = PaddingValues(
                            horizontal = FixoraSpacing.md,
                            vertical = FixoraSpacing.sm,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.sm),
                    ) {
                        items(request.imageUrls) { url ->
                            AsyncImage(
                                model = url,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(88.dp)
                                    .clip(RoundedCornerShape(FixoraSpacing.sm))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PayNowCard(onPayNow: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FixoraSpacing.md),
            verticalArrangement = Arrangement.spacedBy(FixoraSpacing.sm),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.Payments,
                    contentDescription = null,
                    tint = FixoraTheme.extendedColors.accent,
                    modifier = Modifier.size(24.dp),
                )
                Text("Your device is ready", style = MaterialTheme.typography.titleSmall)
            }
            Text(
                "Settle the repair to close it off and move it into your history. " +
                    "Payment in this app is a demo — nothing is ever charged.",
                style = MaterialTheme.typography.bodySmall,
                color = FixoraTheme.extendedColors.textSecondary,
            )
            Button(
                onClick = onPayNow,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FixoraTheme.extendedColors.accent,
                    contentColor = FixoraTheme.extendedColors.onAccent,
                ),
            ) {
                Text("Pay now")
            }
        }
    }
}

@Composable
private fun SummaryCard(request: RepairRequest, serviceName: String?, branchName: String?) {
    val stageCount = RepairStatus.timeline.size
    val stageNumber = request.status.timelineIndex + 1

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(FixoraSpacing.md),
            verticalArrangement = Arrangement.spacedBy(FixoraSpacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${request.deviceDetails.brand} ${request.deviceDetails.model}".trim(),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = serviceName ?: request.deviceDetails.category.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = FixoraTheme.extendedColors.textSecondary,
                    )
                }
                RepairStatusChip(status = request.status)
            }

            Text(
                text = request.status.description,
                style = MaterialTheme.typography.bodyMedium,
            )

            if (request.status.timelineIndex >= 0) {
                Text(
                    text = "Stage $stageNumber of $stageCount",
                    style = MaterialTheme.typography.labelMedium,
                    color = FixoraTheme.extendedColors.textSecondary,
                )
            }

            HorizontalDivider(color = FixoraTheme.extendedColors.border)

            DetailRow("Reference", repairReference(request.id))
            branchName?.let { DetailRow("Branch", it) }
            formatDate(request.createdAt)?.let { DetailRow("Booked", it) }
            formatDateTime(request.scheduledAt)?.let { DetailRow("Drop-off", it) }
        }
    }
}

@Composable
internal fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = FixoraTheme.extendedColors.textSecondary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun CancelledCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FixoraSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(FixoraSpacing.sm),
        ) {
            Icon(
                Icons.Rounded.Cancel,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(40.dp),
            )
            Text("Repair cancelled", style = MaterialTheme.typography.titleSmall)
            Text(
                RepairStatus.CANCELLED.description,
                style = MaterialTheme.typography.bodySmall,
                color = FixoraTheme.extendedColors.textSecondary,
            )
        }
    }
}

@Composable
private fun TrackingSkeleton() {
    val transition = rememberInfiniteTransition(label = "trackingSkeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "trackingSkeletonAlpha",
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(FixoraSpacing.md),
        verticalArrangement = Arrangement.spacedBy(FixoraSpacing.sm),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(12.dp))
                .alpha(alpha)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .clip(RoundedCornerShape(12.dp))
                .alpha(alpha)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
    }
}

@Composable
private fun TrackingError(message: String, onRetry: () -> Unit) {
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
            "Couldn't load this repair",
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
