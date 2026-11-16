package com.network.scanner.pro.presentation.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.network.scanner.pro.R

object Routes {
    const val DASHBOARD = "dashboard"
    const val SCANNER = "scanner"
    const val MAP = "map"
    const val TOOLS = "tools"
    const val SPEEDTEST = "speedtest"
    const val PROFILE = "profile"
    const val AUTH = "auth"
}

data class BottomNavItem(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector
)

/** Exactly five tabs: Dashboard, Scanner, Map, Tools, Profile. */
val bottomNavItems = listOf(
    BottomNavItem(Routes.DASHBOARD, R.string.nav_dashboard, Icons.Default.Dashboard),
    BottomNavItem(Routes.SCANNER, R.string.nav_scanner, Icons.Default.Devices),
    BottomNavItem(Routes.MAP, R.string.nav_map, Icons.Default.Map),
    BottomNavItem(Routes.TOOLS, R.string.nav_tools, Icons.Default.Build),
    BottomNavItem(Routes.PROFILE, R.string.nav_profile, Icons.Default.Person)
)

@Composable
fun BottomNavBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // No tab bar on the sign-in screen.
    if (currentRoute == Routes.AUTH) return

    NavigationBar {
        bottomNavItems.forEach { item ->
            // The speed-test screen is opened from Dashboard/Tools, so keep Tools highlighted.
            val selected = currentRoute == item.route ||
                (item.route == Routes.TOOLS && currentRoute == Routes.SPEEDTEST)
            NavigationBarItem(
                selected = selected,
                onClick = { navController.navigateToTab(item.route) },
                icon = { Icon(item.icon, contentDescription = null) },
                label = {
                    Text(
                        text = stringResource(item.labelRes),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }
    }
}
