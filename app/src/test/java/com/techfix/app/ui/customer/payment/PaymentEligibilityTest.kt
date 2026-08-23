package com.techfix.app.ui.customer.payment

import com.techfix.app.domain.repair.RepairStatus
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two holes the Block 7 audit found, pinned down: a payment must never
 * proceed on an unknown price, and must never proceed on a repair that isn't
 * ready. Both were previously enforced only by the tracking screen hiding a
 * button, which is not a guard.
 */
class PaymentEligibilityTest {

    private fun reason(
        status: RepairStatus = RepairStatus.READY_FOR_PICKUP,
        amount: Double? = 7500.0,
        alreadyPaid: Boolean = false,
    ) = PaymentEligibility.blockReason(status, amount, alreadyPaid)

    @Test
    fun `a ready repair with a known price may be paid`() {
        assertNull(reason())
    }

    @Test
    fun `a failed price lookup blocks the payment instead of charging zero`() {
        val blocked = reason(amount = null)
        assertNotNull(blocked)
        assertTrue(blocked!!.contains("couldn't load the price"))
    }

    @Test
    fun `a zero amount is refused rather than completing the repair for nothing`() {
        assertNotNull(reason(amount = 0.0))
        assertNotNull(reason(amount = -1.0))
    }

    @Test
    fun `price is checked before status, so an unknown price never reads as not-ready`() {
        // Otherwise a broken lookup on an in-progress repair would tell the
        // customer to wait, hiding a real failure behind a normal-looking message.
        val blocked = reason(status = RepairStatus.IN_PROGRESS, amount = null)
        assertTrue(blocked!!.contains("couldn't load the price"))
    }

    @Test
    fun `every status before ready is refused`() {
        val payable = setOf(RepairStatus.READY_FOR_PICKUP)
        RepairStatus.entries
            .filterNot { it in payable }
            .forEach { status ->
                assertNotNull("$status should not be payable", reason(status = status))
            }
    }

    @Test
    fun `a completed repair says it is settled rather than not ready`() {
        assertTrue(reason(status = RepairStatus.COMPLETED)!!.contains("already completed"))
    }

    @Test
    fun `a cancelled repair has nothing to pay`() {
        assertTrue(reason(status = RepairStatus.CANCELLED)!!.contains("cancelled"))
    }

    @Test
    fun `an already-paid repair opens its receipt whatever its status`() {
        // The receipt has to stay readable after the repair moves to COMPLETED,
        // which is exactly what paying does to it.
        assertNull(reason(status = RepairStatus.COMPLETED, alreadyPaid = true))
        assertNull(reason(status = RepairStatus.READY_FOR_PICKUP, alreadyPaid = true))
    }
}
