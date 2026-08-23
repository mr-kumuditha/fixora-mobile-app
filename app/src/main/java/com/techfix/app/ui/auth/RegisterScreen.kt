package com.techfix.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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
import com.techfix.app.core.designsystem.FixoraTheme

/**
 * Sign up.
 *
 * Same visual pass as [LoginScreen], over Block 2's unchanged registration
 * call. The one difference in content is the password field, which states the
 * six-character minimum as a hint before it is ever hit as an error — the
 * same rule [AuthFormValidation] enforces inline.
 *
 * There is no Google button here, matching Block 2: Google is a sign-in entry
 * point in this app, and the first Google sign-in already creates the
 * `users/{uid}` record, so a separate "sign up with Google" would be the same
 * call under a different name.
 */
@Composable
fun RegisterScreen(
    uiState: AuthUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onRegisterClick: () -> Unit,
    onNavigateToLogin: () -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current

    var emailTouched by remember { mutableStateOf(false) }
    var passwordTouched by remember { mutableStateOf(false) }
    var submitAttempted by remember { mutableStateOf(false) }
    var shakeTrigger by remember { mutableIntStateOf(0) }

    val emailError = AuthFormValidation.emailError(uiState.email)
    val passwordError = AuthFormValidation.passwordError(uiState.password)

    LaunchedEffect(uiState.errorMessage) {
        if (uiState.errorMessage != null) shakeTrigger++
    }

    val submit: () -> Unit = {
        submitAttempted = true
        emailTouched = true
        passwordTouched = true
        if (emailError == null && passwordError == null) {
            keyboard?.hide()
            onRegisterClick()
        } else {
            shakeTrigger++
        }
    }

    AuthScreenContainer {
        AuthBrandHeader()

        AuthFormCard(
            title = "Create an account",
            subtitle = "One account books repairs, tracks them, and keeps your history.",
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
                label = "Password (min ${AuthFormValidation.MIN_PASSWORD_LENGTH} characters)",
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

            AuthErrorBanner(message = uiState.errorMessage, shakeTrigger = shakeTrigger)

            AuthPrimaryButton(
                text = "Create account",
                onClick = submit,
                isLoading = uiState.isLoading,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            modifier = Modifier,
        ) {
            Text(
                "Already have an account?",
                style = MaterialTheme.typography.bodySmall,
                color = FixoraTheme.extendedColors.textSecondary,
            )
            TextButton(onClick = onNavigateToLogin, enabled = !uiState.isLoading) {
                Text("Sign in", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
