package com.techfix.app.core.data.branch

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.techfix.app.core.data.FirestoreCollections
import com.techfix.app.domain.branch.Branch
import com.techfix.app.domain.branch.BranchRepository
import kotlinx.coroutines.tasks.await

/**
 * Reads the two branch records from Firestore. Coordinates are stored as a
 * `location` map of lat/lng rather than a GeoPoint, so the same shape reads
 * back identically from the Firestore console, the REST API, and the SDK.
 */
class FirestoreBranchRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : BranchRepository {

    override suspend fun getBranches(): Result<List<Branch>> = runCatching {
        firestore.collection(FirestoreCollections.BRANCHES)
            .get()
            .await()
            .documents
            .mapNotNull { it.toBranch() }
            .sortedBy { it.name }
    }

    override suspend fun getBranch(branchId: String): Result<Branch> = runCatching {
        val snapshot = firestore.collection(FirestoreCollections.BRANCHES)
            .document(branchId)
            .get()
            .await()
        snapshot.toBranch() ?: error("Branch $branchId not found")
    }

    private fun DocumentSnapshot.toBranch(): Branch? {
        if (!exists()) return null
        val location = get(FIELD_LOCATION) as? Map<*, *> ?: return null
        val latitude = (location[FIELD_LAT] as? Number)?.toDouble() ?: return null
        val longitude = (location[FIELD_LNG] as? Number)?.toDouble() ?: return null
        return Branch(
            id = id,
            name = getString(FIELD_NAME).orEmpty(),
            latitude = latitude,
            longitude = longitude,
            address = getString(FIELD_ADDRESS).orEmpty(),
        )
    }

    private companion object {
        const val FIELD_NAME = "name"
        const val FIELD_ADDRESS = "address"
        const val FIELD_LOCATION = "location"
        const val FIELD_LAT = "lat"
        const val FIELD_LNG = "lng"
    }
}
