package com.techfix.app.ui.staff

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.techfix.app.core.navigation.UserRole
import com.techfix.app.domain.branch.BranchRepository
import com.techfix.app.domain.operations.OperationalSnapshot
import com.techfix.app.domain.payment.PaymentRepository
import com.techfix.app.domain.repair.RepairRequestRepository
import com.techfix.app.domain.sparepart.SparePartRepository
import com.techfix.app.domain.technician.TechnicianRepository
import com.techfix.app.domain.user.UserRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StaffOperationsUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val snapshot: OperationalSnapshot = OperationalSnapshot(emptyList()),
)

/** Shared one-shot operational read used by Branches, Users, and Reports. */
class StaffOperationsViewModel(
    private val staffContext: StaffContext,
    private val repairRequests: RepairRequestRepository,
    private val payments: PaymentRepository,
    private val branches: BranchRepository,
    private val technicians: TechnicianRepository,
    private val spareParts: SparePartRepository,
    private val users: UserRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(StaffOperationsUiState())
    val uiState: StateFlow<StaffOperationsUiState> = _uiState

    init { load() }

    fun load() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            if (!staffContext.hasRequiredScope) {
                _uiState.value = StaffOperationsUiState(false, "Your staff account is missing its operational assignment.")
                return@launch
            }
            val repairRead = async { loadRepairsFor(staffContext, repairRequests) }
            val branchRead = async { branches.getBranches() }
            val technicianRead = async {
                if (staffContext.seesAllBranches) technicians.getTechnicians()
                else technicians.getTechniciansForBranch(staffContext.branchId.orEmpty())
            }
            val paymentRead = async {
                if (staffContext.role == UserRole.ADMIN) payments.getAllPayments()
                else Result.success(emptyList())
            }
            val userRead = async {
                if (staffContext.role == UserRole.ADMIN) users.getUsers()
                else Result.success(emptyList())
            }

            repairRead.await().onFailure {
                _uiState.value = StaffOperationsUiState(false, "Unable to load operational data.")
            }.onSuccess { repairRows ->
                val branchResult = branchRead.await()
                val technicianResult = technicianRead.await()
                val paymentResult = paymentRead.await()
                val userResult = userRead.await()
                if (branchResult.isFailure || technicianResult.isFailure ||
                    (staffContext.role == UserRole.ADMIN && (paymentResult.isFailure || userResult.isFailure))
                ) {
                    _uiState.value = StaffOperationsUiState(false, "Unable to load operational data. Please try again.")
                    return@onSuccess
                }
                val branchRows = branchResult.getOrDefault(emptyList()).let { all ->
                    if (staffContext.seesAllBranches) all else all.filter { it.id == staffContext.branchId }
                }
                val stock = branchRows.associate { branch ->
                    branch.id to spareParts.getStockForBranch(branch.id).getOrDefault(emptyList())
                }
                _uiState.value = StaffOperationsUiState(
                    isLoading = false,
                    snapshot = OperationalSnapshot(
                        repairs = repairRows,
                        payments = paymentResult.getOrDefault(emptyList()),
                        technicians = technicianResult.getOrDefault(emptyList()),
                        branches = branchRows,
                        stockByBranch = stock,
                        users = userResult.getOrDefault(emptyList()),
                    ),
                )
            }
        }
    }

    companion object {
        fun factory(
            staffContext: StaffContext,
            repairRequests: RepairRequestRepository,
            payments: PaymentRepository,
            branches: BranchRepository,
            technicians: TechnicianRepository,
            spareParts: SparePartRepository,
            users: UserRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = StaffOperationsViewModel(
                staffContext, repairRequests, payments, branches, technicians, spareParts, users,
            ) as T
        }
    }
}
