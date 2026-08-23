package com.techfix.app.domain.repair

import kotlinx.coroutines.flow.Flow

interface RepairRequestRepository {
    /** Returns the id of the created document. */
    suspend fun createRepairRequest(request: RepairRequest): Result<String>

    suspend fun getRepairRequest(requestId: String): Result<RepairRequest>

    /** Repair history for one customer, newest first. */
    suspend fun getRepairRequestsForCustomer(customerId: String): Result<List<RepairRequest>>

    /** Staff-side queue for one branch, newest first. */
    suspend fun getRepairRequestsForBranch(branchId: String): Result<List<RepairRequest>>

    /** Strict technician scope used by both UI and Firestore rules. */
    suspend fun getRepairRequestsForTechnician(technicianId: String): Result<List<RepairRequest>>

    /**
     * Every repair request, newest first — the staff queue for an Admin, who
     * is not scoped to a single branch.
     *
     * Deliberately not a `whereEqualTo(status, ...)` query: the staff screens
     * slice the same list several ways (new / active / assigned to me), and
     * each server-side filter would need its own composite index for a
     * dataset this small. One ordered read, filtered in the ViewModel.
     */
    suspend fun getAllRepairRequests(): Result<List<RepairRequest>>

    /** Live status updates for the tracking screen (Block 6). */
    fun observeRepairRequest(requestId: String): Flow<RepairRequest>

    /**
     * Moves a repair to [status]. Reaching COMPLETED also stamps
     * `completedAt`, since that is the only point the repair is finished.
     */
    suspend fun updateStatus(requestId: String, status: RepairStatus): Result<Unit>

    /**
     * Branch Manager / Admin confirming an appointment: the branch is
     * confirmed (it can differ from the one the customer's booking matched)
     * and a technician named.
     *
     * The status move follows [RepairStatus.afterAssignment] — a SUBMITTED
     * booking becomes CONFIRMED, while a repair already past that keeps the
     * status it has, so reassigning mid-repair changes only the technician.
     *
     * Returns the status the repair actually holds afterwards, read inside
     * the same transaction as the write, so the caller never has to guess.
     */
    suspend fun assignTechnician(
        requestId: String,
        branchId: String,
        technicianId: String,
    ): Result<RepairStatus>
}
