package com.techfix.app.domain.matching

import com.techfix.app.domain.branch.Branch
import com.techfix.app.domain.branch.BranchRepository
import com.techfix.app.domain.catalog.DeviceCategory
import com.techfix.app.domain.location.Coordinates
import com.techfix.app.domain.location.distanceKmBetween
import com.techfix.app.domain.sparepart.SparePartAvailability
import com.techfix.app.domain.sparepart.SparePartRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * The branch-assignment rule from the brief: pick a branch on distance
 * combined with spare-part availability, never distance alone.
 *
 * This lives in the domain layer on purpose (architecture doc §6). It talks
 * to repository interfaces only — it does not know that branches come from
 * Firestore, that spare-part stock comes from Supabase Postgres, or that
 * the caller is a ViewModel with a map on screen.
 *
 * Scoring, per branch, is a weighted sum of two sub-scores in 0..1:
 * - **parts** (weight [PARTS_WEIGHT]) — 0 if not one compatible part is in
 *   stock. Otherwise the same floor plus a bonus for the fraction of the
 *   compatible catalogue actually on the shelf, because the exact part needed
 *   isn't known until a technician diagnoses the device — breadth of stock is
 *   the best proxy available at booking time.
 * - **distance** (weight [DISTANCE_WEIGHT]) — a smooth decay,
 *   `1 / (1 + km / DISTANCE_HALF_SCORE_KM)`, so it never hard-cuts at a
 *   threshold. When the customer's location is unknown it is held at
 *   [NEUTRAL_DISTANCE_SCORE] for every branch, which leaves availability to
 *   decide rather than silently pretending distance is zero.
 *
 * Parts availability has the larger weight so a nearby branch without useful
 * stock loses to a branch that is currently better prepared.
 */
class MatchBranchesUseCase(
    private val branchRepository: BranchRepository,
    private val sparePartRepository: SparePartRepository,
) {

    suspend operator fun invoke(
        category: DeviceCategory,
        customerLocation: Coordinates?,
    ): Result<BranchMatchResult> = runCatching {
        val branches = branchRepository.getBranches().getOrThrow()
        require(branches.isNotEmpty()) { "No branches configured" }

        val matches = coroutineScope {
            branches.map { branch ->
                async { scoreBranch(branch, category, customerLocation) }
            }.map { it.await() }
        }

        BranchMatchResult(
            // Ties broken by distance so the order is stable and sensible
            // when two branches are equally capable but location is known.
            matches = matches.sortedWith(
                compareByDescending<BranchMatch> { it.score }
                    .thenBy { it.distanceKm ?: Double.MAX_VALUE }
                    .thenBy { it.branch.name },
            ),
            locationKnown = customerLocation != null,
        )
    }

    private suspend fun scoreBranch(
        branch: Branch,
        category: DeviceCategory,
        customerLocation: Coordinates?,
    ): BranchMatch = coroutineScope {
        val partsDeferred = async {
            sparePartRepository.getAvailabilityForCategory(branch.id, category).getOrThrow()
        }

        val parts: List<SparePartAvailability> = partsDeferred.await()
        val (inStock, outOfStock) = parts.partition { it.inStock }

        val distanceKm = customerLocation?.let {
            distanceKmBetween(it, Coordinates(branch.latitude, branch.longitude))
        }

        val partsScore = availabilityScore(
            covered = inStock.isNotEmpty(),
            depth = if (parts.isEmpty()) 0.0 else inStock.size.toDouble() / parts.size,
        )
        val distanceScore = when (distanceKm) {
            null -> NEUTRAL_DISTANCE_SCORE
            else -> 1.0 / (1.0 + distanceKm / DISTANCE_HALF_SCORE_KM)
        }

        BranchMatch(
            branch = branch,
            distanceKm = distanceKm,
            partsInStock = inStock,
            partsOutOfStock = outOfStock,
            partsScore = partsScore,
            distanceScore = distanceScore,
            score = PARTS_WEIGHT * partsScore +
                DISTANCE_WEIGHT * distanceScore,
        )
    }

    /**
     * Zero when the branch can't cover this at all, otherwise a high floor
     * plus a depth bonus. The floor is what makes availability a near-binary
     * gate: covering the job at all is worth far more than covering it well.
     */
    private fun availabilityScore(covered: Boolean, depth: Double): Double =
        if (!covered) 0.0 else AVAILABILITY_FLOOR + (1.0 - AVAILABILITY_FLOOR) * depth.coerceIn(0.0, 1.0)

    companion object {
        const val PARTS_WEIGHT = 0.75
        const val DISTANCE_WEIGHT = 0.25

        /** Score of a branch this many km away is exactly half of one at 0 km. */
        const val DISTANCE_HALF_SCORE_KM = 30.0

        /** Applied to every branch equally when location is unknown. */
        const val NEUTRAL_DISTANCE_SCORE = 0.5

        private const val AVAILABILITY_FLOOR = 0.8

    }
}
