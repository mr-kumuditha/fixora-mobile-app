package com.techfix.app.core.data.auth

import com.techfix.app.domain.auth.AuthRepository

object AuthRepositoryProvider {
    val instance: AuthRepository by lazy { FirebaseAuthRepository() }
}
