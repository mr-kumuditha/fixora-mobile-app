package com.techfix.app.domain.sparepart

import com.techfix.app.domain.catalog.DeviceCategory

interface SparePartRepository {
    suspend fun getSpareParts(): Result<List<SparePart>>

    suspend fun getStockForBranch(branchId: String): Result<List<SparePartStock>>

    /**
     * Parts that fit `category`, with their quantity at `branchId` — the
     * spare-part half of the branch-matching input in Block 5. Parts with
     * no stock row for the branch come back with quantity 0 rather than
     * being dropped, so the caller can tell "out of stock" from "not a part
     * we carry".
     */
    suspend fun getAvailabilityForCategory(
        branchId: String,
        category: DeviceCategory,
    ): Result<List<SparePartAvailability>>

    /**
     * Staff-side stock correction (Block 7). Upserts on (part, branch) rather
     * than updating, because a part that has never been stocked at a branch
     * has no `spare_part_stock` row at all — the read path reports that as
     * quantity 0, so the write path has to be able to create it.
     */
    suspend fun updateStock(partId: String, branchId: String, quantity: Int): Result<Unit>
}
