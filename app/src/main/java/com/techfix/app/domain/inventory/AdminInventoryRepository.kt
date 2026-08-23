package com.techfix.app.domain.inventory

class InventoryAuthorizationException : Exception("Admin inventory authorization required")
class InventoryServiceException(message: String) : Exception(message)

interface AdminInventoryRepository {
    suspend fun getInventory(): Result<AdminInventorySnapshot>

    suspend fun createItem(
        requestId: String,
        draft: InventoryItemDraft,
    ): Result<Unit>

    suspend fun updateItem(
        itemId: String,
        draft: InventoryItemDraft,
    ): Result<Unit>

    suspend fun setItemAvailability(
        itemId: String,
        isAvailable: Boolean,
    ): Result<Unit>

    suspend fun adjustStock(
        requestId: String,
        draft: StockAdjustmentDraft,
    ): Result<Unit>
}
