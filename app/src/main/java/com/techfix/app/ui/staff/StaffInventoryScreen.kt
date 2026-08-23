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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.techfix.app.core.designsystem.FixoraSpacing
import com.techfix.app.core.designsystem.FixoraTheme
import com.techfix.app.domain.technician.Technician
import com.techfix.app.domain.catalog.DeviceCategory
import com.techfix.app.ui.customer.catalog.label

/**
 * Technician & Spare Parts — the combined two-tab screen from the
 * architecture doc's screen list.
 *
 * Admins get technician CRUD actions; Branch Managers and Technicians see the
 * same roster read-only. Stock is editable only for a role with
 * [StaffContext.canEditStock].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffInventoryScreen(
    staffContext: StaffContext,
    uiState: StaffInventoryUiState,
    onTabSelected: (StaffInventoryTab) -> Unit,
    onBranchSelected: (String) -> Unit,
    onStockChange: (String, Int) -> Unit,
    onOpenCreateTechnician: () -> Unit,
    onOpenEditTechnician: (Technician) -> Unit,
    onRequestArchiveTechnician: (Technician) -> Unit,
    onDismissTechnicianForm: () -> Unit,
    onTechnicianNameChange: (String) -> Unit,
    onTechnicianBranchChange: (String) -> Unit,
    onTechnicianSkillToggle: (DeviceCategory) -> Unit,
    onTechnicianAvailableChange: (Boolean) -> Unit,
    onSaveTechnician: () -> Unit,
    onDismissArchiveTechnician: () -> Unit,
    onConfirmArchiveTechnician: () -> Unit,
    onRetry: () -> Unit,
    onMessageShown: () -> Unit,
    onBack: () -> Unit,
    showTabs: Boolean = false,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var attentionOnly by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val message = uiState.actionError ?: uiState.confirmationMessage
    LaunchedEffect(message) {
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            onMessageShown()
        }
    }

    if (uiState.technicianFormVisible) {
        TechnicianFormSheet(
            uiState = uiState,
            onDismiss = onDismissTechnicianForm,
            onNameChange = onTechnicianNameChange,
            onBranchChange = onTechnicianBranchChange,
            onSkillToggle = onTechnicianSkillToggle,
            onAvailableChange = onTechnicianAvailableChange,
            onSave = onSaveTechnician,
        )
    }
    uiState.archiveTechnician?.let { technician ->
        AlertDialog(
            onDismissRequest = onDismissArchiveTechnician,
            title = { Text("Archive technician?") },
            text = {
                Text(
                    "${technician.name} will leave the active roster and assignment list. " +
                        "Existing repair history will keep the same technician reference."
                )
            },
            confirmButton = { TextButton(onClick = onConfirmArchiveTechnician) { Text("Archive") } },
            dismissButton = { TextButton(onClick = onDismissArchiveTechnician) { Text("Cancel") } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.tab == StaffInventoryTab.TECHNICIANS) "Technicians" else "Inventory") },
                actions = {
                    if (staffContext.role == com.techfix.app.core.navigation.UserRole.ADMIN && uiState.tab == StaffInventoryTab.TECHNICIANS) {
                        IconButton(onClick = onOpenCreateTechnician) {
                            Icon(Icons.Rounded.Add, contentDescription = "Create technician")
                        }
                    }
                },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (showTabs) {
                TabRow(
                    selectedTabIndex = StaffInventoryTab.entries.indexOf(uiState.tab),
                    containerColor = MaterialTheme.colorScheme.background,
                ) {
                    StaffInventoryTab.entries.forEach { tab ->
                        Tab(
                            selected = uiState.tab == tab,
                            onClick = { onTabSelected(tab) },
                            text = {
                                Text(
                                    when (tab) {
                                        StaffInventoryTab.TECHNICIANS -> "Technicians"
                                        StaffInventoryTab.PARTS -> "Spare parts"
                                    }
                                )
                            },
                        )
                    }
                }
            }

            // Only an Admin has more than one branch to switch between, so the
            // picker doesn't appear at all for anyone else.
            if (uiState.canSwitchBranch) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = FixoraSpacing.md, vertical = FixoraSpacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.sm),
                ) {
                    uiState.branches.forEach { branch ->
                        FilterChip(
                            selected = uiState.selectedBranchId == branch.id,
                            onClick = { onBranchSelected(branch.id) },
                            label = { Text(branch.name) },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = FixoraSpacing.md, vertical = FixoraSpacing.sm),
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                placeholder = { Text(if (uiState.tab == StaffInventoryTab.TECHNICIANS) "Search technicians" else "Search parts") },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
            )
            if (uiState.tab == StaffInventoryTab.PARTS) {
                Row(Modifier.fillMaxWidth().padding(horizontal = FixoraSpacing.md), horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = attentionOnly, onClick = { attentionOnly = !attentionOnly }, label = { Text("Out of stock") })
                    if (!staffContext.canEditStock) {
                        Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(18.dp), tint = FixoraTheme.extendedColors.textSecondary)
                        Text("Secure read-only inventory", style = MaterialTheme.typography.labelMedium, color = FixoraTheme.extendedColors.textSecondary)
                    }
                }
            }

            Crossfade(
                targetState = when {
                    uiState.isLoading -> InventoryPane.LOADING
                    uiState.errorMessage != null -> InventoryPane.ERROR
                    else -> InventoryPane.CONTENT
                },
                animationSpec = tween(220),
                label = "inventoryPane",
                modifier = Modifier.fillMaxSize(),
            ) { pane ->
                when (pane) {
                    InventoryPane.LOADING -> InventorySkeleton()
                    InventoryPane.ERROR -> InventoryError(
                        message = uiState.errorMessage ?: "Something went wrong.",
                        onRetry = onRetry,
                    )
                    InventoryPane.CONTENT -> when (uiState.tab) {
                        StaffInventoryTab.TECHNICIANS -> TechnicianList(
                            technicians = uiState.technicians.filter { technician ->
                                query.isBlank() || listOf(
                                    technician.name,
                                    technician.branchId,
                                    technician.categorySkills.joinToString(" ") { it.name },
                                ).any { it.contains(query.trim(), true) }
                            },
                            details = uiState.technicianDetails,
                            canManage = staffContext.role == com.techfix.app.core.navigation.UserRole.ADMIN,
                            onEdit = onOpenEditTechnician,
                            onArchive = onRequestArchiveTechnician,
                        )
                        StaffInventoryTab.PARTS -> PartsList(
                            rows = uiState.parts.filter { row ->
                                (!attentionOnly || row.quantity == 0) &&
                                    (query.isBlank() || listOf(row.part.name, row.part.category).any { it.contains(query.trim(), true) })
                            },
                            canEdit = staffContext.canEditStock,
                            savingPartId = uiState.savingPartId,
                            onStockChange = onStockChange,
                        )
                    }
                }
            }
        }
    }
}

private enum class InventoryPane { LOADING, ERROR, CONTENT }

@Composable
private fun TechnicianList(
    technicians: List<Technician>,
    details: Map<String, TechnicianRosterDetails>,
    canManage: Boolean,
    onEdit: (Technician) -> Unit,
    onArchive: (Technician) -> Unit,
) {
    if (technicians.isEmpty()) {
        InventoryEmpty("No technicians at this branch", "The roster is managed in Firestore.")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(FixoraSpacing.md),
        verticalArrangement = Arrangement.spacedBy(FixoraSpacing.sm),
    ) {
        items(technicians, key = { it.id }) { technician ->
            val rosterDetails = details[technician.id] ?: TechnicianRosterDetails()
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(FixoraSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(FixoraSpacing.sm),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.sm),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                technician.name.trim().split(Regex("\\s+")).take(2)
                                    .mapNotNull { it.firstOrNull()?.uppercase() }
                                    .joinToString(""),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(technician.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                rosterDetails.email ?: if (canManage) "No linked email" else
                                    if (technician.branchId == "colombo") "Colombo branch" else "Galle branch",
                                style = MaterialTheme.typography.bodySmall,
                                color = FixoraTheme.extendedColors.textSecondary,
                            )
                        }
                        if (canManage) {
                            IconButton(onClick = { onEdit(technician) }) {
                                Icon(Icons.Rounded.Edit, contentDescription = "Edit ${technician.name}")
                            }
                            IconButton(onClick = { onArchive(technician) }) {
                                Icon(Icons.Rounded.Archive, contentDescription = "Archive ${technician.name}")
                            }
                        }
                    }
                    Text(
                        (if (technician.branchId == "colombo") "Colombo" else "Galle") + " · " +
                            technician.categorySkills.joinToString(", ") { it.label },
                        style = MaterialTheme.typography.bodySmall,
                        color = FixoraTheme.extendedColors.textSecondary,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.xs)) {
                        TechnicianBadge("ACTIVE", MaterialTheme.colorScheme.primary)
                        TechnicianBadge(
                            if (technician.available) "AVAILABLE" else "BUSY",
                            if (technician.available) FixoraTheme.extendedColors.success else FixoraTheme.extendedColors.warning,
                        )
                    }
                    if (canManage) {
                        Row(horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.xs)) {
                            TechnicianBadge(
                                if (rosterDetails.accountLinked) "ACCOUNT LINKED" else "ACCOUNT ERROR",
                                if (rosterDetails.accountLinked) FixoraTheme.extendedColors.success else MaterialTheme.colorScheme.error,
                            )
                            TechnicianBadge(
                                "${rosterDetails.assignedRepairCount} ASSIGNED",
                                FixoraTheme.extendedColors.textSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TechnicianBadge(label: String, color: androidx.compose.ui.graphics.Color) {
    Surface(
        color = color.copy(alpha = 0.12f),
        contentColor = color,
        shape = RoundedCornerShape(20.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = FixoraSpacing.sm, vertical = FixoraSpacing.xs),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun PartsList(
    rows: List<PartStockRow>,
    canEdit: Boolean,
    savingPartId: String?,
    onStockChange: (String, Int) -> Unit,
) {
    if (rows.isEmpty()) {
        InventoryEmpty("No spare parts", "The parts catalogue is set up in Supabase.")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(FixoraSpacing.md),
        verticalArrangement = Arrangement.spacedBy(FixoraSpacing.sm),
    ) {
        items(rows, key = { it.part.id }) { row ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(FixoraSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.sm),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(row.part.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${row.part.category} · fits " +
                                row.part.compatibleCategories.joinToString(", ") { it.label },
                            style = MaterialTheme.typography.bodySmall,
                            color = FixoraTheme.extendedColors.textSecondary,
                        )
                        Text(
                            text = if (row.quantity == 0) "Out of stock" else "${row.quantity} in stock",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (row.quantity == 0) {
                                FixoraTheme.extendedColors.warning
                            } else {
                                FixoraTheme.extendedColors.success
                            },
                        )
                    }

                    if (savingPartId == row.part.id) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    } else if (canEdit) {
                        StockStepper(
                            quantity = row.quantity,
                            onChange = { next -> onStockChange(row.part.id, next) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Stock is corrected one unit at a time rather than through a free-text
 * field: it is the whole of the write path, and a stepper can't produce an
 * invalid value (the Postgres check constraint won't accept a negative
 * quantity either).
 */
@Composable
private fun StockStepper(quantity: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onChange(quantity - 1) }, enabled = quantity > 0) {
            Icon(Icons.Rounded.Remove, contentDescription = "Decrease stock")
        }
        Text(quantity.toString(), style = MaterialTheme.typography.titleSmall)
        IconButton(onClick = { onChange(quantity + 1) }) {
            Icon(Icons.Rounded.Add, contentDescription = "Increase stock")
        }
    }
}

@Composable
private fun InventorySkeleton() {
    val transition = rememberInfiniteTransition(label = "inventorySkeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "inventorySkeletonAlpha",
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(FixoraSpacing.md),
        verticalArrangement = Arrangement.spacedBy(FixoraSpacing.sm),
    ) {
        repeat(6) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(76.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .alpha(alpha)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
    }
}

@Composable
private fun InventoryEmpty(title: String, message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(FixoraSpacing.lg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = FixoraTheme.extendedColors.textSecondary,
        )
    }
}

@Composable
private fun InventoryError(message: String, onRetry: () -> Unit) {
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
            "Couldn't load inventory",
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TechnicianFormSheet(
    uiState: StaffInventoryUiState,
    onDismiss: () -> Unit,
    onNameChange: (String) -> Unit,
    onBranchChange: (String) -> Unit,
    onSkillToggle: (DeviceCategory) -> Unit,
    onAvailableChange: (Boolean) -> Unit,
    onSave: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = FixoraSpacing.lg, vertical = FixoraSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(FixoraSpacing.md),
        ) {
            Text(if (uiState.editingTechnicianId == null) "Create technician" else "Edit technician", style = MaterialTheme.typography.headlineSmall)
            Text("Keep the roster accurate so branch matching uses the right skills and availability.", style = MaterialTheme.typography.bodySmall, color = FixoraTheme.extendedColors.textSecondary)
            OutlinedTextField(value = uiState.formName, onValueChange = onNameChange, label = { Text("Name") }, singleLine = true, isError = uiState.formError?.contains("Name") == true, modifier = Modifier.fillMaxWidth())
            Text("Branch", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.sm)) {
                uiState.branches.forEach { branch ->
                    FilterChip(selected = uiState.formBranchId == branch.id, onClick = { onBranchChange(branch.id) }, label = { Text(branch.name) })
                }
            }
            Text("Category skills", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.sm)) {
                DeviceCategory.entries.take(2).forEach { category ->
                    FilterChip(selected = category in uiState.formSkills, onClick = { onSkillToggle(category) }, label = { Text(category.name.lowercase().replaceFirstChar(Char::uppercaseChar)) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.sm)) {
                DeviceCategory.entries.drop(2).forEach { category ->
                    FilterChip(selected = category in uiState.formSkills, onClick = { onSkillToggle(category) }, label = { Text(category.name.lowercase().replaceFirstChar(Char::uppercaseChar)) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.md)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Available for new repairs", style = MaterialTheme.typography.titleSmall)
                    Text("Used by automatic branch matching", style = MaterialTheme.typography.bodySmall, color = FixoraTheme.extendedColors.textSecondary)
                }
                Switch(checked = uiState.formAvailable, onCheckedChange = onAvailableChange)
            }
            uiState.formError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = onSave,
                enabled = !uiState.savingTechnician,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = FixoraTheme.extendedColors.accent, contentColor = FixoraTheme.extendedColors.onAccent),
            ) {
                if (uiState.savingTechnician) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = FixoraTheme.extendedColors.onAccent) else Text(if (uiState.editingTechnicianId == null) "Create technician" else "Save changes")
            }
        }
    }
}
