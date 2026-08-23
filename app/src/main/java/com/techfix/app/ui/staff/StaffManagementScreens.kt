package com.techfix.app.ui.staff

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.techfix.app.core.designsystem.FixoraRadius
import com.techfix.app.core.designsystem.FixoraSpacing
import com.techfix.app.core.designsystem.FixoraTheme
import com.techfix.app.core.navigation.UserRole
import com.techfix.app.domain.operations.OperationalSnapshot
import com.techfix.app.domain.payment.PaymentStatus
import com.techfix.app.domain.payment.Payment
import com.techfix.app.domain.catalog.DeviceCategory
import com.techfix.app.ui.customer.repair.formatPrice
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Calendar
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OperationsScaffold(
    title: String,
    state: StaffOperationsUiState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    content: @Composable (OperationalSnapshot) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> StaffListSkeleton()
                state.errorMessage != null -> StaffLoadError(state.errorMessage, onRetry)
                else -> content(state.snapshot)
            }
        }
    }
}

@Composable
fun StaffBranchesScreen(staffContext: StaffContext, state: StaffOperationsUiState, onRetry: () -> Unit, onBack: () -> Unit) {
    OperationsScaffold("Branches", state, onRetry, onBack) { snapshot ->
        LazyColumn(contentPadding = PaddingValues(FixoraSpacing.md), verticalArrangement = Arrangement.spacedBy(FixoraSpacing.sm)) {
            items(snapshot.branchPerformance, key = { it.branch.id }) { row ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(FixoraSpacing.md), verticalArrangement = Arrangement.spacedBy(FixoraSpacing.sm)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.sm)) {
                            Icon(Icons.Rounded.LocationOn, null, tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f)) {
                                Text(row.branch.name, style = MaterialTheme.typography.titleMedium)
                                Text(row.branch.address, style = MaterialTheme.typography.bodySmall, color = FixoraTheme.extendedColors.textSecondary)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.sm)) {
                            CompactMetric("Repairs", row.totalRepairs.toString(), Modifier.weight(1f))
                            CompactMetric("Open", row.openRepairs.toString(), Modifier.weight(1f))
                            CompactMetric("Available", row.availableTechnicians.toString(), Modifier.weight(1f))
                        }
                        if (staffContext.role == UserRole.ADMIN) {
                            Text("Recorded demo revenue ${formatPrice(row.recordedRevenue)}", style = MaterialTheme.typography.labelMedium)
                        }
                        Text("${row.outOfStockParts} out-of-stock items", style = MaterialTheme.typography.labelMedium, color = if (row.outOfStockParts > 0) FixoraTheme.extendedColors.warning else FixoraTheme.extendedColors.success)
                    }
                }
            }
        }
    }
}

@Composable
fun StaffUsersScreen(
    staffContext: StaffContext,
    state: StaffOperationsUiState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    if (staffContext.role != UserRole.ADMIN) {
        StaffAccessDenied(onBack)
        return
    }
    var query by rememberSaveable { mutableStateOf("") }
    var role by rememberSaveable { mutableStateOf<UserRole?>(null) }
    OperationsScaffold("Users", state, onRetry, onBack) { snapshot ->
        val filtered = snapshot.users.filter { user ->
            (role == null || user.role == role) &&
                (query.isBlank() || listOfNotNull(user.name, user.email).any { it.contains(query.trim(), true) })
        }
        Column(Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(FixoraSpacing.md),
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                placeholder = { Text("Search users") },
                singleLine = true,
                shape = RoundedCornerShape(FixoraRadius.input),
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = FixoraSpacing.md), horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.sm)) {
                listOf(null, UserRole.CUSTOMER, UserRole.TECHNICIAN, UserRole.BRANCH_MANAGER, UserRole.ADMIN).forEach { item ->
                    FilterChip(selected = role == item, onClick = { role = item }, label = { Text(item?.name?.replace('_', ' ')?.lowercase()?.replaceFirstChar(Char::uppercaseChar) ?: "All") })
                }
            }
            if (filtered.isEmpty()) {
                StaffEmpty("No matching users", "Try a different search or role filter.")
            } else LazyColumn(contentPadding = PaddingValues(FixoraSpacing.md), verticalArrangement = Arrangement.spacedBy(FixoraSpacing.sm)) {
                items(filtered, key = { it.uid }) { user ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(Modifier.fillMaxWidth().padding(FixoraSpacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.md)) {
                            Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                                Text(initials(user.name ?: user.email.orEmpty()), color = MaterialTheme.colorScheme.primary)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(user.name ?: "Fixora user", style = MaterialTheme.typography.titleSmall)
                                Text(user.email.orEmpty(), style = MaterialTheme.typography.bodySmall, color = FixoraTheme.extendedColors.textSecondary)
                                Text(listOfNotNull(user.role.name.replace('_', ' '), user.branchId).joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StaffReportsScreen(
    staffContext: StaffContext,
    state: StaffOperationsUiState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    if (staffContext.role != UserRole.ADMIN) {
        StaffAccessDenied(onBack)
        return
    }
    var period by rememberSaveable { mutableStateOf(ReportPeriod.ALL) }
    var branchId by rememberSaveable { mutableStateOf<String?>(null) }
    var category by rememberSaveable { mutableStateOf<DeviceCategory?>(null) }
    var technicianId by rememberSaveable { mutableStateOf<String?>(null) }
    OperationsScaffold("Reports", state, onRetry, onBack) { snapshot ->
        val start = period.startMillis()
        val branchRepairIds = snapshot.repairs.filter { repair ->
            (branchId == null || repair.branchId == branchId) &&
                (category == null || repair.deviceDetails.category == category) &&
                (technicianId == null || repair.technicianId == technicianId)
        }.mapTo(hashSetOf()) { it.id }
        val filtered = snapshot.copy(
            repairs = snapshot.repairs.filter { repair ->
                repair.id in branchRepairIds && (start == null || (repair.createdAt ?: Long.MIN_VALUE) >= start)
            },
            payments = snapshot.payments.filter { payment ->
                payment.repairRequestId in branchRepairIds && (start == null || (payment.createdAt ?: Long.MIN_VALUE) >= start)
            },
            technicians = snapshot.technicians.filter { branchId == null || it.branchId == branchId },
        )
        val metrics = filtered.metrics
        LazyColumn(contentPadding = PaddingValues(FixoraSpacing.md), verticalArrangement = Arrangement.spacedBy(FixoraSpacing.md)) {
            item {
                Text("Filters", style = MaterialTheme.typography.titleMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.sm)) {
                    items(ReportPeriod.entries) { option ->
                        FilterChip(selected = period == option, onClick = { period = option }, label = { Text(option.label) })
                    }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.sm)) {
                    item { FilterChip(selected = category == null, onClick = { category = null }, label = { Text("All services") }) }
                    items(DeviceCategory.entries) { option ->
                        FilterChip(selected = category == option, onClick = { category = option }, label = { Text(option.name.lowercase().replaceFirstChar(Char::uppercaseChar)) })
                    }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.sm)) {
                    item { FilterChip(selected = technicianId == null, onClick = { technicianId = null }, label = { Text("All technicians") }) }
                    items(snapshot.technicians, key = { it.id }) { technician ->
                        FilterChip(selected = technicianId == technician.id, onClick = { technicianId = technician.id }, label = { Text(technician.name) })
                    }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.sm)) {
                    item { FilterChip(selected = branchId == null, onClick = { branchId = null }, label = { Text("All branches") }) }
                    items(snapshot.branches, key = { it.id }) { branch ->
                        FilterChip(selected = branchId == branch.id, onClick = { branchId = branch.id }, label = { Text(branch.name) })
                    }
                }
            }
            item {
                Text("Recorded revenue", style = MaterialTheme.typography.titleMedium)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(FixoraSpacing.md), verticalArrangement = Arrangement.spacedBy(FixoraSpacing.xs)) {
                        Text(formatPrice(metrics.recordedRevenue), style = MaterialTheme.typography.headlineMedium)
                        Text("Successful simulated payment receipts", style = MaterialTheme.typography.bodySmall, color = FixoraTheme.extendedColors.textSecondary)
                        RevenueBars(filtered.payments.filter { it.status == PaymentStatus.SUCCESS })
                    }
                }
            }
            item {
                Text("Repairs", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.sm)) {
                    CompactMetric("Total", metrics.totalRepairs.toString(), Modifier.weight(1f))
                    CompactMetric("Completed", metrics.completedRepairs.toString(), Modifier.weight(1f))
                    CompactMetric("Cancelled", metrics.cancelledRepairs.toString(), Modifier.weight(1f))
                }
            }
            item {
                val successful = filtered.payments.filter { it.status == PaymentStatus.SUCCESS }
                Text("Recent successful payments", style = MaterialTheme.typography.titleMedium)
                if (successful.isEmpty()) Text("No successful payment history yet.", color = FixoraTheme.extendedColors.textSecondary)
                else successful.take(6).forEach { payment ->
                    Row(Modifier.fillMaxWidth().padding(vertical = FixoraSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                        Text(payment.receiptId, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Column(horizontalAlignment = Alignment.End) {
                            Text(formatPrice(payment.amount), style = MaterialTheme.typography.titleSmall)
                            Text(payment.createdAt?.let { SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(it)) }.orEmpty(), style = MaterialTheme.typography.labelMedium, color = FixoraTheme.extendedColors.textSecondary)
                        }
                    }
                }
            }
        }
    }
}

private enum class ReportPeriod(val label: String) {
    MONTH("This month"), DAYS_90("90 days"), ALL("All time");

    fun startMillis(): Long? = when (this) {
        ALL -> null
        DAYS_90 -> System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000
        MONTH -> Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}

@Composable
private fun RevenueBars(payments: List<Payment>) {
    val formatter = SimpleDateFormat("MMM", Locale.getDefault())
    val buckets = payments.filter { it.createdAt != null }
        .groupBy { formatter.format(Date(it.createdAt!!)) }
        .mapValues { (_, rows) -> rows.sumOf { it.amount } }
        .entries.toList().take(6).reversed()
    if (buckets.isEmpty()) return
    val maximum = buckets.maxOf { it.value }.coerceAtLeast(1.0)
    Row(
        Modifier.fillMaxWidth().height(112.dp).padding(top = FixoraSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.sm),
        verticalAlignment = Alignment.Bottom,
    ) {
        buckets.forEach { bucket ->
            val height = (72 * (bucket.value / maximum)).roundToInt().coerceAtLeast(8).dp
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                Box(Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primary))
                Text(bucket.key, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffMoreScreen(
    staffContext: StaffContext,
    onBranches: () -> Unit,
    onUsers: () -> Unit,
    onReports: () -> Unit,
    onSignOut: () -> Unit,
) {
    Scaffold(topBar = { TopAppBar(title = { Text("More") }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)) }, containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(FixoraSpacing.md), verticalArrangement = Arrangement.spacedBy(FixoraSpacing.sm)) {
            MoreRow(Icons.Rounded.LocationOn, if (staffContext.role == UserRole.ADMIN) "Branches" else "My branch", onBranches)
            if (staffContext.role == UserRole.ADMIN) {
                MoreRow(Icons.Rounded.Groups, "Users", onUsers)
                MoreRow(Icons.Rounded.Assessment, "Reports", onReports)
            }
            MoreRow(Icons.Rounded.Person, "Sign out", onSignOut)
        }
    }
}

@Composable
private fun MoreRow(icon: ImageVector, title: String, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(FixoraSpacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.md)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = FixoraTheme.extendedColors.textSecondary)
        }
    }
}

@Composable
private fun CompactMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(FixoraRadius.card)).background(MaterialTheme.colorScheme.surfaceVariant).padding(FixoraSpacing.sm)) {
        Column { Text(value, style = MaterialTheme.typography.titleMedium); Text(label, style = MaterialTheme.typography.labelMedium, color = FixoraTheme.extendedColors.textSecondary) }
    }
}

@Composable private fun StaffListSkeleton() = Column(Modifier.fillMaxSize().padding(FixoraSpacing.md), verticalArrangement = Arrangement.spacedBy(FixoraSpacing.sm)) { repeat(6) { Box(Modifier.fillMaxWidth().height(84.dp).clip(RoundedCornerShape(FixoraRadius.card)).background(MaterialTheme.colorScheme.surfaceVariant)) } }
@Composable private fun StaffLoadError(message: String, onRetry: () -> Unit) = Column(Modifier.fillMaxSize().padding(FixoraSpacing.lg), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp)); Text(message, modifier = Modifier.padding(FixoraSpacing.sm)); OutlinedButton(onClick = onRetry) { Text("Try again") } }
@Composable private fun StaffEmpty(title: String, message: String) = Column(Modifier.fillMaxSize().padding(FixoraSpacing.lg), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Text(title, style = MaterialTheme.typography.titleMedium); Text(message, color = FixoraTheme.extendedColors.textSecondary) }
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun StaffAccessDenied(onBack: () -> Unit) = Scaffold(topBar = { TopAppBar(title = { Text("Access unavailable") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } }) }) { padding ->
    Box(Modifier.fillMaxSize().padding(padding)) {
        StaffLoadError("Your role does not have access to this area.", onBack)
    }
}
private fun initials(value: String): String = value.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }.ifBlank { "F" }
