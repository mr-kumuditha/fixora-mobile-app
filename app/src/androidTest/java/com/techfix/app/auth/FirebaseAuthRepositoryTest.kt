package com.techfix.app.auth

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.techfix.app.core.data.auth.FirebaseAuthRepository
import com.techfix.app.core.navigation.UserRole
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs against the real techfix-mobile-app Firebase project (no emulator
 * suite) to verify the email/password path end to end: Firebase Auth
 * account creation, the Firestore users/{uid} doc get-or-create, and role
 * resolution on a second sign-in. Cleans up the test account afterwards.
 */
@RunWith(AndroidJUnit4::class)
class FirebaseAuthRepositoryTest {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val repository = FirebaseAuthRepository(auth, firestore)

    private val testEmail = "techfix-androidtest-${System.currentTimeMillis()}@example.com"
    private val testPassword = "TechFixTest123!"

    @After
    fun cleanUp() = runBlocking {
        val user = auth.currentUser
        if (user != null) {
            runCatching { firestore.collection("users").document(user.uid).delete().await() }
            runCatching { user.delete().await() }
        }
    }

    @Test
    fun registerCreatesFirestoreUserDocWithCustomerRole() = runBlocking {
        val result = repository.registerWithEmail(testEmail, testPassword)

        assertTrue("register failed: ${result.exceptionOrNull()}", result.isSuccess)
        val user = result.getOrThrow()
        assertEquals(UserRole.CUSTOMER, user.role)

        val snapshot = firestore.collection("users").document(user.uid).get().await()
        assertTrue("user doc was not created in Firestore", snapshot.exists())
        assertEquals("CUSTOMER", snapshot.getString("role"))
    }

    @Test
    fun signInAfterRegisterResolvesSameRoleFromExistingDoc() = runBlocking {
        val registerResult = repository.registerWithEmail(testEmail, testPassword)
        assertTrue("register failed: ${registerResult.exceptionOrNull()}", registerResult.isSuccess)
        auth.signOut()

        val signInResult = repository.signInWithEmail(testEmail, testPassword)
        assertTrue("sign-in failed: ${signInResult.exceptionOrNull()}", signInResult.isSuccess)
        assertEquals(UserRole.CUSTOMER, signInResult.getOrThrow().role)
    }

    @Test
    fun signInWithWrongPasswordFails(): Unit = runBlocking {
        val registerResult = repository.registerWithEmail(testEmail, testPassword)
        assertTrue("register failed: ${registerResult.exceptionOrNull()}", registerResult.isSuccess)
        auth.signOut()

        val signInResult = repository.signInWithEmail(testEmail, "wrong-password")
        assertTrue("sign-in with wrong password should fail", signInResult.isFailure)

        // Re-authenticate so @After can clean up the account.
        repository.signInWithEmail(testEmail, testPassword)
    }

    @Test
    fun profileFieldsPersistWithoutChangingAuthorizationData() = runBlocking {
        val user = repository.registerWithEmail(testEmail, testPassword).getOrThrow()

        val updated = repository.updateProfile("  Kumuditha   Tharinda  ", "+94 77 123 4567").getOrThrow()
        assertEquals("Kumuditha   Tharinda", updated.name)
        assertEquals("+94 77 123 4567", updated.phone)
        assertEquals(UserRole.CUSTOMER, updated.role)

        val withPhoto = repository.updateProfilePhoto("https://example.com/profile.jpg").getOrThrow()
        assertEquals("https://example.com/profile.jpg", withPhoto.photoUrl)
        assertTrue(withPhoto.hasCustomPhoto)

        val withoutPhoto = repository.updateProfilePhoto(null).getOrThrow()
        assertFalse(withoutPhoto.hasCustomPhoto)

        val snapshot = firestore.collection("users").document(user.uid).get().await()
        assertEquals("CUSTOMER", snapshot.getString("role"))
        assertEquals("Kumuditha   Tharinda", snapshot.getString("name"))
        assertEquals("+94 77 123 4567", snapshot.getString("phone"))
        assertNull(snapshot.getString("photoUrl"))
        assertNull(snapshot.getString("branchId"))
        assertNull(snapshot.getString("technicianId"))
    }

    @Test
    fun customerCannotChangeRoleOrStaffAuthorizationFields() = runBlocking {
        val user = repository.registerWithEmail(testEmail, testPassword).getOrThrow()
        val document = firestore.collection("users").document(user.uid)

        val roleAttempt = runCatching { document.update("role", "ADMIN").await() }
        assertTrue("customer role update should be denied", roleAttempt.isFailure)

        val branchAttempt = runCatching { document.update("branchId", "colombo").await() }
        assertTrue("customer branch update should be denied", branchAttempt.isFailure)

        val technicianAttempt = runCatching { document.update("technicianId", "other-technician").await() }
        assertTrue("customer technician update should be denied", technicianAttempt.isFailure)
    }

    @Test
    fun anotherUserCannotModifySomeoneElsesProfile() = runBlocking {
        val firstEmail = testEmail
        val first = repository.registerWithEmail(firstEmail, testPassword).getOrThrow()
        auth.signOut()

        val secondEmail = "techfix-androidtest-other-${System.currentTimeMillis()}@example.com"
        val second = repository.registerWithEmail(secondEmail, testPassword).getOrThrow()
        try {
            val attempt = runCatching {
                firestore.collection("users").document(first.uid)
                    .update("name", "Unauthorized change")
                    .await()
            }
            assertTrue("another user should not be able to edit this profile", attempt.isFailure)
        } finally {
            runCatching { firestore.collection("users").document(second.uid).delete().await() }
            runCatching { auth.currentUser?.delete()?.await() }
            repository.signInWithEmail(firstEmail, testPassword)
        }
    }
}
