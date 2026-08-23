package com.techfix.app.ui.staff

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.techfix.app.domain.repair.RepairRequest
import com.techfix.app.domain.repair.RepairRequestRepository
import com.techfix.app.domain.repair.RepairStatus
import com.techfix.app.domain.branch.BranchRepository
import com.techfix.app.domain.operations.BranchPerformance
import com.techfix.app.domain.operations.OperationalSnapshot
import com.techfix.app.domain.payment.PaymentRepository
import com.techfix.app.domain.sparepart.SparePartRepository
import com.techfix.app.domain.technician.TechnicianRepository
import com.techfix.app.domain.user.UserRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StaffDashboardUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val newCount: Int = 0,
    val activeCount: Int = 0,
    val readyCount: Int = 0,
    val assignedToMeCount: Int = 0,
    val totalCount: Int = 0,
    val completedCount: Int = 0,
    val cancelledCount: Int = 0,
    val availableTechnicianCount: Int = 0,
    val customerCount: Int = 0,
    val recordedRevenue: Double = 0.0,
    val outOfStockCount: Int = 0,
    val branchPerformance: List<BranchPerformance> = emptyList(),
    val recentRepairs: List<RepairRequest> = emptyList(),
)

/**
 * Staff Dashboard — counts and entry points, deliberately nothing more.
 *
 * The read is scoped the same way every staff screen is: an Admin sees every
 * branch, everyone else sees their own. It reuses the repair-request
 * repository rather than adding a staff-specific one, and counts client-side
 * because a per-status server query would need its own composite index for a
 * dataset this size.
 */
class StaffDashboardViewModel(
    private val staffContext: StaffContext,
    private val repairRequestRepository: RepairRequestRepository,
    private val paymentRepository: PaymentRepository,
    private val branchRepository: BranchRepository,
    private val technicianRepository: TechnicianRepository,
    private val sparePartRepository: SparePartRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StaffDashboardUiState())
    val uiState: StateFlow<StaffDashboardUiState> = _uiState

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val repairsDeferred = async { loadRepairsFor(staffContext, repairRequestRepository) }
            val branchesDeferred = async { branchRepository.getBranches() }
            val techniciansDeferred = async {
                if (staffContext.seesAllBranches) technicianRepository.getTechnicians()
                else technicianRepository.getTechniciansForBranch(staffContext.branchId.orEmpty())
            }
            val paymentsDeferred = async {
                if (staffContext.role == com.techfix.app.core.navigation.UserRole.ADMIN) {
                    paymentRepository.getAllPayments()
                } else Result.success(emptyList())
            }
            val usersDeferred = async {
                if (staffContext.role == com.techfix.app.core.navigation.UserRole.ADMIN) {
                    userRepository.getUsers()
                } else Result.success(emptyList())
            }

            repairsDeferred.await()
                .onSuccess { repairs ->
                    val branchResult = branchesDeferred.await()
                    val technicianResult = techniciansDeferred.await()
                    val paymentResult = paymentsDeferred.await()
                    val userResult = usersDeferred.await()
                    if (branchResult.isFailure || technicianResult.isFailure ||
                        (staffContext.role == com.techfix.app.core.navigation.UserRole.ADMIN &&
                            (paymentResult.isFailure || userResult.isFailure))
                    ) {
                        _uiState.value = StaffDashboardUiState(
                            isLoading = false,
                            errorMessage = "Unable to load operational data. Please try again.",
                        )
                        return@onSuccess
                    }
                    val allBranches = branchResult.getOrDefault(emptyList())
                    val visibleBranches = if (staffContext.seesAllBranches) allBranches
                    else allBranches.filter { it.id == staffContext.branchId }
                    val stock = visibleBranches.associate { branch ->
                        branch.id to sparePartRepository.getStockForBranch(branch.id).getOrDefault(emptyList())
                    }
                    val snapshot = OperationalSnapshot(
                        repairs = repairs,
                        payments = paymentResult.getOrDefault(emptyList()),
                        technicians = technicianResult.getOrDefault(emptyList()),
                        branches = visibleBranches,
                        stockByBranch = stock,
                        users = userResult.getOrDefault(emptyList()),
                    )
                    _uiState.value = snapshot.toUiState()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Unable to load operational data. Please try again.",
                        )
                    }
                }
        }
    }

    private fun OperationalSnapshot.toUiState(): StaffDashboardUiState {
        val metric = metrics
        return StaffDashboardUiState(
        isLoading = false,
        newCount = metric.pendingRepairs,
        activeCount = metric.activeRepairs,
        readyCount = metric.readyRepairs,
        assignedToMeCount = staffContext.technicianId?.let { technicianId ->
            repairs.count { it.technicianId == technicianId && !it.status.isTerminal }
        } ?: 0,
        totalCount = metric.totalRepairs,
        completedCount = metric.completedRepairs,
        cancelledCount = metric.cancelledRepairs,
        availableTechnicianCount = metric.availableTechnicians,
        customerCount = metric.customerUsers,
        recordedRevenue = metric.recordedRevenue,
        outOfStockCount = metric.outOfStockParts,
        branchPerformance = branchPerformance,
        recentRepairs = repairs.take(4),
        )
    }

    companion object {
        fun factory(
            staffContext: StaffContext,
            repairRequestRepository: RepairRequestRepository,
            paymentRepository: PaymentRepository,
            branchRepository: BranchRepository,
            technicianRepository: TechnicianRepository,
            sparePartRepository: SparePartRepository,
            userRepository: UserRepository,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    StaffDashboardViewModel(
                        staffContext,
                        repairRequestRepository,
                        paymentRepository,
                        branchRepository,
                        technicianRepository,
                        sparePartRepository,
                        userRepository,
                    ) as T
            }
    }
}

/**
 * The one place the staff read is scoped. An Admin reads the whole collection,
 * a Branch Manager reads only their branch, and a Technician reads only exact
 * `technicianId` assignments. Missing staff scope fails closed.
 */
internal suspend fun loadRepairsFor(
    staffContext: StaffContext,
    repairRequestRepository: RepairRequestRepository,
): Result<List<RepairRequest>> =
    when {
        !staffContext.hasRequiredScope -> Result.failure(
            IllegalStateException("Your staff account is missing its operational assignment."),
        )
        staffContext.seesAllBranches -> repairRequestRepository.getAllRepairRequests()
        staffContext.seesOnlyOwnRepairs -> repairRequestRepository.getRepairRequestsForTechnician(
            staffContext.technicianId.orEmpty(),
        )
        else -> repairRequestRepository.getRepairRequestsForBranch(staffContext.branchId.orEmpty())
    }
