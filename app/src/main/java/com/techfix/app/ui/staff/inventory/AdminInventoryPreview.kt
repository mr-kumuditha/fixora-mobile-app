package com.techfix.app.ui.staff.inventory

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.techfix.app.core.designsystem.FixoraTheme
import com.techfix.app.domain.catalog.DeviceCategory
import com.techfix.app.domain.inventory.AdminInventoryItem
import com.techfix.app.domain.inventory.InventoryBranchStock
import java.math.BigDecimal

@Preview(name = "Admin inventory light", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun AdminInventoryLightPreview() = FixoraTheme(darkTheme = false) { InventoryPreviewContent() }

@Preview(name = "Admin inventory dark", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun AdminInventoryDarkPreview() = FixoraTheme(darkTheme = true) { InventoryPreviewContent() }

@Composable
private fun InventoryPreviewContent() {
    AdminInventoryScreen(
        uiState = AdminInventoryUiState(
            isLoading = false,
            items = listOf(
                AdminInventoryItem(
                    id = "preview-display",
                    name = "Mobile Display Panel",
                    category = "SCREEN",
                    description = "Replacement OLED display assembly",
                    sku = "SCR-MOB-001",
                    compatibleCategories = listOf(DeviceCategory.MOBILE),
                    minimumStockLevel = 4,
                    unitCost = BigDecimal("8500.00"),
                    sellingPrice = BigDecimal("11200.00"),
                    supplierName = "Preview supplier",
                    supplierContact = null,
                    isAvailable = true,
                    createdAt = null,
                    updatedAt = "2026-08-23T12:00:00Z",
                    archivedAt = null,
                    stocks = listOf(
                        InventoryBranchStock("colombo", 12, null),
                        InventoryBranchStock("galle", 3, null),
                    ),
                ),
                AdminInventoryItem(
                    id = "preview-battery",
                    name = "Mobile Battery Pack",
                    category = "BATTERY",
                    description = null,
                    sku = "BAT-MOB-004",
                    compatibleCategories = listOf(DeviceCategory.MOBILE),
                    minimumStockLevel = 3,
                    unitCost = BigDecimal("3200.00"),
                    sellingPrice = null,
                    supplierName = null,
                    supplierContact = null,
                    isAvailable = true,
                    createdAt = null,
                    updatedAt = "2026-08-22T12:00:00Z",
                    archivedAt = null,
                    stocks = listOf(
                        InventoryBranchStock("colombo", 8, null),
                        InventoryBranchStock("galle", 0, null),
                    ),
                ),
            ),
        ),
        onQueryChange = {},
        onBranchSelected = {},
        onCategorySelected = {},
        onStockFilterSelected = {},
        onSortSelected = {},
        onOpenDetails = {},
        onCloseDetails = {},
        onOpenCreate = {},
        onOpenEdit = {},
        onUpdateItemForm = {},
        onCloseItemForm = {},
        onSaveItem = {},
        onOpenAdjustment = { _, _ -> },
        onUpdateAdjustmentForm = {},
        onCloseAdjustment = {},
        onSaveAdjustment = {},
        onRequestArchive = {},
        onDismissArchive = {},
        onConfirmArchive = {},
        onRestore = {},
        onRetry = {},
        onMessageShown = {},
        onBack = {},
    )
}
