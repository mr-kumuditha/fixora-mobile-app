package com.techfix.app.ui.customer.profile

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.techfix.app.core.designsystem.FixoraRadius
import com.techfix.app.core.designsystem.FixoraSpacing
import com.techfix.app.core.designsystem.FixoraTheme
import com.techfix.app.core.navigation.UserRole
import com.techfix.app.domain.auth.AuthUser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    uiState: ProfileUiState,
    appVersion: String,
    darkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
    onRetry: () -> Unit,
    onEditProfile: () -> Unit,
    onPhotoSelected: (Uri) -> Unit,
    onRemovePhoto: () -> Unit,
    onPhotoMessage: (String) -> Unit,
    onDismissMessage: () -> Unit,
    onBrowseServices: () -> Unit,
    onViewRepairs: () -> Unit,
    onSignOut: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var showPhotoSheet by rememberSaveable { mutableStateOf(false) }
    var showAboutSheet by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(uiState.feedbackMessage) {
        uiState.feedbackMessage?.let {
            snackbarHostState.showSnackbar(it)
            onDismissMessage()
        }
    }

    ProfilePhotoActions(
        showSheet = showPhotoSheet,
        hasCustomPhoto = uiState.user?.hasCustomPhoto == true,
        enabled = !uiState.isPhotoUploading,
        onDismiss = { showPhotoSheet = false },
        onPhotoSelected = onPhotoSelected,
        onRemovePhoto = onRemovePhoto,
        onMessage = onPhotoMessage,
    )

    if (showAboutSheet) {
        AboutFixoraSheet(appVersion = appVersion, onDismiss = { showAboutSheet = false })
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when {
            uiState.isLoading && uiState.user == null -> ProfileLoadingState(Modifier.padding(padding))
            uiState.loadError != null && uiState.user == null -> ProfileErrorState(
                message = uiState.loadError,
                onRetry = onRetry,
                modifier = Modifier.padding(padding),
            )
            else -> ProfileContent(
                user = uiState.user,
                isPhotoUploading = uiState.isPhotoUploading,
                darkTheme = darkTheme,
                onThemeChange = onThemeChange,
                onEditProfile = onEditProfile,
                onPhotoClick = { showPhotoSheet = true },
                onBrowseServices = onBrowseServices,
                onViewRepairs = onViewRepairs,
                onAbout = { showAboutSheet = true },
                onSignOut = onSignOut,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun ProfileContent(
    user: AuthUser?,
    isPhotoUploading: Boolean,
    darkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
    onEditProfile: () -> Unit,
    onPhotoClick: () -> Unit,
    onBrowseServices: () -> Unit,
    onViewRepairs: () -> Unit,
    onAbout: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = FixoraSpacing.md, vertical = FixoraSpacing.md),
        verticalArrangement = Arrangement.spacedBy(FixoraSpacing.lg),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(FixoraSpacing.xs)) {
                Text("PROFILE", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text("Your account and preferences", style = MaterialTheme.typography.titleLarge)
            }
        }

        item {
            ProfileIdentityCard(user, isPhotoUploading, onPhotoClick)
        }

        item {
            ProfileSection(title = "ACCOUNT") {
                ProfileActionRow(
                    icon = Icons.Rounded.Person,
                    title = "Personal information",
                    subtitle = "Manage your name, phone and profile photo",
                    onClick = onEditProfile,
                )
            }
        }

        item {
            ProfileSection(title = "QUICK ACTIONS") {
                ProfileActionRow(
                    icon = Icons.Rounded.Build,
                    title = "My repairs",
                    subtitle = "View active and completed repairs",
                    onClick = onViewRepairs,
                )
                HorizontalDivider(color = FixoraTheme.extendedColors.border)
                ProfileActionRow(
                    icon = Icons.Rounded.Smartphone,
                    title = "Browse services",
                    subtitle = "Explore Fixora repair services",
                    onClick = onBrowseServices,
                )
            }
        }

        item {
            ProfileSection(title = "PREFERENCES") {
                ProfileActionRow(
                    icon = if (darkTheme) Icons.Rounded.DarkMode else Icons.Rounded.LightMode,
                    title = "Appearance",
                    subtitle = if (darkTheme) "Dark theme" else "Light theme",
                    onClick = { onThemeChange(!darkTheme) },
                    trailing = { Switch(checked = darkTheme, onCheckedChange = onThemeChange) },
                )
            }
        }

        item {
            ProfileSection(title = "SUPPORT") {
                ProfileActionRow(
                    icon = Icons.Rounded.Info,
                    title = "About Fixora",
                    subtitle = "App information and demo disclosure",
                    onClick = onAbout,
                )
            }
        }

        item {
            OutlinedButton(
                onClick = onSignOut,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(FixoraRadius.input),
            ) {
                Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(20.dp))
                Text("Secure sign out", modifier = Modifier.padding(start = FixoraSpacing.sm))
            }
        }
    }
}

@Composable
private fun ProfileIdentityCard(
    user: AuthUser?,
    isPhotoUploading: Boolean,
    onPhotoClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, FixoraTheme.extendedColors.border),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(FixoraSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(FixoraSpacing.sm),
        ) {
            ProfileAvatar(
                name = user?.name,
                email = user?.email,
                photoUrl = user?.photoUrl,
                isUploading = isPhotoUploading,
                onClick = onPhotoClick,
            )
            Spacer(Modifier.height(FixoraSpacing.xs))
            Text(
                user?.name?.takeIf(String::isNotBlank) ?: "Your profile",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                user?.email ?: "Email unavailable",
                style = MaterialTheme.typography.bodyMedium,
                color = FixoraTheme.extendedColors.textSecondary,
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(FixoraRadius.sheet))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = FixoraSpacing.md, vertical = FixoraSpacing.sm),
            ) {
                Text(
                    text = user?.role.accountLabel ?: "Fixora Customer",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun ProfileSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(FixoraSpacing.sm)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = FixoraTheme.extendedColors.textSecondary,
            modifier = Modifier.padding(horizontal = FixoraSpacing.xs),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, FixoraTheme.extendedColors.border),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun ProfileActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(FixoraSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.md),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FixoraSpacing.xs)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = FixoraTheme.extendedColors.textSecondary)
        }
        if (trailing != null) {
            trailing()
        } else {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = "Open $title",
                tint = FixoraTheme.extendedColors.textSecondary,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutFixoraSheet(appVersion: String, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = FixoraSpacing.lg).padding(bottom = FixoraSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(FixoraSpacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.sm)) {
                Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("About Fixora", style = MaterialTheme.typography.titleLarge)
            }
            Text(
                "Smart Device Repair Platform · version $appVersion",
                style = MaterialTheme.typography.bodyMedium,
                color = FixoraTheme.extendedColors.textSecondary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.sm), verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Rounded.Payments,
                    contentDescription = null,
                    tint = FixoraTheme.extendedColors.warningOnSurface,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    "Payments are a simulated demo. No card is charged and no card details are stored.",
                    style = MaterialTheme.typography.bodySmall,
                    color = FixoraTheme.extendedColors.warningOnSurface,
                )
            }
        }
    }
}

@Composable
private fun ProfileLoadingState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(FixoraSpacing.md),
        verticalArrangement = Arrangement.spacedBy(FixoraSpacing.md),
    ) {
        Box(Modifier.fillMaxWidth(0.42f).height(24.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(FixoraRadius.input)))
        Box(Modifier.fillMaxWidth().height(220.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(FixoraRadius.card)))
        repeat(3) {
            Box(Modifier.fillMaxWidth().height(88.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(FixoraRadius.card)))
        }
    }
}

@Composable
private fun ProfileErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(FixoraSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Rounded.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(FixoraSpacing.md))
        Text("Profile unavailable", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(FixoraSpacing.sm))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = FixoraTheme.extendedColors.textSecondary)
        Spacer(Modifier.height(FixoraSpacing.md))
        OutlinedButton(onClick = onRetry) { Text("Try again") }
    }
}

internal val UserRole?.accountLabel: String
    get() = when (this) {
        UserRole.CUSTOMER, null -> "Fixora Customer"
    }
