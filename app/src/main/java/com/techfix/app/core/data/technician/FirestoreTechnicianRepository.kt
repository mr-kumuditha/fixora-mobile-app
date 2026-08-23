package com.techfix.app.core.data.technician

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.techfix.app.core.data.FirestoreCollections
import com.techfix.app.domain.catalog.DeviceCategory
import com.techfix.app.domain.technician.Technician
import com.techfix.app.domain.technician.TechnicianRepository
import com.techfix.app.domain.technician.hasValidAccountLink
import com.techfix.app.domain.technician.isEligibleForAssignment
import com.techfix.app.core.navigation.UserRole
import kotlinx.coroutines.tasks.await

/**
 * Firestore-backed technician roster. Firebase Authentication identifies the
 * caller and Firestore Security Rules authorize ADMIN writes from the trusted
 * users/{uid}.role document; this repository never sends an application role.
 */
class FirestoreTechnicianRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : TechnicianRepository {

    private val collection
        get() = firestore.collection(FirestoreCollections.TECHNICIANS)

    override suspend fun getTechnicians(): Result<List<Technician>> = runCatching {
        collection.get().await().documents
            .map { it.toTechnician() }
            .filter { it.active }
            .sortedBy { it.name }
    }

    override suspend fun getAllTechniciansIncludingArchived(): Result<List<Technician>> = runCatching {
        collection.get().await().documents
            .map { it.toTechnician() }
            .sortedWith(compareByDescending<Technician> { it.active }.thenBy { it.name })
    }

    override suspend fun getTechniciansForBranch(
        branchId: String,
    ): Result<List<Technician>> = runCatching {
        validateBranch(branchId)
        collection.whereEqualTo(FIELD_BRANCH_ID, branchId)
            .get()
            .await()
            .documents
            .map { it.toTechnician() }
            .filter { it.active }
            .sortedBy { it.name }
    }

    override suspend fun getAvailableTechnicians(
        branchId: String,
        category: DeviceCategory,
    ): Result<List<Technician>> = runCatching {
        getTechniciansForBranch(branchId).getOrThrow()
            .filter { it.isEligibleForAssignment(branchId, category) }
    }

    override suspend fun getVerifiedAssignableTechnicians(
        branchId: String,
        category: DeviceCategory,
    ): Result<List<Technician>> = runCatching {
        getAvailableTechnicians(branchId, category).getOrThrow().mapNotNull { candidate ->
            verifyAssignmentCandidate(candidate.id, branchId, category).getOrNull()
        }
    }

    override suspend fun verifyAssignmentCandidate(
        technicianId: String,
        branchId: String,
        category: DeviceCategory,
    ): Result<Technician> = runCatching {
        require(technicianId.isNotBlank()) { "Technician assignment is missing" }
        validateBranch(branchId)

        val technician = collection.document(technicianId)
            .get(Source.SERVER)
            .await()
            .toTechnician()
        check(technician.isEligibleForAssignment(branchId, category)) {
            "Technician is archived, unavailable, unlinked, in another branch, or missing the required skill"
        }

        val linkedUserId = technician.linkedUserId
            ?: error("Technician account is not linked")
        val linkedUser = firestore.collection(FirestoreCollections.USERS)
            .document(linkedUserId)
            .get(Source.SERVER)
            .await()
        check(linkedUser.exists()) { "Linked technician account does not exist" }
        val linkedRole = runCatching {
            UserRole.valueOf(linkedUser.getString(FIELD_ROLE).orEmpty())
        }.getOrNull()
        check(
            technician.hasValidAccountLink(
                userUid = linkedUser.id,
                userRole = linkedRole,
                userTechnicianId = linkedUser.getString(FIELD_TECHNICIAN_ID),
                userBranchId = linkedUser.getString(FIELD_BRANCH_ID),
            )
        ) {
            "Technician account role, id, or branch link does not match"
        }
        technician
    }

    override suspend fun createTechnician(
        name: String,
        branchId: String,
        categorySkills: List<DeviceCategory>,
        available: Boolean,
    ): Result<Unit> = runCatching {
        validate(name, branchId, categorySkills)
        val document = collection.document()
        val expected = Technician(
            id = document.id,
            name = name.trim(),
            branchId = branchId,
            categorySkills = categorySkills,
            available = available,
            active = true,
            linkedUserId = null,
            archivedAt = null,
        )

        document.set(
            expected.toFirestoreMap(
                createdAt = FieldValue.serverTimestamp(),
                updatedAt = FieldValue.serverTimestamp(),
            )
        ).await()

        val persisted = document.get(Source.SERVER).await().toTechnician()
        requirePersistedFirestoreTechnician(persisted, expected)
    }

    override suspend fun updateTechnician(
        id: String,
        name: String,
        branchId: String,
        categorySkills: List<DeviceCategory>,
        available: Boolean,
    ): Result<Unit> = runCatching {
        require(id.isNotBlank()) { "Technician id is required" }
        validate(name, branchId, categorySkills)
        val document = collection.document(id)
        val expected = firestore.runTransaction { transaction ->
            val existing = transaction.get(document).toTechnician()
            val resolved = existing.copy(
                name = name.trim(),
                branchId = branchId,
                categorySkills = categorySkills,
                available = available,
            )
            val linkedUser = existing.linkedUserId?.let { linkedUid ->
                firestore.collection(FirestoreCollections.USERS).document(linkedUid)
            }
            val linkedSnapshot = linkedUser?.let(transaction::get)
            if (linkedSnapshot != null) {
                check(linkedSnapshot.exists()) { "Linked technician account does not exist" }
                check(linkedSnapshot.getString(FIELD_ROLE) == UserRole.TECHNICIAN.name) {
                    "Linked account is not a technician"
                }
                check(linkedSnapshot.getString(FIELD_TECHNICIAN_ID) == id) {
                    "Linked account points to another technician"
                }
            }

            transaction.update(
                document,
                mapOf(
                    FIELD_NAME to resolved.name,
                    FIELD_BRANCH_ID to resolved.branchId,
                    FIELD_CATEGORY_SKILLS to resolved.categorySkills.map { it.name },
                    FIELD_AVAILABLE to resolved.available,
                    FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
                ),
            )
            if (linkedUser != null && linkedSnapshot?.getString(FIELD_BRANCH_ID) != branchId) {
                transaction.update(
                    linkedUser,
                    mapOf(
                        FIELD_BRANCH_ID to branchId,
                        FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
                    ),
                )
            }
            resolved
        }.await()

        val persisted = document.get(Source.SERVER).await().toTechnician()
        requirePersistedFirestoreTechnician(persisted, expected)
        expected.linkedUserId?.let { linkedUid ->
            val linkedUser = firestore.collection(FirestoreCollections.USERS)
                .document(linkedUid)
                .get(Source.SERVER)
                .await()
            check(linkedUser.getString(FIELD_BRANCH_ID) == expected.branchId) {
                "Firestore returned a different linked-account branch"
            }
        }
    }

    override suspend fun deleteTechnician(id: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Archive technicians instead of deleting them"))

    override suspend fun archiveTechnician(id: String): Result<Unit> = runCatching {
        require(id.isNotBlank()) { "Technician id is required" }
        val document = collection.document(id)
        document.update(
            mapOf(
                FIELD_ACTIVE to false,
                FIELD_AVAILABLE to false,
                FIELD_ARCHIVED_AT to FieldValue.serverTimestamp(),
                FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
            )
        ).await()
        val persisted = document.get(Source.SERVER).await().toTechnician()
        check(!persisted.active && !persisted.available && persisted.archivedAt != null) {
            "Firestore did not confirm technician archival"
        }
    }

    private fun Technician.toFirestoreMap(
        createdAt: Any,
        updatedAt: Any,
    ): Map<String, Any?> = mapOf(
        FIELD_ID to id,
        FIELD_NAME to name,
        FIELD_BRANCH_ID to branchId,
        FIELD_CATEGORY_SKILLS to categorySkills.map { it.name },
        FIELD_AVAILABLE to available,
        FIELD_ACTIVE to active,
        FIELD_LINKED_USER_ID to linkedUserId,
        FIELD_ARCHIVED_AT to archivedAt?.let { com.google.firebase.Timestamp(it / 1000, 0) },
        FIELD_CREATED_AT to createdAt,
        FIELD_UPDATED_AT to updatedAt,
    )

    private fun DocumentSnapshot.toTechnician(): Technician {
        check(exists()) { "Technician $id not found" }
        val storedId = getString(FIELD_ID) ?: error("Technician $id has no id")
        check(storedId == id) { "Technician document id does not match its stored id" }
        val rawSkills = (get(FIELD_CATEGORY_SKILLS) as? List<*>)
            ?.map { it as? String ?: error("Technician $id has an invalid skill") }
            ?: error("Technician $id has no skills")
        val skills = rawSkills.map { raw ->
            DeviceCategory.fromRaw(raw) ?: error("Technician $id has unknown skill $raw")
        }

        return Technician(
            id = storedId,
            name = getString(FIELD_NAME) ?: error("Technician $id has no name"),
            branchId = getString(FIELD_BRANCH_ID) ?: error("Technician $id has no branch"),
            categorySkills = skills,
            available = getBoolean(FIELD_AVAILABLE) ?: error("Technician $id has no availability"),
            active = getBoolean(FIELD_ACTIVE) ?: true,
            linkedUserId = getString(FIELD_LINKED_USER_ID)?.takeIf { it.isNotBlank() },
            archivedAt = getTimestamp(FIELD_ARCHIVED_AT)?.toDate()?.time,
        )
    }

    private fun validate(
        name: String,
        branchId: String,
        skills: List<DeviceCategory>,
    ) {
        require(name.trim().isNotBlank()) { "Name is required" }
        validateBranch(branchId)
        require(skills.isNotEmpty()) { "Select at least one skill" }
    }

    private fun validateBranch(branchId: String) {
        require(branchId in VALID_BRANCH_IDS) { "Pick a valid branch" }
    }

    private companion object {
        val VALID_BRANCH_IDS = setOf("colombo", "galle")
        const val FIELD_ID = "id"
        const val FIELD_NAME = "name"
        const val FIELD_BRANCH_ID = "branchId"
        const val FIELD_CATEGORY_SKILLS = "categorySkills"
        const val FIELD_AVAILABLE = "available"
        const val FIELD_ACTIVE = "active"
        const val FIELD_LINKED_USER_ID = "linkedUserId"
        const val FIELD_ARCHIVED_AT = "archivedAt"
        const val FIELD_CREATED_AT = "createdAt"
        const val FIELD_UPDATED_AT = "updatedAt"
        const val FIELD_ROLE = "role"
        const val FIELD_TECHNICIAN_ID = "technicianId"
    }
}

/** Require the server-confirmed document to match every editable field. */
internal fun requirePersistedFirestoreTechnician(
    actual: Technician,
    expected: Technician,
) {
    check(actual.id == expected.id) { "Firestore returned a different technician id" }
    check(actual.name == expected.name.trim()) { "Firestore returned a different technician name" }
    check(actual.branchId == expected.branchId) { "Firestore returned a different technician branch" }
    check(actual.categorySkills.toSet() == expected.categorySkills.toSet()) {
        "Firestore returned different technician skills"
    }
    check(actual.available == expected.available) {
        "Firestore returned a different technician availability"
    }
    check(actual.active == expected.active) { "Firestore returned a different technician active state" }
    check(actual.linkedUserId == expected.linkedUserId) {
        "Firestore returned a different technician account link"
    }
}
