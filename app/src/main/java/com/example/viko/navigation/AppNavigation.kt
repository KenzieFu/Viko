package com.example.viko.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.viko.data.AppState
import com.example.viko.ui.screens.channel.SelectChannelScreen
import com.example.viko.ui.screens.directcall.DirectCallScreen
import com.example.viko.ui.screens.home.HomeScreen
import com.example.viko.ui.screens.notifications.NotificationsScreen
import com.example.viko.ui.screens.team.ManageMemberScreen
import com.example.viko.ui.screens.team.MemberDetailScreen
import com.example.viko.ui.screens.team.MemberPairingScreen
import com.example.viko.ui.screens.team.SetupMemberScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val appState = remember { AppState() }

    NavHost(navController = navController, startDestination = Screen.Home.route) {

        composable(Screen.Home.route) {
            HomeScreen(appState = appState, navController = navController)
        }

        composable(Screen.Notifications.route) {
            NotificationsScreen(
                appState = appState,
                onBack = { navController.popBackStack() },
                onDirectCall = { workerId ->
                    navController.navigate(Screen.DirectCall.createRoute(workerId))
                }
            )
        }

        composable(
            route = Screen.DirectCall.route,
            arguments = listOf(navArgument("workerId") { type = NavType.StringType })
        ) { backStackEntry ->
            val workerId = backStackEntry.arguments?.getString("workerId") ?: return@composable
            DirectCallScreen(
                workerId = workerId,
                appState = appState,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.ManageMembers.route) {
            ManageMemberScreen(
                appState = appState,
                onBack = { navController.popBackStack() },
                onMemberClick = { workerId ->
                    navController.navigate(Screen.MemberDetail.createRoute(workerId))
                },
                onAddMember = {
                    navController.navigate(Screen.MemberPairing.createRoute("ch1"))
                }
            )
        }

        composable(
            route = Screen.MemberDetail.route,
            arguments = listOf(navArgument("workerId") { type = NavType.StringType })
        ) { backStackEntry ->
            val workerId = backStackEntry.arguments?.getString("workerId") ?: return@composable
            MemberDetailScreen(
                workerId = workerId,
                appState = appState,
                onBack = { navController.popBackStack() },
                onDirectCall = { wId ->
                    navController.navigate(Screen.DirectCall.createRoute(wId))
                },
                onSelectChannel = { wId ->
                    navController.navigate(Screen.SelectChannel.createRoute(wId))
                },
                onRemove = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.MemberPairing.route,
            arguments = listOf(navArgument("channelId") { type = NavType.StringType })
        ) { backStackEntry ->
            val channelId = backStackEntry.arguments?.getString("channelId") ?: "ch1"
            MemberPairingScreen(
                channelId = channelId,
                appState = appState,
                onBack = { navController.popBackStack() },
                onDevicePaired = { deviceId ->
                    navController.navigate(Screen.SetupMember.createRoute(deviceId)) {
                        popUpTo(Screen.MemberPairing.route) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Screen.SetupMember.route,
            arguments = listOf(navArgument("deviceId") { type = NavType.StringType })
        ) { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getString("deviceId") ?: ""
            SetupMemberScreen(
                deviceId = deviceId,
                appState = appState,
                onBack = { navController.popBackStack() },
                onSetupComplete = {
                    navController.navigate(Screen.ManageMembers.route) {
                        popUpTo(Screen.ManageMembers.route) { inclusive = true }
                    }
                },
                onSelectChannel = { dId ->
                    navController.navigate(Screen.SelectChannel.createRoute(dId))
                }
            )
        }

        composable(
            route = Screen.SelectChannel.route,
            arguments = listOf(navArgument("workerId") { type = NavType.StringType })
        ) { backStackEntry ->
            val workerId = backStackEntry.arguments?.getString("workerId") ?: ""
            SelectChannelScreen(
                workerId = workerId,
                appState = appState,
                onBack = { navController.popBackStack() },
                onChannelSelected = { navController.popBackStack() }
            )
        }
    }
}
