package com.techfix.app.ui.customer.repair

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.techfix.app.domain.branch.BranchRepository
import com.techfix.app.domain.catalog.ServiceRepository
import com.techfix.app.domain.repair.RepairRequest
import com.techfix.app.domain.repair.RepairRequestRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RepairTrackingUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val request: RepairRequest? = null,
    val serviceName: String? = null,
    val branchName: String? = null,
)

/**
 * Repair Tracking Detail.
 *
 * The status comes off `observeRepairRequest`, which is a Firestore snapshot
 * listener — the timeline moves on its own when staff advance the repair, no
 * pull-to-refresh and no polling. Service and branch names are fetched once,
 * the first time a snapshot names them, because neither changes for the life
 * of a request and re-fetching them on every status push would be wasteful.
 */
class RepairTrackingViewModel(
    private val requestId: String,
    private val repairRequestRepository: RepairRequestRepository,
    private val serviceRepository: ServiceRepository,
    private val branchRepository: BranchRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RepairTrackingUiState())
    val uiState: StateFlow<RepairTrackingUiState> = _uiState

    private var observeJob: Job? = null

    init {
        observe()
    }

    /** Also the retry action — a failed listener is restarted, not resumed. */
    fun observe() {
        observeJob?.cancel()
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        observeJob = viewModelScope.launch {
            repairRequestRepository.observeRepairRequest(requestId)
                .catch { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Couldn't load this repair",
                        )
                    }
                }
                .collect { request ->
                    val previous = _uiState.value.request
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = null, request = request)
                    }
                    if (previous?.serviceId != request.serviceId) loadServiceName(request.serviceId)
                    if (previous?.branchId != request.branchId) loadBranchName(request.branchId)
                }
        }
    }

    /**
     * A missing service or branch name is not an error state for this screen
     * — the timeline is still fully usable, so the label just stays absent.
     */
    private fun loadServiceName(serviceId: String) {
        if (serviceId.isBlank()) return
        viewModelScope.launch {
            serviceRepository.getService(serviceId)
                .onSuccess { service -> _uiState.update { it.copy(serviceName = service.name) } }
        }
    }

    private fun loadBranchName(branchId: String) {
        if (branchId.isBlank()) return
        viewModelScope.launch {
            branchRepository.getBranch(branchId)
                .onSuccess { branch -> _uiState.update { it.copy(branchName = branch.name) } }
        }
    }

    companion object {
        fun factory(
            requestId: String,
            repairRequestRepository: RepairRequestRepository,
            serviceRepository: ServiceRepository,
            branchRepository: BranchRepository,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    RepairTrackingViewModel(
                        requestId = requestId,
                        repairRequestRepository = repairRequestRepository,
                        serviceRepository = serviceRepository,
                        branchRepository = branchRepository,
                    ) as T
            }
    }
}
