package com.techfix.app.domain.auth

import com.techfix.app.core.navigation.UserRole

/**
 * The signed-in user, as the app routes on.
 *
 * [branchId] and [technicianId] are only ever set on staff records, and only
 * by an Admin editing `users/{uid}` directly — same pattern as [role], since
 * there is no self-service staff signup. They scope what the shared staff
 * screen set shows:
 *
 * - [branchId] (a Firestore `branches` document id, 'colombo' / 'galle')
 *   narrows the appointment queue and the spare-part stock view to one
 *   branch. Absent on an Admin, who sees every branch.
 * - [technicianId] (a Firestore `technicians` document id) links a staff login
 *   to the technician a repair is assigned to, so a Technician sees the
 *   repairs that are actually theirs. A Technician record without this exact
 *   reciprocal link is rejected at login; it never falls back to branch-wide
 *   repair access.
 */
data class AuthUser(
    val uid: String,
    val email: String?,
    val role: UserRole,
    val name: String? = null,
    val phone: String? = null,
    /** Active custom photo, or the Firebase provider photo when no custom one exists. */
    val photoUrl: String? = null,
    /** Distinguishes a removable Fixora photo from a provider-owned Google photo. */
    val hasCustomPhoto: Boolean = false,
    val emailVerified: Boolean = false,
    val branchId: String? = null,
    val technicianId: String? = null,
)
