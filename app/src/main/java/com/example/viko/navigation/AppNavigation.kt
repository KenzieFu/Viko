package com.example.viko.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.viko.view.screens.onboarding.OnboardingFlow
import com.example.viko.view.screens.channel.ManageChannelScreen
import com.example.viko.view.screens.directcall.DirectCallScreen
import com.example.viko.view.screens.home.HomeScreen
import com.example.viko.view.screens.splash.SplashDestination
import com.example.viko.view.screens.splash.SplashScreen
import com.example.viko.view.screens.notifications.NotificationsScreen
import com.example.viko.view.screens.settings.SettingsScreen
import com.example.viko.view.screens.team.ManageMemberScreen
import com.example.viko.view.screens.team.MemberDetailScreen

/**
 * Every destination in the app. Screens receive navigation callbacks only —
 * they never hold the NavController, and they get their data from a ViewModel
 * scoped to their own back stack entry.
 *
 * Adding a member keeps a shared ViewModel across its discovery sheet and full-screen
 * setup form, so the channel picker can
 * write straight back into the half-filled form. See AddMemberFlow.
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
                        SplashDestination.Home -> Screen.Home.createRoute()
                    }
                    navController.navigate(next) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.FirstChannel.route) {
            OnboardingFlow(onFinished = {
                navController.navigate(Screen.Home.createRoute(startTutorial = true)) {
                    popUpTo(Screen.FirstChannel.route) { inclusive = true }
                }
            })
        }

        composable(Screen.OnboardingReplay.route) {
            OnboardingFlow(replay = true, onFinished = {
                navController.navigate(Screen.Home.createRoute(startTutorial = true)) {
                    popUpTo(Screen.Home.route) { inclusive = true }
                }
            })
        }

        composable(
            route = Screen.Home.route,
            arguments = listOf(
                navArgument(NavArgs.START_TUTORIAL) {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) { entry ->
            HomeScreen(
                startTutorial = entry.arguments?.getBoolean(NavArgs.START_TUTORIAL) == true,
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
                onOpenManageChannels = { navController.navigate(Screen.ManageChannels.createRoute()) },
                onReplayOnboarding = { navController.navigate(Screen.OnboardingReplay.route) }
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

        composable(
            route = Screen.ManageChannels.route,
            arguments = listOf(
                navArgument(NavArgs.SHOW_TEAM_READY) {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) { entry ->
            ManageChannelScreen(
                showTeamReady = entry.arguments?.getBoolean(NavArgs.SHOW_TEAM_READY) == true,
                onBack = { navController.popBackStack() },
                onChannelClick = { channelId ->
                    navController.navigate(Screen.ManageMembers.createRoute(channelId))
                },
                onGoToHomepage = {
                    // Replace the onboarding stack: Home is the top of the app
                    // from here, and it opens offering the tour.
                    navController.navigate(Screen.Home.createRoute(startTutorial = true)) {
                        popUpTo(0) { inclusive = true }
                    }
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
