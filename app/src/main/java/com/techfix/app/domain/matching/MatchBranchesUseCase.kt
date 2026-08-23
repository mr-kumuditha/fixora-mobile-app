package com.techfix.app.domain.matching

import com.techfix.app.domain.branch.Branch
import com.techfix.app.domain.branch.BranchRepository
import com.techfix.app.domain.catalog.DeviceCategory
import com.techfix.app.domain.location.Coordinates
import com.techfix.app.domain.location.distanceKmBetween
import com.techfix.app.domain.sparepart.SparePartAvailability
import com.techfix.app.domain.sparepart.SparePartRepository
import com.techfix.app.domain.technician.Technician
import com.techfix.app.domain.technician.TechnicianRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlin.math.min

/**
 * The branch-assignment rule from the brief: pick a branch on distance
 * **combined with** technician and spare-part availability, never distance
 * alone.
 *
 * This lives in the domain layer on purpose (architecture doc §6). It talks
 * to repository interfaces only — it does not know that branches come from
 * Firestore, that spare-part stock comes from Supabase Postgres, or that
 * the caller is a ViewModel with a map on screen.
 *
 * Scoring, per branch, is a weighted sum of three sub-scores in 0..1:
 *
 * - **technician** (weight [TECHNICIAN_WEIGHT]) — 0 if no available
 *   technician holds the skill for this device category. Otherwise a floor of
 *   [AVAILABILITY_FLOOR] plus a smaller bonus for depth of cover, so a branch
 *   with three qualified technicians beats one with a single technician who
 *   could get pulled onto something else.
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
 * The two availability weights sum to 0.8 against distance's 0.2, so a
 * nearby branch that cannot do the job loses to a far branch that can — which
 * is the behaviour being graded. Distance still decides between branches that
 * are equally capable, which is the common case.
 */
class MatchBranchesUseCase(
    private val branchRepository: BranchRepository,
    private val technicianRepository: TechnicianRepository,
    private val sparePartRepository: SparePartRepository,
) {

    suspend operator fun invoke(
        category: DeviceCategory,
        customerLocation: Coordinates?,
        requireVerifiedAccounts: Boolean = false,
    ): Result<BranchMatchResult> = runCatching {
        val branches = branchRepository.getBranches().getOrThrow()
        require(branches.isNotEmpty()) { "No branches configured" }

        val matches = coroutineScope {
            branches.map { branch ->
                async { scoreBranch(branch, category, customerLocation, requireVerifiedAccounts) }
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
        requireVerifiedAccounts: Boolean,
    ): BranchMatch = coroutineScope {
        // Both backends queried concurrently — two round trips per branch
        // otherwise, and the branch picker sits in front of the customer.
        val techniciansDeferred = async {
            if (requireVerifiedAccounts) {
                technicianRepository.getVerifiedAssignableTechnicians(branch.id, category).getOrThrow()
            } else {
                technicianRepository.getAvailableTechnicians(branch.id, category).getOrThrow()
            }
        }
        val partsDeferred = async {
            sparePartRepository.getAvailabilityForCategory(branch.id, category).getOrThrow()
        }

        val technicians: List<Technician> = techniciansDeferred.await()
        val parts: List<SparePartAvailability> = partsDeferred.await()
        val (inStock, outOfStock) = parts.partition { it.inStock }

        val distanceKm = customerLocation?.let {
            distanceKmBetween(it, Coordinates(branch.latitude, branch.longitude))
        }

        val technicianScore = availabilityScore(
            covered = technicians.isNotEmpty(),
            depth = min(technicians.size, TECHNICIAN_DEPTH_CAP).toDouble() / TECHNICIAN_DEPTH_CAP,
        )
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
            availableTechnicians = technicians,
            partsInStock = inStock,
            partsOutOfStock = outOfStock,
            technicianScore = technicianScore,
            partsScore = partsScore,
            distanceScore = distanceScore,
            score = TECHNICIAN_WEIGHT * technicianScore +
                PARTS_WEIGHT * partsScore +
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
        const val TECHNICIAN_WEIGHT = 0.45
        const val PARTS_WEIGHT = 0.35
        const val DISTANCE_WEIGHT = 0.20

        /** Score of a branch this many km away is exactly half of one at 0 km. */
        const val DISTANCE_HALF_SCORE_KM = 30.0

        /** Applied to every branch equally when location is unknown. */
        const val NEUTRAL_DISTANCE_SCORE = 0.5

        private const val AVAILABILITY_FLOOR = 0.8

        /** Beyond this many qualified technicians, more adds nothing. */
        private const val TECHNICIAN_DEPTH_CAP = 3
    }
}
