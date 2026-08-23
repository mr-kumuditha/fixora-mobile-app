package com.techfix.app.ui.staff

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.techfix.app.domain.catalog.ServiceRepository
import com.techfix.app.domain.matching.BranchMatch
import com.techfix.app.domain.matching.MatchBranchesUseCase
import com.techfix.app.domain.repair.RepairRequest
import com.techfix.app.domain.repair.RepairRequestRepository
import com.techfix.app.domain.repair.RepairStatus
import com.techfix.app.domain.technician.Technician
import com.techfix.app.domain.technician.TechnicianRepository
import com.techfix.app.ui.customer.repair.label
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StaffAppointmentDetailUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val request: RepairRequest? = null,
    val serviceName: String? = null,
    val servicePrice: Double? = null,
    /** Branch options in the same rank order the customer's booking used. */
    val branchMatches: List<BranchMatch> = emptyList(),
    val selectedBranchId: String? = null,
    val selectedTechnicianId: String? = null,
    val isSaving: Boolean = false,
    val actionError: String? = null,
    val confirmationMessage: String? = null,
) {
    val selectedMatch: BranchMatch?
        get() = branchMatches.firstOrNull { it.branch.id == selectedBranchId }

    /** Technicians the selected branch can actually put on this device. */
    val technicianOptions: List<Technician>
        get() = selectedMatch?.availableTechnicians.orEmpty()

    val canConfirmAssignment: Boolean
        get() = !isSaving && selectedBranchId != null && selectedTechnicianId != null

    /** Null when moving this repair on is not a staff action — see [RepairStatus.nextStaffStage]. */
    val nextStage: RepairStatus?
        get() = request?.status?.nextStaffStage
}

/**
 * Appointment Detail / Assignment, and the status-advance action, on one
 * screen shared by all three staff roles.
 *
 * The branch options come from [MatchBranchesUseCase] — the same rule the
 * customer's booking ran in Block 5, not a second copy of it — so a manager
 * confirming a branch sees the same technician and spare-part availability
 * the automatic match was scored on. It is invoked with a null location on
 * purpose: the staff member is not standing where the customer is, so
 * distance is held neutral and availability alone orders the list.
 */
class StaffAppointmentDetailViewModel(
    private val requestId: String,
    private val staffContext: StaffContext,
    private val repairRequestRepository: RepairRequestRepository,
    private val serviceRepository: ServiceRepository,
    private val matchBranches: MatchBranchesUseCase,
    private val technicianRepository: TechnicianRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StaffAppointmentDetailUiState())
    val uiState: StateFlow<StaffAppointmentDetailUiState> = _uiState

    val canAssign: Boolean get() = staffContext.canAssign

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            repairRequestRepository.getRepairRequest(requestId)
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Couldn't load this appointment",
                        )
                    }
                }
                .onSuccess { request ->
                    val serviceDeferred = async { serviceRepository.getService(request.serviceId) }
                    val matchDeferred = async {
                        matchBranches(
                            category = request.deviceDetails.category,
                            customerLocation = null,
                            requireVerifiedAccounts = true,
                        )
                    }

                    val service = serviceDeferred.await().getOrNull()
                    // A failed match still leaves a usable screen: the details
                    // and the status-advance action don't depend on it, only
                    // the assignment section does.
                    val matches = matchDeferred.await().getOrNull()?.matches.orEmpty().let { all ->
                        if (staffContext.seesAllBranches) all
                        else all.filter { it.branch.id == staffContext.branchId }
                    }

                    val branchId = request.branchId.takeIf { id ->
                        matches.any { it.branch.id == id }
                    } ?: matches.firstOrNull()?.branch.let { it?.id } ?: request.branchId

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = null,
                            request = request,
                            serviceName = service?.name,
                            servicePrice = service?.basePrice,
                            branchMatches = matches,
                            selectedBranchId = branchId,
                            selectedTechnicianId = request.technicianId
                                ?: defaultTechnicianFor(matches, branchId),
                        )
                    }
                }
        }
    }

    fun onBranchSelected(branchId: String) {
        _uiState.update { state ->
            // The technician list belongs to the branch, so changing branch
            // clears a technician who no longer works there rather than
            // silently keeping an invalid assignment.
            val stillValid = state.branchMatches
                .firstOrNull { it.branch.id == branchId }
                ?.availableTechnicians
                ?.any { it.id == state.selectedTechnicianId } == true
            state.copy(
                selectedBranchId = branchId,
                selectedTechnicianId = if (stillValid) {
                    state.selectedTechnicianId
                } else {
                    defaultTechnicianFor(state.branchMatches, branchId)
                },
                actionError = null,
            )
        }
    }

    fun onTechnicianSelected(technicianId: String) {
        _uiState.update { it.copy(selectedTechnicianId = technicianId, actionError = null) }
    }

    /** Confirms the branch and names a technician, which moves the repair to CONFIRMED. */
    fun confirmAssignment(onConfirmed: () -> Unit = {}) {
        if (!staffContext.canAssign) {
            _uiState.update { it.copy(actionError = "Your role can't assign appointments.") }
            return
        }
        val state = _uiState.value
        val branchId = state.selectedBranchId
        val technicianId = state.selectedTechnicianId
        if (branchId == null || technicianId == null) {
            _uiState.update { it.copy(actionError = "Pick a branch and a technician first.") }
            return
        }

        _uiState.update { it.copy(isSaving = true, actionError = null, confirmationMessage = null) }
        viewModelScope.launch {
            val request = _uiState.value.request
                ?: return@launch _uiState.update { it.copy(isSaving = false, actionError = "Appointment is unavailable.") }
            val selectedTechnician = state.technicianOptions.firstOrNull { it.id == technicianId }
            if (selectedTechnician == null) {
                _uiState.update {
                    it.copy(isSaving = false, actionError = "This technician is no longer eligible for the repair.")
                }
                return@launch
            }
            technicianRepository.verifyAssignmentCandidate(
                technicianId = technicianId,
                branchId = branchId,
                category = request.deviceDetails.category,
            ).onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        actionError = error.message ?: "Technician account verification failed.",
                    )
                }
                return@launch
            }
            repairRequestRepository.assignTechnician(requestId, branchId, technicianId)
                .onSuccess { resolvedStatus ->
                    // The status comes back from the transaction rather than
                    // being assumed here: assigning confirms a new booking,
                    // but reassigning a repair already under way leaves its
                    // status where it was.
                    val wasConfirmation = resolvedStatus == RepairStatus.CONFIRMED &&
                        _uiState.value.request?.status == RepairStatus.SUBMITTED
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            request = it.request?.copy(
                                branchId = branchId,
                                technicianId = technicianId,
                                status = resolvedStatus,
                            ),
                            confirmationMessage = if (wasConfirmation) {
                                "Appointment confirmed and assigned."
                            } else {
                                "Technician reassigned. The repair stays at " +
                                    "${resolvedStatus.label}."
                            },
                        )
                    }
                    onConfirmed()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            actionError = error.message ?: "Couldn't confirm this appointment",
                        )
                    }
                }
        }
    }

    /** Moves the repair to the next stage. COMPLETED is never reachable here — payment does that. */
    fun advanceStatus() {
        val state = _uiState.value
        val next = state.nextStage ?: return
        if (state.request?.technicianId == null) {
            _uiState.update { it.copy(actionError = "Assign a technician before moving this repair on.") }
            return
        }
        if (staffContext.seesOnlyOwnRepairs && state.request.technicianId != staffContext.technicianId) {
            _uiState.update { it.copy(actionError = "This repair is assigned to another technician.") }
            return
        }

        _uiState.update { it.copy(isSaving = true, actionError = null, confirmationMessage = null) }
        viewModelScope.launch {
            repairRequestRepository.updateStatus(requestId, next)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            request = it.request?.copy(status = next),
                            confirmationMessage = "Moved to ${next.name.lowercase().replace('_', ' ')}.",
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            actionError = error.message ?: "Couldn't update the status",
                        )
                    }
                }
        }
    }

    fun dismissMessages() {
        _uiState.update { it.copy(actionError = null, confirmationMessage = null) }
    }

    private fun defaultTechnicianFor(matches: List<BranchMatch>, branchId: String?): String? =
        matches.firstOrNull { it.branch.id == branchId }
            ?.availableTechnicians
            ?.firstOrNull()
            ?.id

    companion object {
        fun factory(
            requestId: String,
            staffContext: StaffContext,
            repairRequestRepository: RepairRequestRepository,
            serviceRepository: ServiceRepository,
            matchBranches: MatchBranchesUseCase,
            technicianRepository: TechnicianRepository,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    StaffAppointmentDetailViewModel(
                        requestId = requestId,
                        staffContext = staffContext,
                        repairRequestRepository = repairRequestRepository,
                        serviceRepository = serviceRepository,
                        matchBranches = matchBranches,
                        technicianRepository = technicianRepository,
                    ) as T
            }
    }
}
