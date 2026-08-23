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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HistoryToggleOff
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.techfix.app.core.designsystem.FixoraSpacing
import com.techfix.app.core.designsystem.FixoraTheme
import com.techfix.app.domain.repair.RepairRequest
import com.techfix.app.ui.customer.catalog.icon
import com.techfix.app.ui.customer.catalog.label

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepairHistoryScreen(
    uiState: RepairHistoryUiState,
    onRetry: () -> Unit,
    onRepairClick: (RepairRequest) -> Unit,
    /** A live repair opens the tracking timeline instead of the history detail. */
    onTrackRepair: (RepairRequest) -> Unit,
    onBrowseServices: () -> Unit,
    onBack: () -> Unit,
    /** False on the My Repairs tab, where there is nothing behind to go back to. */
    showBack: Boolean = true,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Repairs") },
                navigationIcon = {
                    if (showBack) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Crossfade(
            targetState = when {
                uiState.isLoading -> HistoryPane.LOADING
                uiState.errorMessage != null -> HistoryPane.ERROR
                uiState.isEmpty -> HistoryPane.EMPTY
                else -> HistoryPane.CONTENT
            },
            animationSpec = tween(220),
            label = "historyPane",
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) { pane ->
            when (pane) {
                HistoryPane.LOADING -> HistorySkeleton()
                HistoryPane.ERROR -> HistoryError(
                    message = uiState.errorMessage ?: "Something went wrong.",
                    onRetry = onRetry,
                )
                HistoryPane.EMPTY -> HistoryEmpty(onBrowseServices)
                HistoryPane.CONTENT -> HistoryContent(
                    activeRepairs = uiState.activeRepairs,
                    pastRepairs = uiState.repairs,
                    serviceNames = uiState.serviceNames,
                    onRepairClick = onRepairClick,
                    onTrackRepair = onTrackRepair,
                )
            }
        }
    }
}

private enum class HistoryPane { LOADING, ERROR, EMPTY, CONTENT }

@Composable
private fun HistoryContent(
    activeRepairs: List<RepairRequest>,
    pastRepairs: List<RepairRequest>,
    serviceNames: Map<String, String>,
    onRepairClick: (RepairRequest) -> Unit,
    onTrackRepair: (RepairRequest) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(FixoraSpacing.md),
        verticalArrangement = Arrangement.spacedBy(FixoraSpacing.sm),
    ) {
        // The section headers only appear when there is something in both
        // halves — a single unlabelled list reads better than one list under
        // a header with nothing to contrast it against.
        val showHeaders = activeRepairs.isNotEmpty() && pastRepairs.isNotEmpty()

        if (activeRepairs.isNotEmpty()) {
            if (showHeaders) {
                item(key = "header_active") { SectionLabel("In progress") }
            }
            items(activeRepairs, key = { it.id }) { repair ->
                HistoryCard(
                    repair = repair,
                    serviceName = serviceNames[repair.serviceId],
                    onClick = { onTrackRepair(repair) },
                )
            }
        }

        if (pastRepairs.isNotEmpty()) {
            if (showHeaders) {
                item(key = "header_past") { SectionLabel("Past repairs") }
            }
            items(pastRepairs, key = { it.id }) { repair ->
                HistoryCard(
                    repair = repair,
                    serviceName = serviceNames[repair.serviceId],
                    onClick = { onRepairClick(repair) },
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = FixoraTheme.extendedColors.textSecondary,
        modifier = Modifier.padding(top = FixoraSpacing.sm),
    )
}

@Composable
private fun HistoryCard(repair: RepairRequest, serviceName: String?, onClick: () -> Unit) {
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
            // The first photo doubles as the card's thumbnail; without one,
            // the device category icon stands in so the row never looks broken.
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(FixoraSpacing.sm))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                val thumbnail = repair.imageUrls.firstOrNull()
                if (thumbnail != null) {
                    AsyncImage(
                        model = thumbnail,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        repair.deviceDetails.category.icon,
                        contentDescription = null,
                        tint = FixoraTheme.extendedColors.textSecondary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "${repair.deviceDetails.brand} ${repair.deviceDetails.model}".trim()
                        .ifBlank { repair.deviceDetails.category.label },
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = serviceName ?: repair.deviceDetails.category.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = FixoraTheme.extendedColors.textSecondary,
                    maxLines = 1,
                )
                formatDate(repair.createdAt)?.let { booked ->
                    Text(
                        text = booked,
                        style = MaterialTheme.typography.labelMedium,
                        color = FixoraTheme.extendedColors.textSecondary,
                    )
                }
                RepairStatusChip(
                    status = repair.status,
                    modifier = Modifier.padding(top = FixoraSpacing.xs),
                )
            }
        }
    }
}

@Composable
private fun HistorySkeleton() {
    val transition = rememberInfiniteTransition(label = "historySkeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "historySkeletonAlpha",
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
                    .height(88.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .alpha(alpha)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
    }
}

@Composable
private fun HistoryEmpty(onBrowseServices: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(FixoraSpacing.lg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Rounded.HistoryToggleOff,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = FixoraTheme.extendedColors.textSecondary,
        )
        Text(
            "No repairs yet",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = FixoraSpacing.sm),
        )
        Text(
            "Repairs you book show up here — live ones to track, finished ones with their cost, dates, and photos.",
            style = MaterialTheme.typography.bodySmall,
            color = FixoraTheme.extendedColors.textSecondary,
        )
        OutlinedButton(onClick = onBrowseServices, modifier = Modifier.padding(top = FixoraSpacing.md)) {
            Text("Browse services")
        }
    }
}

@Composable
private fun HistoryError(message: String, onRetry: () -> Unit) {
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
            "Couldn't load your history",
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
