package com.techfix.app.ui.customer.profile

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.techfix.app.core.util.ImageCompressor
import com.techfix.app.core.util.UploadDiagnostics
import com.techfix.app.domain.auth.AuthRepository
import com.techfix.app.domain.auth.AuthUser
import com.techfix.app.domain.storage.ImageUploadRepository
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ProfileUiState(
    val user: AuthUser? = null,
    val isLoading: Boolean = user == null,
    val loadError: String? = null,
    val name: String = user?.name.orEmpty(),
    val phone: String = user?.phone.orEmpty(),
    val nameTouched: Boolean = false,
    val phoneTouched: Boolean = false,
    val isSaving: Boolean = false,
    val isPhotoUploading: Boolean = false,
    val formError: String? = null,
    val feedbackMessage: String? = null,
) {
    val nameError: String?
        get() = ProfileValidation.nameError(name)

    val phoneError: String?
        get() = ProfileValidation.phoneError(phone)

    val hasUnsavedChanges: Boolean
        get() = user != null && (
            ProfileValidation.normalizeName(name) != ProfileValidation.normalizeName(user.name.orEmpty()) ||
                ProfileValidation.normalizePhone(phone) != ProfileValidation.normalizePhone(user.phone.orEmpty())
            )

    val canSave: Boolean
        get() = hasUnsavedChanges && nameError == null && phoneError == null && !isSaving && !isPhotoUploading
}

class ProfileViewModel(
    initialUser: AuthUser?,
    private val authRepository: AuthRepository,
    private val imageUploadRepository: ImageUploadRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUiState(user = initialUser))
    val uiState: StateFlow<ProfileUiState> = _uiState

    fun refresh(onUpdated: (AuthUser) -> Unit = {}) {
        _uiState.update { it.copy(isLoading = it.user == null, loadError = null) }
        viewModelScope.launch {
            authRepository.refreshCurrentUser()
                .onSuccess { user ->
                    applyUser(user)
                    onUpdated(user)
                }
                .onFailure {
                    _uiState.update { state ->
                        if (state.user == null) {
                            state.copy(
                                isLoading = false,
                                loadError = "Unable to load your profile. Please try again.",
                            )
                        } else {
                            state.copy(
                                isLoading = false,
                                feedbackMessage = "Unable to refresh your profile. Showing your saved account details.",
                            )
                        }
                    }
                }
        }
    }

    fun syncUser(user: AuthUser?) {
        if (user == null || user == _uiState.value.user) return
        val state = _uiState.value
        _uiState.value = state.copy(
            user = user,
            isLoading = false,
            loadError = null,
            name = if (state.hasUnsavedChanges) state.name else user.name.orEmpty(),
            phone = if (state.hasUnsavedChanges) state.phone else user.phone.orEmpty(),
        )
    }

    fun resetForm() {
        val user = _uiState.value.user
        _uiState.update {
            it.copy(
                name = user?.name.orEmpty(),
                phone = user?.phone.orEmpty(),
                nameTouched = false,
                phoneTouched = false,
                formError = null,
            )
        }
    }

    fun onNameChange(value: String) = _uiState.update {
        it.copy(name = value.take(ProfileValidation.MAX_NAME_LENGTH + 1), formError = null)
    }

    fun onPhoneChange(value: String) = _uiState.update {
        it.copy(phone = ProfileValidation.filterPhoneInput(value), formError = null)
    }

    fun onNameFocusLost() = _uiState.update { it.copy(nameTouched = true) }

    fun onPhoneFocusLost() = _uiState.update { it.copy(phoneTouched = true) }

    fun save(
        onUpdated: (AuthUser) -> Unit = {},
        onSaved: () -> Unit = {},
    ) {
        val state = _uiState.value
        if (state.isSaving || !state.hasUnsavedChanges) return
        if (state.nameError != null || state.phoneError != null) {
            _uiState.update {
                it.copy(
                    nameTouched = true,
                    phoneTouched = true,
                    formError = "Check the highlighted information and try again.",
                )
            }
            return
        }

        _uiState.update { it.copy(isSaving = true, formError = null) }
        viewModelScope.launch {
            authRepository.updateProfile(
                name = ProfileValidation.normalizeName(state.name),
                phone = ProfileValidation.normalizePhone(state.phone),
            ).onSuccess { user ->
                applyUser(user, feedbackMessage = "Your profile has been updated.")
                onUpdated(user)
                onSaved()
            }.onFailure {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        formError = "Unable to update your profile. Please try again.",
                    )
                }
            }
        }
    }

    fun updatePhoto(
        context: Context,
        uri: Uri,
        onUpdated: (AuthUser) -> Unit = {},
    ) {
        val ownerId = _uiState.value.user?.uid ?: authRepository.currentUserId()
        if (uri == Uri.EMPTY || ownerId.isNullOrBlank() || _uiState.value.isPhotoUploading) return

        _uiState.update {
            it.copy(isPhotoUploading = true, feedbackMessage = null, formError = null)
        }
        viewModelScope.launch {
            var compressed: File? = null
            val result = runCatching {
                val uploadedUrl = withContext(Dispatchers.IO) {
                    compressed = ImageCompressor.compress(context.applicationContext, uri)
                    imageUploadRepository.uploadProfileImage(ownerId, compressed!!).getOrThrow()
                }
                authRepository.updateProfilePhoto(uploadedUrl).getOrThrow()
            }
            compressed?.delete()

            result.onSuccess { user ->
                applyUser(
                    user,
                    feedbackMessage = "Your profile photo has been updated.",
                    preserveFormEdits = true,
                )
                onUpdated(user)
            }.onFailure { error ->
                UploadDiagnostics.error("Profile photo update failed for user=$ownerId", error)
                _uiState.update {
                    it.copy(
                        isPhotoUploading = false,
                        feedbackMessage = "Unable to update your profile photo. Your previous photo is unchanged.",
                    )
                }
            }
        }
    }

    fun removePhoto(onUpdated: (AuthUser) -> Unit = {}) {
        val state = _uiState.value
        if (state.user?.hasCustomPhoto != true || state.isPhotoUploading) return
        _uiState.update { it.copy(isPhotoUploading = true, feedbackMessage = null) }
        viewModelScope.launch {
            authRepository.updateProfilePhoto(null)
                .onSuccess { user ->
                    applyUser(
                        user,
                        feedbackMessage = "Your profile photo has been removed.",
                        preserveFormEdits = true,
                    )
                    onUpdated(user)
                }
                .onFailure {
                    _uiState.update {
                        it.copy(
                            isPhotoUploading = false,
                            feedbackMessage = "Unable to remove your profile photo. Please try again.",
                        )
                    }
                }
        }
    }

    fun showMessage(message: String) = _uiState.update { it.copy(feedbackMessage = message) }

    fun clearFeedback() = _uiState.update { it.copy(feedbackMessage = null) }

    private fun applyUser(
        user: AuthUser,
        feedbackMessage: String? = null,
        preserveFormEdits: Boolean = false,
    ) {
        val previous = _uiState.value
        val keepEdits = preserveFormEdits && previous.hasUnsavedChanges
        _uiState.value = ProfileUiState(
            user = user,
            isLoading = false,
            name = if (keepEdits) previous.name else user.name.orEmpty(),
            phone = if (keepEdits) previous.phone else user.phone.orEmpty(),
            nameTouched = if (keepEdits) previous.nameTouched else false,
            phoneTouched = if (keepEdits) previous.phoneTouched else false,
            feedbackMessage = feedbackMessage,
        )
    }

    companion object {
        fun factory(
            initialUser: AuthUser?,
            authRepository: AuthRepository,
            imageUploadRepository: ImageUploadRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ProfileViewModel(initialUser, authRepository, imageUploadRepository) as T
        }
    }
}
