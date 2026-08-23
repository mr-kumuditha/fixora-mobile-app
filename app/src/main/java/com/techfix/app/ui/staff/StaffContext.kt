package com.techfix.app.ui.staff

import com.techfix.app.core.navigation.UserRole
import com.techfix.app.domain.auth.AuthUser

/**
 * Who the signed-in staff member is, and what the one shared staff screen set
 * lets them do.
 *
 * Admin, Branch Manager, and Technician stay three distinct roles in the data
 * model (see CLAUDE.md) but share these screens; every difference between
 * them is a flag here, not a separate screen. Keeping the flags in one place
 * means a screen never re-derives "can this role do X" from the enum itself.
 */
data class StaffContext(
    val uid: String,
    val role: UserRole,
    /**
     * The branch this staff member works at, or null for an Admin (and for a
     * staff record whose `branchId` was never filled in, which falls back to
     * the unscoped view rather than showing nothing).
     */
    val branchId: String?,
    /** Firestore `technicians` document id, when this login is tied to a technician. */
    val technicianId: String?,
) {
    /** Confirming the branch and naming a technician is a manager action. */
    val canAssign: Boolean
        get() = role == UserRole.ADMIN || role == UserRole.BRANCH_MANAGER

    /** Supabase cannot authorize Firebase roles, so stock fails closed. */
    val canEditStock: Boolean
        get() = false

    /**
     * Full inventory management uses the Firebase-verified Edge Function,
     * never the anonymous PostgREST client. It is intentionally Admin-only.
     */
    val canManageInventory: Boolean
        get() = role == UserRole.ADMIN

    /** Only an Admin is unscoped. Missing staff scope fails closed. */
    val seesAllBranches: Boolean
        get() = role == UserRole.ADMIN

    /**
     * A Technician always uses the assigned-work query. A missing technician
     * link resolves to an empty/error scope rather than branch access.
     */
    val seesOnlyOwnRepairs: Boolean
        get() = role == UserRole.TECHNICIAN

    val hasRequiredScope: Boolean
        get() = when (role) {
            UserRole.ADMIN -> true
            UserRole.BRANCH_MANAGER -> branchId != null
            UserRole.TECHNICIAN -> branchId != null && technicianId != null
            UserRole.CUSTOMER -> false
        }

    val roleLabel: String
        get() = when (role) {
            UserRole.ADMIN -> "Admin"
            UserRole.BRANCH_MANAGER -> "Branch Manager"
            UserRole.TECHNICIAN -> "Technician"
            UserRole.CUSTOMER -> "Customer"
        }

    companion object {
        fun from(user: AuthUser?, fallbackRole: UserRole = UserRole.TECHNICIAN) = StaffContext(
            uid = user?.uid.orEmpty(),
            role = user?.role ?: fallbackRole,
            branchId = user?.branchId,
            technicianId = user?.technicianId,
        )
    }
}
