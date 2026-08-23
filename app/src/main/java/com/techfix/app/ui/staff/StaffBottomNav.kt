package com.techfix.app.ui.staff

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.PendingActions
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.PendingActions
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.techfix.app.core.designsystem.FixoraTheme
import com.techfix.app.core.navigation.StaffRoutes

private data class StaffTab(val label: String, val route: String, val active: ImageVector, val inactive: ImageVector)

internal data class StaffDestinationSpec(val label: String, val route: String)

internal fun staffDestinations(role: com.techfix.app.core.navigation.UserRole): List<StaffDestinationSpec> {
    val canAssign = role == com.techfix.app.core.navigation.UserRole.ADMIN || role == com.techfix.app.core.navigation.UserRole.BRANCH_MANAGER
    return buildList {
        add(StaffDestinationSpec("Dashboard", StaffRoutes.DASHBOARD))
        add(StaffDestinationSpec(if (canAssign) "Queue" else "My repairs", StaffRoutes.queue(if (canAssign) StaffQueueTab.NEW.name else StaffQueueTab.ACTIVE.name)))
        if (role != com.techfix.app.core.navigation.UserRole.TECHNICIAN) add(StaffDestinationSpec("Team", StaffRoutes.TECHNICIANS))
        add(StaffDestinationSpec("Inventory", StaffRoutes.INVENTORY))
        add(StaffDestinationSpec("More", StaffRoutes.MORE))
    }
}

@Composable
fun StaffBottomBar(
    role: com.techfix.app.core.navigation.UserRole,
    currentRoute: String?,
    onTabSelected: (String) -> Unit,
) {
    val tabs = staffDestinations(role).map { spec ->
        when (spec.route.substringBefore('?')) {
            StaffRoutes.DASHBOARD -> StaffTab(spec.label, spec.route, Icons.Rounded.Home, Icons.Outlined.Home)
            StaffRoutes.TECHNICIANS -> StaffTab(spec.label, spec.route, Icons.Rounded.Groups, Icons.Outlined.Groups)
            StaffRoutes.INVENTORY -> StaffTab(spec.label, spec.route, Icons.Rounded.Inventory2, Icons.Outlined.Inventory2)
            StaffRoutes.MORE -> StaffTab(spec.label, spec.route, Icons.Rounded.MoreHoriz, Icons.Outlined.MoreHoriz)
            else -> StaffTab(spec.label, spec.route, if (role == com.techfix.app.core.navigation.UserRole.TECHNICIAN) Icons.Rounded.Build else Icons.Rounded.PendingActions, if (role == com.techfix.app.core.navigation.UserRole.TECHNICIAN) Icons.Outlined.Build else Icons.Outlined.PendingActions)
        }
    }
    Column {
        HorizontalDivider(color = FixoraTheme.extendedColors.border)
        NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
            tabs.forEach { tab ->
                val selected = currentRoute == tab.route || (tab.route.startsWith("staff/queue") && currentRoute?.startsWith("staff/queue") == true)
                NavigationBarItem(
                    selected = selected,
                    onClick = { onTabSelected(tab.route) },
                    icon = { Icon(if (selected) tab.active else tab.inactive, contentDescription = tab.label, modifier = Modifier.size(24.dp)) },
                    label = { Text(tab.label, style = MaterialTheme.typography.labelMedium, maxLines = 1) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = FixoraTheme.extendedColors.textSecondary,
                        unselectedTextColor = FixoraTheme.extendedColors.textSecondary,
                    ),
                )
            }
        }
    }
}
