package com.techfix.app.ui.customer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.techfix.app.core.designsystem.FixoraSpacing
import com.techfix.app.core.designsystem.FixoraTheme
import com.techfix.app.domain.catalog.DeviceCategory
import com.techfix.app.domain.repair.RepairRequest
import com.techfix.app.domain.repair.RepairStatus
import com.techfix.app.ui.customer.catalog.icon
import com.techfix.app.ui.customer.catalog.label
import com.techfix.app.ui.customer.repair.RepairStatusChip
import com.techfix.app.ui.customer.repair.description
import com.techfix.app.ui.customer.repair.repairReference

/**
 * Customer Home.
 *
 * Four blocks, in the order a returning customer needs them: what state their
 * repairs are in (stat row), what the app can fix (service grid), the repair
 * they most likely opened the app to check (Recent Repairs), and two links
 * into the screens they reach most often.
 *
 * Sign-out moved off this screen when the bottom bar landed — it lives on
 * Profile now, which is where the account lives.
 */
@Composable
fun CustomerHomeScreen(
    uiState: CustomerHomeUiState,
    onBookRepair: () -> Unit,
    onBrowseServices: () -> Unit,
    onCategorySelected: (DeviceCategory) -> Unit,
    onTrackRepair: (String) -> Unit,
    onViewRepairDetail: (String) -> Unit,
    onViewAllRepairs: () -> Unit,
) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = FixoraSpacing.md)
                .padding(top = FixoraSpacing.md, bottom = FixoraSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(FixoraSpacing.lg),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(FixoraSpacing.xs)) {
                Text(
                    "Welcome back",
                    style = MaterialTheme.typography.labelMedium,
                    color = FixoraTheme.extendedColors.textSecondary,
                )
                Text("Fixora", style = MaterialTheme.typography.displayLarge)
            }

            HeroCard(onBookRepair = onBookRepair)

            StatRow(uiState = uiState)

            // A failed read is a caption under the stats, not a full-screen
            // error — the grid, the hero and the quick links still work.
            AnimatedVisibility(
                visible = uiState.errorMessage != null,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(150)),
            ) {
                Text(
                    text = uiState.errorMessage.orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    color = FixoraTheme.extendedColors.textSecondary,
                )
            }

            SectionHeader(
                title = "Our Services",
                actionLabel = "See all",
                onAction = onBrowseServices,
            )
            ServiceTileGrid(onCategorySelected = onCategorySelected)

            SectionHeader(
                title = "Recent Repairs",
                actionLabel = if (uiState.totalCount > 1) "View all" else null,
                onAction = onViewAllRepairs,
            )
            Crossfade(
                targetState = when {
                    uiState.isLoading -> RecentPane.LOADING
                    uiState.recentRepair != null -> RecentPane.CONTENT
                    else -> RecentPane.EMPTY
                },
                animationSpec = tween(220),
                label = "recentRepairPane",
            ) { pane ->
                when (pane) {
                    RecentPane.LOADING -> RecentRepairSkeleton()
                    RecentPane.EMPTY -> NoRepairsCard(onBookRepair = onBookRepair)
                    RecentPane.CONTENT -> uiState.recentRepair?.let { repair ->
                        RecentRepairCard(
                            repair = repair,
                            serviceName = uiState.recentServiceName,
                            onClick = {
                                if (repair.status.isTerminal) {
                                    onViewRepairDetail(repair.id)
                                } else {
                                    onTrackRepair(repair.id)
                                }
                            },
                        )
                    }
                }
            }

            QuickLinkRow(
                onTrackRepair = {
                    // Straight to the timeline when something is live;
                    // otherwise to the list, which is where a customer with
                    // nothing in progress would have to look anyway.
                    val active = uiState.activeRepair
                    if (active != null) onTrackRepair(active.id) else onViewAllRepairs()
                },
                onViewAllRepairs = onViewAllRepairs,
            )
        }
    }
}

private enum class RecentPane { LOADING, EMPTY, CONTENT }

@Composable
private fun HeroCard(onBookRepair: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(180.dp)) {
            Image(
                painter = painterResource(HomeImagery.hero),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // The photo is a backdrop for text, so it carries a scrim rather
            // than being left to fight the copy for contrast. Both themes get
            // the same treatment: the text on it is always light.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xE6111827), Color(0x66111827)),
                        ),
                    ),
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(FixoraSpacing.md),
                verticalArrangement = Arrangement.spacedBy(FixoraSpacing.xs),
            ) {
                Text(
                    "Smart device repair",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                )
                Text(
                    "Mobiles, laptops, desktops and tablets — booked in a few taps and tracked to pickup.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFE5E7EB),
                    modifier = Modifier.fillMaxWidth(0.8f),
                )
                Box(modifier = Modifier.weight(1f))
                Button(
                    onClick = onBookRepair,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FixoraTheme.extendedColors.accent,
                        contentColor = FixoraTheme.extendedColors.onAccent,
                    ),
                ) {
                    Icon(
                        Icons.Rounded.AddCircle,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp).padding(end = 0.dp),
                    )
                    Text("Book Repair", modifier = Modifier.padding(start = FixoraSpacing.sm))
                }
            }
        }
    }
}

@Composable
private fun StatRow(uiState: CustomerHomeUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.sm)) {
        StatCard(
            modifier = Modifier.weight(1f),
            value = uiState.totalCount,
            label = "Total",
            valueColor = MaterialTheme.colorScheme.primary,
            isLoading = uiState.isLoading,
        )
        StatCard(
            modifier = Modifier.weight(1f),
            value = uiState.activeCount,
            label = "Active",
            valueColor = FixoraTheme.extendedColors.warningOnSurface,
            isLoading = uiState.isLoading,
        )
        StatCard(
            modifier = Modifier.weight(1f),
            value = uiState.doneCount,
            label = "Done",
            valueColor = FixoraTheme.extendedColors.successOnSurface,
            isLoading = uiState.isLoading,
        )
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    value: Int,
    label: String,
    valueColor: Color,
    isLoading: Boolean,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = FixoraSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(FixoraSpacing.xs),
        ) {
            Crossfade(targetState = isLoading, animationSpec = tween(200), label = "statValue") { loading ->
                if (loading) {
                    ShimmerBox(
                        modifier = Modifier
                            .size(width = 40.dp, height = 28.dp)
                            .clip(RoundedCornerShape(FixoraSpacing.xs)),
                    )
                } else {
                    Text(
                        text = value.toString(),
                        style = MaterialTheme.typography.displayLarge,
                        color = valueColor,
                    )
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = FixoraTheme.extendedColors.textSecondary,
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, actionLabel: String?, onAction: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (actionLabel != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/**
 * The four device categories the catalog is actually built from
 * ([DeviceCategory]), not an invented taxonomy — tapping one opens the
 * catalog already filtered to it.
 *
 * Each tile takes a different palette accent so the grid reads as four
 * things rather than one thing four times. The tint is the fill and the
 * matching `…OnSurface` token is the ink, per the design system's split
 * between the two.
 */
@Composable
private fun ServiceTileGrid(onCategorySelected: (DeviceCategory) -> Unit) {
    val categories = DeviceCategory.entries.toList()
    Column(verticalArrangement = Arrangement.spacedBy(FixoraSpacing.sm)) {
        categories.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.sm)) {
                row.forEach { category ->
                    ServiceTile(
                        modifier = Modifier.weight(1f),
                        category = category,
                        onClick = { onCategorySelected(category) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ServiceTile(
    modifier: Modifier = Modifier,
    category: DeviceCategory,
    onClick: () -> Unit,
) {
    val ink = when (category) {
        DeviceCategory.MOBILE -> MaterialTheme.colorScheme.primary
        DeviceCategory.LAPTOP -> FixoraTheme.extendedColors.accentOnSurface
        DeviceCategory.DESKTOP -> FixoraTheme.extendedColors.successOnSurface
        DeviceCategory.TABLET -> FixoraTheme.extendedColors.warningOnSurface
    }
    val fill = when (category) {
        DeviceCategory.MOBILE -> MaterialTheme.colorScheme.primary
        DeviceCategory.LAPTOP -> FixoraTheme.extendedColors.accent
        DeviceCategory.DESKTOP -> FixoraTheme.extendedColors.success
        DeviceCategory.TABLET -> FixoraTheme.extendedColors.warning
    }
    val caption = when (category) {
        DeviceCategory.MOBILE -> "Screens, batteries, charging"
        DeviceCategory.LAPTOP -> "Keyboards, storage, cooling"
        DeviceCategory.DESKTOP -> "Power, upgrades, diagnostics"
        DeviceCategory.TABLET -> "Glass, batteries, software"
    }

    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FixoraSpacing.md),
            verticalArrangement = Arrangement.spacedBy(FixoraSpacing.sm),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(fill.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = category.icon,
                    contentDescription = null,
                    tint = ink,
                    modifier = Modifier.size(24.dp),
                )
            }
            Text(category.label, style = MaterialTheme.typography.titleSmall)
            Text(
                caption,
                style = MaterialTheme.typography.labelMedium,
                color = FixoraTheme.extendedColors.textSecondary,
            )
        }
    }
}

@Composable
private fun RecentRepairCard(
    repair: RepairRequest,
    serviceName: String?,
    onClick: () -> Unit,
) {
    val stageCount = RepairStatus.timeline.size
    val stageNumber = (repair.status.timelineIndex + 1).coerceAtLeast(1)
    // Same animated progress as the tracking timeline: when the live listener
    // pushes a new status, the bar travels instead of jumping.
    val progress by animateFloatAsState(
        targetValue = stageNumber.toFloat() / stageCount,
        animationSpec = tween(280),
        label = "recentRepairProgress",
    )
    val isActive = !repair.status.isTerminal

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(FixoraSpacing.md),
            verticalArrangement = Arrangement.spacedBy(FixoraSpacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The repair's own first photo where there is one; the bundled
                // bench shot only stands in so the row never renders empty.
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
                        Image(
                            painter = painterResource(HomeImagery.technicianAtWork),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${repair.deviceDetails.brand} ${repair.deviceDetails.model}".trim()
                            .ifBlank { repair.deviceDetails.category.label },
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = serviceName ?: "Ref ${repairReference(repair.id)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = FixoraTheme.extendedColors.textSecondary,
                        maxLines = 1,
                    )
                }
                RepairStatusChip(status = repair.status)
            }

            if (isActive) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Text(
                    text = "Stage $stageNumber of $stageCount — ${repair.status.description}",
                    style = MaterialTheme.typography.bodySmall,
                    color = FixoraTheme.extendedColors.textSecondary,
                )
                OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
                    Text("Track repair")
                }
            } else {
                Text(
                    text = repair.status.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = FixoraTheme.extendedColors.textSecondary,
                )
                OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
                    Text("View details")
                }
            }
        }
    }
}

/** Empty state for a customer who has never booked: image, message, action. */
@Composable
private fun NoRepairsCard(onBookRepair: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
            ) {
                Image(
                    painter = painterResource(HomeImagery.noRepairs),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(FixoraSpacing.md),
                verticalArrangement = Arrangement.spacedBy(FixoraSpacing.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("No repairs yet", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Book your first repair and you'll be able to follow it here, stage by stage, until pickup.",
                    style = MaterialTheme.typography.bodySmall,
                    color = FixoraTheme.extendedColors.textSecondary,
                    textAlign = TextAlign.Center,
                )
                Button(
                    onClick = onBookRepair,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = FixoraSpacing.sm),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FixoraTheme.extendedColors.accent,
                        contentColor = FixoraTheme.extendedColors.onAccent,
                    ),
                ) {
                    Text("Book Repair")
                }
            }
        }
    }
}

/**
 * Two links into screens the app actually has — the live timeline and the
 * repair list. Nothing here opens a feature that doesn't exist.
 */
@Composable
private fun QuickLinkRow(
    onTrackRepair: () -> Unit,
    onViewAllRepairs: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.sm)) {
        QuickLinkCard(
            modifier = Modifier.weight(1f),
            image = HomeImagery.trackRepair,
            icon = Icons.Rounded.MyLocation,
            title = "Track repair",
            subtitle = "Live status",
            onClick = onTrackRepair,
        )
        QuickLinkCard(
            modifier = Modifier.weight(1f),
            image = HomeImagery.repairHistory,
            icon = Icons.Rounded.History,
            title = "My repairs",
            subtitle = "Past and active",
            onClick = onViewAllRepairs,
        )
    }
}

@Composable
private fun QuickLinkCard(
    modifier: Modifier = Modifier,
    image: Int,
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
            ) {
                Image(
                    painter = painterResource(image),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0x33111827), Color(0xCC111827)),
                            ),
                        ),
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(FixoraSpacing.sm)
                        .size(20.dp),
                )
            }
            Column(
                modifier = Modifier.padding(FixoraSpacing.md),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.xs),
                ) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = FixoraTheme.extendedColors.textSecondary,
                    )
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = FixoraTheme.extendedColors.textSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentRepairSkeleton() {
    ShimmerBox(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(RoundedCornerShape(12.dp)),
    )
}

/** The same pulsing placeholder the catalog and history skeletons use. */
@Composable
private fun ShimmerBox(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "homeSkeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "homeSkeletonAlpha",
    )
    Box(
        modifier = modifier
            .alpha(alpha)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}
