package com.techfix.app.core.data.catalog

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.techfix.app.core.data.FirestoreCollections
import com.techfix.app.domain.catalog.DeviceCategory
import com.techfix.app.domain.catalog.RepairService
import com.techfix.app.domain.catalog.ServiceRepository
import kotlinx.coroutines.tasks.await

/**
 * Reads the service catalog from the Firestore `services` collection. The
 * Room read cache added in Block 8 wraps this rather than replacing it.
 */
class FirestoreServiceRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : ServiceRepository {

    override suspend fun getServices(): Result<List<RepairService>> = runCatching {
        firestore.collection(FirestoreCollections.SERVICES)
            .get()
            .await()
            .documents
            .mapNotNull { it.toRepairService() }
            .sortedWith(compareBy({ it.category.name }, { it.name }))
    }

    override suspend fun getServicesByCategory(
        category: DeviceCategory,
    ): Result<List<RepairService>> = runCatching {
        firestore.collection(FirestoreCollections.SERVICES)
            .whereEqualTo(FIELD_CATEGORY, category.name)
            .get()
            .await()
            .documents
            .mapNotNull { it.toRepairService() }
            .sortedBy { it.name }
    }

    override suspend fun getService(serviceId: String): Result<RepairService> = runCatching {
        val snapshot = firestore.collection(FirestoreCollections.SERVICES)
            .document(serviceId)
            .get()
            .await()
        snapshot.toRepairService() ?: error("Service $serviceId not found")
    }

    private fun DocumentSnapshot.toRepairService(): RepairService? {
        if (!exists()) return null
        val category = DeviceCategory.fromRaw(getString(FIELD_CATEGORY)) ?: return null
        return RepairService(
            id = id,
            category = category,
            name = getString(FIELD_NAME).orEmpty(),
            description = getString(FIELD_DESCRIPTION).orEmpty(),
            basePrice = getDouble(FIELD_BASE_PRICE) ?: 0.0,
        )
    }

    private companion object {
        const val FIELD_CATEGORY = "category"
        const val FIELD_NAME = "name"
        const val FIELD_DESCRIPTION = "description"
        const val FIELD_BASE_PRICE = "basePrice"
    }
}
