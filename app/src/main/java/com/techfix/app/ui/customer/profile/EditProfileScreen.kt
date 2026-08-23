package com.techfix.app.ui.customer.profile

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.techfix.app.core.designsystem.FixoraRadius
import com.techfix.app.core.designsystem.FixoraSpacing
import com.techfix.app.core.designsystem.FixoraTheme
import com.techfix.app.core.designsystem.pressScale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    uiState: ProfileUiState,
    onNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onNameFocusLost: () -> Unit,
    onPhoneFocusLost: () -> Unit,
    onSave: () -> Unit,
    onDiscardChanges: () -> Unit,
    onBack: () -> Unit,
    onPhotoSelected: (Uri) -> Unit,
    onRemovePhoto: () -> Unit,
    onPhotoMessage: (String) -> Unit,
    onDismissMessage: () -> Unit,
) {
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }
    var showPhotoSheet by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val saveInteractionSource = remember { MutableInteractionSource() }

    fun requestExit() {
        when {
            uiState.isSaving -> Unit
            uiState.hasUnsavedChanges -> showDiscardDialog = true
            else -> onBack()
        }
    }

    BackHandler(enabled = uiState.hasUnsavedChanges || uiState.isSaving) { requestExit() }

    LaunchedEffect(uiState.feedbackMessage) {
        uiState.feedbackMessage?.let {
            snackbarHostState.showSnackbar(it)
            onDismissMessage()
        }
    }

    ProfilePhotoActions(
        showSheet = showPhotoSheet,
        hasCustomPhoto = uiState.user?.hasCustomPhoto == true,
        enabled = !uiState.isSaving && !uiState.isPhotoUploading,
        onDismiss = { showPhotoSheet = false },
        onPhotoSelected = onPhotoSelected,
        onRemovePhoto = onRemovePhoto,
        onMessage = onPhotoMessage,
    )

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            icon = { Icon(Icons.Rounded.Info, contentDescription = null) },
            title = { Text("Discard changes?") },
            text = { Text("Your unsaved profile information will be lost.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        onDiscardChanges()
                        onBack()
                    },
                ) { Text("Discard", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text("Keep editing") }
            },
            shape = RoundedCornerShape(FixoraRadius.sheet),
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Profile") },
                navigationIcon = {
                    IconButton(onClick = ::requestExit, enabled = !uiState.isSaving) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = onSave, enabled = uiState.canSave) {
                        Text("Save")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Button(
                    onClick = onSave,
                    enabled = uiState.canSave,
                    interactionSource = saveInteractionSource,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = FixoraSpacing.md, vertical = FixoraSpacing.sm)
                        .imePadding()
                        .pressScale(saveInteractionSource),
                    shape = RoundedCornerShape(FixoraRadius.input),
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text("Save changes")
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = FixoraSpacing.md, vertical = FixoraSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(FixoraSpacing.lg),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(FixoraSpacing.sm),
            ) {
                ProfileAvatar(
                    name = uiState.user?.name,
                    email = uiState.user?.email,
                    photoUrl = uiState.user?.photoUrl,
                    isUploading = uiState.isPhotoUploading,
                    onClick = { showPhotoSheet = true },
                    size = 104.dp,
                )
                Text("Profile photo", style = MaterialTheme.typography.labelLarge)
                Text(
                    "Add a photo to personalize your Fixora account",
                    style = MaterialTheme.typography.bodySmall,
                    color = FixoraTheme.extendedColors.textSecondary,
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(FixoraRadius.card),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, FixoraTheme.extendedColors.border),
            ) {
                Column(
                    modifier = Modifier.padding(FixoraSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(FixoraSpacing.md),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(FixoraSpacing.xs)) {
                        Text("Personal information", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Keep your contact details accurate for repair updates.",
                            style = MaterialTheme.typography.bodySmall,
                            color = FixoraTheme.extendedColors.textSecondary,
                        )
                    }

                    ProfileTextField(
                        value = uiState.name,
                        onValueChange = onNameChange,
                        label = "Full Name",
                        icon = Icons.Rounded.Person,
                        enabled = !uiState.isSaving,
                        error = uiState.nameError.takeIf { uiState.nameTouched },
                        keyboardType = KeyboardType.Text,
                        onFocusLost = onNameFocusLost,
                    )
                    ProfileTextField(
                        value = uiState.phone,
                        onValueChange = onPhoneChange,
                        label = "Phone Number",
                        icon = Icons.Rounded.Phone,
                        enabled = !uiState.isSaving,
                        error = uiState.phoneError.takeIf { uiState.phoneTouched },
                        keyboardType = KeyboardType.Phone,
                        onFocusLost = onPhoneFocusLost,
                    )

                    HorizontalDivider(color = FixoraTheme.extendedColors.border)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.md),
                    ) {
                        Icon(Icons.Rounded.Email, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FixoraSpacing.xs)) {
                            Text("Email Address", style = MaterialTheme.typography.labelMedium, color = FixoraTheme.extendedColors.textSecondary)
                            Text(uiState.user?.email ?: "Not available", style = MaterialTheme.typography.bodyMedium)
                        }
                        Icon(
                            if (uiState.user?.emailVerified == true) Icons.Rounded.CheckCircle else Icons.Rounded.Info,
                            contentDescription = null,
                            tint = if (uiState.user?.emailVerified == true) FixoraTheme.extendedColors.successOnSurface else FixoraTheme.extendedColors.warningOnSurface,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            if (uiState.user?.emailVerified == true) "Verified" else "Not verified",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (uiState.user?.emailVerified == true) FixoraTheme.extendedColors.successOnSurface else FixoraTheme.extendedColors.warningOnSurface,
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = uiState.formError != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(FixoraRadius.input),
                ) {
                    Text(
                        uiState.formError.orEmpty(),
                        modifier = Modifier.padding(FixoraSpacing.md),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    error: String?,
    keyboardType: KeyboardType,
    onFocusLost: () -> Unit,
) {
    var hadFocus by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp)) },
        supportingText = error?.let { message -> { Text(message) } },
        isError = error != null,
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(FixoraRadius.input),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = FixoraTheme.extendedColors.border,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = FixoraTheme.extendedColors.textSecondary,
            focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
            unfocusedLeadingIconColor = FixoraTheme.extendedColors.textSecondary,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            errorContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { state ->
                if (hadFocus && !state.isFocused) onFocusLost()
                hadFocus = state.isFocused
            },
    )
}
