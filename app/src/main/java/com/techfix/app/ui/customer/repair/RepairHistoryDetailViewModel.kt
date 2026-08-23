package com.techfix.app.ui.customer.repair

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.techfix.app.domain.branch.BranchRepository
import com.techfix.app.domain.catalog.ServiceRepository
import com.techfix.app.domain.payment.PaymentStatus
import com.techfix.app.domain.payment.PaymentRepository
import com.techfix.app.domain.repair.RepairRequest
import com.techfix.app.domain.repair.RepairRequestRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RepairHistoryDetailUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val request: RepairRequest? = null,
    val serviceName: String? = null,
    val branchName: String? = null,
    val cost: Double? = null,
    /**
     * True when the figure shown is the service's base price because no
     * payment record exists yet — the screen must label it as an estimate
     * rather than pass it off as what was charged.
     */
    val costIsEstimate: Boolean = true,
    val paidAt: Long? = null,
    val receiptId: String? = null,
)

/**
 * History Detail — device info, images, cost, dates, final status.
 *
 * Cost prefers the successful payment record for this request (Block 7's
 * simulated payment writes it) and falls back to the service's base price,
 * flagged as an estimate. Nothing here invents a figure.
 */
class RepairHistoryDetailViewModel(
    private val requestId: String,
    private val repairRequestRepository: RepairRequestRepository,
    private val serviceRepository: ServiceRepository,
    private val branchRepository: BranchRepository,
    private val paymentRepository: PaymentRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RepairHistoryDetailUiState())
    val uiState: StateFlow<RepairHistoryDetailUiState> = _uiState

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val requestResult = repairRequestRepository.getRepairRequest(requestId)
            val request = requestResult.getOrElse { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Couldn't load this repair",
                    )
                }
                return@launch
            }

            // Everything below only decorates the repair — a failure in any
            // of them leaves the field blank rather than failing the screen.
            val serviceDeferred = async { serviceRepository.getService(request.serviceId) }
            val branchDeferred = async { branchRepository.getBranch(request.branchId) }
            val paymentsDeferred = async { paymentRepository.getPaymentsForRepairRequest(requestId) }

            val service = serviceDeferred.await().getOrNull()
            val branch = branchDeferred.await().getOrNull()
            val payment = paymentsDeferred.await().getOrNull()
                ?.firstOrNull { it.status == PaymentStatus.SUCCESS }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = null,
                    request = request,
                    serviceName = service?.name,
                    branchName = branch?.name,
                    cost = payment?.amount ?: service?.basePrice,
                    costIsEstimate = payment == null,
                    paidAt = payment?.createdAt,
                    receiptId = payment?.receiptId,
                )
            }
        }
    }

    companion object {
        fun factory(
            requestId: String,
            repairRequestRepository: RepairRequestRepository,
            serviceRepository: ServiceRepository,
            branchRepository: BranchRepository,
            paymentRepository: PaymentRepository,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    RepairHistoryDetailViewModel(
                        requestId = requestId,
                        repairRequestRepository = repairRequestRepository,
                        serviceRepository = serviceRepository,
                        branchRepository = branchRepository,
                        paymentRepository = paymentRepository,
                    ) as T
            }
    }
}
