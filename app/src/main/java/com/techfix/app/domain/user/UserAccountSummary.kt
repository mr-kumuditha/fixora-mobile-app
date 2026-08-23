package com.techfix.app.domain.user

import com.techfix.app.core.navigation.UserRole

/** Read-only Firestore profile projection for the Admin user directory. */
data class UserAccountSummary(
    val uid: String,
    val email: String?,
    val name: String?,
    val phone: String?,
    val photoUrl: String?,
    val role: UserRole,
    val branchId: String?,
    val technicianId: String?,
    val createdAt: Long?,
)
