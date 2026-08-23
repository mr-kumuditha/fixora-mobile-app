package com.techfix.app.core.data.user

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.techfix.app.core.data.FirestoreCollections
import com.techfix.app.core.navigation.UserRole
import com.techfix.app.domain.user.UserAccountSummary
import com.techfix.app.domain.user.UserRepository
import kotlinx.coroutines.tasks.await

class FirestoreUserRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : UserRepository {
    override suspend fun getUsers(): Result<List<UserAccountSummary>> = runCatching {
        firestore.collection(FirestoreCollections.USERS)
            .get(Source.SERVER)
            .await()
            .documents
            .map { it.toUserSummary() }
            .sortedWith(compareByDescending<UserAccountSummary> { it.createdAt }.thenBy { it.name.orEmpty() })
    }

    override suspend fun getUser(uid: String): Result<UserAccountSummary> = runCatching {
        require(uid.isNotBlank()) { "User id is required" }
        val snapshot = firestore.collection(FirestoreCollections.USERS)
            .document(uid)
            .get(Source.SERVER)
            .await()
        check(snapshot.exists()) { "Linked technician account does not exist" }
        snapshot.toUserSummary()
    }

    private fun DocumentSnapshot.toUserSummary() = UserAccountSummary(
        uid = getString("uid") ?: id,
        email = getString("email"),
        name = getString("name"),
        phone = getString("phone"),
        photoUrl = getString("photoUrl"),
        role = runCatching { UserRole.valueOf(getString("role").orEmpty()) }
            .getOrDefault(UserRole.CUSTOMER),
        branchId = getString("branchId"),
        technicianId = getString("technicianId"),
        createdAt = getTimestamp("createdAt")?.toDate()?.time,
    )
}
