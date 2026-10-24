package com.example.viko.navigation

/**
 * Names of the arguments carried in a route. ViewModels read them back out of
 * their `SavedStateHandle` using these same keys, so they live here rather than
 * as loose strings on both sides.
 */
object NavArgs {
    const val WORKER_ID = "workerId"
    const val CHANNEL_ID = "channelId"
}

sealed class Screen(val route: String) {
    data object Home : Screen("home")

    data object Notifications : Screen("notifications")

    data object Settings : Screen("settings")

    /** [NavArgs.CHANNEL_ID] is optional: set, the roster opens filtered to that channel. */
    data object ManageMembers : Screen("manage_members?${NavArgs.CHANNEL_ID}={${NavArgs.CHANNEL_ID}}") {
        fun createRoute(channelId: String? = null) =
            if (channelId == null) "manage_members" else "manage_members?${NavArgs.CHANNEL_ID}=$channelId"
    }

    data object ManageChannels : Screen("manage_channels")

    data object DirectCall : Screen("direct_call/{${NavArgs.WORKER_ID}}") {
        fun createRoute(workerId: String) = "direct_call/$workerId"
    }

    data object MemberDetail : Screen("member_detail/{${NavArgs.WORKER_ID}}") {
        fun createRoute(workerId: String) = "member_detail/$workerId"
    }
}
