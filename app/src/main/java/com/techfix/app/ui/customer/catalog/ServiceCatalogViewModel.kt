package com.techfix.app.ui.customer.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.techfix.app.domain.catalog.DeviceCategory
import com.techfix.app.domain.catalog.RepairService
import com.techfix.app.domain.catalog.ServiceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ServiceCatalogUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val allServices: List<RepairService> = emptyList(),
    val query: String = "",
    val selectedCategory: DeviceCategory? = null,
    /** The list came out of the Room cache because the network read failed. */
    val isOffline: Boolean = false,
) {
    /** Filtered services grouped by category, category order matching the enum. */
    val groupedServices: List<Pair<DeviceCategory, List<RepairService>>>
        get() {
            val trimmedQuery = query.trim()
            return allServices
                .asSequence()
                .filter { selectedCategory == null || it.category == selectedCategory }
                .filter {
                        trimmedQuery.isBlank() ||
                        it.name.contains(trimmedQuery, ignoreCase = true) ||
                        it.description.contains(trimmedQuery, ignoreCase = true) ||
                        it.category.label.contains(trimmedQuery, ignoreCase = true)
                }
                .groupBy { it.category }
                .toList()
                .sortedBy { it.first.ordinal }
                .map { (category, services) -> category to services.sortedBy { it.name } }
        }

    val isEmpty: Boolean
        get() = !isLoading && errorMessage == null && groupedServices.isEmpty()
}

class ServiceCatalogViewModel(
    private val serviceRepository: ServiceRepository,
    /**
     * Set when the catalog was opened from a Home service tile, so the
     * screen arrives on that category's chip already selected. The customer
     * can clear it like any other filter.
     */
    initialCategory: DeviceCategory? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ServiceCatalogUiState(selectedCategory = initialCategory))
    val uiState: StateFlow<ServiceCatalogUiState> = _uiState

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            serviceRepository.getServicesWithSource()
                .onSuccess { read ->
                    _uiState.update {
                        it.copy(isLoading = false, allServices = read.value, isOffline = read.fromCache)
                    }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isOffline = false,
                            errorMessage = "Unable to load services. Please try again.",
                        )
                    }
                }
        }
    }

    fun onQueryChange(value: String) {
        _uiState.update { it.copy(query = value) }
    }

    fun onCategorySelect(category: DeviceCategory?) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun clearFilters() {
        _uiState.update { it.copy(query = "", selectedCategory = null) }
    }

    companion object {
        fun factory(
            serviceRepository: ServiceRepository,
            initialCategory: DeviceCategory? = null,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ServiceCatalogViewModel(serviceRepository, initialCategory) as T
            }
    }
}
