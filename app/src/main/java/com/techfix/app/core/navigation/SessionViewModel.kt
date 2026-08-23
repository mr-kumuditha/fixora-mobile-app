package com.techfix.app.core.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.techfix.app.domain.auth.AuthUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Holds the signed-in user for routing.
 *
 * It carries the whole [AuthUser] rather than just the role because the
 * shared staff screen set also needs the staff member's branch and, for a
 * Technician, which technician row they are (Block 7). [role] stays exposed
 * separately so the routing in [FixoraNavHost] reads the same as before.
 */
class SessionViewModel : ViewModel() {
    private val _user = MutableStateFlow<AuthUser?>(null)
    val user: StateFlow<AuthUser?> = _user

    val role: StateFlow<UserRole?> = _user
        .map { it?.role }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun signIn(user: AuthUser) {
        _user.value = user
    }

    fun signOut() {
        _user.value = null
    }
}
