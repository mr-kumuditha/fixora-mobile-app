package com.techfix.app.ui.customer.repair

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.ErrorOutline
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.techfix.app.core.designsystem.FixoraSpacing
import com.techfix.app.core.designsystem.FixoraTheme
import com.techfix.app.domain.repair.RepairRequest
import com.techfix.app.ui.customer.catalog.label

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepairHistoryDetailScreen(
    uiState: RepairHistoryDetailUiState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Repair Details") },
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
        Crossfade(
            targetState = when {
                uiState.isLoading -> DetailPane.LOADING
                uiState.request != null -> DetailPane.CONTENT
                else -> DetailPane.ERROR
            },
            animationSpec = tween(220),
            label = "historyDetailPane",
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) { pane ->
            when (pane) {
                DetailPane.LOADING -> DetailSkeleton()
                DetailPane.ERROR -> DetailError(
                    message = uiState.errorMessage ?: "This repair couldn't be found.",
                    onRetry = onRetry,
                )
                DetailPane.CONTENT -> uiState.request?.let { DetailContent(it, uiState) }
            }
        }
    }
}

private enum class DetailPane { LOADING, ERROR, CONTENT }

@Composable
private fun DetailContent(request: RepairRequest, uiState: RepairHistoryDetailUiState) {
    var expandedImageUrl by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(FixoraSpacing.md),
        verticalArrangement = Arrangement.spacedBy(FixoraSpacing.md),
    ) {
        // ------------------------------------------------ final status + cost
        SectionCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${request.deviceDetails.brand} ${request.deviceDetails.model}".trim()
                            .ifBlank { request.deviceDetails.category.label },
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = uiState.serviceName ?: request.deviceDetails.category.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = FixoraTheme.extendedColors.textSecondary,
                    )
                }
                RepairStatusChip(status = request.status)
            }

            uiState.cost?.let { cost ->
                HorizontalDivider(
                    color = FixoraTheme.extendedColors.border,
                    modifier = Modifier.padding(vertical = FixoraSpacing.sm),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = if (uiState.costIsEstimate) "Estimated cost" else "Amount paid",
                            style = MaterialTheme.typography.bodySmall,
                            color = FixoraTheme.extendedColors.textSecondary,
                        )
                        if (uiState.costIsEstimate) {
                            Text(
                                "Base price for this service — no payment recorded.",
                                style = MaterialTheme.typography.labelMedium,
                                color = FixoraTheme.extendedColors.textSecondary,
                            )
                        }
                    }
                    Text(
                        text = formatPrice(cost),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        // ------------------------------------------------------- device info
        SectionCard {
            Text("Device", style = MaterialTheme.typography.titleSmall)
            Column(
                modifier = Modifier.padding(top = FixoraSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(FixoraSpacing.xs),
            ) {
                DetailRow("Category", request.deviceDetails.category.label)
                DetailRow("Brand", request.deviceDetails.brand.ifBlank { "—" })
                DetailRow("Model", request.deviceDetails.model.ifBlank { "—" })
                request.deviceDetails.serialNumber
                    ?.takeIf { it.isNotBlank() }
                    ?.let { DetailRow("Serial", it) }
            }
        }

        // ------------------------------------------------------- reported issue
        SectionCard {
            Text("Reported issue", style = MaterialTheme.typography.titleSmall)
            Text(
                text = request.issueDescription.ifBlank { "No description was given." },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = FixoraSpacing.sm),
            )
        }

        // -------------------------------------------------------------- dates
        SectionCard {
            Text("Dates", style = MaterialTheme.typography.titleSmall)
            Column(
                modifier = Modifier.padding(top = FixoraSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(FixoraSpacing.xs),
            ) {
                DetailRow("Reference", repairReference(request.id))
                uiState.branchName?.let { DetailRow("Branch", it) }
                DetailRow("Booked", formatDateTime(request.createdAt) ?: "—")
                DetailRow("Drop-off", formatDateTime(request.scheduledAt) ?: "—")
                // Written when the repair reaches COMPLETED, which only
                // happens through the payment flow (Block 7). Repairs finished
                // before that field existed simply don't show the row.
                formatDateTime(request.completedAt)?.let { DetailRow("Completed", it) }
                formatDateTime(uiState.paidAt)?.let { DetailRow("Paid", it) }
                uiState.receiptId?.let { DetailRow("Receipt", it) }
            }
        }

        // ------------------------------------------------------------- photos
        if (request.imageUrls.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(modifier = Modifier.padding(vertical = FixoraSpacing.md)) {
                    Text(
                        "Photos",
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
                                contentDescription = "Repair photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(112.dp)
                                    .clip(RoundedCornerShape(FixoraSpacing.sm))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { expandedImageUrl = url },
                            )
                        }
                    }
                }
            }
        }
    }

    expandedImageUrl?.let { url ->
        Dialog(onDismissRequest = { expandedImageUrl = null }) {
            AsyncImage(
                model = url,
                contentDescription = "Repair photo",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(FixoraSpacing.md))
                    .background(MaterialTheme.colorScheme.surface),
            )
        }
    }
}

@Composable
private fun SectionCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(FixoraSpacing.md), content = content)
    }
}

@Composable
private fun DetailSkeleton() {
    val transition = rememberInfiniteTransition(label = "detailSkeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "detailSkeletonAlpha",
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(FixoraSpacing.md),
        verticalArrangement = Arrangement.spacedBy(FixoraSpacing.sm),
    ) {
        listOf(120.dp, 150.dp, 110.dp, 160.dp).forEach { blockHeight ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(blockHeight)
                    .clip(RoundedCornerShape(12.dp))
                    .alpha(alpha)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
    }
}

@Composable
private fun DetailError(message: String, onRetry: () -> Unit) {
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
