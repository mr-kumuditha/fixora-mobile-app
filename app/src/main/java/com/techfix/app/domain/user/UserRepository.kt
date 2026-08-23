package com.techfix.app.domain.user

interface UserRepository {
    /** Admin-only directory read. Roles are deliberately not editable here. */
    suspend fun getUsers(): Result<List<UserAccountSummary>>

    /** Exact server-backed account link lookup used before technician assignment. */
    suspend fun getUser(uid: String): Result<UserAccountSummary>
}
