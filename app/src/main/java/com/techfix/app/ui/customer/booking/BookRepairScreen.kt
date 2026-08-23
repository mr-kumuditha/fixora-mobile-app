package com.techfix.app.ui.customer.booking

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.techfix.app.core.designsystem.FixoraSpacing
import com.techfix.app.core.designsystem.FixoraRadius
import com.techfix.app.core.designsystem.FixoraTheme
import com.techfix.app.domain.catalog.DeviceCategory
import com.techfix.app.ui.customer.catalog.icon
import com.techfix.app.ui.customer.catalog.label
import com.techfix.app.ui.customer.catalog.DeviceBrandCatalog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookRepairScreen(
    uiState: BookRepairUiState,
    onBrandChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onSerialChange: (String) -> Unit,
    onIssueChange: (String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onAddImages: (List<Uri>) -> Unit,
    onRemoveImage: (String) -> Unit,
    onRetryImage: (String) -> Unit,
    onCameraPermissionDenied: () -> Unit,
    onDismissPermissionMessage: () -> Unit,
    onPhotoError: (String) -> Unit,
    onDismissPhotoMessage: () -> Unit,
    onDismissDraftRestoredMessage: () -> Unit,
    onLocationPermissionResult: (Boolean) -> Unit,
    onBranchSelected: (String) -> Unit,
    onScheduleChange: (Long) -> Unit,
    onRetryMatching: () -> Unit,
    onSubmit: () -> Unit,
    onDismissSubmitError: () -> Unit,
    hasLocationPermission: () -> Boolean,
    onDone: () -> Unit,
    onTrackRepair: (String) -> Unit,
) {
    var showCamera by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.permissionDeniedMessage) {
        val message = uiState.permissionDeniedMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            onDismissPermissionMessage()
        }
    }

    LaunchedEffect(uiState.photoMessage) {
        val message = uiState.photoMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            onDismissPhotoMessage()
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) showCamera = true else onCameraPermissionDenied() }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(5),
    ) { uris -> if (uris.isNotEmpty()) onAddImages(uris) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted -> onLocationPermissionResult(granted.values.any { it }) }

    // Entering step 4 is what kicks off matching: ask for location once (the
    // system prompt only appears if it isn't already granted), then let the
    // ViewModel resolve a position and run the use case. Keyed on the step so
    // stepping back to fix a photo and forward again re-runs it with fresh
    // availability rather than showing a stale ranking.
    LaunchedEffect(uiState.step) {
        if (uiState.step == 4) {
            if (hasLocationPermission()) {
                onLocationPermissionResult(true)
            } else {
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ),
                )
            }
        }
    }

    LaunchedEffect(uiState.draftRestoredMessage) {
        val message = uiState.draftRestoredMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            onDismissDraftRestoredMessage()
        }
    }

    LaunchedEffect(uiState.submitError) {
        val message = uiState.submitError
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            onDismissSubmitError()
        }
    }

    if (showCamera) {
        CameraCaptureDialog(
            onImageCaptured = { uri -> onAddImages(listOf(uri)) },
            onError = onPhotoError,
            onDismiss = { showCamera = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.service?.name ?: "Book Repair") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (uiState.submittedRequestId != null) {
            BookingSubmittedPane(
                requestId = uiState.submittedRequestId,
                branchName = uiState.matchResult?.matches
                    ?.firstOrNull { it.branch.id == uiState.selectedBranchId }
                    ?.branch?.name,
                onDone = onDone,
                onTrackRepair = { onTrackRepair(uiState.submittedRequestId) },
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            return@Scaffold
        }

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            StepIndicator(currentStep = uiState.step, modifier = Modifier.padding(FixoraSpacing.md))

            Box(modifier = Modifier.weight(1f)) {
                Crossfade(targetState = uiState.step, animationSpec = tween(220), label = "bookingStep") { step ->
                when (step) {
                    1 -> DeviceDetailsStep(uiState, onBrandChange, onModelChange, onSerialChange)
                    2 -> IssueDescriptionStep(uiState, onIssueChange)
                    3 -> PhotosStep(
                        uiState = uiState,
                        onCameraClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                        onGalleryClick = {
                            galleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        onRemoveImage = onRemoveImage,
                        onRetryImage = onRetryImage,
                    )

                    else -> BranchPickerStep(
                        uiState = uiState,
                        onBranchSelected = onBranchSelected,
                        onScheduleChange = onScheduleChange,
                        onRetryMatching = onRetryMatching,
                    )
                }
                }
            }

            BookRepairBottomBar(
                uiState = uiState,
                onNext = { if (uiState.step == BookRepairViewModel.LAST_STEP) onSubmit() else onNext() },
                onBack = onBack,
            )
        }
    }
}

@Composable
private fun StepIndicator(currentStep: Int, modifier: Modifier = Modifier) {
    val labels = listOf("Device", "Issue", "Photos", "Schedule")
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        labels.forEachIndexed { index, label ->
            val stepNumber = index + 1
            val active = stepNumber <= currentStep
            val dotColor by animateColorAsState(
                targetValue = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                label = "stepDotColor",
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(dotColor),
                    contentAlignment = Alignment.Center,
                ) {
                    if (stepNumber < currentStep) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            // onPrimary, not white: in dark mode primary is a
                            // light indigo and white on it barely reads.
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                    } else {
                        Text(
                            "$stepNumber",
                            color = if (active) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                FixoraTheme.extendedColors.textSecondary
                            },
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (active) MaterialTheme.colorScheme.primary else FixoraTheme.extendedColors.textSecondary,
                    modifier = Modifier.padding(top = FixoraSpacing.xs),
                )
            }
            if (index != labels.lastIndex) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(2.dp)
                        .background(if (stepNumber < currentStep) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                )
            }
        }
    }
}

@Composable
private fun DeviceDetailsStep(
    uiState: BookRepairUiState,
    onBrandChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onSerialChange: (String) -> Unit,
) {
    var picker by rememberSaveable { mutableStateOf(DevicePicker.NONE) }

    if (picker != DevicePicker.NONE) {
        val isBrandPicker = picker == DevicePicker.BRAND
        SelectionBottomSheet(
            title = if (isBrandPicker) "Select your brand" else "Choose a suggested model",
            options = if (isBrandPicker) {
                DeviceBrandCatalog.brandsFor(uiState.category)
            } else {
                DeviceBrandCatalog.suggestedModelsFor(uiState.category, uiState.brand)
            },
            selected = if (isBrandPicker) uiState.brand else uiState.model,
            onSelect = { value ->
                if (isBrandPicker) {
                    onBrandChange(value)
                    onModelChange("")
                } else if (value != "Other model") {
                    onModelChange(value)
                } else {
                    onModelChange("")
                }
                picker = DevicePicker.NONE
            },
            onDismiss = { picker = DevicePicker.NONE },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = FixoraSpacing.md),
        verticalArrangement = Arrangement.spacedBy(FixoraSpacing.md),
    ) {
        Text("Tell us about your device", style = MaterialTheme.typography.titleLarge)
        Text(
            "A few details help us prepare the right repair for you.",
            style = MaterialTheme.typography.bodySmall,
            color = FixoraTheme.extendedColors.textSecondary,
        )

        uiState.category?.let { category ->
            Column(verticalArrangement = Arrangement.spacedBy(FixoraSpacing.xs)) {
                Text("Device type", style = MaterialTheme.typography.labelLarge, color = FixoraTheme.extendedColors.textSecondary)
                FilterChip(selected = true, onClick = {}, enabled = false, leadingIcon = { Icon(category.icon, contentDescription = null, modifier = Modifier.size(18.dp)) }, label = { Text(category.label) })
            }
        }

        SelectionField(
            label = "Brand",
            value = uiState.brand,
            placeholder = "Choose a brand",
            onClick = { picker = DevicePicker.BRAND },
        )
        OutlinedTextField(
            value = uiState.model,
            onValueChange = onModelChange,
            label = { Text("Model") },
            placeholder = { Text("Search or enter your model") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (uiState.brand.isNotBlank() && DeviceBrandCatalog.suggestedModelsFor(uiState.category, uiState.brand).isNotEmpty()) {
            OutlinedButton(onClick = { picker = DevicePicker.MODEL }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Choose from suggested models", modifier = Modifier.padding(start = FixoraSpacing.sm))
            }
        }
        Text(
            "Suggestions are optional — you can enter any model manually.",
            style = MaterialTheme.typography.labelMedium,
            color = FixoraTheme.extendedColors.textSecondary,
        )
        OutlinedTextField(
            value = uiState.serialNumber,
            onValueChange = onSerialChange,
            label = { Text("Serial number (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private enum class DevicePicker { NONE, BRAND, MODEL }

@Composable
private fun SelectionField(
    label: String,
    value: String,
    placeholder: String,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(FixoraRadius.input),
        border = androidx.compose.foundation.BorderStroke(1.dp, FixoraTheme.extendedColors.border),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = FixoraSpacing.md, vertical = FixoraSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FixoraSpacing.xs)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = FixoraTheme.extendedColors.textSecondary)
                Text(value.ifBlank { placeholder }, style = MaterialTheme.typography.bodyLarge, color = if (value.isBlank()) FixoraTheme.extendedColors.textSecondary else MaterialTheme.colorScheme.onSurface)
            }
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Choose $label", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionBottomSheet(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .padding(horizontal = FixoraSpacing.md)
                .padding(bottom = FixoraSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(FixoraSpacing.md),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Search") }, leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) }, singleLine = true)
            val filteredOptions = options.filter { query.isBlank() || it.contains(query, ignoreCase = true) }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(FixoraSpacing.xs),
                contentPadding = PaddingValues(bottom = FixoraSpacing.md),
            ) {
                if (filteredOptions.isEmpty()) {
                    item {
                        Text(
                            "No matching options. Try another search.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = FixoraTheme.extendedColors.textSecondary,
                            modifier = Modifier.fillMaxWidth().padding(FixoraSpacing.md),
                        )
                    }
                }
                items(filteredOptions) { option ->
                    val chosen = option == selected
                    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(FixoraRadius.input)).background(if (chosen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant).clickable { onSelect(option) }.padding(FixoraSpacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(option, style = MaterialTheme.typography.bodyLarge)
                        if (chosen) Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun IssueDescriptionStep(uiState: BookRepairUiState, onIssueChange: (String) -> Unit) {
    val issueSuggestions = remember(uiState.category, uiState.service?.name) {
        BookingIssueCatalog.suggestionsFor(uiState.category, uiState.service?.name.orEmpty())
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = FixoraSpacing.md),
        verticalArrangement = Arrangement.spacedBy(FixoraSpacing.sm),
    ) {
        Text("What's wrong with it?", style = MaterialTheme.typography.titleLarge)
        Text(
            "Describe the issue in a few sentences — this helps the technician prepare.",
            style = MaterialTheme.typography.bodySmall,
            color = FixoraTheme.extendedColors.textSecondary,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.sm), contentPadding = PaddingValues(vertical = FixoraSpacing.xs)) {
            items(issueSuggestions) { suggestion ->
                AssistChip(
                    onClick = {
                        val current = uiState.issueDescription.trim()
                        if (!current.contains(suggestion, ignoreCase = true)) {
                            onIssueChange(if (current.isBlank()) suggestion else "$current. $suggestion")
                        }
                    },
                    label = { Text(suggestion) },
                )
            }
        }
        OutlinedTextField(
            value = uiState.issueDescription,
            onValueChange = { if (it.length <= 500) onIssueChange(it) },
            placeholder = { Text("e.g. Screen cracked after a drop, touch stopped responding in the top corner.") },
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
        )
        Text(
            "${uiState.issueDescription.length}/500",
            style = MaterialTheme.typography.labelMedium,
            color = FixoraTheme.extendedColors.textSecondary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PhotosStep(
    uiState: BookRepairUiState,
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onRemoveImage: (String) -> Unit,
    onRetryImage: (String) -> Unit,
) {
    var previewImage by remember { mutableStateOf<BookingImage?>(null) }

    previewImage?.let { image ->
        PhotoPreviewDialog(image = image, onDismiss = { previewImage = null })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = FixoraSpacing.md),
        verticalArrangement = Arrangement.spacedBy(FixoraSpacing.md),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(FixoraSpacing.xs)) {
            Text("Show us the problem", style = MaterialTheme.typography.titleLarge)
            Text(
                "Photos help our technicians understand the issue before your visit.",
                style = MaterialTheme.typography.bodyMedium,
                color = FixoraTheme.extendedColors.textSecondary,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.sm),
        ) {
            PhotoActionCard(
                title = "Take Photo",
                supportingText = "Use camera",
                icon = Icons.Rounded.PhotoCamera,
                enabled = uiState.remainingPhotoSlots > 0,
                onClick = onCameraClick,
                modifier = Modifier.weight(1f),
            )
            PhotoActionCard(
                title = "Choose Photos",
                supportingText = "From gallery",
                icon = Icons.Rounded.PhotoLibrary,
                enabled = uiState.remainingPhotoSlots > 0,
                onClick = onGalleryClick,
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Selected photos", style = MaterialTheme.typography.titleMedium)
            Text(
                "${uiState.images.size} of ${BookRepairViewModel.MAX_PHOTOS} photos added",
                style = MaterialTheme.typography.labelMedium,
                color = FixoraTheme.extendedColors.textSecondary,
            )
        }

        Crossfade(targetState = uiState.images.isEmpty(), animationSpec = tween(200), label = "photoCollection") { isEmpty ->
            if (isEmpty) {
                Card(
                    shape = RoundedCornerShape(FixoraRadius.card),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, FixoraTheme.extendedColors.border),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(FixoraSpacing.lg),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(FixoraSpacing.sm),
                    ) {
                        Icon(
                            Icons.Rounded.AddAPhoto,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp),
                        )
                        Text("No photos added yet", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Add a clear photo of the damage. At least one uploaded photo is required to continue.",
                            style = MaterialTheme.typography.bodySmall,
                            color = FixoraTheme.extendedColors.textSecondary,
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(FixoraSpacing.sm)) {
                    uiState.images.chunked(PHOTO_GRID_COLUMNS).forEach { rowImages ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.sm),
                        ) {
                            rowImages.forEach { image ->
                                BookingImageThumbnail(
                                    image = image,
                                    onRemoveImage = onRemoveImage,
                                    onRetryImage = onRetryImage,
                                    onPreview = { previewImage = image },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            repeat(PHOTO_GRID_COLUMNS - rowImages.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        Text(
            when {
                uiState.isUploadingAnyImage -> "Keep this screen open while your photos upload."
                uiState.hasFailedImages -> "A photo could not be uploaded. Tap retry or remove it."
                uiState.canAdvanceFromStep3 -> "Your photos are ready. You can continue."
                else -> "Add at least one clear photo to continue."
            },
            style = MaterialTheme.typography.labelMedium,
            color = when {
                uiState.hasFailedImages -> MaterialTheme.colorScheme.error
                uiState.canAdvanceFromStep3 -> FixoraTheme.extendedColors.success
                else -> FixoraTheme.extendedColors.textSecondary
            },
        )
    }
}

@Composable
private fun PhotoActionCard(
    title: String,
    supportingText: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(FixoraRadius.card),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        border = BorderStroke(1.dp, FixoraTheme.extendedColors.border),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(FixoraSpacing.md),
            verticalArrangement = Arrangement.spacedBy(FixoraSpacing.sm),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(FixoraRadius.input))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Column(verticalArrangement = Arrangement.spacedBy(FixoraSpacing.xs)) {
                Text(title, style = MaterialTheme.typography.labelLarge)
                Text(
                    supportingText,
                    style = MaterialTheme.typography.labelMedium,
                    color = FixoraTheme.extendedColors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun BookingImageThumbnail(
    image: BookingImage,
    onRemoveImage: (String) -> Unit,
    onRetryImage: (String) -> Unit,
    onPreview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(FixoraRadius.input))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onPreview),
    ) {
        SubcomposeAsyncImage(
            model = image.thumbnailModel,
            contentDescription = "Open repair photo preview",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            loading = { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)) },
            error = {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.BrokenImage,
                        contentDescription = "Image could not be displayed",
                        tint = FixoraTheme.extendedColors.textSecondary,
                    )
                }
            },
            success = { SubcomposeAsyncImageContent() },
        )

        when (image.status) {
            ImageUploadStatus.UPLOADING -> Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = Color.White)
            }

            ImageUploadStatus.FAILED -> Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                IconButton(onClick = { onRetryImage(image.id) }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Retry photo upload", tint = Color.White)
                }
            }

            ImageUploadStatus.UPLOADED -> Unit
        }

        IconButton(
            onClick = { onRemoveImage(image.id) },
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            Box(
                modifier = Modifier.size(32.dp).background(Color.Black.copy(alpha = 0.55f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Remove photo",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun PhotoPreviewDialog(image: BookingImage, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) {
            SubcomposeAsyncImage(
                model = image.thumbnailModel,
                contentDescription = "Repair photo preview",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(FixoraSpacing.md),
                loading = {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                },
                error = {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            Icons.Rounded.BrokenImage,
                            contentDescription = null,
                            tint = FixoraTheme.extendedColors.textSecondary,
                            modifier = Modifier.size(32.dp),
                        )
                        Text(
                            "Unable to preview this photo.",
                            color = FixoraTheme.extendedColors.textSecondary,
                            modifier = Modifier.padding(top = FixoraSpacing.sm),
                        )
                    }
                },
                success = { SubcomposeAsyncImageContent() },
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopStart).padding(FixoraSpacing.md),
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Close photo preview")
            }
        }
    }
}

private const val PHOTO_GRID_COLUMNS = 3

@Composable
private fun BookRepairBottomBar(uiState: BookRepairUiState, onNext: () -> Unit, onBack: () -> Unit) {
    Column(modifier = Modifier.padding(FixoraSpacing.md)) {
        val caption = when (uiState.step) {
            3 -> when {
                uiState.isUploadingAnyImage -> "Waiting for photos to finish uploading…"
                uiState.hasFailedImages -> "Retry or remove the photo that failed to upload."
                uiState.canAdvanceFromStep3 -> null
                else -> "Add at least one photo to continue."
            }

            BookRepairViewModel.LAST_STEP -> when {
                uiState.matchLoading -> "Working out the best branch for this repair…"
                uiState.selectedBranchId == null -> "Pick a branch to continue."
                else -> null
            }

            else -> null
        }
        if (caption != null) {
            Text(
                text = caption,
                style = MaterialTheme.typography.labelMedium,
                color = FixoraTheme.extendedColors.textSecondary,
                modifier = Modifier.padding(bottom = FixoraSpacing.xs),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.sm)) {
            OutlinedButton(
                onClick = onBack,
                enabled = !uiState.submitting,
                modifier = Modifier.weight(1f),
            ) {
                Text(if (uiState.step == 1) "Cancel" else "Back")
            }
            val enabled = when (uiState.step) {
                1 -> uiState.canAdvanceFromStep1
                2 -> uiState.canAdvanceFromStep2
                3 -> uiState.canAdvanceFromStep3
                else -> uiState.canSubmit
            }
            Button(
                onClick = onNext,
                enabled = enabled,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FixoraTheme.extendedColors.accent,
                    contentColor = FixoraTheme.extendedColors.onAccent,
                ),
            ) {
                when {
                    uiState.submitting -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = FixoraTheme.extendedColors.onAccent,
                        )
                        Text("  Submitting…")
                    }

                    uiState.step < BookRepairViewModel.LAST_STEP -> Text("Continue")
                    uiState.step == BookRepairViewModel.LAST_STEP -> Text("Confirm booking")
                }
            }
        }
    }
}

/**
 * Terminal state of the flow: the request is in Firestore. The reference is
 * the same short id the tracking and history screens show, and the primary
 * action hands straight over to Block 6's live timeline.
 */
@Composable
private fun BookingSubmittedPane(
    requestId: String,
    branchName: String?,
    onDone: () -> Unit,
    onTrackRepair: () -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = true,
        enter = fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.94f),
        modifier = modifier,
    ) {
    Column(
        modifier = Modifier.fillMaxSize().padding(FixoraSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(FixoraSpacing.md, Alignment.CenterVertically),
    ) {
        Icon(
            Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = FixoraTheme.extendedColors.success,
            modifier = Modifier.size(56.dp),
        )
        Text("Booking confirmed", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = branchName?.let { "Your repair is booked at $it." }
                ?: "Your repair request has been submitted.",
            style = MaterialTheme.typography.bodyMedium,
            color = FixoraTheme.extendedColors.textSecondary,
        )
        Text(
            "Reference: ${requestId.take(8).uppercase()}",
            style = MaterialTheme.typography.labelMedium,
            color = FixoraTheme.extendedColors.textSecondary,
        )
        Button(
            onClick = onTrackRepair,
            colors = ButtonDefaults.buttonColors(
                containerColor = FixoraTheme.extendedColors.accent,
                contentColor = FixoraTheme.extendedColors.onAccent,
            ),
        ) {
            Text("Track repair")
        }
        TextButton(onClick = onDone) {
            Text("Back to home")
        }
    }
    }
}
