package com.techfix.app.ui.customer.booking

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Engineering
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import com.techfix.app.R
import com.techfix.app.core.designsystem.FixoraRadius
import com.techfix.app.core.designsystem.FixoraSpacing
import com.techfix.app.core.designsystem.FixoraTheme
import com.techfix.app.domain.matching.BranchMatch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Step 4 of Book Repair: where the GPS match result is shown and confirmed.
 *
 * The screen renders the outcome of [com.techfix.app.domain.matching.MatchBranchesUseCase];
 * it contains no matching logic of its own. Loading, error, and content
 * states follow the design system — the map is styled off the Fixora
 * palette in both themes rather than left on default Maps colours.
 */
@Composable
fun BranchPickerStep(
    uiState: BookRepairUiState,
    onBranchSelected: (String) -> Unit,
    onScheduleChange: (Long) -> Unit,
    onRetryMatching: () -> Unit,
) {
    Crossfade(
        targetState = when {
            uiState.matchLoading -> BranchPickerPane.LOADING
            uiState.matchError != null -> BranchPickerPane.ERROR
            uiState.matchResult == null -> BranchPickerPane.LOADING
            uiState.matchResult.matches.isEmpty() -> BranchPickerPane.EMPTY
            else -> BranchPickerPane.CONTENT
        },
        label = "branchPickerPane",
    ) { pane ->
        when (pane) {
            BranchPickerPane.LOADING -> BranchMatchSkeleton(uiState)
            BranchPickerPane.ERROR -> BranchMatchError(uiState.matchError.orEmpty(), onRetryMatching)
            BranchPickerPane.EMPTY -> BranchMatchEmpty(onRetryMatching)
            BranchPickerPane.CONTENT -> BranchMatchContent(
                uiState = uiState,
                onBranchSelected = onBranchSelected,
                onScheduleChange = onScheduleChange,
                onRetryMatching = onRetryMatching,
            )
        }
    }
}

private enum class BranchPickerPane { LOADING, ERROR, EMPTY, CONTENT }

// ---------------------------------------------------------------- content

@Composable
private fun BranchMatchContent(
    uiState: BookRepairUiState,
    onBranchSelected: (String) -> Unit,
    onScheduleChange: (Long) -> Unit,
    onRetryMatching: () -> Unit,
) {
    val result = uiState.matchResult ?: return

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = FixoraSpacing.md),
        verticalArrangement = Arrangement.spacedBy(FixoraSpacing.sm),
    ) {
        item {
            Text("Choose a branch", style = MaterialTheme.typography.titleLarge)
        }
        item {
            Text(
                "Ranked on how far each branch is plus whether it has a free technician " +
                    "for your device and the parts on the shelf.",
                style = MaterialTheme.typography.bodySmall,
                color = FixoraTheme.extendedColors.textSecondary,
            )
        }

        item { BranchMap(uiState) }

        item { LocationStatusBanner(uiState, onRetryMatching) }

        if (result.allBranchesBlocked) {
            item { BlockedBranchesBanner() }
        }

        items(result.matches, key = { it.branch.id }) { match ->
            BranchMatchCard(
                match = match,
                isRecommended = match.branch.id == result.recommended?.branch?.id,
                isSelected = match.branch.id == uiState.selectedBranchId,
                onClick = { onBranchSelected(match.branch.id) },
            )
        }

        item { BookingSummaryCard(uiState) }
        item { ScheduleRow(uiState.scheduledAt, onScheduleChange) }

        item { Box(modifier = Modifier.height(FixoraSpacing.sm)) }
    }
}

@Composable
private fun BookingSummaryCard(uiState: BookRepairUiState) {
    val branchName = uiState.matchResult?.matches
        ?.firstOrNull { it.branch.id == uiState.selectedBranchId }
        ?.branch?.name
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(FixoraRadius.card),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(FixoraSpacing.md), verticalArrangement = Arrangement.spacedBy(FixoraSpacing.sm)) {
            Text("Your repair", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(uiState.service?.name ?: "Selected service", style = MaterialTheme.typography.titleMedium)
            SummaryLine("Device", listOfNotNull(uiState.brand.takeIf { it.isNotBlank() }, uiState.model.takeIf { it.isNotBlank() }).joinToString(" ").ifBlank { "Device details pending" })
            SummaryLine("Branch", branchName ?: "Choose a branch above")
            uiState.service?.let { service ->
                SummaryLine("Estimated starting price", "Rs. %,.0f".format(service.basePrice))
                Text("Final price is confirmed after inspection.", style = MaterialTheme.typography.labelMedium, color = FixoraTheme.extendedColors.textSecondary)
            }
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = FixoraTheme.extendedColors.textSecondary, modifier = Modifier.weight(0.42f))
        Text(value, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.58f))
    }
}

// -------------------------------------------------------------------- map

@Composable
private fun BranchMap(uiState: BookRepairUiState) {
    val darkTheme = isSystemInDarkTheme()
    val matches = uiState.matchResult?.matches.orEmpty()
    val customer = uiState.customerLocation?.let { LatLng(it.latitude, it.longitude) }
    val selectedBranchId = uiState.selectedBranchId
    val branchMarkerStates = matches.associate { match ->
        match.branch.id to rememberMarkerState(
            key = match.branch.id,
            position = LatLng(match.branch.latitude, match.branch.longitude),
        )
    }
    val customerMarkerState = customer?.let {
        rememberMarkerState(key = "customer", position = it)
    }

    val branchPoints = matches.map { LatLng(it.branch.latitude, it.branch.longitude) }
    val allPoints = branchPoints + listOfNotNull(customer)

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            allPoints.firstOrNull() ?: SRI_LANKA_CENTRE,
            if (allPoints.size > 1) 7f else 11f,
        )
    }

    // Parsed from a raw resource, so it is remembered rather than re-read on
    // every recomposition of the branch list above it.
    val context = LocalContext.current
    val mapProperties = remember(darkTheme) {
        MapProperties(
            mapType = MapType.NORMAL,
            // The customer's own marker is drawn below, so Maps' blue dot
            // would just be a second, differently-styled version of it.
            isMyLocationEnabled = false,
            mapStyleOptions = MapStyleOptions.loadRawResourceStyle(
                context,
                if (darkTheme) R.raw.map_style_dark else R.raw.map_style_light,
            ),
        )
    }
    val mapUiSettings = remember {
        MapUiSettings(
            zoomControlsEnabled = false,
            mapToolbarEnabled = false,
            myLocationButtonEnabled = false,
            compassEnabled = false,
            tiltGesturesEnabled = false,
            rotationGesturesEnabled = false,
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        shape = RoundedCornerShape(FixoraRadius.card),
        border = BorderStroke(1.dp, FixoraTheme.extendedColors.border),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = mapProperties,
            uiSettings = mapUiSettings,
            onMapLoaded = {
                // Frame every pin once the map has a size — newLatLngBounds
                // needs real dimensions, which it only has after load.
                if (allPoints.size > 1) {
                    val bounds = LatLngBounds.builder()
                        .apply { allPoints.forEach { include(it) } }
                        .build()
                    runCatching {
                        cameraPositionState.move(
                            CameraUpdateFactory.newLatLngBounds(bounds, MAP_BOUNDS_PADDING_PX),
                        )
                    }
                }
            },
        ) {
            matches.forEach { match ->
                Marker(
                    state = branchMarkerStates.getValue(match.branch.id),
                    title = match.branch.name,
                    snippet = match.branch.address,
                    icon = BitmapDescriptorFactory.defaultMarker(
                        if (match.branch.id == selectedBranchId) MARKER_HUE_ACCENT else MARKER_HUE_PRIMARY,
                    ),
                )
            }
            if (customerMarkerState != null) {
                Marker(
                    state = customerMarkerState,
                    title = "You are here",
                    icon = BitmapDescriptorFactory.defaultMarker(MARKER_HUE_CUSTOMER),
                )
            }
        }
    }
}

// ------------------------------------------------------------ status banners

@Composable
private fun LocationStatusBanner(uiState: BookRepairUiState, onRetryMatching: () -> Unit) {
    val extended = FixoraTheme.extendedColors
    val (icon, message, tint) = when (uiState.locationStatus) {
        LocationStatus.AVAILABLE -> Triple(
            Icons.Rounded.MyLocation,
            "Using your current location to rank branches by distance.",
            MaterialTheme.colorScheme.primary,
        )

        LocationStatus.PERMISSION_DENIED -> Triple(
            Icons.Rounded.LocationOff,
            "Location is off, so branches are ranked on technician and parts availability only. " +
                "Turn location on to include distance.",
            extended.warning,
        )

        LocationStatus.UNAVAILABLE -> Triple(
            Icons.Rounded.LocationOff,
            "Couldn't get a GPS fix. Branches are ranked on availability only.",
            extended.warning,
        )

        LocationStatus.IDLE, LocationStatus.RESOLVING -> Triple(
            Icons.Rounded.MyLocation,
            "Finding your location…",
            extended.textSecondary,
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(FixoraRadius.card))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(
                enabled = uiState.locationStatus != LocationStatus.AVAILABLE,
                onClick = onRetryMatching,
            )
            .padding(FixoraSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.sm),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(
            message,
            style = MaterialTheme.typography.labelMedium,
            color = extended.textSecondary,
        )
    }
}

@Composable
private fun BlockedBranchesBanner() {
    val extended = FixoraTheme.extendedColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(FixoraRadius.card))
            .background(extended.warning.copy(alpha = 0.12f))
            .padding(FixoraSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.sm),
    ) {
        Icon(
            Icons.Rounded.WarningAmber,
            contentDescription = null,
            tint = extended.warning,
            modifier = Modifier.size(20.dp),
        )
        Text(
            "No branch can start this repair straight away. The branch at the top is the " +
                "closest match — expect a short wait for a technician or parts.",
            style = MaterialTheme.typography.labelMedium,
            color = extended.textSecondary,
        )
    }
}

// ------------------------------------------------------------- branch card

@Composable
private fun BranchMatchCard(
    match: BranchMatch,
    isRecommended: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val extended = FixoraTheme.extendedColors
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else extended.border,
        label = "branchCardBorder",
    )
    val elevation by animateDpAsState(
        targetValue = if (isSelected) 4.dp else 0.dp,
        label = "branchCardElevation",
    )

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(FixoraRadius.card),
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(FixoraSpacing.md),
            verticalArrangement = Arrangement.spacedBy(FixoraSpacing.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Storefront,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Column(modifier = Modifier
                    .weight(1f)
                    .padding(start = FixoraSpacing.sm)) {
                    Text(
                        match.branch.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        match.branch.address,
                        style = MaterialTheme.typography.labelMedium,
                        color = extended.textSecondary,
                    )
                }
                if (isSelected) {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.xs)) {
                if (isRecommended) {
                    MatchPill(
                        text = "Best match",
                        icon = Icons.Rounded.CheckCircle,
                        color = extended.accentOnSurface,
                    )
                }
                MatchPill(
                    text = match.distanceKm?.let { formatDistance(it) } ?: "Distance unknown",
                    icon = Icons.Rounded.NearMe,
                    color = if (match.distanceKm == null) extended.textSecondary else MaterialTheme.colorScheme.primary,
                )
            }

            AvailabilityRow(
                icon = Icons.Rounded.Engineering,
                available = match.hasTechnician,
                availableText = when (match.availableTechnicians.size) {
                    1 -> "1 technician free for this device type"
                    else -> "${match.availableTechnicians.size} technicians free for this device type"
                },
                unavailableText = "No free technician for this device type",
            )
            AvailabilityRow(
                icon = Icons.Rounded.Inventory2,
                available = match.hasParts,
                availableText = "${match.partsInStock.size} of ${match.totalPartsTracked} " +
                    "compatible parts in stock",
                unavailableText = "No compatible parts in stock",
            )
        }
    }
}

@Composable
private fun MatchPill(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(FixoraRadius.input))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = FixoraSpacing.sm, vertical = FixoraSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.xs),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
private fun AvailabilityRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    available: Boolean,
    availableText: String,
    unavailableText: String,
) {
    val extended = FixoraTheme.extendedColors
    val tint = if (available) extended.success else extended.warning
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.sm),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(
            if (available) availableText else unavailableText,
            style = MaterialTheme.typography.bodySmall,
            color = if (available) MaterialTheme.colorScheme.onSurface else extended.textSecondary,
        )
    }
}

// -------------------------------------------------------------- scheduling

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleRow(scheduledAt: Long?, onScheduleChange: (Long) -> Unit) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val millis = scheduledAt ?: return

    Column(verticalArrangement = Arrangement.spacedBy(FixoraSpacing.sm)) {
        Text("Drop-off slot", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(FixoraRadius.card))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { showDatePicker = true }
                .padding(FixoraSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.sm),
        ) {
            Icon(
                Icons.Rounded.CalendarMonth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Text(formatSchedule(millis), style = MaterialTheme.typography.bodyMedium)
            Box(modifier = Modifier.weight(1f))
            Text(
                "Change",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }

    if (showDatePicker) {
        // The picker works in UTC-midnight terms; the chosen day is merged
        // back onto the existing local time-of-day rather than replacing it.
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = toUtcDateMillis(millis))
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        onScheduleChange(mergeDate(existing = millis, utcDateMillis = it))
                    }
                    showDatePicker = false
                    showTimePicker = true
                }) { Text("Next") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val calendar = Calendar.getInstance().apply { timeInMillis = millis }
        val timePickerState = rememberTimePickerState(
            initialHour = calendar.get(Calendar.HOUR_OF_DAY),
            initialMinute = calendar.get(Calendar.MINUTE),
            is24Hour = false,
        )
        DatePickerDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onScheduleChange(
                        mergeTime(millis, timePickerState.hour, timePickerState.minute),
                    )
                    showTimePicker = false
                }) { Text("Done") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(FixoraSpacing.md),
                contentAlignment = Alignment.Center,
            ) {
                TimePicker(state = timePickerState)
            }
        }
    }
}

// --------------------------------------------------------- loading / empty

@Composable
private fun BranchMatchSkeleton(uiState: BookRepairUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = FixoraSpacing.md),
        verticalArrangement = Arrangement.spacedBy(FixoraSpacing.sm),
    ) {
        Text("Choose a branch", style = MaterialTheme.typography.titleLarge)
        Text(
            when (uiState.locationStatus) {
                LocationStatus.RESOLVING -> "Finding your location…"
                else -> "Checking technicians and parts at each branch…"
            },
            style = MaterialTheme.typography.bodySmall,
            color = FixoraTheme.extendedColors.textSecondary,
        )
        SkeletonBlock(height = 220.dp)
        SkeletonBlock(height = 132.dp)
        SkeletonBlock(height = 132.dp)
    }
}

@Composable
private fun SkeletonBlock(height: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(FixoraRadius.card))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

@Composable
private fun BranchMatchError(message: String, onRetry: () -> Unit) {
    BranchPickerMessage(
        icon = Icons.Rounded.WarningAmber,
        title = "Couldn't match a branch",
        body = "We couldn't check branch availability right now. Please try again.",
        actionLabel = "Try again",
        onAction = onRetry,
    )
}

@Composable
private fun BranchMatchEmpty(onRetry: () -> Unit) {
    BranchPickerMessage(
        icon = Icons.Rounded.Storefront,
        title = "No branches available",
        body = "No Fixora branch is set up to take bookings right now.",
        actionLabel = "Refresh",
        onAction = onRetry,
    )
}

@Composable
private fun BranchPickerMessage(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(FixoraSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(FixoraSpacing.sm, Alignment.CenterVertically),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = FixoraTheme.extendedColors.textSecondary,
            modifier = Modifier.size(40.dp),
        )
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            body,
            style = MaterialTheme.typography.bodySmall,
            color = FixoraTheme.extendedColors.textSecondary,
        )
        TextButton(onClick = onAction) { Text(actionLabel) }
    }
}

// ------------------------------------------------------------- formatting

private fun formatDistance(km: Double): String = when {
    km < 1.0 -> "${(km * 1000).toInt()} m away"
    km < 10.0 -> String.format(Locale.getDefault(), "%.1f km away", km)
    else -> "${km.toInt()} km away"
}

private fun formatSchedule(millis: Long): String =
    SimpleDateFormat("EEE d MMM, h:mm a", Locale.getDefault()).format(millis)

/** Local wall-clock day of [millis], expressed as the UTC midnight the M3 date picker expects. */
private fun toUtcDateMillis(millis: Long): Long {
    val local = Calendar.getInstance().apply { timeInMillis = millis }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

/** Puts the picked calendar day onto [existing], keeping its local time of day. */
private fun mergeDate(existing: Long, utcDateMillis: Long): Long {
    val picked = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcDateMillis }
    return Calendar.getInstance().apply {
        timeInMillis = existing
        set(Calendar.YEAR, picked.get(Calendar.YEAR))
        set(Calendar.MONTH, picked.get(Calendar.MONTH))
        set(Calendar.DAY_OF_MONTH, picked.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

private fun mergeTime(existing: Long, hour: Int, minute: Int): Long =
    Calendar.getInstance().apply {
        timeInMillis = existing
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

private val SRI_LANKA_CENTRE = LatLng(7.0, 80.0)
private const val MAP_BOUNDS_PADDING_PX = 120
private const val MARKER_HUE_PRIMARY = BitmapDescriptorFactory.HUE_VIOLET
private const val MARKER_HUE_ACCENT = BitmapDescriptorFactory.HUE_ORANGE
private const val MARKER_HUE_CUSTOMER = BitmapDescriptorFactory.HUE_AZURE
