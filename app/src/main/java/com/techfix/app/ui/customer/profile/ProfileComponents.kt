package com.techfix.app.ui.customer.profile

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.techfix.app.core.designsystem.FixoraSpacing
import com.techfix.app.core.designsystem.FixoraTheme
import com.techfix.app.ui.customer.booking.CameraCaptureDialog

@Composable
fun ProfileAvatar(
    name: String?,
    email: String?,
    photoUrl: String?,
    isUploading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
) {
    val initials = profileInitials(name, email)
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        if (photoUrl.isNullOrBlank()) {
            AvatarFallback(initials, Modifier.matchParentSize())
        } else {
            SubcomposeAsyncImage(
                model = photoUrl,
                contentDescription = "Profile photo",
                contentScale = ContentScale.Crop,
                loading = { AvatarFallback(initials, Modifier.matchParentSize(), showLoading = true) },
                error = { AvatarFallback(initials, Modifier.matchParentSize()) },
                success = { SubcomposeAsyncImageContent() },
                modifier = Modifier
                    .matchParentSize()
                    .clip(CircleShape)
                    .clickable(enabled = !isUploading, onClick = onClick),
            )
        }

        if (photoUrl.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(CircleShape)
                    .clickable(enabled = !isUploading, onClick = onClick),
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .border(BorderStroke(2.dp, MaterialTheme.colorScheme.surface), CircleShape)
                .clickable(enabled = !isUploading, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (isUploading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.PhotoCamera,
                    contentDescription = "Change profile photo",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

@Composable
private fun AvatarFallback(
    initials: String,
    modifier: Modifier = Modifier,
    showLoading: Boolean = false,
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        if (showLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilePhotoActions(
    showSheet: Boolean,
    hasCustomPhoto: Boolean,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onPhotoSelected: (Uri) -> Unit,
    onRemovePhoto: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    var showCamera by rememberSaveable { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let(onPhotoSelected) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            showCamera = true
        } else {
            onMessage("Camera permission is required to take a profile photo.")
        }
    }

    if (showCamera) {
        CameraCaptureDialog(
            onImageCaptured = onPhotoSelected,
            onError = onMessage,
            onDismiss = { showCamera = false },
        )
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = FixoraSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(FixoraSpacing.xs),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = FixoraSpacing.lg, vertical = FixoraSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(FixoraSpacing.xs),
                ) {
                    Text("Profile photo", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Choose how you'd like to personalize your Fixora account.",
                        style = MaterialTheme.typography.bodySmall,
                        color = FixoraTheme.extendedColors.textSecondary,
                    )
                }
                PhotoActionRow(
                    icon = Icons.Rounded.PhotoCamera,
                    title = "Take photo",
                    subtitle = "Use your device camera",
                    enabled = enabled,
                    onClick = {
                        onDismiss()
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            showCamera = true
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                )
                PhotoActionRow(
                    icon = Icons.Rounded.PhotoLibrary,
                    title = "Choose from gallery",
                    subtitle = "Select a photo from your device",
                    enabled = enabled,
                    onClick = {
                        onDismiss()
                        galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                )
                if (hasCustomPhoto) {
                    PhotoActionRow(
                        icon = Icons.Rounded.DeleteOutline,
                        title = "Remove photo",
                        subtitle = "Return to your account default",
                        enabled = enabled,
                        destructive = true,
                        onClick = {
                            onDismiss()
                            onRemovePhoto()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PhotoActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    ListItem(
        headlineContent = { Text(title, color = if (enabled) color else FixoraTheme.extendedColors.textSecondary) },
        supportingContent = {
            Text(subtitle, color = FixoraTheme.extendedColors.textSecondary)
        },
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (destructive) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
    )
}

private fun profileInitials(name: String?, email: String?): String {
    val source = name?.trim()?.takeIf { it.isNotBlank() }
        ?: email?.substringBefore('@')?.takeIf { it.isNotBlank() }
        ?: "F"
    return source
        .split(Regex("\\s+"))
        .filter(String::isNotBlank)
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
        .ifBlank { "F" }
}
