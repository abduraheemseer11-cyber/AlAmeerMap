package com.network.scanner.pro.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.network.scanner.pro.presentation.auth.AuthScreen
import com.network.scanner.pro.presentation.dashboard.DashboardScreen
import com.network.scanner.pro.presentation.map.MapScreen
import com.network.scanner.pro.presentation.profile.ProfileScreen
import com.network.scanner.pro.presentation.scanner.ScannerScreen
import com.network.scanner.pro.presentation.speedtest.SpeedTestScreen
import com.network.scanner.pro.presentation.tools.ToolsScreen

/** Navigates to a top-level tab, keeping a single back stack per tab. */
fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Routes.DASHBOARD,
        modifier = modifier
    ) {
        composable(Routes.DASHBOARD) {
            DashboardScreen(onNavigate = { route -> navController.navigateToTab(route) })
        }
        composable(Routes.SCANNER) {
            ScannerScreen()
        }
        composable(Routes.MAP) {
            MapScreen(onNavigateToAuth = { navController.navigate(Routes.AUTH) { launchSingleTop = true } })
        }
        composable(Routes.TOOLS) {
            ToolsScreen(onOpenSpeedTest = { navController.navigate(Routes.SPEEDTEST) { launchSingleTop = true } })
        }
        composable(Routes.SPEEDTEST) {
            SpeedTestScreen()
        }
        composable(Routes.PROFILE) {
            ProfileScreen(onNavigateToAuth = { navController.navigate(Routes.AUTH) { launchSingleTop = true } })
        }
        composable(Routes.AUTH) {
            AuthScreen(
                onAuthenticated = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
