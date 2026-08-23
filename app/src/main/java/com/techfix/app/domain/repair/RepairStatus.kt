package com.techfix.app.domain.repair

/**
 * Lifecycle of a repair request.
 *
 * The first nine entries are the tracking timeline, in the order the
 * customer sees them (Submitted → Confirmed → Received → Diagnosis →
 * Approved → In Progress → Quality Check → Ready → Completed).
 *
 * AWAITING_PARTS and CANCELLED sit outside that order: AWAITING_PARTS is a
 * hold that can happen while work is under way, so the timeline keeps
 * showing the In Progress stage and the screen flags the hold separately;
 * CANCELLED ends the request without reaching Completed.
 */
enum class RepairStatus(val isTimelineStage: Boolean) {
    SUBMITTED(true),
    CONFIRMED(true),
    RECEIVED(true),
    DIAGNOSIS(true),
    APPROVED(true),
    IN_PROGRESS(true),
    QUALITY_CHECK(true),
    READY_FOR_PICKUP(true),
    COMPLETED(true),

    AWAITING_PARTS(false),
    CANCELLED(false);

    /** COMPLETED and CANCELLED are the only states a repair never leaves. */
    val isTerminal: Boolean
        get() = this == COMPLETED || this == CANCELLED

    /**
     * Position on the nine-stage timeline. A hold maps onto the stage the
     * work is actually paused at, and CANCELLED maps to -1 because it is not
     * on the timeline at all.
     */
    val timelineIndex: Int
        get() = when (this) {
            AWAITING_PARTS -> timeline.indexOf(IN_PROGRESS)
            CANCELLED -> -1
            else -> timeline.indexOf(this)
        }

    /**
     * The stage a staff member can move this repair to next (Block 7), or
     * null if moving it on isn't theirs to do.
     *
     * The staff-advanced range is RECEIVED → DIAGNOSIS → APPROVED →
     * IN_PROGRESS → QUALITY_CHECK → READY, exactly as the brief specifies:
     *
     * - SUBMITTED has no next stage here — it needs a branch and a technician
     *   first, which is the assignment action, not an advance.
     * - READY_FOR_PICKUP has no next stage either: COMPLETED is reached only
     *   by the customer's payment succeeding, never by staff.
     * - AWAITING_PARTS resumes at IN_PROGRESS, so a repair put on hold is
     *   not a dead end.
     */
    val nextStaffStage: RepairStatus?
        get() = when (this) {
            CONFIRMED -> RECEIVED
            RECEIVED -> DIAGNOSIS
            DIAGNOSIS -> APPROVED
            APPROVED -> IN_PROGRESS
            IN_PROGRESS -> QUALITY_CHECK
            QUALITY_CHECK -> READY_FOR_PICKUP
            AWAITING_PARTS -> IN_PROGRESS
            SUBMITTED, READY_FOR_PICKUP, COMPLETED, CANCELLED -> null
        }

    /**
     * The status a repair holds after a Branch Manager assigns a technician.
     *
     * Assigning is what confirms a **new** booking, so SUBMITTED becomes
     * CONFIRMED. Every other status is returned unchanged: reassigning a
     * repair that is already under way must not drag the customer's timeline
     * backwards to Confirmed, which is what the customer is watching live on
     * the tracking screen.
     */
    val afterAssignment: RepairStatus
        get() = if (this == SUBMITTED) CONFIRMED else this

    companion object {
        /** The nine stages, in display order. */
        val timeline: List<RepairStatus> = entries.filter { it.isTimelineStage }

        /**
         * Status names written before the timeline was widened from seven
         * states to nine (Block 6). Documents already in Firestore still
         * carry these, so they keep resolving instead of silently falling
         * back to SUBMITTED.
         */
        private val legacyNames = mapOf(
            "PENDING" to SUBMITTED,
            "ASSIGNED" to CONFIRMED,
        )

        fun fromRaw(raw: String?): RepairStatus =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
                ?: legacyNames[raw?.uppercase()]
                ?: SUBMITTED
    }
}
