package com.techfix.app.ui.customer.payment

import com.techfix.app.domain.repair.RepairStatus

/**
 * Whether a repair can be paid for, and why not when it can't.
 *
 * Pulled out of the ViewModel so the rule is one pure function that can be
 * tested directly. Two things it guarantees, both of which were holes the
 * Block 7 audit found:
 *
 * - **The price must be known.** If the service lookup fails there is no
 *   amount to charge, and the flow must stop rather than fall back to zero —
 *   a Rs. 0 "successful" payment would still mark the repair COMPLETED and
 *   write a receipt for nothing.
 * - **The repair must actually be ready.** The tracking screen only offers
 *   "Pay now" on READY_FOR_PICKUP, but the screen hiding a button is not a
 *   guard; anything else reaching this route has to be refused here too.
 */
object PaymentEligibility {

    /**
     * Null when the flow may proceed. Otherwise a message for the error pane.
     *
     * [alreadyPaid] short-circuits everything: a repair with a successful
     * receipt opens on that receipt, which stays readable no matter what
     * status the repair has moved to since.
     */
    fun blockReason(
        status: RepairStatus,
        amount: Double?,
        alreadyPaid: Boolean,
    ): String? = when {
        alreadyPaid -> null

        amount == null -> "We couldn't load the price for this repair, so it can't be paid " +
            "right now. Check your connection and try again."

        amount <= 0.0 -> "This repair has no payable amount recorded, so it can't be paid here. " +
            "Ask your branch to check the service price."

        status == RepairStatus.READY_FOR_PICKUP -> null

        status == RepairStatus.COMPLETED ->
            "This repair is already completed and has no payment outstanding."

        status == RepairStatus.CANCELLED -> "This repair was cancelled, so there's nothing to pay."

        else -> "This repair isn't ready for collection yet, so there's nothing to pay. " +
            "You'll be able to pay once it reaches Ready."
    }
}
