package com.techfix.app.ui.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.techfix.app.domain.auth.AuthRepository
import com.techfix.app.domain.auth.AuthUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

class AuthViewModel(private val authRepository: AuthRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState

    fun onEmailChange(value: String) {
        _uiState.update { it.copy(email = value, errorMessage = null) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, errorMessage = null) }
    }

    fun register(onSuccess: (AuthUser) -> Unit) = runAuthAction(onSuccess) {
        authRepository.registerWithEmail(_uiState.value.email, _uiState.value.password)
    }

    fun login(onSuccess: (AuthUser) -> Unit) = runAuthAction(onSuccess) {
        authRepository.signInWithEmail(_uiState.value.email, _uiState.value.password)
    }

    fun loginWithGoogle(context: Context, onSuccess: (AuthUser) -> Unit) = runAuthAction(onSuccess) {
        authRepository.signInWithGoogle(context)
    }

    private fun runAuthAction(onSuccess: (AuthUser) -> Unit, action: suspend () -> Result<AuthUser>) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            action()
                .onSuccess { user ->
                    _uiState.update { it.copy(isLoading = false) }
                    onSuccess(user)
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = error.message ?: "Something went wrong")
                    }
                }
        }
    }

    companion object {
        fun factory(authRepository: AuthRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AuthViewModel(authRepository) as T
            }
    }
}
