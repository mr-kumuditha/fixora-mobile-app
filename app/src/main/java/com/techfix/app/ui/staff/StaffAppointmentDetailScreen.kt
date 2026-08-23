package com.techfix.app.ui.staff

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lock
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.techfix.app.core.designsystem.FixoraSpacing
import com.techfix.app.core.designsystem.FixoraRadius
import com.techfix.app.core.designsystem.FixoraTheme
import com.techfix.app.domain.matching.BranchMatch
import com.techfix.app.domain.repair.RepairRequest
import com.techfix.app.domain.repair.RepairStatus
import com.techfix.app.domain.technician.Technician
import com.techfix.app.ui.customer.catalog.label
import com.techfix.app.ui.customer.catalog.icon
import com.techfix.app.ui.customer.repair.DetailRow
import com.techfix.app.ui.customer.repair.RepairStatusChip
import com.techfix.app.ui.customer.repair.formatDate
import com.techfix.app.ui.customer.repair.formatDateTime
import com.techfix.app.ui.customer.repair.formatPrice
import com.techfix.app.ui.customer.repair.description as statusDescription
import com.techfix.app.ui.customer.repair.label as statusLabel
import com.techfix.app.ui.customer.repair.repairReference

/**
 * Appointment Detail / Assignment plus the status-advance action.
 *
 * The assignment section is gated on [StaffContext.canAssign] — a Technician
 * sees the same screen but reads the assignment rather than making it. The
 * branch options carry the technician and spare-part counts the Block 5
 * matching rule scored, so confirming a branch is an informed decision rather
 * than a dropdown of names.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffAppointmentDetailScreen(
    staffContext: StaffContext,
    uiState: StaffAppointmentDetailUiState,
    onRetry: () -> Unit,
    onBranchSelected: (String) -> Unit,
    onTechnicianSelected: (String) -> Unit,
    onConfirmAssignment: () -> Unit,
    onAdvanceStatus: () -> Unit,
    onMessageShown: () -> Unit,
    onBack: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val message = uiState.actionError ?: uiState.confirmationMessage
    LaunchedEffect(message) {
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            onMessageShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Appointment") },
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Crossfade(
            targetState = when {
                uiState.isLoading -> DetailPane.LOADING
                uiState.request == null -> DetailPane.ERROR
                else -> DetailPane.CONTENT
            },
            animationSpec = tween(220),
            label = "appointmentPane",
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) { pane ->
            when (pane) {
                DetailPane.LOADING -> DetailSkeleton()
                DetailPane.ERROR -> DetailError(
                    message = uiState.errorMessage ?: "This appointment couldn't be found.",
                    onRetry = onRetry,
                )
                DetailPane.CONTENT -> uiState.request?.let { request ->
                    DetailContent(
                        staffContext = staffContext,
                        uiState = uiState,
                        request = request,
                        onBranchSelected = onBranchSelected,
                        onTechnicianSelected = onTechnicianSelected,
                        onConfirmAssignment = onConfirmAssignment,
                        onAdvanceStatus = onAdvanceStatus,
                    )
                }
            }
        }
    }
}

private enum class DetailPane { LOADING, ERROR, CONTENT }

@Composable
private fun DetailContent(
    staffContext: StaffContext,
    uiState: StaffAppointmentDetailUiState,
    request: RepairRequest,
    onBranchSelected: (String) -> Unit,
    onTechnicianSelected: (String) -> Unit,
    onConfirmAssignment: () -> Unit,
    onAdvanceStatus: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(FixoraSpacing.md),
        verticalArrangement = Arrangement.spacedBy(FixoraSpacing.md),
    ) {
        AppointmentHeroCard(request)
        RequestCard(uiState, request)

        if (request.imageUrls.isNotEmpty()) {
            PhotosCard(request)
        }

        if (staffContext.canAssign) {
            AssignmentCard(
                uiState = uiState,
                onBranchSelected = onBranchSelected,
                onTechnicianSelected = onTechnicianSelected,
                onConfirmAssignment = onConfirmAssignment,
            )
        } else {
            ReadOnlyAssignmentCard(uiState)
        }

        StatusCard(uiState, request, onAdvanceStatus)
    }
}

@Composable
private fun AppointmentHeroCard(request: RepairRequest) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(FixoraSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.md),
        ) {
            Box(modifier = Modifier.size(52.dp).clip(RoundedCornerShape(FixoraRadius.card)).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)), contentAlignment = Alignment.Center) {
                Icon(request.deviceDetails.category.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FixoraSpacing.xs)) {
                Text("Appointment ${repairReference(request.id)}", style = MaterialTheme.typography.titleSmall)
                Text("${request.deviceDetails.brand} ${request.deviceDetails.model}".trim().ifBlank { request.deviceDetails.category.label }, style = MaterialTheme.typography.bodySmall, color = FixoraTheme.extendedColors.textSecondary)
            }
            RepairStatusChip(status = request.status)
        }
    }
}

@Composable
private fun RequestCard(uiState: StaffAppointmentDetailUiState, request: RepairRequest) {
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${request.deviceDetails.brand} ${request.deviceDetails.model}".trim()
                            .ifBlank { request.deviceDetails.category.label },
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = uiState.serviceName ?: request.deviceDetails.category.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = FixoraTheme.extendedColors.textSecondary,
                    )
                }
                RepairStatusChip(status = request.status)
            }

            Text(request.issueDescription, style = MaterialTheme.typography.bodyMedium)

            HorizontalDivider(color = FixoraTheme.extendedColors.border)

            DetailRow("Reference", repairReference(request.id))
            DetailRow("Device", request.deviceDetails.category.label)
            request.deviceDetails.serialNumber?.takeIf { it.isNotBlank() }?.let {
                DetailRow("Serial", it)
            }
            uiState.servicePrice?.let { DetailRow("Estimated cost", formatPrice(it)) }
            formatDate(request.createdAt)?.let { DetailRow("Booked", it) }
            formatDateTime(request.scheduledAt)?.let { DetailRow("Drop-off", it) }
        }
    }
}

@Composable
private fun PhotosCard(request: RepairRequest) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(vertical = FixoraSpacing.md)) {
            Text(
                "Customer photos",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = FixoraSpacing.md),
            )
            LazyRow(
                contentPadding = PaddingValues(
                    horizontal = FixoraSpacing.md,
                    vertical = FixoraSpacing.sm,
                ),
                horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.sm),
            ) {
                items(request.imageUrls) { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(88.dp)
                            .clip(RoundedCornerShape(FixoraSpacing.sm))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                }
            }
        }
    }
}

@Composable
private fun AssignmentCard(
    uiState: StaffAppointmentDetailUiState,
    onBranchSelected: (String) -> Unit,
    onTechnicianSelected: (String) -> Unit,
    onConfirmAssignment: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(FixoraSpacing.md),
            verticalArrangement = Arrangement.spacedBy(FixoraSpacing.sm),
        ) {
            Text("Confirm branch", style = MaterialTheme.typography.titleSmall)
            Text(
                "Ranked on technician and spare-part cover for this device, the same way the " +
                    "customer's booking was matched.",
                style = MaterialTheme.typography.bodySmall,
                color = FixoraTheme.extendedColors.textSecondary,
            )

            if (uiState.branchMatches.isEmpty()) {
                Text(
                    "Branch availability couldn't be loaded, so this appointment can't be " +
                        "assigned right now.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                uiState.branchMatches.forEachIndexed { index, match ->
                    BranchOption(
                        match = match,
                        selected = uiState.selectedBranchId == match.branch.id,
                        isBestMatch = index == 0,
                        onClick = { onBranchSelected(match.branch.id) },
                    )
                }

                HorizontalDivider(color = FixoraTheme.extendedColors.border)

                Text("Assign technician", style = MaterialTheme.typography.titleSmall)
                val technicians = uiState.technicianOptions
                if (technicians.isEmpty()) {
                    Text(
                        "No available technician at this branch holds the skill for this device. " +
                            "Pick another branch, or free someone up first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = FixoraTheme.extendedColors.warningOnSurface,
                    )
                } else {
                    technicians.forEach { technician ->
                        TechnicianOption(
                            technician = technician,
                            selected = uiState.selectedTechnicianId == technician.id,
                            onClick = { onTechnicianSelected(technician.id) },
                        )
                    }
                }

                Button(
                    onClick = onConfirmAssignment,
                    enabled = uiState.canConfirmAssignment,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FixoraTheme.extendedColors.accent,
                        contentColor = FixoraTheme.extendedColors.onAccent,
                    ),
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = FixoraTheme.extendedColors.onAccent,
                        )
                    } else {
                        Text(
                            if (uiState.request?.status == RepairStatus.SUBMITTED) {
                                "Confirm appointment"
                            } else {
                                "Update assignment"
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadOnlyAssignmentCard(uiState: StaffAppointmentDetailUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(FixoraSpacing.md),
            verticalArrangement = Arrangement.spacedBy(FixoraSpacing.sm),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = FixoraTheme.extendedColors.textSecondary,
                    modifier = Modifier.size(20.dp),
                )
                Text("Assignment", style = MaterialTheme.typography.titleSmall)
            }
            Text(
                "Confirming the branch and naming a technician is a Branch Manager action.",
                style = MaterialTheme.typography.bodySmall,
                color = FixoraTheme.extendedColors.textSecondary,
            )
            DetailRow(
                "Branch",
                uiState.selectedMatch?.branch?.name ?: uiState.request?.branchId.orEmpty(),
            )
            DetailRow(
                "Technician",
                uiState.technicianOptions
                    .firstOrNull { it.id == uiState.request?.technicianId }
                    ?.name
                    ?: "Not assigned yet",
            )
        }
    }
}

@Composable
private fun BranchOption(
    match: BranchMatch,
    selected: Boolean,
    isBestMatch: Boolean,
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
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FixoraSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.sm),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(match.branch.name, style = MaterialTheme.typography.titleSmall)
                    if (isBestMatch) {
                        Text(
                            "Best match",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Text(
                    text = "${match.availableTechnicians.size} technician(s) free · " +
                        "${match.partsInStock.size}/${match.totalPartsTracked} parts in stock",
                    style = MaterialTheme.typography.bodySmall,
                    color = FixoraTheme.extendedColors.textSecondary,
                )
                if (!match.canHandleNow) {
                    Text(
                        text = if (!match.hasTechnician) {
                            "No qualified technician free here right now"
                        } else {
                            "No compatible spare part in stock here right now"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = FixoraTheme.extendedColors.warningOnSurface,
                    )
                }
            }
            RadioButton(selected = selected, onClick = onClick)
        }
    }
}

@Composable
private fun TechnicianOption(technician: Technician, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick)
            .padding(vertical = FixoraSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.sm),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(modifier = Modifier.weight(1f)) {
            Text(technician.name, style = MaterialTheme.typography.bodyMedium)
            Text(
                technician.categorySkills.joinToString(", ") { it.label },
                style = MaterialTheme.typography.labelMedium,
                color = FixoraTheme.extendedColors.textSecondary,
            )
        }
    }
}

@Composable
private fun StatusCard(
    uiState: StaffAppointmentDetailUiState,
    request: RepairRequest,
    onAdvanceStatus: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(FixoraSpacing.md),
            verticalArrangement = Arrangement.spacedBy(FixoraSpacing.sm),
        ) {
            Text("Repair status", style = MaterialTheme.typography.titleSmall)
            Text(
                request.status.statusDescription,
                style = MaterialTheme.typography.bodySmall,
                color = FixoraTheme.extendedColors.textSecondary,
            )

            val next = uiState.nextStage
            when {
                next != null -> Button(
                    onClick = onAdvanceStatus,
                    enabled = !uiState.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text("Move to ${next.statusLabel}")
                    }
                }

                request.status == RepairStatus.SUBMITTED -> Text(
                    "Confirm the appointment first — assigning a technician is what moves it on.",
                    style = MaterialTheme.typography.bodySmall,
                    color = FixoraTheme.extendedColors.textSecondary,
                )

                request.status == RepairStatus.READY_FOR_PICKUP -> Text(
                    "Waiting on the customer's payment. Completing the repair is theirs to do, " +
                        "not a staff action.",
                    style = MaterialTheme.typography.bodySmall,
                    color = FixoraTheme.extendedColors.textSecondary,
                )

                else -> Text(
                    "This repair is finished.",
                    style = MaterialTheme.typography.bodySmall,
                    color = FixoraTheme.extendedColors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun DetailSkeleton() {
    val transition = rememberInfiniteTransition(label = "appointmentSkeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "appointmentSkeletonAlpha",
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
                .height(180.dp)
                .clip(RoundedCornerShape(12.dp))
                .alpha(alpha)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(RoundedCornerShape(12.dp))
                .alpha(alpha)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
    }
}

@Composable
private fun DetailError(message: String, onRetry: () -> Unit) {
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
            "Couldn't load this appointment",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = FixoraSpacing.sm),
        )
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = FixoraTheme.extendedColors.textSecondary,
        )
        OutlinedButton(onClick = onRetry, modifier = Modifier.padding(top = FixoraSpacing.md)) {
            Text("Retry")
        }
    }
}
