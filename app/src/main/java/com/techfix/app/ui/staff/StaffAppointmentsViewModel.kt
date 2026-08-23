package com.techfix.app.ui.staff

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.techfix.app.domain.branch.BranchRepository
import com.techfix.app.domain.catalog.ServiceRepository
import com.techfix.app.domain.repair.RepairRequest
import com.techfix.app.domain.repair.RepairRequestRepository
import com.techfix.app.domain.repair.RepairStatus
import com.techfix.app.domain.technician.TechnicianRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Which slice of the queue is on screen. NEW is the appointment queue the
 * brief asks for (SUBMITTED requests waiting to be confirmed); ACTIVE is
 * everything already confirmed and still moving, which is where the
 * status-advance action lives.
 */
enum class StaffQueueTab { NEW, ACTIVE, COMPLETED, CANCELLED }

data class StaffAppointmentsUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val tab: StaffQueueTab = StaffQueueTab.NEW,
    val newRequests: List<RepairRequest> = emptyList(),
    val activeRequests: List<RepairRequest> = emptyList(),
    val completedRequests: List<RepairRequest> = emptyList(),
    val cancelledRequests: List<RepairRequest> = emptyList(),
    val query: String = "",
    val serviceNames: Map<String, String> = emptyMap(),
    val branchNames: Map<String, String> = emptyMap(),
    val technicianNames: Map<String, String> = emptyMap(),
) {
    private val tabRequests: List<RepairRequest>
        get() = when (tab) {
            StaffQueueTab.NEW -> newRequests
            StaffQueueTab.ACTIVE -> activeRequests
            StaffQueueTab.COMPLETED -> completedRequests
            StaffQueueTab.CANCELLED -> cancelledRequests
        }

    val visibleRequests: List<RepairRequest>
        get() = tabRequests.filter { request ->
            query.isBlank() || listOf(
                request.id,
                request.deviceDetails.brand,
                request.deviceDetails.model,
                serviceNames[request.serviceId].orEmpty(),
                branchNames[request.branchId].orEmpty(),
                request.technicianId?.let { technicianNames[it] }.orEmpty(),
            ).any { it.contains(query.trim(), ignoreCase = true) }
        }

    val isEmpty: Boolean
        get() = !isLoading && errorMessage == null && visibleRequests.isEmpty()
}

/**
 * Appointment Queue, shared by all three staff roles.
 *
 * The repair read is branch-scoped by [loadRepairsFor]; on top of that, a
 * Technician's ACTIVE list is narrowed again to the repairs assigned to them,
 * so "my work" and "the branch's work" are the same screen with a different
 * filter rather than two screens.
 *
 * Service, branch, and technician names are looked up through the
 * repositories Blocks 3 and 5 already built — a failed name lookup costs a
 * card its label, never the whole screen.
 */
class StaffAppointmentsViewModel(
    private val staffContext: StaffContext,
    private val repairRequestRepository: RepairRequestRepository,
    private val serviceRepository: ServiceRepository,
    private val branchRepository: BranchRepository,
    private val technicianRepository: TechnicianRepository,
    initialTab: StaffQueueTab,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StaffAppointmentsUiState(tab = initialTab))
    val uiState: StateFlow<StaffAppointmentsUiState> = _uiState

    init {
        load()
    }

    fun onTabSelected(tab: StaffQueueTab) {
        _uiState.update { it.copy(tab = tab) }
    }

    fun onQueryChanged(query: String) = _uiState.update { it.copy(query = query) }

    fun load() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            // Four independent reads across two backends — overlapped rather
            // than queued, same as the history screen does.
            val repairsDeferred = async { loadRepairsFor(staffContext, repairRequestRepository) }
            val servicesDeferred = async { serviceRepository.getServices() }
            val branchesDeferred = async { branchRepository.getBranches() }
            // Include archived names so completed repairs retain readable historical assignments.
            val techniciansDeferred = async { technicianRepository.getAllTechniciansIncludingArchived() }

            val serviceNames = servicesDeferred.await().getOrNull()
                ?.associate { it.id to it.name }.orEmpty()
            val branchNames = branchesDeferred.await().getOrNull()
                ?.associate { it.id to it.name }.orEmpty()
            val technicianNames = techniciansDeferred.await().getOrNull()
                ?.associate { it.id to it.name }.orEmpty()

            repairsDeferred.await()
                .onSuccess { repairs ->
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            errorMessage = null,
                            newRequests = repairs.filter { it.status == RepairStatus.SUBMITTED },
                            activeRequests = repairs
                                .filter { !it.status.isTerminal && it.status != RepairStatus.SUBMITTED }
                                .filter { request ->
                                    !staffContext.seesOnlyOwnRepairs ||
                                        request.technicianId == staffContext.technicianId
                                },
                            completedRequests = repairs.filter { it.status == RepairStatus.COMPLETED },
                            cancelledRequests = repairs.filter { it.status == RepairStatus.CANCELLED },
                            serviceNames = serviceNames,
                            branchNames = branchNames,
                            technicianNames = technicianNames,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Unable to load repairs. Please try again.",
                        )
                    }
                }
        }
    }

    companion object {
        fun factory(
            staffContext: StaffContext,
            repairRequestRepository: RepairRequestRepository,
            serviceRepository: ServiceRepository,
            branchRepository: BranchRepository,
            technicianRepository: TechnicianRepository,
            initialTab: StaffQueueTab,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    StaffAppointmentsViewModel(
                        staffContext = staffContext,
                        repairRequestRepository = repairRequestRepository,
                        serviceRepository = serviceRepository,
                        branchRepository = branchRepository,
                        technicianRepository = technicianRepository,
                        initialTab = initialTab,
                    ) as T
            }
    }
}
