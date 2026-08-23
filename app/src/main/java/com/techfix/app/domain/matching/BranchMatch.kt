package com.techfix.app.domain.matching

import com.techfix.app.domain.branch.Branch
import com.techfix.app.domain.sparepart.SparePartAvailability

/**
 * One branch scored against a repair request. Carries the inputs that
 * produced the score, not just the number, so the branch card can explain
 * *why* a branch was recommended rather than showing an opaque ranking.
 */
data class BranchMatch(
    val branch: Branch,
    /** Null when the customer's location is unknown — not zero. */
    val distanceKm: Double?,
    val partsInStock: List<SparePartAvailability>,
    val partsOutOfStock: List<SparePartAvailability>,
    val partsScore: Double,
    val distanceScore: Double,
    val score: Double,
) {
    val hasParts: Boolean get() = partsInStock.isNotEmpty()

    val canHandleNow: Boolean get() = hasParts

    val totalPartsTracked: Int get() = partsInStock.size + partsOutOfStock.size
}

/**
 * The full result of matching, in rank order.
 *
 * [allBranchesBlocked] covers the case the architecture doc calls out: if no
 * branch can currently handle the job, the flow still offers the best-ranked
 * branch with a wait rather than dead-ending the customer.
 */
data class BranchMatchResult(
    val matches: List<BranchMatch>,
    val locationKnown: Boolean,
) {
    val recommended: BranchMatch? get() = matches.firstOrNull()

    val allBranchesBlocked: Boolean
        get() = matches.isNotEmpty() && matches.none { it.canHandleNow }
}
