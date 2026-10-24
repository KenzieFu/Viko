package com.example.viko.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.viko.view.screens.channel.ManageChannelScreen
import com.example.viko.view.screens.directcall.DirectCallScreen
import com.example.viko.view.screens.home.HomeScreen
import com.example.viko.view.screens.notifications.NotificationsScreen
import com.example.viko.view.screens.settings.SettingsScreen
import com.example.viko.view.screens.team.ManageMemberScreen
import com.example.viko.view.screens.team.MemberDetailScreen

/**
 * Every destination in the app. Screens receive navigation callbacks only —
 * they never hold the NavController, and they get their data from a ViewModel
 * scoped to their own back stack entry.
 *
 * Adding a member and picking a channel are deliberately NOT destinations: they
 * are stacks of bottom sheets over the screen that needs them, so the picker can
 * write straight back into the half-filled form. See AddMemberFlow.
 */
@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Screen.Home.route) {

        composable(Screen.Home.route) {
            HomeScreen(
                onOpenNotifications = { navController.navigate(Screen.Notifications.route) },
                onOpenSettings = { navController.navigate(Screen.Settings.route) },
                onDirectCall = { workerId ->
                    navController.navigate(Screen.DirectCall.createRoute(workerId))
                }
            )
        }

        composable(Screen.Notifications.route) {
            NotificationsScreen(
                onBack = { navController.popBackStack() },
                onDirectCall = { workerId ->
                    navController.navigate(Screen.DirectCall.createRoute(workerId))
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenManageMembers = { navController.navigate(Screen.ManageMembers.createRoute()) },
                onOpenManageChannels = { navController.navigate(Screen.ManageChannels.route) }
            )
        }

        composable(
            route = Screen.DirectCall.route,
            arguments = listOf(navArgument(NavArgs.WORKER_ID) { type = NavType.StringType })
        ) {
            DirectCallScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.ManageMembers.route,
            arguments = listOf(
                navArgument(NavArgs.CHANNEL_ID) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) {
            ManageMemberScreen(
                onBack = { navController.popBackStack() },
                onMemberClick = { workerId ->
                    navController.navigate(Screen.MemberDetail.createRoute(workerId))
                }
            )
        }

        composable(Screen.ManageChannels.route) {
            ManageChannelScreen(
                onBack = { navController.popBackStack() },
                onChannelClick = { channelId ->
                    navController.navigate(Screen.ManageMembers.createRoute(channelId))
                }
            )
        }

        composable(
            route = Screen.MemberDetail.route,
            arguments = listOf(navArgument(NavArgs.WORKER_ID) { type = NavType.StringType })
        ) {
            MemberDetailScreen(onBack = { navController.popBackStack() })
        }
    }
}
