package com.techfix.app.ui.customer.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.techfix.app.domain.catalog.RepairService
import com.techfix.app.domain.catalog.ServiceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ServiceDetailUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val service: RepairService? = null,
    /** The service came out of the Room cache because the network read failed. */
    val isOffline: Boolean = false,
)

class ServiceDetailViewModel(
    private val serviceId: String,
    private val serviceRepository: ServiceRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ServiceDetailUiState())
    val uiState: StateFlow<ServiceDetailUiState> = _uiState

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            serviceRepository.getServiceWithSource(serviceId)
                .onSuccess { read ->
                    _uiState.update {
                        it.copy(isLoading = false, service = read.value, isOffline = read.fromCache)
                    }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isOffline = false,
                            errorMessage = "Unable to load this service. Please try again.",
                        )
                    }
                }
        }
    }

    companion object {
        fun factory(serviceId: String, serviceRepository: ServiceRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ServiceDetailViewModel(serviceId, serviceRepository) as T
            }
    }
}
