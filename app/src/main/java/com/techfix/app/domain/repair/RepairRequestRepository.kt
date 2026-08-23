package com.techfix.app.domain.repair

import kotlinx.coroutines.flow.Flow

interface RepairRequestRepository {
    /** Returns the id of the created document. */
    suspend fun createRepairRequest(request: RepairRequest): Result<String>

    suspend fun getRepairRequest(requestId: String): Result<RepairRequest>

    /** Repair history for one customer, newest first. */
    suspend fun getRepairRequestsForCustomer(customerId: String): Result<List<RepairRequest>>

    /** Live status updates for the tracking screen (Block 6). */
    fun observeRepairRequest(requestId: String): Flow<RepairRequest>

    /**
     * Moves a repair to [status]. Reaching COMPLETED also stamps
     * `completedAt`, since that is the only point the repair is finished.
     */
    suspend fun updateStatus(requestId: String, status: RepairStatus): Result<Unit>
}
