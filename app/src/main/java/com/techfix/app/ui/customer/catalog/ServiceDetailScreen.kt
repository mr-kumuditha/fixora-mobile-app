package com.techfix.app.ui.customer.catalog

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.techfix.app.core.designsystem.FixoraRadius
import com.techfix.app.core.designsystem.FixoraSpacing
import com.techfix.app.core.designsystem.FixoraTheme
import com.techfix.app.core.designsystem.OfflineNotice
import com.techfix.app.domain.catalog.RepairService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceDetailScreen(uiState: ServiceDetailUiState, onRetry: () -> Unit, onBookRepair: (RepairService) -> Unit, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.service?.name ?: "Service details") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            if (uiState.service != null) {
                Button(
                    onClick = { onBookRepair(uiState.service) },
                    modifier = Modifier.fillMaxWidth().padding(FixoraSpacing.md),
                    colors = ButtonDefaults.buttonColors(containerColor = FixoraTheme.extendedColors.accent, contentColor = FixoraTheme.extendedColors.onAccent),
                ) { Text("Book this service") }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Crossfade(targetState = uiState, label = "serviceDetail", modifier = Modifier.fillMaxSize().padding(padding)) { state ->
            when {
                state.isLoading -> DetailSkeleton()
                state.errorMessage != null -> DetailError(onRetry)
                state.service != null -> DetailContent(state.service, state.isOffline)
                else -> Box(Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun DetailContent(service: RepairService, isOffline: Boolean) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = FixoraSpacing.md).padding(bottom = FixoraSpacing.md), verticalArrangement = Arrangement.spacedBy(FixoraSpacing.md)) {
        OfflineNotice(visible = isOffline, message = "Offline — showing this service from your saved catalog.")
        Box(modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(FixoraRadius.card))) {
            ServiceImage(service, modifier = Modifier.fillMaxSize())
            Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC0F1115)))))
            Row(modifier = Modifier.align(Alignment.BottomStart).padding(FixoraSpacing.md), horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                Icon(service.category.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text(service.category.label, style = MaterialTheme.typography.labelLarge, color = Color.White)
            }
        }
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(FixoraRadius.card), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(FixoraSpacing.md), verticalArrangement = Arrangement.spacedBy(FixoraSpacing.sm)) {
                Text(service.name, style = MaterialTheme.typography.headlineSmall)
                Text("Starting from", style = MaterialTheme.typography.labelMedium, color = FixoraTheme.extendedColors.textSecondary)
                Text("Rs. %,.0f".format(service.basePrice), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                Text(service.description, style = MaterialTheme.typography.bodyMedium, color = FixoraTheme.extendedColors.textSecondary)
            }
        }
        Text("About this service", style = MaterialTheme.typography.titleLarge)
        Text(service.description, style = MaterialTheme.typography.bodyLarge, color = FixoraTheme.extendedColors.textSecondary)
        Text("Suggested device brands", style = MaterialTheme.typography.titleLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.sm), contentPadding = androidx.compose.foundation.layout.PaddingValues(end = FixoraSpacing.md)) {
            items(DeviceBrandCatalog.brandsFor(service.category).take(6)) { brand ->
                androidx.compose.material3.AssistChip(onClick = {}, label = { Text(brand) }, leadingIcon = { Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp)) })
            }
        }
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(FixoraRadius.card)) {
            Row(modifier = Modifier.padding(FixoraSpacing.md), horizontalArrangement = Arrangement.spacedBy(FixoraSpacing.sm), verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("The displayed amount is a starting price. Your technician confirms the final quote after inspecting the device.", style = MaterialTheme.typography.bodySmall, color = FixoraTheme.extendedColors.textSecondary)
            }
        }
    }
}

@Composable
private fun DetailSkeleton() {
    val transition = rememberInfiniteTransition(label = "detailSkeleton")
    val alpha by transition.animateFloat(0.4f, 1f, infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse), label = "skeletonAlpha")
    Column(modifier = Modifier.fillMaxSize().padding(FixoraSpacing.md), verticalArrangement = Arrangement.spacedBy(FixoraSpacing.md)) {
        Box(modifier = Modifier.fillMaxWidth().height(220.dp).alpha(alpha).clip(RoundedCornerShape(FixoraRadius.card)).background(MaterialTheme.colorScheme.surfaceVariant))
        Box(modifier = Modifier.fillMaxWidth().height(120.dp).alpha(alpha).clip(RoundedCornerShape(FixoraRadius.card)).background(MaterialTheme.colorScheme.surfaceVariant))
        Box(modifier = Modifier.fillMaxWidth(0.65f).height(24.dp).alpha(alpha).clip(RoundedCornerShape(FixoraRadius.input)).background(MaterialTheme.colorScheme.surfaceVariant))
    }
}

@Composable
private fun DetailError(onRetry: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(FixoraSpacing.xl), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.ErrorOutline, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
        Text("Unable to load service", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = FixoraSpacing.md))
        Text("Please check your connection and try again.", style = MaterialTheme.typography.bodySmall, color = FixoraTheme.extendedColors.textSecondary)
        OutlinedButton(onClick = onRetry, modifier = Modifier.padding(top = FixoraSpacing.md)) { Text("Retry") }
    }
}
