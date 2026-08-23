package com.techfix.app.ui.staff.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AttachMoney
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Inventory
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Store
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.techfix.app.core.designsystem.FixoraSpacing
import com.techfix.app.core.designsystem.FixoraTheme
import com.techfix.app.domain.catalog.DeviceCategory
import com.techfix.app.domain.inventory.AdminInventoryItem
import com.techfix.app.domain.inventory.InventoryAdjustment
import com.techfix.app.domain.inventory.InventorySort
import com.techfix.app.domain.inventory.InventoryStockFilter
import com.techfix.app.domain.inventory.InventoryStockStatus
import com.techfix.app.domain.inventory.StockAdjustmentType
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminInventoryScreen(
    uiState: AdminInventoryUiState,
    onQueryChange: (String) -> Unit,
    onBranchSelected: (String?) -> Unit,
    onCategorySelected: (String?) -> Unit,
    onStockFilterSelected: (InventoryStockFilter) -> Unit,
    onSortSelected: (InventorySort) -> Unit,
    onOpenDetails: (String) -> Unit,
    onCloseDetails: () -> Unit,
    onOpenCreate: () -> Unit,
    onOpenEdit: (AdminInventoryItem) -> Unit,
    onUpdateItemForm: (InventoryItemFormState) -> Unit,
    onCloseItemForm: () -> Unit,
    onSaveItem: () -> Unit,
    onOpenAdjustment: (AdminInventoryItem, String?) -> Unit,
    onUpdateAdjustmentForm: (InventoryAdjustmentFormState) -> Unit,
    onCloseAdjustment: () -> Unit,
    onSaveAdjustment: () -> Unit,
    onRequestArchive: (AdminInventoryItem) -> Unit,
    onDismissArchive: () -> Unit,
    onConfirmArchive: () -> Unit,
    onRestore: (AdminInventoryItem) -> Unit,
    onRetry: () -> Unit,
    onMessageShown: () -> Unit,
    onBack: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.actionMessage, uiState.actionError) {
        val message = uiState.actionMessage ?: uiState.actionError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onMessageShown()
    }

    uiState.selectedItem?.let { item ->
        InventoryDetailSheet(
            item = item,
            selectedBranchId = uiState.selectedBranchId,
            isSubmitting = uiState.isSubmitting,
            onDismiss = onCloseDetails,
            onAdjust = { branch -> onOpenAdjustment(item, branch) },
            onEdit = { onOpenEdit(item) },
            onArchive = { onRequestArchive(item) },
            onRestore = { onRestore(item) },
        )
    }
    uiState.itemForm?.let { form ->
        InventoryItemFormSheet(
            form = form,
            isSubmitting = uiState.isSubmitting,
            onChange = onUpdateItemForm,
            onDismiss = onCloseItemForm,
            onSave = onSaveItem,
        )
    }
    uiState.adjustmentForm?.let { form ->
        StockAdjustmentSheet(
            form = form,
            isSubmitting = uiState.isSubmitting,
            onChange = onUpdateAdjustmentForm,
            onDismiss = onCloseAdjustment,
            onSave = onSaveAdjustment,
        )
    }
    uiState.archiveItem?.let { item ->
        AlertDialog(
            onDismissRequest = onDismissArchive,
            shape = RoundedCornerShape(20.dp),
            icon = { Icon(Icons.Rounded.Archive, contentDescription = null) },
            title = { Text("Archive ${item.name}?") },
            text = {
                Text("It will be removed from active inventory and branch matching. Existing stock history is preserved, and the item can be restored later.")
            },
            confirmButton = {
                TextButton(onClick = onConfirmArchive, enabled = !uiState.isSubmitting) {
                    Text("Archive", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = onDismissArchive) { Text("Cancel") } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Column {
                        Text("Inventory", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Parts, stock and supplier records",
                            style = MaterialTheme.typography.labelMedium,
                            color = FixoraTheme.extendedColors.textSecondary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            if (!uiState.isLoading && uiState.errorMessage == null) {
                ExtendedFloatingActionButton(
                    onClick = onOpenCreate,
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text("Add item") },
                    containerColor = FixoraTheme.extendedColors.accent,
                    contentColor = FixoraTheme.extendedColors.onAccent,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when {
            uiState.isLoading -> InventoryManagementSkeleton(Modifier.padding(padding))
            uiState.errorMessage != null -> InventoryManagementError(
                message = uiState.errorMessage,
                onRetry = onRetry,
                modifier = Modifier.padding(padding),
            )
            else -> InventoryManagementContent(
                uiState = uiState,
                onQueryChange = onQueryChange,
                onBranchSelected = onBranchSelected,
                onCategorySelected = onCategorySelected,
                onStockFilterSelected = onStockFilterSelected,
                onSortSelected = onSortSelected,
                onOpenDetails = onOpenDetails,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun InventoryManagementContent(
    uiState: AdminInventoryUiState,
    onQueryChange: (String) -> Unit,
    onBranchSelected: (String?) -> Unit,
    onCategorySelected: (String?) -> Unit,
    onStockFilterSelected: (InventoryStockFilter) -> Unit,
    onSortSelected: (InventorySort) -> Unit,
    onOpenDetails: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = FixoraSpacing.md, end = FixoraSpacing.md, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(FixoraSpacing.md),
    ) {
        item {
            Text("Inventory overview", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(FixoraSpacing.sm))
            InventoryMetricGrid(uiState)
        }
        item {
            OutlinedTextField(
                value = uiState.query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = if (uiState.query.isNotBlank()) {
                    { IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Rounded.Close, "Clear search") } }
                } else null,
                placeholder = { Text("Search name, SKU, category or supplier") },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
            )
        }
        item {
            FilterSection("Stock location", Icons.Rounded.Store) {
                FilterChip(selected = uiState.selectedBranchId == null, onClick = { onBranchSelected(null) }, label = { Text("All branches") })
                AdminInventoryItem.STOCK_BRANCH_IDS.forEach { branch ->
                    FilterChip(
                        selected = uiState.selectedBranchId == branch,
                        onClick = { onBranchSelected(branch) },
                        label = { Text(branch.branchLabel()) },
                    )
                }
            }
        }
        if (uiState.categories.isNotEmpty()) {
            item {
                FilterSection("Category", Icons.Rounded.Category) {
                    FilterChip(selected = uiState.selectedCategory == null, onClick = { onCategorySelected(null) }, label = { Text("All") })
                    uiState.categories.forEach { category ->
                        FilterChip(
                            selected = uiState.selectedCategory == category,
                            onClick = { onCategorySelected(category) },
                            label = { Text(category.displayCategory()) },
                        )
                    }
                }
            }
        }
        item {
            FilterSection("Stock status", Icons.Rounded.Tune) {
                InventoryStockFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = uiState.stockFilter == filter,
                        onClick = { onStockFilterSelected(filter) },
                        label = { Text(filter.label()) },
                    )
                }
            }
        }
        item {
            FilterSection("Sort", Icons.AutoMirrored.Rounded.Sort) {
                InventorySort.entries.forEach { sort ->
                    FilterChip(
                        selected = uiState.sort == sort,
                        onClick = { onSortSelected(sort) },
                        label = { Text(sort.label()) },
                    )
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Inventory items", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    "${uiState.visibleItems.size} shown",
                    style = MaterialTheme.typography.labelMedium,
                    color = FixoraTheme.extendedColors.textSecondary,
                )
            }
        }
        if (uiState.visibleItems.isEmpty()) {
            item {
                InventoryEmptyCard(
                    if (uiState.items.isEmpty()) "No inventory items yet" else "No matching inventory",
                    if (uiState.items.isEmpty()) "Add the first spare part to start managing stock." else "Try clearing a search or filter.",
                )
            }
        } else {
            items(uiState.visibleItems, key = { it.id }) { item ->
                InventoryItemCard(
                    item = item,
                    branchId = uiState.selectedBranchId,
                    onClick = { onOpenDetails(item.id) },
                )
            }
        }
        if (uiState.recentAdjustments.isNotEmpty()) {
            item { Text("Recent inventory activity", style = MaterialTheme.typography.titleMedium) }
            items(uiState.recentAdjustments.take(6), key = { it.id }) { adjustment ->
                InventoryActivityRow(adjustment)
            }
        }
    }
}

@Composable
private fun InventoryMetricGrid(uiState: AdminInventoryUiState) {
    val metrics = uiState.metrics
    Column(verticalArrangement = Arrangement.spacedBy(FixoraSpacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.sm)) {
            InventoryMetricCard("Total items", metrics.totalItems.toString(), Icons.Rounded.Inventory2, Modifier.weight(1f))
            InventoryMetricCard("Available units", metrics.totalAvailableStock.toString(), Icons.Rounded.Inventory, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.sm)) {
            InventoryMetricCard("Low stock", metrics.lowStockItems.toString(), Icons.Rounded.WarningAmber, Modifier.weight(1f), FixoraTheme.extendedColors.warning)
            InventoryMetricCard("Out of stock", metrics.outOfStockItems.toString(), Icons.Rounded.ErrorOutline, Modifier.weight(1f), MaterialTheme.colorScheme.error)
        }
        metrics.inventoryValue?.let { value ->
            InventoryMetricCard(
                "Recorded inventory value",
                value.lkr(),
                Icons.Rounded.AttachMoney,
                Modifier.fillMaxWidth(),
                MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun InventoryMetricCard(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(FixoraSpacing.md), verticalArrangement = Arrangement.spacedBy(FixoraSpacing.sm)) {
            Box(
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp)) }
            Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, style = MaterialTheme.typography.labelMedium, color = FixoraTheme.extendedColors.textSecondary)
        }
    }
}

@Composable
private fun FilterSection(title: String, icon: ImageVector, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(FixoraSpacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.xs)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = FixoraTheme.extendedColors.textSecondary)
            Text(title, style = MaterialTheme.typography.labelMedium, color = FixoraTheme.extendedColors.textSecondary)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.sm)) { item { Row(horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.sm)) { content() } } }
    }
}

@Composable
private fun InventoryItemCard(item: AdminInventoryItem, branchId: String?, onClick: () -> Unit) {
    val status = item.statusFor(branchId)
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(FixoraSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.md),
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Inventory2, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FixoraSpacing.xs)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Icon(Icons.Rounded.MoreVert, contentDescription = null, tint = FixoraTheme.extendedColors.textSecondary)
                }
                Text(
                    listOfNotNull(item.sku, item.category.displayCategory()).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = FixoraTheme.extendedColors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.sm)) {
                    InventoryStatusChip(status)
                    Text(
                        "${item.quantityFor(branchId)} units${if (branchId == null) " total" else ""}",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    if (item.minimumStockLevel > 0) {
                        Text("Min ${item.minimumStockLevel}", style = MaterialTheme.typography.labelMedium, color = FixoraTheme.extendedColors.textSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun InventoryStatusChip(status: InventoryStockStatus) {
    val (label, icon, color) = when (status) {
        InventoryStockStatus.IN_STOCK -> Triple("In stock", Icons.Rounded.CheckCircle, FixoraTheme.extendedColors.success)
        InventoryStockStatus.LOW_STOCK -> Triple("Low stock", Icons.Rounded.WarningAmber, FixoraTheme.extendedColors.warning)
        InventoryStockStatus.OUT_OF_STOCK -> Triple("Out of stock", Icons.Rounded.RemoveCircleOutline, MaterialTheme.colorScheme.error)
        InventoryStockStatus.UNAVAILABLE -> Triple("Unavailable", Icons.Rounded.Archive, FixoraTheme.extendedColors.textSecondary)
    }
    Row(
        modifier = Modifier.clip(CircleShape).background(color.copy(alpha = 0.12f)).padding(horizontal = FixoraSpacing.sm, vertical = FixoraSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.xs),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun InventoryActivityRow(adjustment: InventoryAdjustment) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(FixoraSpacing.md), horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.md)) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FixoraSpacing.xs)) {
                Text(adjustment.itemName, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${adjustment.type.label()} · ${adjustment.previousQuantity} → ${adjustment.newQuantity} · ${adjustment.branchId.branchLabel()}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(adjustment.reason, style = MaterialTheme.typography.bodySmall, color = FixoraTheme.extendedColors.textSecondary)
                Text(
                    listOfNotNull(adjustment.performedByEmail, adjustment.createdAt.readableDateTime()).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = FixoraTheme.extendedColors.textSecondary,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InventoryDetailSheet(
    item: AdminInventoryItem,
    selectedBranchId: String?,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onAdjust: (String?) -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onRestore: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = FixoraSpacing.lg).padding(bottom = FixoraSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(FixoraSpacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.md)) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Inventory2, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
                Column(Modifier.weight(1f)) {
                    Text(item.name, style = MaterialTheme.typography.headlineSmall)
                    Text(item.sku ?: "No item code", style = MaterialTheme.typography.bodySmall, color = FixoraTheme.extendedColors.textSecondary)
                }
                InventoryStatusChip(item.statusFor(selectedBranchId))
            }
            item.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = FixoraTheme.extendedColors.textSecondary) }
            HorizontalDivider(color = FixoraTheme.extendedColors.border)
            Text("Stock by branch", style = MaterialTheme.typography.titleMedium)
            AdminInventoryItem.STOCK_BRANCH_IDS.forEach { branch ->
                val quantity = item.quantityFor(branch)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(branch.branchLabel(), style = MaterialTheme.typography.titleSmall)
                        Text("$quantity units · minimum ${item.minimumStockLevel}", style = MaterialTheme.typography.bodySmall, color = FixoraTheme.extendedColors.textSecondary)
                    }
                    if (item.isAvailable) OutlinedButton(onClick = { onAdjust(branch) }, enabled = !isSubmitting) { Text("Adjust") }
                }
            }
            HorizontalDivider(color = FixoraTheme.extendedColors.border)
            Text("Item information", style = MaterialTheme.typography.titleMedium)
            DetailLine("Category", item.category.displayCategory())
            DetailLine("Compatible devices", item.compatibleCategories.joinToString { it.name.lowercase().replaceFirstChar(Char::uppercaseChar) })
            item.unitCost?.let { DetailLine("Unit cost", it.lkr()) }
            item.sellingPrice?.let { DetailLine("Selling price", it.lkr()) }
            item.supplierName?.let { DetailLine("Supplier", it) }
            item.supplierContact?.let { DetailLine("Supplier contact", it) }
            item.createdAt.readableDateTime()?.let { DetailLine("Created", it) }
            item.updatedAt.readableDateTime()?.let { DetailLine("Last updated", it) }
            Button(onClick = onEdit, enabled = !isSubmitting, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Edit, contentDescription = null)
                Spacer(Modifier.width(FixoraSpacing.sm))
                Text("Edit item")
            }
            if (item.isAvailable) {
                TextButton(onClick = onArchive, enabled = !isSubmitting, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Archive, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(FixoraSpacing.sm))
                    Text("Archive item", color = MaterialTheme.colorScheme.error)
                }
            } else {
                OutlinedButton(onClick = onRestore, enabled = !isSubmitting, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Restore, contentDescription = null)
                    Spacer(Modifier.width(FixoraSpacing.sm))
                    Text("Restore item")
                }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.md)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = FixoraTheme.extendedColors.textSecondary, modifier = Modifier.weight(0.42f))
        Text(value.ifBlank { "Not provided" }, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.58f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InventoryItemFormSheet(
    form: InventoryItemFormState,
    isSubmitting: Boolean,
    onChange: (InventoryItemFormState) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = FixoraSpacing.lg).padding(bottom = FixoraSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(FixoraSpacing.md),
        ) {
            Text(if (form.isEditing) "Edit inventory item" else "Add inventory item", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Commercial fields are optional. Inventory value appears only when every active item has a recorded unit cost.",
                style = MaterialTheme.typography.bodySmall,
                color = FixoraTheme.extendedColors.textSecondary,
            )
            InventoryTextField(form.name, { onChange(form.copy(name = it)) }, "Item name", form.validation.nameError)
            InventoryTextField(form.category, { onChange(form.copy(category = it)) }, "Category", form.validation.categoryError, "e.g. Screen")
            InventoryTextField(form.sku, { onChange(form.copy(sku = it)) }, "SKU / item code", form.validation.skuError, "Optional")
            InventoryTextField(form.description, { onChange(form.copy(description = it)) }, "Description", form.validation.descriptionError, "Optional", singleLine = false)
            Text("Compatible devices", style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.sm)) {
                items(DeviceCategory.entries) { category ->
                    FilterChip(
                        selected = category in form.compatibleCategories,
                        onClick = {
                            onChange(form.copy(compatibleCategories = if (category in form.compatibleCategories) form.compatibleCategories - category else form.compatibleCategories + category))
                        },
                        label = { Text(category.name.lowercase().replaceFirstChar(Char::uppercaseChar)) },
                    )
                }
            }
            form.validation.compatibilityError?.let { ValidationText(it) }
            InventoryTextField(
                form.minimumStockLevel,
                { onChange(form.copy(minimumStockLevel = it.filter(Char::isDigit).take(9))) },
                "Minimum stock level",
                form.validation.minimumStockError,
                keyboardType = KeyboardType.Number,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.sm)) {
                InventoryTextField(
                    form.unitCost, { onChange(form.copy(unitCost = it.moneyInput())) }, "Unit cost", form.validation.unitCostError,
                    "Optional", KeyboardType.Decimal, modifier = Modifier.weight(1f),
                )
                InventoryTextField(
                    form.sellingPrice, { onChange(form.copy(sellingPrice = it.moneyInput())) }, "Selling price", form.validation.sellingPriceError,
                    "Optional", KeyboardType.Decimal, modifier = Modifier.weight(1f),
                )
            }
            InventoryTextField(form.supplierName, { onChange(form.copy(supplierName = it)) }, "Supplier", form.validation.supplierError, "Optional")
            InventoryTextField(form.supplierContact, { onChange(form.copy(supplierContact = it)) }, "Supplier contact", form.validation.supplierError, "Optional")
            if (form.isEditing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Available for operations", style = MaterialTheme.typography.titleSmall)
                        Text("Unavailable items are excluded from branch matching.", style = MaterialTheme.typography.bodySmall, color = FixoraTheme.extendedColors.textSecondary)
                    }
                    Switch(checked = form.isAvailable, onCheckedChange = { onChange(form.copy(isAvailable = it)) })
                }
            }
            Button(
                onClick = onSave,
                enabled = !isSubmitting,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = FixoraTheme.extendedColors.accent, contentColor = FixoraTheme.extendedColors.onAccent),
            ) {
                if (isSubmitting) CircularProgressIndicator(Modifier.size(20.dp), color = FixoraTheme.extendedColors.onAccent, strokeWidth = 2.dp)
                else Text(if (form.isEditing) "Save changes" else "Add inventory item")
            }
        }
    }
}

@Composable
private fun InventoryTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        isError = error != null,
        supportingText = error?.let { { ValidationText(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(8.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StockAdjustmentSheet(
    form: InventoryAdjustmentFormState,
    isSubmitting: Boolean,
    onChange: (InventoryAdjustmentFormState) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val draft = com.techfix.app.domain.inventory.StockAdjustmentDraft(
        form.itemId, form.branchId, form.type, form.quantity.toIntOrNull(), form.reason,
    )
    val preview = com.techfix.app.domain.inventory.validateStockAdjustment(form.previousQuantity, draft)
    ModalBottomSheet(onDismissRequest = onDismiss, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = FixoraSpacing.lg).padding(bottom = FixoraSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(FixoraSpacing.md),
        ) {
            Text("Adjust stock", style = MaterialTheme.typography.headlineSmall)
            Text("${form.itemName} · ${form.branchId.branchLabel()}", style = MaterialTheme.typography.bodyMedium, color = FixoraTheme.extendedColors.textSecondary)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(12.dp)) {
                Row(Modifier.fillMaxWidth().padding(FixoraSpacing.md), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column { Text("Current", style = MaterialTheme.typography.labelMedium); Text(form.previousQuantity.toString(), style = MaterialTheme.typography.titleLarge) }
                    Icon(Icons.Rounded.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Result", style = MaterialTheme.typography.labelMedium)
                        Text(preview.resultingQuantity?.toString() ?: "—", style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
            Text("Adjustment type", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.sm)) {
                StockAdjustmentType.entries.forEach { type ->
                    FilterChip(selected = form.type == type, onClick = { onChange(form.copy(type = type)) }, label = { Text(type.label()) })
                }
            }
            InventoryTextField(
                value = form.quantity,
                onValueChange = { onChange(form.copy(quantity = it.filter(Char::isDigit).take(9))) },
                label = if (form.type == StockAdjustmentType.CORRECT) "Correct quantity" else "Quantity",
                error = form.validation.quantityError,
                keyboardType = KeyboardType.Number,
            )
            InventoryTextField(
                value = form.reason,
                onValueChange = { onChange(form.copy(reason = it.take(200))) },
                label = "Reason",
                error = form.validation.reasonError,
                placeholder = "e.g. Supplier delivery received",
                singleLine = false,
            )
            Text("This adjustment is recorded with your account and cannot produce negative stock.", style = MaterialTheme.typography.bodySmall, color = FixoraTheme.extendedColors.textSecondary)
            Button(
                onClick = onSave,
                enabled = !isSubmitting,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = FixoraTheme.extendedColors.accent, contentColor = FixoraTheme.extendedColors.onAccent),
            ) {
                if (isSubmitting) CircularProgressIndicator(Modifier.size(20.dp), color = FixoraTheme.extendedColors.onAccent, strokeWidth = 2.dp)
                else Text("Confirm adjustment")
            }
        }
    }
}

@Composable
private fun ValidationText(message: String) {
    Text(message, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
}

@Composable
private fun InventoryEmptyCard(title: String, message: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(12.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(FixoraSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(FixoraSpacing.sm),
        ) {
            Icon(Icons.Rounded.Inventory2, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(message, style = MaterialTheme.typography.bodySmall, color = FixoraTheme.extendedColors.textSecondary)
        }
    }
}

@Composable
private fun InventoryManagementSkeleton(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(FixoraSpacing.md), verticalArrangement = Arrangement.spacedBy(FixoraSpacing.sm)) {
        repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.sm)) {
                repeat(2) { Box(Modifier.weight(1f).height(112.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).alpha(0.7f)) }
            }
        }
        Spacer(Modifier.height(FixoraSpacing.sm))
        repeat(4) { Box(Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).alpha(0.7f)) }
    }
}

@Composable
private fun InventoryManagementError(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(FixoraSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Rounded.ErrorOutline, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
        Text("Unable to load inventory", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = FixoraSpacing.md))
        Text(message, style = MaterialTheme.typography.bodySmall, color = FixoraTheme.extendedColors.textSecondary, modifier = Modifier.padding(top = FixoraSpacing.xs))
        OutlinedButton(onClick = onRetry, modifier = Modifier.padding(top = FixoraSpacing.md)) { Text("Try again") }
    }
}

private fun InventoryStockFilter.label() = when (this) {
    InventoryStockFilter.ALL -> "All"
    InventoryStockFilter.IN_STOCK -> "In stock"
    InventoryStockFilter.LOW_STOCK -> "Low stock"
    InventoryStockFilter.OUT_OF_STOCK -> "Out of stock"
    InventoryStockFilter.UNAVAILABLE -> "Unavailable"
}

private fun InventorySort.label() = when (this) {
    InventorySort.NAME -> "Name"
    InventorySort.QUANTITY_LOW -> "Lowest quantity"
    InventorySort.QUANTITY_HIGH -> "Highest quantity"
    InventorySort.RECENTLY_UPDATED -> "Recently updated"
}

private fun StockAdjustmentType.label() = when (this) {
    StockAdjustmentType.ADD -> "Add"
    StockAdjustmentType.REMOVE -> "Remove"
    StockAdjustmentType.CORRECT -> "Correct"
}

private fun String.branchLabel() = when (lowercase()) {
    "colombo" -> "Colombo"
    "galle" -> "Galle"
    else -> replaceFirstChar(Char::uppercaseChar)
}

private fun String.displayCategory() = lowercase().replace('_', ' ').replaceFirstChar(Char::uppercaseChar)

private fun String.moneyInput(): String {
    var dotSeen = false
    var decimals = 0
    return buildString {
        this@moneyInput.forEach { char ->
            when {
                char.isDigit() && (!dotSeen || decimals < 2) -> {
                    append(char)
                    if (dotSeen) decimals++
                }
                char == '.' && !dotSeen -> {
                    append(char)
                    dotSeen = true
                }
            }
        }
    }.take(12)
}

private fun BigDecimal.lkr(): String = "LKR ${NumberFormat.getNumberInstance(Locale("en", "LK")).format(this)}"

private fun String?.readableDateTime(): String? = this?.let { raw ->
    runCatching {
        DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a")
            .withLocale(Locale.getDefault())
            .format(Instant.parse(raw).atZone(ZoneId.systemDefault()))
    }.getOrNull()
}
