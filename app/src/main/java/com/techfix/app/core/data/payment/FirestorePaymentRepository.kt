package com.techfix.app.core.data.payment

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.techfix.app.core.data.FirestoreCollections
import com.techfix.app.domain.payment.Payment
import com.techfix.app.domain.payment.PaymentMethod
import com.techfix.app.domain.payment.PaymentRepository
import com.techfix.app.domain.payment.PaymentStatus
import kotlinx.coroutines.tasks.await

/**
 * Receipt records for the simulated payment flow (Block 7). Nothing here
 * moves real money — the record exists so the receipt and repair history
 * have something to read back.
 */
class FirestorePaymentRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : PaymentRepository {

    private val collection get() = firestore.collection(FirestoreCollections.PAYMENTS)

    override suspend fun createPayment(payment: Payment): Result<String> = runCatching {
        val document = if (payment.id.isBlank()) collection.document() else collection.document(payment.id)
        document.set(
            mapOf(
                FIELD_REPAIR_REQUEST_ID to payment.repairRequestId,
                FIELD_AMOUNT to payment.amount,
                FIELD_METHOD to payment.method.name,
                FIELD_STATUS to payment.status.name,
                FIELD_RECEIPT_ID to payment.receiptId,
                FIELD_CREATED_AT to (
                    payment.createdAt?.let { Timestamp(it / 1000, 0) }
                        ?: FieldValue.serverTimestamp()
                    ),
            )
        ).await()
        document.id
    }

    override suspend fun getPaymentsForRepairRequest(
        repairRequestId: String,
    ): Result<List<Payment>> = runCatching {
        collection
            .whereEqualTo(FIELD_REPAIR_REQUEST_ID, repairRequestId)
            .orderBy(FIELD_CREATED_AT, Query.Direction.DESCENDING)
            .get()
            .await()
            .documents
            .mapNotNull { it.toPayment() }
    }

    override suspend fun getAllPayments(): Result<List<Payment>> = runCatching {
        collection
            .orderBy(FIELD_CREATED_AT, Query.Direction.DESCENDING)
            .get()
            .await()
            .documents
            .mapNotNull { it.toPayment() }
    }

    private fun DocumentSnapshot.toPayment(): Payment? {
        if (!exists()) return null
        return Payment(
            id = id,
            repairRequestId = getString(FIELD_REPAIR_REQUEST_ID).orEmpty(),
            amount = getDouble(FIELD_AMOUNT) ?: 0.0,
            method = runCatching { PaymentMethod.valueOf(getString(FIELD_METHOD).orEmpty()) }
                .getOrDefault(PaymentMethod.CARD),
            status = runCatching { PaymentStatus.valueOf(getString(FIELD_STATUS).orEmpty()) }
                .getOrDefault(PaymentStatus.PENDING),
            receiptId = getString(FIELD_RECEIPT_ID).orEmpty(),
            createdAt = getTimestamp(FIELD_CREATED_AT)?.toDate()?.time,
        )
    }

    private companion object {
        const val FIELD_REPAIR_REQUEST_ID = "repairRequestId"
        const val FIELD_AMOUNT = "amount"
        const val FIELD_METHOD = "method"
        const val FIELD_STATUS = "status"
        const val FIELD_RECEIPT_ID = "receiptId"
        const val FIELD_CREATED_AT = "createdAt"
    }
}
