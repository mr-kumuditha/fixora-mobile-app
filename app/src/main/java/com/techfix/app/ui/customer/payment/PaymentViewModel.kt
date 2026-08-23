package com.techfix.app.ui.customer.payment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.techfix.app.domain.catalog.ServiceRepository
import com.techfix.app.domain.payment.Payment
import com.techfix.app.domain.payment.PaymentMethod
import com.techfix.app.domain.payment.PaymentRepository
import com.techfix.app.domain.payment.PaymentStatus
import com.techfix.app.domain.repair.RepairRequest
import com.techfix.app.domain.repair.RepairRequestRepository
import com.techfix.app.domain.repair.RepairStatus
import java.util.Locale
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The one payment flow, as the architecture doc lays it out. */
enum class PaymentStep { SUMMARY, METHOD, DETAILS, PROCESSING, RESULT, RECEIPT }

data class CardFormState(
    val number: String = "",
    val expiry: String = "",
    val cvv: String = "",
    val name: String = "",
    /**
     * The demo's way of reaching the failure branch on purpose. A real
     * checkout decides this server-side; there is no server here, so it is an
     * explicit switch on the form rather than a hidden magic card number.
     */
    val simulateFailure: Boolean = false,
    val showErrors: Boolean = false,
) {
    val numberError: String? get() = DemoCard.cardNumberError(number)
    val expiryError: String? get() = DemoCard.expiryError(expiry)
    val cvvError: String? get() = DemoCard.cvvError(cvv)
    val nameError: String? get() = DemoCard.nameError(name)

    val isValid: Boolean
        get() = numberError == null && expiryError == null && cvvError == null && nameError == null
}

data class PaymentUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val step: PaymentStep = PaymentStep.SUMMARY,
    val request: RepairRequest? = null,
    val serviceName: String? = null,
    /**
     * Null when the service price could not be read. Deliberately not
     * defaulted to 0.0 — see [PaymentEligibility]; a zero amount would sail
     * through the flow and complete the repair for nothing.
     */
    val amount: Double? = null,
    val method: PaymentMethod? = null,
    val card: CardFormState = CardFormState(),
    val payment: Payment? = null,
    val failureReason: String? = null,
    /**
     * Set when the receipt was written but the repair couldn't be moved to
     * COMPLETED. The payment still stands, so this is a warning on the
     * receipt, not a failed payment.
     */
    val statusWarning: String? = null,
    /** True when this repair already had a successful payment on open. */
    val wasAlreadyPaid: Boolean = false,
) {
    val requiresCardDetails: Boolean get() = method == PaymentMethod.CARD

    val canContinueFromMethod: Boolean get() = method != null

    val isPaid: Boolean get() = payment?.status == PaymentStatus.SUCCESS
}

/**
 * The simulated payment flow.
 *
 * **No money moves anywhere in this app.** The card form is format-validated
 * only, card details never leave the ViewModel, and the "processing" step is
 * a fixed delay, not a network call. What is real is the receipt: a document
 * in the existing `payments` collection, which is what Repair History reads
 * back as the actual cost instead of the estimated one.
 *
 * A successful payment is also what moves the repair to COMPLETED — the one
 * transition staff cannot make themselves (see [RepairStatus.nextStaffStage]),
 * which is how a paid repair lands in Repair History.
 */
class PaymentViewModel(
    private val requestId: String,
    private val repairRequestRepository: RepairRequestRepository,
    private val serviceRepository: ServiceRepository,
    private val paymentRepository: PaymentRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PaymentUiState())
    val uiState: StateFlow<PaymentUiState> = _uiState

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
                            errorMessage = error.message ?: "Couldn't load this repair",
                        )
                    }
                }
                .onSuccess { request ->
                    val service = serviceRepository.getService(request.serviceId).getOrNull()
                    // An existing SUCCESS receipt means this repair is already
                    // paid; the flow opens on the receipt rather than letting
                    // it be paid twice.
                    val existing = paymentRepository.getPaymentsForRepairRequest(requestId)
                        .getOrNull()
                        ?.firstOrNull { it.status == PaymentStatus.SUCCESS }

                    // A failed price lookup is not a missing label here, it is
                    // a blocker: there is nothing to charge.
                    val amount = existing?.amount ?: service?.basePrice
                    val blockReason = PaymentEligibility.blockReason(
                        status = request.status,
                        amount = amount,
                        alreadyPaid = existing != null,
                    )

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = blockReason,
                            request = request,
                            serviceName = service?.name,
                            amount = amount,
                            method = existing?.method ?: it.method,
                            payment = existing,
                            wasAlreadyPaid = existing != null,
                            step = if (existing != null) PaymentStep.RECEIPT else PaymentStep.SUMMARY,
                        )
                    }
                }
        }
    }

    fun onMethodSelected(method: PaymentMethod) {
        _uiState.update { it.copy(method = method) }
    }

    fun onCardNumberChange(value: String) {
        _uiState.update { it.copy(card = it.card.copy(number = DemoCard.formatCardNumber(value))) }
    }

    fun onExpiryChange(value: String) {
        _uiState.update { it.copy(card = it.card.copy(expiry = DemoCard.formatExpiry(value))) }
    }

    fun onCvvChange(value: String) {
        _uiState.update {
            it.copy(card = it.card.copy(cvv = DemoCard.digitsOf(value).take(DemoCard.CVV_DIGITS)))
        }
    }

    fun onCardNameChange(value: String) {
        _uiState.update { it.copy(card = it.card.copy(name = value)) }
    }

    fun onSimulateFailureChange(value: Boolean) {
        _uiState.update { it.copy(card = it.card.copy(simulateFailure = value)) }
    }

    fun goToMethod() {
        if (blockIfNotPayable()) return
        _uiState.update { it.copy(step = PaymentStep.METHOD) }
    }

    /** Cash on pickup has nothing to fill in, so it skips the details step. */
    fun continueFromMethod() {
        if (blockIfNotPayable()) return
        val state = _uiState.value
        val next = if (state.requiresCardDetails) PaymentStep.DETAILS else PaymentStep.PROCESSING
        _uiState.update { it.copy(step = next) }
        if (next == PaymentStep.PROCESSING) process()
    }

    fun payNow() {
        if (blockIfNotPayable()) return
        val state = _uiState.value
        if (!state.card.isValid) {
            _uiState.update { it.copy(card = it.card.copy(showErrors = true)) }
            return
        }
        _uiState.update { it.copy(step = PaymentStep.PROCESSING) }
        process()
    }

    /** Back through the flow; from SUMMARY the screen leaves instead. */
    fun goBack(onLeave: () -> Unit) {
        when (_uiState.value.step) {
            PaymentStep.SUMMARY, PaymentStep.PROCESSING, PaymentStep.RECEIPT -> onLeave()
            PaymentStep.METHOD -> _uiState.update { it.copy(step = PaymentStep.SUMMARY) }
            PaymentStep.DETAILS -> _uiState.update { it.copy(step = PaymentStep.METHOD) }
            PaymentStep.RESULT -> if (_uiState.value.isPaid) onLeave() else retry()
        }
    }

    fun retry() {
        _uiState.update {
            it.copy(
                step = if (it.requiresCardDetails) PaymentStep.DETAILS else PaymentStep.METHOD,
                failureReason = null,
                payment = null,
            )
        }
    }

    fun viewReceipt() {
        _uiState.update { it.copy(step = PaymentStep.RECEIPT) }
    }

    /**
     * Re-runs the eligibility rule at every step that moves money, not just on
     * load. Returns true when the flow was stopped, so callers can bail out.
     */
    private fun blockIfNotPayable(): Boolean {
        val state = _uiState.value
        val request = state.request ?: return true
        val reason = PaymentEligibility.blockReason(
            status = request.status,
            amount = state.amount,
            alreadyPaid = state.wasAlreadyPaid,
        ) ?: return false
        _uiState.update { it.copy(errorMessage = reason) }
        return true
    }

    private fun process() {
        viewModelScope.launch {
            // The "processing" wait is a fixed delay, not a network call —
            // there is no processor to wait on.
            delay(PROCESSING_DELAY_MS)

            val state = _uiState.value
            val amount = state.amount
            if (amount == null || amount <= 0.0) {
                // Belt and braces: the guards above should make this
                // unreachable, but nothing may write a zero-value receipt.
                _uiState.update {
                    it.copy(
                        step = PaymentStep.SUMMARY,
                        errorMessage = PaymentEligibility.blockReason(
                            status = it.request?.status ?: RepairStatus.SUBMITTED,
                            amount = amount,
                            alreadyPaid = false,
                        ),
                    )
                }
                return@launch
            }
            val declined = state.requiresCardDetails && state.card.simulateFailure
            val method = state.method ?: PaymentMethod.CARD
            val receiptId = generateReceiptId()

            val record = Payment(
                id = "",
                repairRequestId = requestId,
                amount = amount,
                method = method,
                status = if (declined) PaymentStatus.FAILED else PaymentStatus.SUCCESS,
                receiptId = receiptId,
            )

            val created = paymentRepository.createPayment(record)

            if (declined) {
                // A failed attempt is still recorded, but a write failure here
                // must not hide the decline from the customer.
                _uiState.update {
                    it.copy(
                        step = PaymentStep.RESULT,
                        payment = record.copy(id = created.getOrNull().orEmpty()),
                        failureReason = "The demo card was declined. No money was taken — " +
                            "nothing is ever charged in this app.",
                    )
                }
                return@launch
            }

            created
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            step = PaymentStep.RESULT,
                            payment = null,
                            failureReason = error.message ?: "Couldn't record the payment",
                        )
                    }
                }
                .onSuccess { paymentId ->
                    // Paying is what finishes the repair, so the status move
                    // belongs here rather than on a staff screen.
                    val statusResult = repairRequestRepository
                        .updateStatus(requestId, RepairStatus.COMPLETED)

                    _uiState.update {
                        it.copy(
                            step = PaymentStep.RESULT,
                            payment = record.copy(id = paymentId, createdAt = System.currentTimeMillis()),
                            failureReason = null,
                            request = it.request?.copy(status = RepairStatus.COMPLETED),
                            statusWarning = statusResult.exceptionOrNull()?.let { error ->
                                "Payment recorded, but the repair couldn't be marked completed " +
                                    "(${error.message ?: "unknown error"}). Your receipt still stands."
                            },
                        )
                    }
                }
        }
    }

    /** Short, human-quotable, and unique enough for a demo receipt. */
    private fun generateReceiptId(): String =
        "FX-" + (1..8)
            .map { RECEIPT_ALPHABET[Random.nextInt(RECEIPT_ALPHABET.length)] }
            .joinToString("")
            .uppercase(Locale.ROOT)

    companion object {
        private const val PROCESSING_DELAY_MS = 1_800L
        private const val RECEIPT_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

        fun factory(
            requestId: String,
            repairRequestRepository: RepairRequestRepository,
            serviceRepository: ServiceRepository,
            paymentRepository: PaymentRepository,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    PaymentViewModel(
                        requestId = requestId,
                        repairRequestRepository = repairRequestRepository,
                        serviceRepository = serviceRepository,
                        paymentRepository = paymentRepository,
                    ) as T
            }
    }
}
