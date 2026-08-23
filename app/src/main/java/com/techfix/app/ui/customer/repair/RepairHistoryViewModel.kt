package com.techfix.app.ui.customer.repair

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.techfix.app.domain.catalog.ServiceRepository
import com.techfix.app.domain.repair.RepairRequest
import com.techfix.app.domain.repair.RepairRequestRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RepairHistoryUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    /**
     * Repairs still under way, newest first. The screen is reached from the
     * My Repairs tab, so it lists these above the finished ones — a customer
     * looking for "my repairs" means the live ones first of all. Tapping one
     * opens the tracking timeline, not the history detail.
     */
    val activeRepairs: List<RepairRequest> = emptyList(),
    /** Finished repairs (COMPLETED and CANCELLED), newest first. */
    val repairs: List<RepairRequest> = emptyList(),
    /** serviceId → service name, so a card can name the service it booked. */
    val serviceNames: Map<String, String> = emptyMap(),
) {
    val isEmpty: Boolean
        get() = !isLoading && errorMessage == null && repairs.isEmpty() && activeRepairs.isEmpty()
}

/**
 * My Repairs — the customer's repairs, live ones first, finished ones under
 * them.
 *
 * "Finished" is `RepairStatus.isTerminal`: COMPLETED plus CANCELLED. A
 * cancelled repair is no longer trackable, so if history filtered on
 * COMPLETED alone it would vanish from the app entirely.
 *
 * The filter is client-side rather than a `whereIn` query: the existing
 * `customerId` + `createdAt` composite index already serves this read, and
 * one customer's repair list is small enough that adding another index to
 * split it is not worth it.
 */
class RepairHistoryViewModel(
    private val customerId: String,
    private val repairRequestRepository: RepairRequestRepository,
    private val serviceRepository: ServiceRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RepairHistoryUiState())
    val uiState: StateFlow<RepairHistoryUiState> = _uiState

    init {
        load()
    }

    fun load() {
        if (customerId.isBlank()) {
            _uiState.update {
                it.copy(isLoading = false, errorMessage = "You need to be signed in to see your history.")
            }
            return
        }
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            // Both reads are independent, so they overlap rather than queue.
            val repairsDeferred = async { repairRequestRepository.getRepairRequestsForCustomer(customerId) }
            val servicesDeferred = async { serviceRepository.getServices() }

            // A failed service lookup only costs the card its service name,
            // so it must not fail the whole screen.
            val serviceNames = servicesDeferred.await()
                .getOrNull()
                ?.associate { it.id to it.name }
                .orEmpty()

            repairsDeferred.await()
                .onSuccess { requests ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            activeRepairs = requests.filterNot { request -> request.status.isTerminal },
                            repairs = requests.filter { request -> request.status.isTerminal },
                            serviceNames = serviceNames,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Couldn't load your repair history",
                        )
                    }
                }
        }
    }

    companion object {
        fun factory(
            customerId: String,
            repairRequestRepository: RepairRequestRepository,
            serviceRepository: ServiceRepository,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    RepairHistoryViewModel(
                        customerId = customerId,
                        repairRequestRepository = repairRequestRepository,
                        serviceRepository = serviceRepository,
                    ) as T
            }
    }
}
