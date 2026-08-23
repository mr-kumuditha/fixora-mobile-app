package com.techfix.app.ui.customer

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.techfix.app.core.designsystem.FixoraRadius
import com.techfix.app.core.designsystem.FixoraSpacing
import com.techfix.app.core.designsystem.FixoraTheme
import com.techfix.app.core.navigation.CustomerRoutes

/**
 * The five top-level customer destinations.
 *
 * Only these five show the bottom bar — anything reached by drilling down
 * (service detail, the booking flow, tracking, payment, a history entry)
 * keeps the full screen and its own Back arrow. Staff have their own
 * navigation and never see this bar.
 *
 * Each tab carries two icons, per the design system: outlined when inactive,
 * filled when active. Compose ships the filled weight as `Icons.Rounded` and
 * the outlined weight as `Icons.Outlined` — there is no outlined *Rounded*
 * variant in the Material Symbols set bundled with Compose, so the outlined
 * pair is the closest match available rather than a second icon family.
 */
enum class CustomerTab(
    val route: String,
    val label: String,
    /** Read by TalkBack, so it says "Book Repair" where the label says "Book". */
    val contentDescription: String,
    val activeIcon: ImageVector,
    val inactiveIcon: ImageVector,
) {
    HOME(
        route = CustomerRoutes.HOME,
        label = "Home",
        contentDescription = "Home",
        activeIcon = Icons.Rounded.Home,
        inactiveIcon = Icons.Outlined.Home,
    ),
    SERVICES(
        route = CustomerRoutes.CATALOG,
        label = "Services",
        contentDescription = "Services",
        activeIcon = Icons.Rounded.Smartphone,
        inactiveIcon = Icons.Outlined.Smartphone,
    ),
    BOOK(
        route = CustomerRoutes.BOOK_START,
        // "Book Repair" does not fit a fifth of a phone-width nav bar without
        // truncating, so the label is short and the full name is the
        // content description and the screen title.
        label = "Book",
        contentDescription = "Book Repair",
        activeIcon = Icons.Rounded.CalendarMonth,
        inactiveIcon = Icons.Outlined.CalendarMonth,
    ),
    REPAIRS(
        route = CustomerRoutes.HISTORY,
        label = "My Repairs",
        contentDescription = "My Repairs",
        activeIcon = Icons.Rounded.Build,
        inactiveIcon = Icons.Outlined.Build,
    ),
    PROFILE(
        route = CustomerRoutes.PROFILE,
        label = "Profile",
        contentDescription = "Profile",
        activeIcon = Icons.Rounded.Person,
        inactiveIcon = Icons.Outlined.Person,
    ),
    ;

    companion object {
        /** The routes that show the bar. Anything else is a drill-down. */
        val routes: Set<String> = entries.map { it.route }.toSet()

        fun forRoute(route: String?): CustomerTab? = entries.firstOrNull { it.route == route }
    }
}

@Composable
fun CustomerBottomBar(
    currentRoute: String?,
    onTabSelected: (CustomerTab) -> Unit,
) {
    val selectedTab = CustomerTab.forRoute(currentRoute)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = FixoraSpacing.md, vertical = FixoraSpacing.sm),
        shape = RoundedCornerShape(FixoraRadius.sheet),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, FixoraTheme.extendedColors.border),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        NavigationBar(
            modifier = Modifier.fillMaxWidth(),
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            tonalElevation = 0.dp,
        ) {
            CustomerTab.entries.forEach { tab ->
                val selected = tab == selectedTab
                NavigationBarItem(
                    selected = selected,
                    onClick = { onTabSelected(tab) },
                    icon = {
                        Crossfade(targetState = selected, animationSpec = tween(180), label = "${tab.name}Icon") { active ->
                            Icon(
                                imageVector = if (active) tab.activeIcon else tab.inactiveIcon,
                                contentDescription = tab.contentDescription,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    },
                    label = {
                        Text(text = tab.label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    },
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
