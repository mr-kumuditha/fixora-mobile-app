package com.techfix.app.domain.inventory

import com.techfix.app.domain.catalog.DeviceCategory
import java.math.BigDecimal
import java.util.concurrent.atomic.AtomicBoolean

enum class InventoryStockStatus { IN_STOCK, LOW_STOCK, OUT_OF_STOCK, UNAVAILABLE }

enum class InventoryStockFilter { ALL, IN_STOCK, LOW_STOCK, OUT_OF_STOCK, UNAVAILABLE }

enum class InventorySort { NAME, QUANTITY_LOW, QUANTITY_HIGH, RECENTLY_UPDATED }

enum class StockAdjustmentType { ADD, REMOVE, CORRECT }

data class InventoryBranchStock(
    val branchId: String,
    val quantity: Int,
    val updatedAt: String?,
)

data class AdminInventoryItem(
    val id: String,
    val name: String,
    val category: String,
    val description: String?,
    val sku: String?,
    val compatibleCategories: List<DeviceCategory>,
    val minimumStockLevel: Int,
    val unitCost: BigDecimal?,
    val sellingPrice: BigDecimal?,
    val supplierName: String?,
    val supplierContact: String?,
    val isAvailable: Boolean,
    val createdAt: String?,
    val updatedAt: String?,
    val archivedAt: String?,
    val stocks: List<InventoryBranchStock>,
) {
    fun quantityFor(branchId: String?): Int = if (branchId == null) {
        stocks.sumOf { it.quantity }
    } else {
        stocks.firstOrNull { it.branchId == branchId }?.quantity ?: 0
    }

    /**
     * In the all-branch scope, an item is low when any stocked location has
     * reached its threshold, or one location is empty while another still has
     * units. It is out only when no branch has a unit left.
     */
    fun statusFor(branchId: String?): InventoryStockStatus {
        if (!isAvailable) return InventoryStockStatus.UNAVAILABLE
        val quantities = if (branchId == null) {
            STOCK_BRANCH_IDS.map { id -> stocks.firstOrNull { it.branchId == id }?.quantity ?: 0 }
        } else {
            listOf(quantityFor(branchId))
        }
        if (quantities.all { it == 0 }) return InventoryStockStatus.OUT_OF_STOCK
        if (quantities.any { it == 0 }) return InventoryStockStatus.LOW_STOCK
        if (minimumStockLevel > 0 && quantities.any { it <= minimumStockLevel }) {
            return InventoryStockStatus.LOW_STOCK
        }
        return InventoryStockStatus.IN_STOCK
    }

    companion object {
        val STOCK_BRANCH_IDS = listOf("colombo", "galle")
    }
}

data class InventoryAdjustment(
    val id: String,
    val requestId: String,
    val itemId: String,
    val itemName: String,
    val branchId: String,
    val previousQuantity: Int,
    val newQuantity: Int,
    val type: StockAdjustmentType,
    val reason: String,
    val performedByUid: String,
    val performedByEmail: String?,
    val createdAt: String?,
)

data class AdminInventorySnapshot(
    val items: List<AdminInventoryItem>,
    val recentAdjustments: List<InventoryAdjustment>,
)

data class InventoryDashboardMetrics(
    val totalItems: Int,
    val totalAvailableStock: Int,
    val lowStockItems: Int,
    val outOfStockItems: Int,
    val inventoryValue: BigDecimal?,
) {
    companion object {
        fun from(items: List<AdminInventoryItem>): InventoryDashboardMetrics {
            val active = items.filter { it.isAvailable }
            val valueIsReliable = active.isNotEmpty() && active.all { it.unitCost != null }
            return InventoryDashboardMetrics(
                totalItems = active.size,
                totalAvailableStock = active.sumOf { it.quantityFor(null) },
                lowStockItems = active.count { it.statusFor(null) == InventoryStockStatus.LOW_STOCK },
                outOfStockItems = active.count { it.statusFor(null) == InventoryStockStatus.OUT_OF_STOCK },
                inventoryValue = if (valueIsReliable) {
                    active.fold(BigDecimal.ZERO) { total, item ->
                        total + requireNotNull(item.unitCost).multiply(item.quantityFor(null).toBigDecimal())
                    }
                } else {
                    null
                },
            )
        }
    }
}

data class InventoryItemDraft(
    val name: String,
    val category: String,
    val description: String,
    val sku: String,
    val compatibleCategories: Set<DeviceCategory>,
    val minimumStockLevel: Int?,
    val unitCost: BigDecimal?,
    val sellingPrice: BigDecimal?,
    val supplierName: String,
    val supplierContact: String,
    val isAvailable: Boolean = true,
)

data class InventoryItemValidation(
    val nameError: String? = null,
    val categoryError: String? = null,
    val compatibilityError: String? = null,
    val minimumStockError: String? = null,
    val unitCostError: String? = null,
    val sellingPriceError: String? = null,
    val descriptionError: String? = null,
    val skuError: String? = null,
    val supplierError: String? = null,
) {
    val isValid: Boolean
        get() = listOf(
            nameError, categoryError, compatibilityError, minimumStockError,
            unitCostError, sellingPriceError, descriptionError, skuError, supplierError,
        ).all { it == null }
}

fun validateInventoryItem(draft: InventoryItemDraft): InventoryItemValidation {
    val name = draft.name.trim()
    val category = draft.category.trim()
    return InventoryItemValidation(
        nameError = when {
            name.isBlank() -> "Item name is required"
            name.length !in 2..80 -> "Use 2 to 80 characters"
            else -> null
        },
        categoryError = when {
            category.isBlank() -> "Category is required"
            category.length !in 2..40 -> "Use 2 to 40 characters"
            !category.matches(Regex("[A-Za-z0-9 _-]+")) -> "Use letters, numbers, spaces or hyphens"
            else -> null
        },
        compatibilityError = "Select at least one device type".takeIf { draft.compatibleCategories.isEmpty() },
        minimumStockError = when {
            draft.minimumStockLevel == null -> "Minimum stock level is required"
            draft.minimumStockLevel < 0 -> "Minimum stock cannot be negative"
            draft.minimumStockLevel > MAX_QUANTITY -> "Minimum stock is too large"
            else -> null
        },
        unitCostError = when {
            draft.unitCost?.signum() == -1 -> "Unit cost cannot be negative"
            draft.unitCost != null && draft.unitCost > MAX_MONEY -> "Unit cost is too large"
            else -> null
        },
        sellingPriceError = when {
            draft.sellingPrice?.signum() == -1 -> "Selling price cannot be negative"
            draft.sellingPrice != null && draft.sellingPrice > MAX_MONEY -> "Selling price is too large"
            else -> null
        },
        descriptionError = "Description must be 500 characters or fewer".takeIf { draft.description.trim().length > 500 },
        skuError = "Item code must be 64 characters or fewer".takeIf { draft.sku.trim().length > 64 },
        supplierError = "Supplier information is too long".takeIf {
            draft.supplierName.trim().length > 120 || draft.supplierContact.trim().length > 160
        },
    )
}

data class StockAdjustmentDraft(
    val itemId: String,
    val branchId: String,
    val type: StockAdjustmentType,
    val quantity: Int?,
    val reason: String,
)

data class StockAdjustmentValidation(
    val quantityError: String? = null,
    val reasonError: String? = null,
    val resultingQuantity: Int? = null,
) {
    val isValid: Boolean get() = quantityError == null && reasonError == null && resultingQuantity != null
}

fun validateStockAdjustment(
    previousQuantity: Int,
    draft: StockAdjustmentDraft,
): StockAdjustmentValidation {
    val quantity = draft.quantity
    val resultingLong = quantity?.let {
        when (draft.type) {
            StockAdjustmentType.ADD -> previousQuantity.toLong() + it.toLong()
            StockAdjustmentType.REMOVE -> previousQuantity.toLong() - it.toLong()
            StockAdjustmentType.CORRECT -> it.toLong()
        }
    }
    val quantityError = when {
        quantity == null -> "Quantity is required"
        quantity < 0 -> "Quantity cannot be negative"
        quantity > MAX_QUANTITY -> "Quantity is too large"
        draft.type != StockAdjustmentType.CORRECT && quantity == 0 -> "Quantity must be greater than zero"
        draft.type == StockAdjustmentType.REMOVE && quantity > previousQuantity -> "Only $previousQuantity units are available"
        resultingLong != null && resultingLong > MAX_QUANTITY -> "Resulting quantity is too large"
        else -> null
    }
    val resulting = resultingLong?.toInt()?.takeIf { quantityError == null }
    val trimmedReason = draft.reason.trim()
    val reasonError = when {
        trimmedReason.length < 3 -> "Enter a short reason"
        trimmedReason.length > 200 -> "Reason must be 200 characters or fewer"
        else -> null
    }
    return StockAdjustmentValidation(quantityError, reasonError, resulting)
}

fun filterAndSortInventory(
    items: List<AdminInventoryItem>,
    query: String,
    category: String?,
    stockFilter: InventoryStockFilter,
    sort: InventorySort,
    branchId: String?,
): List<AdminInventoryItem> {
    val needle = query.trim()
    val filtered = items.filter { item ->
        val matchesQuery = needle.isBlank() || listOfNotNull(
            item.name, item.category, item.sku, item.description, item.supplierName,
        ).any { it.contains(needle, ignoreCase = true) }
        val matchesCategory = category == null || item.category.equals(category, ignoreCase = true)
        val status = item.statusFor(branchId)
        val matchesStock = when (stockFilter) {
            InventoryStockFilter.ALL -> true
            InventoryStockFilter.IN_STOCK -> status == InventoryStockStatus.IN_STOCK
            InventoryStockFilter.LOW_STOCK -> status == InventoryStockStatus.LOW_STOCK
            InventoryStockFilter.OUT_OF_STOCK -> status == InventoryStockStatus.OUT_OF_STOCK
            InventoryStockFilter.UNAVAILABLE -> status == InventoryStockStatus.UNAVAILABLE
        }
        matchesQuery && matchesCategory && matchesStock
    }
    return when (sort) {
        InventorySort.NAME -> filtered.sortedBy { it.name.lowercase() }
        InventorySort.QUANTITY_LOW -> filtered.sortedWith(compareBy({ it.quantityFor(branchId) }, { it.name.lowercase() }))
        InventorySort.QUANTITY_HIGH -> filtered.sortedWith(compareByDescending<AdminInventoryItem> { it.quantityFor(branchId) }.thenBy { it.name.lowercase() })
        InventorySort.RECENTLY_UPDATED -> filtered.sortedByDescending { it.updatedAt.orEmpty() }
    }
}

/** Prevents double taps and concurrent retries from starting two mutations. */
class InventorySubmissionGate {
    private val busy = AtomicBoolean(false)
    fun tryAcquire(): Boolean = busy.compareAndSet(false, true)
    fun release() = busy.set(false)
    val isBusy: Boolean get() = busy.get()
}

const val MAX_QUANTITY = 999_999_999
val MAX_MONEY: BigDecimal = BigDecimal("999999999.99")
