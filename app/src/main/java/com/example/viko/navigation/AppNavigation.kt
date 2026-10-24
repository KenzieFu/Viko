package com.example.viko.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.viko.view.screens.channel.ManageChannelScreen
import com.example.viko.view.screens.channel.SelectChannelScreen
import com.example.viko.view.screens.directcall.DirectCallScreen
import com.example.viko.view.screens.home.HomeScreen
import com.example.viko.view.screens.notifications.NotificationsScreen
import com.example.viko.view.screens.onboarding.FirstChannelScreen
import com.example.viko.view.screens.splash.SplashDestination
import com.example.viko.view.screens.splash.SplashScreen
import com.example.viko.view.screens.team.MemberDetailScreen

/**
 * Every destination in the app. Screens receive navigation callbacks only —
 * they never hold the NavController, and they get their data from a ViewModel
 * scoped to their own back stack entry.
 *
 * Two journeys are deliberately NOT destinations and live as stacks of bottom
 * sheets instead: a channel's members (ChannelDetailSheet, over Manage Channel)
 * and adding a member (AddMemberFlow, over that). Pairing happens over the list
 * the badge is being added to.
 */
@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Screen.Splash.route) {

        composable(Screen.Splash.route) {
            SplashScreen(
                onFinished = { destination ->
                    val next = when (destination) {
                        SplashDestination.Onboarding -> Screen.FirstChannel.route
                        SplashDestination.Home -> Screen.Home.route
                    }
                    navController.navigate(next) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.FirstChannel.route) {
            FirstChannelScreen(
                onChannelCreated = {
                    // Land on Home and push Manage Channel on top of it, so the
                    // supervisor can carry on adding members and Back still has
                    // somewhere sensible to go.
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.FirstChannel.route) { inclusive = true }
                    }
                    navController.navigate(Screen.ManageChannels.route)
                }
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                onOpenNotifications = { navController.navigate(Screen.Notifications.route) },
                onOpenManageMembers = { navController.navigate(Screen.ManageChannels.route) },
                onDirectCall = { workerId ->
                    navController.navigate(Screen.DirectCall.createRoute(workerId))
                }
            )
        }

        composable(Screen.ManageChannels.route) {
            ManageChannelScreen(
                onBack = { navController.popBackStack() },
                onMemberClick = { workerId ->
                    navController.navigate(Screen.MemberDetail.createRoute(workerId))
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
