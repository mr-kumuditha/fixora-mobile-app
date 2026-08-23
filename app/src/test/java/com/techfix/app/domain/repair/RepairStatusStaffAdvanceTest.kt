package com.techfix.app.domain.repair

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The staff status-advance rule (Block 7). These are the transitions the
 * Appointment Detail screen's one button offers, so the boundaries matter:
 * an appointment can't be advanced before it is assigned, and staff can never
 * mark a repair COMPLETED — only a successful payment does that.
 */
class RepairStatusStaffAdvanceTest {

    @Test
    fun `staff advance walks the brief's six stages in order`() {
        val walked = generateSequence(RepairStatus.CONFIRMED) { it.nextStaffStage }
            .takeWhile { true }
            .take(7)
            .toList()

        assertEquals(
            listOf(
                RepairStatus.CONFIRMED,
                RepairStatus.RECEIVED,
                RepairStatus.DIAGNOSIS,
                RepairStatus.APPROVED,
                RepairStatus.IN_PROGRESS,
                RepairStatus.QUALITY_CHECK,
                RepairStatus.READY_FOR_PICKUP,
            ),
            walked,
        )
    }

    @Test
    fun `a submitted request cannot be advanced before it is assigned`() {
        assertNull(RepairStatus.SUBMITTED.nextStaffStage)
    }

    @Test
    fun `staff cannot complete a repair - only payment does that`() {
        assertNull(RepairStatus.READY_FOR_PICKUP.nextStaffStage)
    }

    @Test
    fun `terminal states have nowhere to go`() {
        assertNull(RepairStatus.COMPLETED.nextStaffStage)
        assertNull(RepairStatus.CANCELLED.nextStaffStage)
    }

    @Test
    fun `a repair held for parts resumes at in progress rather than dead-ending`() {
        assertEquals(RepairStatus.IN_PROGRESS, RepairStatus.AWAITING_PARTS.nextStaffStage)
    }

    // ---- assignment ------------------------------------------------------
    //
    // Assigning a technician confirms a new booking, but reassigning one on a
    // repair already under way must not drag the customer's live timeline
    // backwards. That regression is what these pin down.

    @Test
    fun `assigning a submitted booking confirms it`() {
        assertEquals(RepairStatus.CONFIRMED, RepairStatus.SUBMITTED.afterAssignment)
    }

    @Test
    fun `reassigning a repair in progress leaves its status alone`() {
        listOf(
            RepairStatus.CONFIRMED,
            RepairStatus.RECEIVED,
            RepairStatus.DIAGNOSIS,
            RepairStatus.APPROVED,
            RepairStatus.IN_PROGRESS,
            RepairStatus.QUALITY_CHECK,
            RepairStatus.READY_FOR_PICKUP,
        ).forEach { status ->
            assertEquals("$status must not move on reassignment", status, status.afterAssignment)
        }
    }

    @Test
    fun `reassignment never moves a finished or held repair either`() {
        assertEquals(RepairStatus.COMPLETED, RepairStatus.COMPLETED.afterAssignment)
        assertEquals(RepairStatus.CANCELLED, RepairStatus.CANCELLED.afterAssignment)
        assertEquals(RepairStatus.AWAITING_PARTS, RepairStatus.AWAITING_PARTS.afterAssignment)
    }

    @Test
    fun `submitted is the only status assignment changes at all`() {
        val moved = RepairStatus.entries.filter { it.afterAssignment != it }
        assertEquals(listOf(RepairStatus.SUBMITTED), moved)
    }
}
