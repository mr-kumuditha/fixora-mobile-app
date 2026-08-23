package com.techfix.app.domain.auth

import com.techfix.app.core.navigation.UserRole

/** The signed-in customer, as the app routes on. */
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
)
