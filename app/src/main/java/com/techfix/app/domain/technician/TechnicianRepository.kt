package com.techfix.app.domain.technician

import com.techfix.app.domain.catalog.DeviceCategory

interface TechnicianRepository {
    /** Active roster only. Archived records remain in Firestore for historical references. */
    suspend fun getTechnicians(): Result<List<Technician>>

    /** Active roster for one branch only. */
    suspend fun getTechniciansForBranch(branchId: String): Result<List<Technician>>

    /** Admin audit/management read including safely archived records. */
    suspend fun getAllTechniciansIncludingArchived(): Result<List<Technician>> = getTechnicians()

    /**
     * Available technicians at one branch who can handle one category — half
     * of the branch-matching input in Block 5 (the other half is part stock).
     */
    suspend fun getAvailableTechnicians(
        branchId: String,
        category: DeviceCategory,
    ): Result<List<Technician>>

    /**
     * Staff assignment candidates with a server-verified Firebase account link.
     * This is deliberately stricter than customer-side branch capacity scoring.
     */
    suspend fun getVerifiedAssignableTechnicians(
        branchId: String,
        category: DeviceCategory,
    ): Result<List<Technician>> = getAvailableTechnicians(branchId, category)

    /** Re-check one selected technician and its linked user from Firestore server data. */
    suspend fun verifyAssignmentCandidate(
        technicianId: String,
        branchId: String,
        category: DeviceCategory,
    ): Result<Technician> = getAvailableTechnicians(branchId, category).mapCatching { candidates ->
        candidates.firstOrNull { it.id == technicianId }
            ?: error("Technician is not eligible for this repair")
    }

    suspend fun createTechnician(
        name: String,
        branchId: String,
        categorySkills: List<DeviceCategory>,
        available: Boolean,
    ): Result<Unit> = Result.failure(UnsupportedOperationException("Technician creation is not supported by this repository"))

    suspend fun updateTechnician(
        id: String,
        name: String,
        branchId: String,
        categorySkills: List<DeviceCategory>,
        available: Boolean,
    ): Result<Unit> = Result.failure(UnsupportedOperationException("Technician updates are not supported by this repository"))

    suspend fun deleteTechnician(id: String): Result<Unit> = Result.failure(UnsupportedOperationException("Technician deletion is not supported by this repository"))

    /** Safe replacement for deletion: preserves ids referenced by repair history. */
    suspend fun archiveTechnician(id: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Technician archival is not supported by this repository"))
}
