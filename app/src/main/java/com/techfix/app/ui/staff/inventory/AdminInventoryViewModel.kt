package com.techfix.app.ui.staff.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.techfix.app.domain.catalog.DeviceCategory
import com.techfix.app.domain.inventory.AdminInventoryItem
import com.techfix.app.domain.inventory.AdminInventoryRepository
import com.techfix.app.domain.inventory.InventoryAdjustment
import com.techfix.app.domain.inventory.InventoryAuthorizationException
import com.techfix.app.domain.inventory.InventoryDashboardMetrics
import com.techfix.app.domain.inventory.InventoryItemDraft
import com.techfix.app.domain.inventory.InventoryItemValidation
import com.techfix.app.domain.inventory.InventorySort
import com.techfix.app.domain.inventory.InventoryStockFilter
import com.techfix.app.domain.inventory.InventorySubmissionGate
import com.techfix.app.domain.inventory.StockAdjustmentDraft
import com.techfix.app.domain.inventory.StockAdjustmentType
import com.techfix.app.domain.inventory.StockAdjustmentValidation
import com.techfix.app.domain.inventory.filterAndSortInventory
import com.techfix.app.domain.inventory.validateInventoryItem
import com.techfix.app.domain.inventory.validateStockAdjustment
import com.techfix.app.ui.staff.StaffContext
import java.math.BigDecimal
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InventoryItemFormState(
    val itemId: String? = null,
    val requestId: String = UUID.randomUUID().toString(),
    val name: String = "",
    val category: String = "",
    val description: String = "",
    val sku: String = "",
    val compatibleCategories: Set<DeviceCategory> = emptySet(),
    val minimumStockLevel: String = "0",
    val unitCost: String = "",
    val sellingPrice: String = "",
    val supplierName: String = "",
    val supplierContact: String = "",
    val isAvailable: Boolean = true,
    val validation: InventoryItemValidation = InventoryItemValidation(),
) {
    val isEditing: Boolean get() = itemId != null
}

data class InventoryAdjustmentFormState(
    val requestId: String = UUID.randomUUID().toString(),
    val itemId: String,
    val itemName: String,
    val branchId: String,
    val previousQuantity: Int,
    val type: StockAdjustmentType = StockAdjustmentType.ADD,
    val quantity: String = "",
    val reason: String = "",
    val validation: StockAdjustmentValidation = StockAdjustmentValidation(),
)

data class AdminInventoryUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val items: List<AdminInventoryItem> = emptyList(),
    val recentAdjustments: List<InventoryAdjustment> = emptyList(),
    val query: String = "",
    val selectedBranchId: String? = null,
    val selectedCategory: String? = null,
    val stockFilter: InventoryStockFilter = InventoryStockFilter.ALL,
    val sort: InventorySort = InventorySort.NAME,
    val selectedItemId: String? = null,
    val itemForm: InventoryItemFormState? = null,
    val adjustmentForm: InventoryAdjustmentFormState? = null,
    val archiveItemId: String? = null,
    val isSubmitting: Boolean = false,
    val actionMessage: String? = null,
    val actionError: String? = null,
) {
    val metrics: InventoryDashboardMetrics get() = InventoryDashboardMetrics.from(items)
    val categories: List<String> get() = items.map { it.category }.distinct().sorted()
    val visibleItems: List<AdminInventoryItem>
        get() = filterAndSortInventory(
            items, query, selectedCategory, stockFilter, sort, selectedBranchId,
        )
    val selectedItem: AdminInventoryItem? get() = items.firstOrNull { it.id == selectedItemId }
    val archiveItem: AdminInventoryItem? get() = items.firstOrNull { it.id == archiveItemId }
}

class AdminInventoryViewModel(
    private val staffContext: StaffContext,
    private val repository: AdminInventoryRepository,
    private val submissionGate: InventorySubmissionGate = InventorySubmissionGate(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(AdminInventoryUiState())
    val uiState: StateFlow<AdminInventoryUiState> = _uiState

    init {
        load()
    }

    fun load() {
        if (!staffContext.canManageInventory) {
            _uiState.update { it.copy(isLoading = false, errorMessage = ACCESS_DENIED) }
            return
        }
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            repository.getInventory()
                .onSuccess { snapshot ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            items = snapshot.items,
                            recentAdjustments = snapshot.recentAdjustments,
                            errorMessage = null,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = error.userMessage()) }
                }
        }
    }

    fun onQueryChange(value: String) = _uiState.update { it.copy(query = value) }
    fun onBranchSelected(value: String?) = _uiState.update { it.copy(selectedBranchId = value) }
    fun onCategorySelected(value: String?) = _uiState.update { it.copy(selectedCategory = value) }
    fun onStockFilterSelected(value: InventoryStockFilter) = _uiState.update { it.copy(stockFilter = value) }
    fun onSortSelected(value: InventorySort) = _uiState.update { it.copy(sort = value) }
    fun openDetails(itemId: String) = _uiState.update { it.copy(selectedItemId = itemId) }
    fun closeDetails() = _uiState.update { it.copy(selectedItemId = null) }

    fun openCreateItem() {
        if (!staffContext.canManageInventory || _uiState.value.isSubmitting) return
        _uiState.update { it.copy(itemForm = InventoryItemFormState()) }
    }

    fun openEditItem(item: AdminInventoryItem) {
        if (!staffContext.canManageInventory || _uiState.value.isSubmitting) return
        _uiState.update {
            it.copy(
                selectedItemId = null,
                itemForm = InventoryItemFormState(
                    itemId = item.id,
                    name = item.name,
                    category = item.category,
                    description = item.description.orEmpty(),
                    sku = item.sku.orEmpty(),
                    compatibleCategories = item.compatibleCategories.toSet(),
                    minimumStockLevel = item.minimumStockLevel.toString(),
                    unitCost = item.unitCost?.stripTrailingZeros()?.toPlainString().orEmpty(),
                    sellingPrice = item.sellingPrice?.stripTrailingZeros()?.toPlainString().orEmpty(),
                    supplierName = item.supplierName.orEmpty(),
                    supplierContact = item.supplierContact.orEmpty(),
                    isAvailable = item.isAvailable,
                ),
            )
        }
    }

    fun updateItemForm(value: InventoryItemFormState) = _uiState.update {
        it.copy(itemForm = value.copy(validation = InventoryItemValidation()))
    }

    fun closeItemForm() {
        if (!_uiState.value.isSubmitting) _uiState.update { it.copy(itemForm = null) }
    }

    fun saveItem() {
        if (!staffContext.canManageInventory || !submissionGate.tryAcquire()) return
        val form = _uiState.value.itemForm ?: run {
            submissionGate.release()
            return
        }
        val evaluation = form.evaluate()
        if (!evaluation.validation.isValid) {
            submissionGate.release()
            _uiState.update { it.copy(itemForm = form.copy(validation = evaluation.validation)) }
            return
        }
        val draft = requireNotNull(evaluation.draft)
        _uiState.update { it.copy(isSubmitting = true, actionError = null, actionMessage = null) }
        viewModelScope.launch {
            try {
                val result = form.itemId?.let { repository.updateItem(it, draft) }
                    ?: repository.createItem(form.requestId, draft)
                result.onSuccess {
                    refreshAfterMutation(if (form.isEditing) "Inventory item updated." else "Inventory item added.")
                }.onFailure { error ->
                    _uiState.update { it.copy(isSubmitting = false, actionError = error.userMessage()) }
                }
            } finally {
                submissionGate.release()
            }
        }
    }

    fun openAdjustment(item: AdminInventoryItem, branchId: String? = _uiState.value.selectedBranchId) {
        if (!staffContext.canManageInventory || !item.isAvailable || _uiState.value.isSubmitting) return
        val resolvedBranch = branchId ?: AdminInventoryItem.STOCK_BRANCH_IDS.first()
        _uiState.update {
            it.copy(
                selectedItemId = null,
                adjustmentForm = InventoryAdjustmentFormState(
                    itemId = item.id,
                    itemName = item.name,
                    branchId = resolvedBranch,
                    previousQuantity = item.quantityFor(resolvedBranch),
                ),
            )
        }
    }

    fun updateAdjustmentForm(value: InventoryAdjustmentFormState) = _uiState.update {
        it.copy(adjustmentForm = value.copy(validation = StockAdjustmentValidation()))
    }

    fun closeAdjustment() {
        if (!_uiState.value.isSubmitting) _uiState.update { it.copy(adjustmentForm = null) }
    }

    fun saveAdjustment() {
        if (!staffContext.canManageInventory || !submissionGate.tryAcquire()) return
        val form = _uiState.value.adjustmentForm ?: run {
            submissionGate.release()
            return
        }
        val draft = StockAdjustmentDraft(
            itemId = form.itemId,
            branchId = form.branchId,
            type = form.type,
            quantity = form.quantity.toIntOrNull(),
            reason = form.reason,
        )
        val validation = validateStockAdjustment(form.previousQuantity, draft)
        if (!validation.isValid) {
            submissionGate.release()
            _uiState.update { it.copy(adjustmentForm = form.copy(validation = validation)) }
            return
        }
        _uiState.update { it.copy(isSubmitting = true, actionError = null, actionMessage = null) }
        viewModelScope.launch {
            try {
                repository.adjustStock(form.requestId, draft)
                    .onSuccess { refreshAfterMutation("Stock adjusted to ${validation.resultingQuantity}.") }
                    .onFailure { error ->
                        _uiState.update { it.copy(isSubmitting = false, actionError = error.userMessage()) }
                    }
            } finally {
                submissionGate.release()
            }
        }
    }

    fun requestArchive(item: AdminInventoryItem) {
        if (staffContext.canManageInventory && item.isAvailable && !_uiState.value.isSubmitting) {
            _uiState.update { it.copy(selectedItemId = null, archiveItemId = item.id) }
        }
    }

    fun dismissArchive() = _uiState.update { it.copy(archiveItemId = null) }

    fun confirmArchive() {
        if (!staffContext.canManageInventory || !submissionGate.tryAcquire()) return
        val item = _uiState.value.archiveItem ?: run {
            submissionGate.release()
            return
        }
        _uiState.update { it.copy(isSubmitting = true, archiveItemId = null, actionError = null) }
        viewModelScope.launch {
            try {
                repository.setItemAvailability(item.id, false)
                    .onSuccess { refreshAfterMutation("${item.name} archived safely.") }
                    .onFailure { error ->
                        _uiState.update { it.copy(isSubmitting = false, actionError = error.userMessage()) }
                    }
            } finally {
                submissionGate.release()
            }
        }
    }

    fun restoreItem(item: AdminInventoryItem) {
        if (!staffContext.canManageInventory || !submissionGate.tryAcquire()) return
        _uiState.update { it.copy(selectedItemId = null, isSubmitting = true, actionError = null) }
        viewModelScope.launch {
            try {
                repository.setItemAvailability(item.id, true)
                    .onSuccess { refreshAfterMutation("${item.name} is available again.") }
                    .onFailure { error ->
                        _uiState.update { it.copy(isSubmitting = false, actionError = error.userMessage()) }
                    }
            } finally {
                submissionGate.release()
            }
        }
    }

    fun dismissMessages() = _uiState.update { it.copy(actionMessage = null, actionError = null) }

    private suspend fun refreshAfterMutation(message: String) {
        repository.getInventory()
            .onSuccess { snapshot ->
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        itemForm = null,
                        adjustmentForm = null,
                        selectedItemId = null,
                        items = snapshot.items,
                        recentAdjustments = snapshot.recentAdjustments,
                        actionMessage = message,
                    )
                }
            }
            .onFailure {
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        itemForm = null,
                        adjustmentForm = null,
                        actionMessage = "$message Refresh to see the latest values.",
                    )
                }
            }
    }

    private data class FormEvaluation(
        val draft: InventoryItemDraft?,
        val validation: InventoryItemValidation,
    )

    private fun InventoryItemFormState.evaluate(): FormEvaluation {
        val min = minimumStockLevel.trim().toIntOrNull()
        val unit = unitCost.trim().takeIf(String::isNotBlank)?.toBigDecimalOrNull()
        val selling = sellingPrice.trim().takeIf(String::isNotBlank)?.toBigDecimalOrNull()
        val draft = InventoryItemDraft(
            name, category, description, sku, compatibleCategories, min,
            unit, selling, supplierName, supplierContact, isAvailable,
        )
        var validation = validateInventoryItem(draft)
        if (unitCost.isNotBlank() && unit == null) validation = validation.copy(unitCostError = "Enter a valid amount")
        if (sellingPrice.isNotBlank() && selling == null) validation = validation.copy(sellingPriceError = "Enter a valid amount")
        return FormEvaluation(draft.takeIf { validation.isValid }, validation)
    }

    private fun Throwable.userMessage(): String = when (this) {
        is InventoryAuthorizationException -> ACCESS_DENIED
        else -> "Unable to update inventory. Please try again."
    }

    companion object {
        private const val ACCESS_DENIED = "Admin authorization is required to manage inventory."

        fun factory(
            staffContext: StaffContext,
            repository: AdminInventoryRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                AdminInventoryViewModel(staffContext, repository) as T
        }
    }
}
