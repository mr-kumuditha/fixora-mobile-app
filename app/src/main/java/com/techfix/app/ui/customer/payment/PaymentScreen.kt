package com.techfix.app.ui.customer.payment

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.techfix.app.core.designsystem.FixoraSpacing
import com.techfix.app.core.designsystem.FixoraTheme
import com.techfix.app.domain.payment.PaymentMethod
import com.techfix.app.ui.customer.catalog.label
import com.techfix.app.ui.customer.repair.DetailRow
import com.techfix.app.ui.customer.repair.formatDateTime
import com.techfix.app.ui.customer.repair.formatPrice
import com.techfix.app.ui.customer.repair.repairReference

/**
 * Payment Summary → Method → Demo Details → Processing → Result → Receipt,
 * one screen with six panes rather than six destinations, so the flow keeps
 * one ViewModel and Back inside it never lands on a half-finished payment.
 *
 * The demo banner is outside the pane switcher on purpose: it is on screen at
 * every step, because nothing here ever charges anything.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentScreen(
    uiState: PaymentUiState,
    onRetryLoad: () -> Unit,
    onStart: () -> Unit,
    onMethodSelected: (PaymentMethod) -> Unit,
    onContinueFromMethod: () -> Unit,
    onCardNumberChange: (String) -> Unit,
    onExpiryChange: (String) -> Unit,
    onCvvChange: (String) -> Unit,
    onCardNameChange: (String) -> Unit,
    onSimulateFailureChange: (Boolean) -> Unit,
    onPayNow: () -> Unit,
    onRetryPayment: () -> Unit,
    onViewReceipt: () -> Unit,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.step == PaymentStep.RECEIPT) "Receipt" else "Payment") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            DemoPaymentBanner(modifier = Modifier.padding(horizontal = FixoraSpacing.md, vertical = FixoraSpacing.sm))

            Crossfade(
                targetState = when {
                    uiState.isLoading -> PaymentPane.LOADING
                    uiState.errorMessage != null -> PaymentPane.ERROR
                    else -> when (uiState.step) {
                        PaymentStep.SUMMARY -> PaymentPane.SUMMARY
                        PaymentStep.METHOD -> PaymentPane.METHOD
                        PaymentStep.DETAILS -> PaymentPane.DETAILS
                        PaymentStep.PROCESSING -> PaymentPane.PROCESSING
                        PaymentStep.RESULT -> PaymentPane.RESULT
                        PaymentStep.RECEIPT -> PaymentPane.RECEIPT
                    }
                },
                animationSpec = tween(220),
                label = "paymentPane",
                modifier = Modifier.fillMaxSize(),
            ) { pane ->
                when (pane) {
                    PaymentPane.LOADING -> PaymentSkeleton()
                    PaymentPane.ERROR -> PaymentBlocked(
                        // Distinguishes "we couldn't load this" from "this
                        // repair isn't payable" — retrying only helps the first.
                        canRetry = uiState.request == null || uiState.amount == null,
                        message = uiState.errorMessage ?: "Something went wrong.",
                        onRetry = onRetryLoad,
                        onBack = onBack,
                    )
                    PaymentPane.SUMMARY -> SummaryPane(uiState, onStart)
                    PaymentPane.METHOD -> MethodPane(uiState, onMethodSelected, onContinueFromMethod)
                    PaymentPane.DETAILS -> DetailsPane(
                        uiState = uiState,
                        onCardNumberChange = onCardNumberChange,
                        onExpiryChange = onExpiryChange,
                        onCvvChange = onCvvChange,
                        onCardNameChange = onCardNameChange,
                        onSimulateFailureChange = onSimulateFailureChange,
                        onPayNow = onPayNow,
                    )
                    PaymentPane.PROCESSING -> ProcessingPane(uiState)
                    PaymentPane.RESULT -> ResultPane(uiState, onViewReceipt, onRetryPayment)
                    PaymentPane.RECEIPT -> ReceiptPane(uiState, onDone)
                }
            }
        }
    }
}

private enum class PaymentPane { LOADING, ERROR, SUMMARY, METHOD, DETAILS, PROCESSING, RESULT, RECEIPT }

/**
 * On screen at every step of the flow, per CLAUDE.md: the payment is
 * simulated and must always be labelled as such.
 */
@Composable
private fun DemoPaymentBanner(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(FixoraSpacing.sm))
            .background(FixoraTheme.extendedColors.warning.copy(alpha = 0.16f))
            .padding(FixoraSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.Science,
            contentDescription = null,
            tint = FixoraTheme.extendedColors.warning,
            modifier = Modifier.size(20.dp),
        )
        Text(
            "Demo payment — no card is charged and no card details are stored.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun PaneScaffold(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(FixoraSpacing.md),
        verticalArrangement = Arrangement.spacedBy(FixoraSpacing.md),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(FixoraSpacing.xs)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = FixoraTheme.extendedColors.textSecondary,
            )
        }
        content()
    }
}

@Composable
private fun SummaryPane(uiState: PaymentUiState, onStart: () -> Unit) {
    val request = uiState.request
    // The ViewModel blocks the flow when the price is unknown, so this pane is
    // only reachable with a real amount. Bail out rather than invent a zero.
    val amount = uiState.amount ?: return
    PaneScaffold(
        title = "Payment summary",
        subtitle = "What this repair comes to, before you pick how to pay.",
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier.padding(FixoraSpacing.md),
                verticalArrangement = Arrangement.spacedBy(FixoraSpacing.sm),
            ) {
                Text(
                    text = uiState.serviceName
                        ?: request?.deviceDetails?.category?.label
                        ?: "Repair",
                    style = MaterialTheme.typography.titleSmall,
                )
                request?.let {
                    Text(
                        text = "${it.deviceDetails.brand} ${it.deviceDetails.model}".trim(),
                        style = MaterialTheme.typography.bodySmall,
                        color = FixoraTheme.extendedColors.textSecondary,
                    )
                }
                HorizontalDivider(color = FixoraTheme.extendedColors.border)
                request?.let { DetailRow("Reference", repairReference(it.id)) }
                DetailRow("Repair cost", formatPrice(amount))
                DetailRow("Service charge", formatPrice(0.0))
                HorizontalDivider(color = FixoraTheme.extendedColors.border)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Total due", style = MaterialTheme.typography.titleSmall)
                    Text(formatPrice(amount), style = MaterialTheme.typography.titleSmall)
                }
            }
        }

        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = FixoraTheme.extendedColors.accent,
                contentColor = FixoraTheme.extendedColors.onAccent,
            ),
        ) {
            Text("Choose payment method")
        }
    }
}

@Composable
private fun MethodPane(
    uiState: PaymentUiState,
    onMethodSelected: (PaymentMethod) -> Unit,
    onContinue: () -> Unit,
) {
    PaneScaffold(
        title = "How would you like to pay?",
        subtitle = "Both options are simulated. Neither takes a real payment.",
    ) {
        MethodOption(
            selected = uiState.method == PaymentMethod.CARD,
            title = "Card",
            description = "Enter demo card details on the next step.",
            icon = Icons.Rounded.CreditCard,
            onClick = { onMethodSelected(PaymentMethod.CARD) },
        )
        MethodOption(
            selected = uiState.method == PaymentMethod.CASH_ON_PICKUP,
            title = "Cash on pickup",
            description = "Recorded now, paid at the branch when you collect the device.",
            icon = Icons.Rounded.Payments,
            onClick = { onMethodSelected(PaymentMethod.CASH_ON_PICKUP) },
        )

        Button(
            onClick = onContinue,
            enabled = uiState.canContinueFromMethod,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = FixoraTheme.extendedColors.accent,
                contentColor = FixoraTheme.extendedColors.onAccent,
            ),
        ) {
            Text(if (uiState.method == PaymentMethod.CASH_ON_PICKUP) "Confirm" else "Continue")
        }
    }
}

@Composable
private fun MethodOption(
    selected: Boolean,
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FixoraSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = FixoraTheme.extendedColors.textSecondary,
                )
            }
            RadioButton(selected = selected, onClick = onClick)
        }
    }
}

@Composable
private fun DetailsPane(
    uiState: PaymentUiState,
    onCardNumberChange: (String) -> Unit,
    onExpiryChange: (String) -> Unit,
    onCvvChange: (String) -> Unit,
    onCardNameChange: (String) -> Unit,
    onSimulateFailureChange: (Boolean) -> Unit,
    onPayNow: () -> Unit,
) {
    val card = uiState.card
    val show = card.showErrors
    val amount = uiState.amount ?: return

    PaneScaffold(
        title = "Demo card details",
        subtitle = "Checked for format only. Nothing is sent anywhere and nothing is stored.",
    ) {
        OutlinedTextField(
            value = card.number,
            onValueChange = onCardNumberChange,
            label = { Text("Card number") },
            placeholder = { Text("4242 4242 4242 4242") },
            singleLine = true,
            isError = show && card.numberError != null,
            supportingText = { if (show) card.numberError?.let { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.sm)) {
            OutlinedTextField(
                value = card.expiry,
                onValueChange = onExpiryChange,
                label = { Text("Expiry") },
                placeholder = { Text("MM/YY") },
                singleLine = true,
                isError = show && card.expiryError != null,
                supportingText = { if (show) card.expiryError?.let { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = card.cvv,
                onValueChange = onCvvChange,
                label = { Text("CVV") },
                singleLine = true,
                isError = show && card.cvvError != null,
                supportingText = { if (show) card.cvvError?.let { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }
        OutlinedTextField(
            value = card.name,
            onValueChange = onCardNameChange,
            label = { Text("Name on card") },
            singleLine = true,
            isError = show && card.nameError != null,
            supportingText = { if (show) card.nameError?.let { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(FixoraSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.md),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Simulate a declined payment", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Turn this on to see the failure path. There is no real processor to decline it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = FixoraTheme.extendedColors.textSecondary,
                    )
                }
                Switch(checked = card.simulateFailure, onCheckedChange = onSimulateFailureChange)
            }
        }

        Button(
            onClick = onPayNow,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = FixoraTheme.extendedColors.accent,
                contentColor = FixoraTheme.extendedColors.onAccent,
            ),
        ) {
            Text("Pay ${formatPrice(amount)} (demo)")
        }
    }
}

@Composable
private fun ProcessingPane(uiState: PaymentUiState) {
    val amount = uiState.amount ?: return
    val progress by animateFloatAsState(targetValue = 0.82f, animationSpec = tween(1_500), label = "paymentProgress")
    val pulse = rememberInfiniteTransition(label = "paymentPulse")
    val pulseAlpha by pulse.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "paymentPulseAlpha",
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(FixoraSpacing.lg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedVisibility(visible = true, enter = fadeIn(tween(180)) + scaleIn(tween(180))) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.alpha(pulseAlpha))
        }
        Text(
            "Processing your demo payment",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = FixoraSpacing.md),
        )
        androidx.compose.material3.LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().padding(top = FixoraSpacing.md),
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            "Simulating a ${formatPrice(amount)} payment. Nothing is being charged.",
            style = MaterialTheme.typography.bodySmall,
            color = FixoraTheme.extendedColors.textSecondary,
        )
    }
}

@Composable
private fun ResultPane(
    uiState: PaymentUiState,
    onViewReceipt: () -> Unit,
    onRetry: () -> Unit,
) {
    val success = uiState.isPaid
    val tint: Color = if (success) FixoraTheme.extendedColors.success else MaterialTheme.colorScheme.error

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(FixoraSpacing.lg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedVisibility(visible = true, enter = fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.82f)) {
            Icon(
                imageVector = if (success) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(56.dp),
            )
        }
        Text(
            text = if (success) "Demo payment successful" else "Demo payment failed",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = FixoraSpacing.md),
        )
        Text(
            text = if (success) {
                "Your repair is marked completed and has moved into your repair history."
            } else {
                uiState.failureReason ?: "The payment didn't go through."
            },
            style = MaterialTheme.typography.bodySmall,
            color = FixoraTheme.extendedColors.textSecondary,
            modifier = Modifier.padding(top = FixoraSpacing.xs),
        )

        AnimatedVisibility(visible = success && uiState.statusWarning != null) {
            Text(
                text = uiState.statusWarning.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = FixoraTheme.extendedColors.warningOnSurface,
                modifier = Modifier.padding(top = FixoraSpacing.sm),
            )
        }

        if (success) {
            Button(
                onClick = onViewReceipt,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = FixoraSpacing.lg),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FixoraTheme.extendedColors.accent,
                    contentColor = FixoraTheme.extendedColors.onAccent,
                ),
            ) {
                Text("View receipt")
            }
        } else {
            Button(
                onClick = onRetry,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = FixoraSpacing.lg),
            ) {
                Text("Try again")
            }
        }
    }
}

@Composable
private fun ReceiptPane(uiState: PaymentUiState, onDone: () -> Unit) {
    val payment = uiState.payment
    PaneScaffold(
        title = "Receipt",
        subtitle = if (uiState.wasAlreadyPaid) {
            "This repair has already been paid for."
        } else {
            "Keep this reference if you need to ask about the repair."
        },
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier.padding(FixoraSpacing.md),
                verticalArrangement = Arrangement.spacedBy(FixoraSpacing.sm),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Paid (demo)", style = MaterialTheme.typography.titleSmall)
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = FixoraTheme.extendedColors.success,
                        modifier = Modifier.size(24.dp),
                    )
                }
                HorizontalDivider(color = FixoraTheme.extendedColors.border)
                payment?.let { DetailRow("Receipt", it.receiptId) }
                uiState.request?.let { DetailRow("Repair reference", repairReference(it.id)) }
                uiState.serviceName?.let { DetailRow("Service", it) }
                (payment?.amount ?: uiState.amount)?.let { DetailRow("Amount", formatPrice(it)) }
                DetailRow(
                    "Method",
                    when (payment?.method ?: uiState.method) {
                        PaymentMethod.CARD -> "Card (demo)"
                        PaymentMethod.CASH_ON_PICKUP -> "Cash on pickup"
                        null -> "—"
                    },
                )
                if (uiState.method == PaymentMethod.CARD && uiState.card.number.isNotBlank()) {
                    DetailRow("Card", "•••• ${DemoCard.lastFour(uiState.card.number)}")
                }
                formatDateTime(payment?.createdAt)?.let { DetailRow("Paid on", it) }
            }
        }

        Text(
            "This is a simulated receipt for a coursework demo. No payment was taken and no " +
                "card details were saved.",
            style = MaterialTheme.typography.labelMedium,
            color = FixoraTheme.extendedColors.textSecondary,
        )

        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text("Done")
        }
    }
}

@Composable
private fun PaymentSkeleton() {
    val transition = rememberInfiniteTransition(label = "paymentSkeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "paymentSkeletonAlpha",
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(FixoraSpacing.md),
        verticalArrangement = Arrangement.spacedBy(FixoraSpacing.sm),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(12.dp))
                .alpha(alpha)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .alpha(alpha)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
    }
}

@Composable
private fun PaymentBlocked(
    canRetry: Boolean,
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(FixoraSpacing.lg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Rounded.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.error,
        )
        Text(
            if (canRetry) "Couldn't open payment" else "Nothing to pay yet",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = FixoraSpacing.sm),
        )
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = FixoraTheme.extendedColors.textSecondary,
        )
        OutlinedButton(
            onClick = if (canRetry) onRetry else onBack,
            modifier = Modifier.padding(top = FixoraSpacing.md),
        ) {
            Text(if (canRetry) "Retry" else "Back to repair")
        }
    }
}
