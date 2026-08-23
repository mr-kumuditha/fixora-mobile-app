package com.techfix.app.domain.auth

import android.content.Context

/**
 * Hides whether sign-in is backed by Firebase Auth, or anything else later,
 * so ViewModels never call Firebase directly (see architecture doc, layered
 * MVVM section).
 */
interface AuthRepository {
    suspend fun registerWithEmail(email: String, password: String): Result<AuthUser>
    suspend fun signInWithEmail(email: String, password: String): Result<AuthUser>
    suspend fun signInWithGoogle(context: Context): Result<AuthUser>
    suspend fun refreshCurrentUser(): Result<AuthUser>
    suspend fun updateProfile(name: String, phone: String?): Result<AuthUser>
    suspend fun updateProfilePhoto(photoUrl: String?): Result<AuthUser>
    fun signOut()
    fun currentUserId(): String?
}
