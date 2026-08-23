package com.techfix.app.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MailOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.techfix.app.core.designsystem.FixoraSpacing
import com.techfix.app.core.designsystem.FixoraTheme

/**
 * Sign in.
 *
 * A visual pass over Block 2's flow: the callbacks, the ViewModel and the
 * repository behind them are unchanged. What is new here is presentation
 * only — the form card, inline per-field validation before anything is sent,
 * Google's own branded button, and the load / press / error motion.
 */
@Composable
fun LoginScreen(
    uiState: AuthUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLoginClick: () -> Unit,
    onGoogleClick: () -> Unit,
    onNavigateToRegister: () -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current

    // Validation state is the screen's own: it decides what the fields say
    // before a request is made and never touches the auth layer.
    var emailTouched by remember { mutableStateOf(false) }
    var passwordTouched by remember { mutableStateOf(false) }
    var submitAttempted by remember { mutableStateOf(false) }
    var shakeTrigger by remember { mutableIntStateOf(0) }
    var showResetNotice by remember { mutableStateOf(false) }

    val emailError = AuthFormValidation.emailError(uiState.email)
    val passwordError = AuthFormValidation.passwordError(uiState.password)

    // A rejection from Firebase gets the same attention as a rejected field.
    LaunchedEffect(uiState.errorMessage) {
        if (uiState.errorMessage != null) shakeTrigger++
    }

    val submit: () -> Unit = {
        submitAttempted = true
        emailTouched = true
        passwordTouched = true
        if (emailError == null && passwordError == null) {
            keyboard?.hide()
            onLoginClick()
        } else {
            shakeTrigger++
        }
    }

    AuthScreenContainer {
        AuthBrandHeader()

        AuthFormCard(
            title = "Sign in",
            subtitle = "Welcome back — sign in to book and track repairs.",
        ) {
            AuthTextField(
                value = uiState.email,
                onValueChange = onEmailChange,
                label = "Email",
                leadingIcon = Icons.Rounded.MailOutline,
                enabled = !uiState.isLoading,
                errorMessage = emailError,
                showError = (emailTouched || submitAttempted) && emailError != null,
                imeAction = ImeAction.Next,
                onFocusLost = { emailTouched = true },
                shakeTrigger = shakeTrigger,
            )

            AuthTextField(
                value = uiState.password,
                onValueChange = onPasswordChange,
                label = "Password",
                leadingIcon = Icons.Rounded.Lock,
                enabled = !uiState.isLoading,
                isPassword = true,
                errorMessage = passwordError,
                showError = (passwordTouched || submitAttempted) && passwordError != null,
                imeAction = ImeAction.Go,
                onImeAction = { submit() },
                onFocusLost = { passwordTouched = true },
                shakeTrigger = shakeTrigger,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = { showResetNotice = !showResetNotice },
                    enabled = !uiState.isLoading,
                ) {
                    Text("Forgot password?", style = MaterialTheme.typography.labelMedium)
                }
            }

            // Honest rather than decorative: password reset is not part of
            // this build, and Block 10 was a visual pass with the auth layer
            // explicitly out of scope, so the link says what it can do
            // instead of pretending to send an email.
            AnimatedVisibility(
                visible = showResetNotice,
                enter = fadeIn(tween(180)) + expandVertically(tween(180)),
                exit = fadeOut(tween(140)) + shrinkVertically(tween(140)),
            ) {
                Text(
                    text = "Password reset isn't part of this build yet. Ask a Fixora branch to reset it for you.",
                    style = MaterialTheme.typography.labelMedium,
                    color = FixoraTheme.extendedColors.textSecondary,
                    modifier = Modifier.padding(bottom = FixoraSpacing.xs),
                )
            }

            AuthErrorBanner(message = uiState.errorMessage, shakeTrigger = shakeTrigger)

            AuthPrimaryButton(
                text = "Sign in",
                onClick = submit,
                isLoading = uiState.isLoading,
            )

            AuthOrDivider()

            GoogleSignInButton(
                onClick = {
                    keyboard?.hide()
                    onGoogleClick()
                },
                enabled = !uiState.isLoading,
                text = "Sign in with Google",
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Text(
                "New here?",
                style = MaterialTheme.typography.bodySmall,
                color = FixoraTheme.extendedColors.textSecondary,
            )
            TextButton(onClick = onNavigateToRegister, enabled = !uiState.isLoading) {
                Text("Create an account", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
