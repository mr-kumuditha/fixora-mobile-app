package com.techfix.app.ui.staff

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.techfix.app.core.designsystem.FixoraTheme
import com.techfix.app.core.navigation.UserRole
import com.techfix.app.domain.branch.Branch
import com.techfix.app.domain.operations.BranchPerformance

@Preview(name = "Admin overview light", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun AdminOverviewLightPreview() = FixoraTheme(darkTheme = false) { AdminOverviewPreviewContent() }

@Preview(name = "Admin overview dark", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun AdminOverviewDarkPreview() = FixoraTheme(darkTheme = true) { AdminOverviewPreviewContent() }

@Composable
private fun AdminOverviewPreviewContent() {
    StaffDashboardScreen(
        staffContext = StaffContext("admin", UserRole.ADMIN, null, null),
        uiState = StaffDashboardUiState(
            isLoading = false,
            newCount = 7,
            activeCount = 18,
            readyCount = 4,
            totalCount = 42,
            completedCount = 15,
            availableTechnicianCount = 4,
            customerCount = 28,
            recordedRevenue = 48_700.0,
            outOfStockCount = 2,
            branchPerformance = listOf(
                BranchPerformance(Branch("colombo", "Colombo", 0.0, 0.0, "Colombo"), 27, 12, 15, 2, 32_000.0, 1),
                BranchPerformance(Branch("galle", "Galle", 0.0, 0.0, "Galle"), 15, 8, 7, 2, 16_700.0, 1),
            ),
        ),
        branchName = null,
        onRetry = {},
        onOpenQueue = {},
        onOpenInventory = {},
        onSignOut = {},
    )
}
