package com.techfix.app.core.data.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.techfix.app.R
import com.techfix.app.core.navigation.UserRole
import com.techfix.app.domain.auth.AuthRepository
import com.techfix.app.domain.auth.AuthUser
import com.techfix.app.domain.auth.GoogleSignInUnavailableException
import kotlinx.coroutines.tasks.await

/**
 * Firebase Auth for sign-in, Firestore for the `users/{uid}` role record.
 * On first sign-in by either method the user doc is created with role
 * CUSTOMER if it doesn't already exist.
 */
class FirebaseAuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : AuthRepository {

    override suspend fun registerWithEmail(email: String, password: String): Result<AuthUser> =
        runCatching {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val firebaseUser = requireNotNull(result.user)
            resolveUser(firebaseUser)
        }

    override suspend fun signInWithEmail(email: String, password: String): Result<AuthUser> =
        runCatching {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val firebaseUser = requireNotNull(result.user)
            resolveUser(firebaseUser)
        }

    override suspend fun signInWithGoogle(context: Context): Result<AuthUser> =
        runCatching {
            val webClientId = context.getString(R.string.default_web_client_id)
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val credentialManager = CredentialManager.create(context)
            val response = credentialManager.getCredential(context, request)
            val credential = response.credential

            check(
                credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) { "Unexpected credential type from Credential Manager: ${credential.type}" }

            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val firebaseCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
            val result = auth.signInWithCredential(firebaseCredential).await()
            val firebaseUser = requireNotNull(result.user)
            resolveUser(firebaseUser)
        }.recoverCatching { error ->
            throw when (error) {
                is GetCredentialCancellationException -> GoogleSignInUnavailableException("Google sign-in was cancelled.")
                is NoCredentialException -> GoogleSignInUnavailableException(
                    "No Google account is available on this device. Add one in Settings, or sign in with your email and password."
                )
                // Play Services reports the account-level "Sign-in prompts"
                // switch being off as a generic GetCredentialException whose
                // only marker is this message, so it has to be matched on text.
                is GetCredentialException ->
                    if (error.message?.contains("User disabled the feature", ignoreCase = true) == true) {
                        GoogleSignInUnavailableException(
                            "Google sign-in prompts are turned off for this Google account. " +
                                "Turn \"Sign-in prompts\" back on in your Google Account security settings, " +
                                "or sign in with your email and password."
                        )
                    } else {
                        GoogleSignInUnavailableException("Google sign-in isn't available right now. Try email and password instead.")
                    }
                else -> error
            }
        }

    override fun signOut() {
        auth.signOut()
    }

    override fun currentUserId(): String? = auth.currentUser?.uid

    override suspend fun refreshCurrentUser(): Result<AuthUser> = runCatching {
        resolveUser(auth.currentUser ?: error("You are signed out"))
    }

    private suspend fun resolveUser(firebaseUser: FirebaseUser): AuthUser {
        val uid = firebaseUser.uid
        val docRef = firestore.collection(USERS_COLLECTION).document(uid)
        val snapshot = docRef.get(Source.SERVER).await()

        if (!snapshot.exists()) {
            docRef.set(
                mapOf(
                    "uid" to uid,
                    "email" to firebaseUser.email,
                    ROLE_FIELD to UserRole.CUSTOMER.name,
                    "createdAt" to Timestamp.now(),
                )
            ).await()
            return AuthUser(
                uid = uid,
                email = firebaseUser.email,
                role = UserRole.CUSTOMER,
                name = firebaseUser.displayName?.takeIf { it.isNotBlank() },
                photoUrl = firebaseUser.photoUrl?.toString(),
                emailVerified = firebaseUser.isEmailVerified,
            )
        }

        val storedRole = snapshot.getString(ROLE_FIELD) ?: UserRole.CUSTOMER.name
        if (storedRole != UserRole.CUSTOMER.name) {
            auth.signOut()
            error("This repository version supports customer accounts only.")
        }

        val customPhotoUrl = snapshot.getString(PHOTO_URL_FIELD)?.takeIf { it.isNotBlank() }
        return AuthUser(
            uid = uid,
            email = firebaseUser.email,
            role = UserRole.CUSTOMER,
            name = snapshot.getString(NAME_FIELD)?.takeIf { it.isNotBlank() }
                ?: firebaseUser.displayName?.takeIf { it.isNotBlank() },
            phone = snapshot.getString(PHONE_FIELD)?.takeIf { it.isNotBlank() },
            photoUrl = customPhotoUrl ?: firebaseUser.photoUrl?.toString(),
            hasCustomPhoto = customPhotoUrl != null,
            emailVerified = firebaseUser.isEmailVerified,
        )
    }

    override suspend fun updateProfile(name: String, phone: String?): Result<AuthUser> = runCatching {
        val current = auth.currentUser ?: error("You are signed out")
        val cleanName = name.trim()
        require(cleanName.isNotBlank()) { "Name is required" }
        val cleanPhone = phone?.trim()?.takeIf { it.isNotBlank() }
        firestore.collection(USERS_COLLECTION).document(current.uid).update(
            mapOf(
                NAME_FIELD to cleanName,
                PHONE_FIELD to (cleanPhone ?: FieldValue.delete()),
                UPDATED_AT_FIELD to FieldValue.serverTimestamp(),
            ),
        ).await()
        resolveUser(current)
    }

    override suspend fun updateProfilePhoto(photoUrl: String?): Result<AuthUser> = runCatching {
        val current = auth.currentUser ?: error("You are signed out")
        val cleanUrl = photoUrl?.trim()?.takeIf { it.isNotBlank() }
        require(cleanUrl == null || (cleanUrl.startsWith("https://") && cleanUrl.length <= 2_048)) {
            "Profile photo URL is invalid"
        }
        firestore.collection(USERS_COLLECTION).document(current.uid).update(
            mapOf(
                PHOTO_URL_FIELD to (cleanUrl ?: FieldValue.delete()),
                UPDATED_AT_FIELD to FieldValue.serverTimestamp(),
            ),
        ).await()
        resolveUser(current)
    }

    private companion object {
        const val USERS_COLLECTION = "users"
        const val ROLE_FIELD = "role"
        const val NAME_FIELD = "name"
        const val PHONE_FIELD = "phone"
        const val PHOTO_URL_FIELD = "photoUrl"
        const val UPDATED_AT_FIELD = "updatedAt"
    }
}
