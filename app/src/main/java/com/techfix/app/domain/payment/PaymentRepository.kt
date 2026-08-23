package com.techfix.app.domain.payment

interface PaymentRepository {
    /** Returns the id of the created document. */
    suspend fun createPayment(payment: Payment): Result<String>

    suspend fun getPaymentsForRepairRequest(repairRequestId: String): Result<List<Payment>>

    /** Admin reporting read, newest first. */
    suspend fun getAllPayments(): Result<List<Payment>>
}
