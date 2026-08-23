package com.techfix.app.ui.customer.catalog

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.techfix.app.core.designsystem.FixoraRadius
import com.techfix.app.core.designsystem.FixoraSpacing
import com.techfix.app.core.designsystem.FixoraTheme
import com.techfix.app.core.designsystem.OfflineNotice
import com.techfix.app.core.designsystem.pressScale
import com.techfix.app.domain.catalog.DeviceCategory
import com.techfix.app.domain.catalog.RepairService
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceCatalogScreen(
    uiState: ServiceCatalogUiState,
    onQueryChange: (String) -> Unit,
    onCategorySelect: (DeviceCategory?) -> Unit,
    onRetry: () -> Unit,
    onClearFilters: () -> Unit,
    onServiceClick: (RepairService) -> Unit,
    onBack: () -> Unit,
    title: String = "Services",
    hint: String? = null,
    showBack: Boolean = true,
) {
    Scaffold(
        topBar = {
            if (showBack) {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            CatalogHeader(
                title = title,
                hint = hint,
                query = uiState.query,
                onQueryChange = onQueryChange,
                selectedCategory = uiState.selectedCategory,
                onCategorySelect = onCategorySelect,
            )
            OfflineNotice(visible = uiState.isOffline, modifier = Modifier.padding(horizontal = FixoraSpacing.md))
            Crossfade(
                targetState = when {
                    uiState.isLoading -> CatalogPane.LOADING
                    uiState.errorMessage != null -> CatalogPane.ERROR
                    uiState.isEmpty -> CatalogPane.EMPTY
                    else -> CatalogPane.CONTENT
                },
                label = "catalogPane",
                modifier = Modifier.weight(1f),
            ) { pane ->
                when (pane) {
                    CatalogPane.LOADING -> CatalogSkeleton()
                    CatalogPane.ERROR -> CatalogError(onRetry)
                    CatalogPane.EMPTY -> CatalogEmpty(hasSearch = uiState.query.isNotBlank(), onClearFilters = onClearFilters)
                    CatalogPane.CONTENT -> CatalogContent(uiState.groupedServices, onServiceClick)
                }
            }
        }
    }
}

private enum class CatalogPane { LOADING, ERROR, EMPTY, CONTENT }

@Composable
private fun CatalogHeader(
    title: String,
    hint: String?,
    query: String,
    onQueryChange: (String) -> Unit,
    selectedCategory: DeviceCategory?,
    onCategorySelect: (DeviceCategory?) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = FixoraSpacing.md, vertical = FixoraSpacing.md),
        verticalArrangement = Arrangement.spacedBy(FixoraSpacing.sm),
    ) {
        Text("FIXORA / REPAIR STUDIO", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.displayLarge)
        Text(hint ?: "Choose the repair service you need", style = MaterialTheme.typography.bodyMedium, color = FixoraTheme.extendedColors.textSecondary)
        PremiumSearchField(query = query, onQueryChange = onQueryChange)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.sm), contentPadding = PaddingValues(vertical = FixoraSpacing.xs)) {
            item {
                CategoryChip(
                    label = "All",
                    category = null,
                    selectedCategory = selectedCategory,
                    onCategorySelect = onCategorySelect,
                )
            }
            items(DeviceCategory.entries.toList()) { category -> CategoryChip(category.label, category.icon, category, selectedCategory, onCategorySelect) }
        }
    }
}

@Composable
private fun PremiumSearchField(query: String, onQueryChange: (String) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(FixoraRadius.input)
    val borderColor by animateColorAsState(
        targetValue = if (focused) MaterialTheme.colorScheme.primary else FixoraTheme.extendedColors.border,
        animationSpec = tween(180),
        label = "searchBorder",
    )
    val iconColor by animateColorAsState(
        targetValue = if (focused) MaterialTheme.colorScheme.primary else FixoraTheme.extendedColors.textSecondary,
        animationSpec = tween(180),
        label = "searchIcon",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(BorderStroke(1.dp, borderColor), shape)
            .padding(horizontal = FixoraSpacing.md, vertical = FixoraSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.sm),
    ) {
        Icon(Icons.Rounded.Search, contentDescription = "Search services", tint = iconColor, modifier = Modifier.size(24.dp))
        Box(modifier = Modifier.weight(1f)) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    if (query.isBlank()) Text("Search repairs, devices or issues", style = MaterialTheme.typography.bodyMedium, color = FixoraTheme.extendedColors.textSecondary)
                    innerTextField()
                },
            )
        }
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = "Clear search", modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun CategoryChip(
    label: String,
    icon: ImageVector? = null,
    category: DeviceCategory?,
    selectedCategory: DeviceCategory?,
    onCategorySelect: (DeviceCategory?) -> Unit,
) {
    FilterChip(
        selected = selectedCategory == category,
        onClick = { onCategorySelect(if (selectedCategory == category && category != null) null else category) },
        leadingIcon = icon?.let { { Icon(it, contentDescription = null, modifier = Modifier.size(18.dp)) } },
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

@Composable
private fun CatalogContent(grouped: List<Pair<DeviceCategory, List<RepairService>>>, onServiceClick: (RepairService) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = FixoraSpacing.md, end = FixoraSpacing.md, bottom = FixoraSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(FixoraSpacing.md),
    ) {
        grouped.forEach { (category, services) ->
            item(key = "header_${category.name}") { CategorySectionHeader(category, services.size) }
            services.firstOrNull()?.let { featured ->
                item(key = "featured_${featured.id}") {
                    ServiceEntrance(service = featured, index = 0) {
                        FeaturedServiceCard(featured, onClick = { onServiceClick(featured) })
                    }
                }
            }
            itemsIndexed(services.drop(1), key = { _, service -> service.id }) { index, service ->
                ServiceEntrance(service = service, index = index + 1) {
                    CompactServiceCard(service, onClick = { onServiceClick(service) })
                }
            }
        }
    }
}

@Composable
private fun CategorySectionHeader(category: DeviceCategory, count: Int) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Icon(category.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Text(category.label, style = MaterialTheme.typography.titleSmall)
        }
        Text("$count services", style = MaterialTheme.typography.labelMedium, color = FixoraTheme.extendedColors.textSecondary)
    }
}

@Composable
private fun ServiceEntrance(service: RepairService, index: Int, content: @Composable () -> Unit) {
    var visible by rememberSaveable(service.id) { mutableStateOf(false) }
    LaunchedEffect(service.id) {
        if (!visible) {
            delay(index * 35L)
            visible = true
        }
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 8 },
    ) { content() }
}

@Composable
private fun FeaturedServiceCard(service: RepairService, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interactionSource)
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current, role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(FixoraRadius.card),
        border = BorderStroke(1.dp, FixoraTheme.extendedColors.border),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().height(164.dp)) {
                ServiceImage(service, modifier = Modifier.fillMaxSize())
                ServiceImageBadge(service.category.label, service.category.icon)
                Icon(Icons.Rounded.ChevronRight, contentDescription = "Open ${service.name}", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.align(Alignment.TopEnd).padding(FixoraSpacing.md).size(24.dp))
            }
            ServiceCardCopy(service, featured = true)
        }
    }
}

@Composable
private fun CompactServiceCard(service: RepairService, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interactionSource)
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current, role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(FixoraRadius.card),
        border = BorderStroke(1.dp, FixoraTheme.extendedColors.border),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(FixoraSpacing.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.md)) {
            ServiceImage(service, modifier = Modifier.size(96.dp).clip(RoundedCornerShape(FixoraRadius.input)))
            ServiceCardCopy(service, featured = false, modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.ChevronRight, contentDescription = "Open ${service.name}", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun ServiceCardCopy(service: RepairService, featured: Boolean, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(if (featured) FixoraSpacing.md else FixoraSpacing.xs), verticalArrangement = Arrangement.spacedBy(FixoraSpacing.xs)) {
        if (featured) {
            Text(service.category.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        Text(service.name, style = if (featured) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(service.description, style = MaterialTheme.typography.bodySmall, color = FixoraTheme.extendedColors.textSecondary, maxLines = if (featured) 2 else 1, overflow = TextOverflow.Ellipsis)
        Row(modifier = Modifier.fillMaxWidth().padding(top = FixoraSpacing.xs), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Column(verticalArrangement = Arrangement.spacedBy(FixoraSpacing.xs)) {
                Text("Starting from", style = MaterialTheme.typography.labelMedium, color = FixoraTheme.extendedColors.textSecondary)
                Text("Rs. %,.0f".format(service.basePrice), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }
            if (featured) Text("View service", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun BoxScope.ServiceImageBadge(label: String, icon: ImageVector) {
    Row(
        modifier = Modifier.align(Alignment.TopStart).padding(FixoraSpacing.md).clip(RoundedCornerShape(FixoraRadius.input)).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)).padding(horizontal = FixoraSpacing.sm, vertical = FixoraSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun CatalogSkeleton() {
    val transition = rememberInfiniteTransition(label = "catalogSkeleton")
    val alpha by transition.animateFloat(0.4f, 1f, infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse), label = "skeletonAlpha")
    Column(modifier = Modifier.fillMaxSize().padding(FixoraSpacing.md), verticalArrangement = Arrangement.spacedBy(FixoraSpacing.md)) {
        SkeletonBlock(large = true, alpha = alpha)
        repeat(3) { SkeletonBlock(large = false, alpha = alpha) }
    }
}

@Composable
private fun SkeletonBlock(large: Boolean, alpha: Float) {
    if (large) {
        Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(FixoraRadius.card)).background(MaterialTheme.colorScheme.surface).padding(FixoraSpacing.md), verticalArrangement = Arrangement.spacedBy(FixoraSpacing.sm)) {
            Box(modifier = Modifier.fillMaxWidth().height(156.dp).alpha(alpha).clip(RoundedCornerShape(FixoraRadius.input)).background(MaterialTheme.colorScheme.surfaceVariant))
            Box(modifier = Modifier.fillMaxWidth(0.7f).height(20.dp).alpha(alpha).clip(RoundedCornerShape(FixoraRadius.input)).background(MaterialTheme.colorScheme.surfaceVariant))
            Box(modifier = Modifier.fillMaxWidth(0.45f).height(16.dp).alpha(alpha).clip(RoundedCornerShape(FixoraRadius.input)).background(MaterialTheme.colorScheme.surfaceVariant))
        }
    } else {
        Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(FixoraRadius.card)).background(MaterialTheme.colorScheme.surface).padding(FixoraSpacing.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.md)) {
            Box(modifier = Modifier.size(96.dp).alpha(alpha).clip(RoundedCornerShape(FixoraRadius.input)).background(MaterialTheme.colorScheme.surfaceVariant))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FixoraSpacing.sm)) {
                Box(modifier = Modifier.fillMaxWidth(0.8f).height(20.dp).alpha(alpha).clip(RoundedCornerShape(FixoraRadius.input)).background(MaterialTheme.colorScheme.surfaceVariant))
                Box(modifier = Modifier.fillMaxWidth(0.55f).height(16.dp).alpha(alpha).clip(RoundedCornerShape(FixoraRadius.input)).background(MaterialTheme.colorScheme.surfaceVariant))
            }
        }
    }
}

@Composable
private fun CatalogEmpty(hasSearch: Boolean, onClearFilters: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(FixoraSpacing.xl), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.SearchOff, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Text(if (hasSearch) "No services found" else "No services available", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = FixoraSpacing.md))
        Text("Try another category or search for a different repair.", style = MaterialTheme.typography.bodySmall, color = FixoraTheme.extendedColors.textSecondary)
        OutlinedButton(onClick = onClearFilters, modifier = Modifier.padding(top = FixoraSpacing.md)) { Text("View all services") }
    }
}

@Composable
private fun CatalogError(onRetry: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(FixoraSpacing.xl), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.ErrorOutline, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
        Text("Unable to load services", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = FixoraSpacing.md))
        Text("Please check your connection and try again.", style = MaterialTheme.typography.bodySmall, color = FixoraTheme.extendedColors.textSecondary)
        Button(onClick = onRetry, modifier = Modifier.padding(top = FixoraSpacing.md), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { Text("Try again") }
    }
}
