package com.example.viko.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.viko.view.screens.channel.SelectChannelScreen
import com.example.viko.view.screens.directcall.DirectCallScreen
import com.example.viko.view.screens.home.HomeScreen
import com.example.viko.view.screens.notifications.NotificationsScreen
import com.example.viko.view.screens.team.ManageMemberScreen
import com.example.viko.view.screens.team.MemberDetailScreen

/**
 * Every destination in the app. Screens receive navigation callbacks only —
 * they never hold the NavController, and they get their data from a ViewModel
 * scoped to their own back stack entry.
 *
 * Adding a member is deliberately NOT a destination: it is a stack of bottom
 * sheets over ManageMemberScreen. See AddMemberFlow.
 */
@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Screen.Home.route) {

        composable(Screen.Home.route) {
            HomeScreen(
                onOpenNotifications = { navController.navigate(Screen.Notifications.route) },
                onOpenManageMembers = { navController.navigate(Screen.ManageMembers.route) },
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

        composable(
            route = Screen.DirectCall.route,
            arguments = listOf(navArgument(NavArgs.WORKER_ID) { type = NavType.StringType })
        ) {
            DirectCallScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.ManageMembers.route) {
            ManageMemberScreen(
                onBack = { navController.popBackStack() },
                onMemberClick = { workerId ->
                    navController.navigate(Screen.MemberDetail.createRoute(workerId))
                }
            )
        }

        composable(
            route = Screen.MemberDetail.route,
            arguments = listOf(navArgument(NavArgs.WORKER_ID) { type = NavType.StringType })
        ) {
            MemberDetailScreen(
                onBack = { navController.popBackStack() },
                onDirectCall = { workerId ->
                    navController.navigate(Screen.DirectCall.createRoute(workerId))
                },
                onSelectChannel = { workerId ->
                    navController.navigate(Screen.SelectChannel.createRoute(workerId))
                }
            )
        }

        composable(
            route = Screen.SelectChannel.route,
            arguments = listOf(navArgument(NavArgs.WORKER_ID) { type = NavType.StringType })
        ) {
            SelectChannelScreen(
                onBack = { navController.popBackStack() },
                onChannelSelected = { navController.popBackStack() }
            )
        }
    }
}
