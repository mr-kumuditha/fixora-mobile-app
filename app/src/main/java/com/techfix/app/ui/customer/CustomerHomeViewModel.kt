package com.techfix.app.ui.customer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.techfix.app.domain.catalog.ServiceRepository
import com.techfix.app.domain.repair.RepairRequest
import com.techfix.app.domain.repair.RepairRequestRepository
import com.techfix.app.domain.repair.RepairStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CustomerHomeUiState(
    val isLoading: Boolean = true,
    /**
     * Home degrades rather than failing: a read that goes wrong leaves the
     * hero, the service grid and the quick links usable and shows this as a
     * caption under the stats, instead of replacing the screen with an error.
     */
    val errorMessage: String? = null,
    /** This customer's repairs, newest first — the one source the counts and cards derive from. */
    val repairs: List<RepairRequest> = emptyList(),
    /** serviceId → service name, so a card can name the service it booked. */
    val serviceNames: Map<String, String> = emptyMap(),
) {
    val totalCount: Int get() = repairs.size

    val activeCount: Int get() = repairs.count { !it.status.isTerminal }

    /**
     * Done is COMPLETED only. A cancelled repair is finished but not done,
     * and counting it here would tell the customer work happened that didn't.
     */
    val doneCount: Int get() = repairs.count { it.status == RepairStatus.COMPLETED }

    /** The repair still in progress, if there is one. Newest first, so the first match is it. */
    val activeRepair: RepairRequest? get() = repairs.firstOrNull { !it.status.isTerminal }

    /**
     * What "Recent Repairs" shows: the live one if there is one, otherwise
     * the most recent finished repair, so the section is only empty for a
     * customer who has never booked.
     */
    val recentRepair: RepairRequest? get() = activeRepair ?: repairs.firstOrNull()

    val recentServiceName: String? get() = recentRepair?.let { serviceNames[it.serviceId] }

    val hasNoRepairs: Boolean get() = !isLoading && repairs.isEmpty()
}

/**
 * Home.
 *
 * One read of the customer's repairs backs everything on the screen — the
 * Total / Active / Done stat row, the Recent Repairs card, and whether the
 * Track Repair quick link has something to open. The counts are derived from
 * that list rather than stored, so a status arriving on the live listener
 * moves the card and the counters together and they can't drift apart.
 *
 * The live listener is still only attached to the active repair: it is the
 * only one whose status can change while Home is open.
 */
class CustomerHomeViewModel(
    private val customerId: String,
    private val repairRequestRepository: RepairRequestRepository,
    private val serviceRepository: ServiceRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CustomerHomeUiState())
    val uiState: StateFlow<CustomerHomeUiState> = _uiState

    private var observeJob: Job? = null

    fun refresh() {
        if (customerId.isBlank()) {
            _uiState.update { it.copy(isLoading = false, repairs = emptyList()) }
            return
        }
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            // Independent reads, so they overlap rather than queue.
            val repairsDeferred = async { repairRequestRepository.getRepairRequestsForCustomer(customerId) }
            val servicesDeferred = async { serviceRepository.getServices() }

            // A failed service lookup costs a card its service name and
            // nothing else, so it must not fail the screen.
            val serviceNames = servicesDeferred.await()
                .getOrNull()
                ?.associate { it.id to it.name }
                .orEmpty()

            repairsDeferred.await()
                .onSuccess { requests ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = null,
                            repairs = requests,
                            serviceNames = serviceNames,
                        )
                    }
                    val active = requests.firstOrNull { request -> !request.status.isTerminal }
                    if (active != null) observe(active.id) else observeJob?.cancel()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Couldn't load your repairs",
                            serviceNames = serviceNames,
                        )
                    }
                }
        }
    }

    private fun observe(requestId: String) {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            repairRequestRepository.observeRepairRequest(requestId)
                // A dropped listener leaves the card showing the status from
                // the list read rather than replacing the card with an error.
                // The repository re-establishes recoverable failures itself.
                .catch { }
                .collect { request ->
                    _uiState.update { state ->
                        // Replaced in place: the counts read off this list, so
                        // a repair reaching COMPLETED moves Active → Done here
                        // without a second network read.
                        state.copy(
                            repairs = state.repairs.map { existing ->
                                if (existing.id == request.id) request else existing
                            },
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
                    CustomerHomeViewModel(
                        customerId = customerId,
                        repairRequestRepository = repairRequestRepository,
                        serviceRepository = serviceRepository,
                    ) as T
            }
    }
}
