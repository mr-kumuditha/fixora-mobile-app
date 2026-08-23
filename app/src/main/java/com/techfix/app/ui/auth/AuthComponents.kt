package com.techfix.app.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.techfix.app.R
import com.techfix.app.core.designsystem.FixoraRadius
import com.techfix.app.core.designsystem.FixoraSpacing
import com.techfix.app.core.designsystem.FixoraTheme
import com.techfix.app.core.designsystem.pressScale

/**
 * The pieces the sign-in and sign-up screens share, so the two screens stay
 * one design rather than two that drift apart.
 *
 * All of it is presentation: nothing here calls the auth repository or the
 * ViewModel. The screens still hand their existing callbacks down unchanged.
 */

/**
 * Screen frame: background token, scroll, and the entrance animation — a
 * 260ms fade with a short upward slide, inside the design system's 300ms
 * ceiling. The whole form arrives as one movement rather than each row
 * animating separately, which at this size reads as fussy.
 */
@Composable
fun AuthScreenContainer(
    content: @Composable ColumnScope.() -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(260)) +
                slideInVertically(tween(260)) { fullHeight -> fullHeight / 14 },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = FixoraSpacing.lg, vertical = FixoraSpacing.xl),
                verticalArrangement = Arrangement.spacedBy(FixoraSpacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = content,
            )
        }
    }
}

/** Logo, wordmark, tagline — identical on both screens so the brand doesn't move between them. */
@Composable
fun AuthBrandHeader() {
    Image(
        painter = painterResource(R.drawable.ic_fixora_logo),
        contentDescription = stringResource(R.string.cd_app_logo),
        modifier = Modifier.size(72.dp),
    )
    Text(
        text = stringResource(R.string.app_name),
        style = MaterialTheme.typography.displayLarge,
    )
    Text(
        text = stringResource(R.string.app_tagline),
        style = MaterialTheme.typography.labelLarge,
        color = FixoraTheme.extendedColors.textSecondary,
    )
}

/**
 * The form card. Both screens put their fields and actions inside one surface
 * card, so the form reads as a single object on the background rather than as
 * controls floating loose down the screen.
 */
@Composable
fun AuthFormCard(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 420.dp)
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(FixoraRadius.card),
            )
            .padding(FixoraSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(FixoraSpacing.md),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(FixoraSpacing.xs)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = FixoraTheme.extendedColors.textSecondary,
            )
        }
        content()
    }
}

/**
 * An outlined field styled off the design tokens rather than the Material
 * defaults, carrying its own inline error.
 *
 * [errorMessage] is shown only once [showError] is true — the screens set that
 * when the field has been left or the form submitted, so a message never
 * appears while the customer is still part-way through typing the value.
 */
@Composable
fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
    isPassword: Boolean = false,
    errorMessage: String? = null,
    showError: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
    onFocusLost: () -> Unit = {},
    /** Bumped by the screen to replay the shake, e.g. on a rejected submit. */
    shakeTrigger: Int = 0,
) {
    var passwordVisible by remember { mutableStateOf(false) }
    val isError = showError && errorMessage != null

    Column(
        modifier = modifier
            .fillMaxWidth()
            .shake(if (isError) shakeTrigger else 0),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            enabled = enabled,
            isError = isError,
            leadingIcon = leadingIcon?.let {
                { Icon(it, contentDescription = null, modifier = Modifier.size(20.dp)) }
            },
            trailingIcon = if (isPassword) {
                {
                    IconButton(onClick = { passwordVisible = !passwordVisible }, enabled = enabled) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            } else {
                null
            },
            visualTransformation = when {
                !isPassword -> VisualTransformation.None
                passwordVisible -> VisualTransformation.None
                else -> PasswordVisualTransformation()
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Email,
                imeAction = imeAction,
            ),
            keyboardActions = KeyboardActions(
                onNext = { onImeAction() },
                onDone = { onImeAction() },
                onGo = { onImeAction() },
            ),
            shape = RoundedCornerShape(FixoraRadius.input),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = FixoraTheme.extendedColors.border,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                unfocusedLabelColor = FixoraTheme.extendedColors.textSecondary,
                focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
                unfocusedLeadingIconColor = FixoraTheme.extendedColors.textSecondary,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                errorContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                cursorColor = MaterialTheme.colorScheme.primary,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .focusLostNotifier(onFocusLost),
        )

        AnimatedVisibility(
            visible = isError,
            enter = fadeIn(tween(150)) + expandVertically(tween(150)),
            exit = fadeOut(tween(120)) + shrinkVertically(tween(120)),
        ) {
            Text(
                text = errorMessage.orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = FixoraSpacing.md, top = FixoraSpacing.xs),
            )
        }
    }
}

/**
 * Fires once when the field loses focus after having had it — that is the
 * moment a field's own error message is allowed to appear, so a message never
 * lands while the value is still being typed.
 */
@Composable
private fun Modifier.focusLostNotifier(onLost: () -> Unit): Modifier {
    var hadFocus by remember { mutableStateOf(false) }
    return onFocusChanged { state ->
        if (state.isFocused) {
            hadFocus = true
        } else if (hadFocus) {
            hadFocus = false
            onLost()
        }
    }
}

/**
 * Form-level error — what came back from Firebase, not what the fields caught.
 * An inline strip in the error token that animates in, not a blocking dialog:
 * the customer can fix the field it refers to without dismissing anything.
 */
@Composable
fun AuthErrorBanner(message: String?, shakeTrigger: Int = 0) {
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn(tween(180)) + expandVertically(tween(180)),
        exit = fadeOut(tween(140)) + shrinkVertically(tween(140)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shake(shakeTrigger)
                .background(
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(FixoraRadius.input),
                )
                .padding(FixoraSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = message.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * The screen's one primary action. Carries the design system's press scale
 * and, while the auth call is in flight, a spinner in place of its label so
 * the button itself is the loading indicator rather than a separate one
 * appearing somewhere else on the screen.
 */
@Composable
fun AuthPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        interactionSource = interactionSource,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .pressScale(interactionSource),
        colors = ButtonDefaults.buttonColors(
            containerColor = FixoraTheme.extendedColors.accent,
            contentColor = FixoraTheme.extendedColors.onAccent,
        ),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = FixoraTheme.extendedColors.onAccent,
            )
        } else {
            Text(text)
        }
    }
}

/** "or" rule between the email form and the Google button. */
@Composable
fun AuthOrDivider() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.sm),
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = FixoraTheme.extendedColors.border)
        Text(
            "or",
            style = MaterialTheme.typography.labelMedium,
            color = FixoraTheme.extendedColors.textSecondary,
            textAlign = TextAlign.Center,
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = FixoraTheme.extendedColors.border)
    }
}

/**
 * A brief horizontal shake, replayed whenever [trigger] changes to a new
 * non-zero value. 280ms end to end, four decaying swings — enough to draw the
 * eye to the field that was rejected without throwing a dialog in front of
 * the customer, and inside the design system's animation ceiling.
 */
fun Modifier.shake(trigger: Int): Modifier = composed {
    val offsetX = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        offsetX.animateTo(
            targetValue = 0f,
            animationSpec = keyframes {
                durationMillis = 280
                0f at 0
                -8f at 50
                8f at 110
                -5f at 170
                3f at 220
                0f at 280
            },
        )
    }
    graphicsLayer { translationX = offsetX.value }
}
