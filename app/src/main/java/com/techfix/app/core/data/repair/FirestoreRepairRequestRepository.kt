package com.techfix.app.core.data.repair

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.techfix.app.core.data.FirestoreCollections
import com.techfix.app.domain.catalog.DeviceCategory
import com.techfix.app.domain.repair.DeviceDetails
import com.techfix.app.domain.repair.RepairRequest
import com.techfix.app.domain.repair.RepairRequestRepository
import com.techfix.app.domain.repair.RepairStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.tasks.await

/**
 * Repair requests in the Firestore `repairRequests` collection.
 * `createdAt` is written with a server timestamp so ordering doesn't depend
 * on the device clock; the exposed model carries it back as epoch millis.
 */
class FirestoreRepairRequestRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : RepairRequestRepository {

    private val collection get() = firestore.collection(FirestoreCollections.REPAIR_REQUESTS)

    override suspend fun createRepairRequest(request: RepairRequest): Result<String> = runCatching {
        val document = if (request.id.isBlank()) collection.document() else collection.document(request.id)
        document.set(request.toFirestoreMap()).await()
        document.id
    }

    override suspend fun getRepairRequest(requestId: String): Result<RepairRequest> = runCatching {
        collection.document(requestId).get().await().toRepairRequest()
            ?: error("Repair request $requestId not found")
    }

    override suspend fun getRepairRequestsForCustomer(
        customerId: String,
    ): Result<List<RepairRequest>> = runCatching {
        collection
            .whereEqualTo(FIELD_CUSTOMER_ID, customerId)
            .orderBy(FIELD_CREATED_AT, Query.Direction.DESCENDING)
            .get()
            .await()
            .documents
            .mapNotNull { it.toRepairRequest() }
    }

    /**
     * Live status for the tracking screen.
     *
     * A snapshot listener that reports an error is spent — its registration is
     * dropped and nothing further arrives on it. Closing the flow there would
     * end the stream for good, leaving the tracking screen showing whichever
     * status happened to arrive last with no further updates, so the retry is
     * not a nicety: it is what makes this "live" at all across a repair that
     * takes minutes to move through its stages.
     *
     * [retryWhen] re-runs the whole builder, which registers a *new* listener;
     * Firestore then delivers the current document immediately, so the screen
     * catches up on anything it missed while disconnected. Terminal failures
     * are rethrown so the UI can show an error rather than retry forever.
     */
    override fun observeRepairRequest(requestId: String): Flow<RepairRequest> = callbackFlow {
        val registration = collection.document(requestId).addSnapshotListener { snapshot, error ->
            when {
                error != null -> close(error)
                else -> snapshot?.toRepairRequest()?.let { trySend(it) }
            }
        }
        awaitClose { registration.remove() }
    }.retryWhen { cause, attempt ->
        if (!SnapshotListenerRetry.isRecoverable(cause)) {
            false
        } else {
            delay(SnapshotListenerRetry.backoffMillis(attempt))
            true
        }
    }

    override suspend fun updateStatus(requestId: String, status: RepairStatus): Result<Unit> =
        runCatching {
            // COMPLETED is the only terminal-by-success state, and it is only
            // reached through the payment flow, so it is also the only place
            // completedAt is written. Server timestamp for the same reason
            // createdAt uses one — the device clock isn't trusted for ordering.
            val fields = buildMap<String, Any> {
                put(FIELD_STATUS, status.name)
                if (status == RepairStatus.COMPLETED) {
                    put(FIELD_COMPLETED_AT, FieldValue.serverTimestamp())
                }
            }
            collection.document(requestId).update(fields).await()
            Unit
        }

    private fun RepairRequest.toFirestoreMap(): Map<String, Any?> = mapOf(
        FIELD_CUSTOMER_ID to customerId,
        FIELD_SERVICE_ID to serviceId,
        FIELD_DEVICE_DETAILS to mapOf(
            FIELD_DEVICE_CATEGORY to deviceDetails.category.name,
            FIELD_DEVICE_BRAND to deviceDetails.brand,
            FIELD_DEVICE_MODEL to deviceDetails.model,
            FIELD_DEVICE_SERIAL to deviceDetails.serialNumber,
        ),
        FIELD_ISSUE_DESCRIPTION to issueDescription,
        FIELD_IMAGE_URLS to imageUrls,
        FIELD_BRANCH_ID to branchId,
        FIELD_TECHNICIAN_ID to technicianId,
        FIELD_STATUS to status.name,
        FIELD_CREATED_AT to (createdAt?.let { Timestamp(it / 1000, 0) } ?: FieldValue.serverTimestamp()),
        FIELD_SCHEDULED_AT to scheduledAt?.let { Timestamp(it / 1000, 0) },
        FIELD_COMPLETED_AT to completedAt?.let { Timestamp(it / 1000, 0) },
    )

    private fun DocumentSnapshot.toRepairRequest(): RepairRequest? {
        if (!exists()) return null
        val device = get(FIELD_DEVICE_DETAILS) as? Map<*, *> ?: return null
        val category = DeviceCategory.fromRaw(device[FIELD_DEVICE_CATEGORY] as? String) ?: return null
        return RepairRequest(
            id = id,
            customerId = getString(FIELD_CUSTOMER_ID).orEmpty(),
            serviceId = getString(FIELD_SERVICE_ID).orEmpty(),
            deviceDetails = DeviceDetails(
                category = category,
                brand = device[FIELD_DEVICE_BRAND] as? String ?: "",
                model = device[FIELD_DEVICE_MODEL] as? String ?: "",
                serialNumber = device[FIELD_DEVICE_SERIAL] as? String,
            ),
            issueDescription = getString(FIELD_ISSUE_DESCRIPTION).orEmpty(),
            imageUrls = (get(FIELD_IMAGE_URLS) as? List<*>)?.filterIsInstance<String>().orEmpty(),
            branchId = getString(FIELD_BRANCH_ID).orEmpty(),
            technicianId = getString(FIELD_TECHNICIAN_ID),
            status = RepairStatus.fromRaw(getString(FIELD_STATUS)),
            createdAt = getTimestamp(FIELD_CREATED_AT)?.toDate()?.time,
            scheduledAt = getTimestamp(FIELD_SCHEDULED_AT)?.toDate()?.time,
            completedAt = getTimestamp(FIELD_COMPLETED_AT)?.toDate()?.time,
        )
    }

    private companion object {
        const val FIELD_CUSTOMER_ID = "customerId"
        const val FIELD_SERVICE_ID = "serviceId"
        const val FIELD_DEVICE_DETAILS = "deviceDetails"
        const val FIELD_DEVICE_CATEGORY = "category"
        const val FIELD_DEVICE_BRAND = "brand"
        const val FIELD_DEVICE_MODEL = "model"
        const val FIELD_DEVICE_SERIAL = "serialNumber"
        const val FIELD_ISSUE_DESCRIPTION = "issueDescription"
        const val FIELD_IMAGE_URLS = "imageUrls"
        const val FIELD_BRANCH_ID = "branchId"
        const val FIELD_TECHNICIAN_ID = "technicianId"
        const val FIELD_STATUS = "status"
        const val FIELD_CREATED_AT = "createdAt"
        const val FIELD_SCHEDULED_AT = "scheduledAt"
        const val FIELD_COMPLETED_AT = "completedAt"
    }
}
