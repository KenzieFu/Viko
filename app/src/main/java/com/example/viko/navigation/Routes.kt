package com.example.viko.navigation

/**
 * Names of the arguments carried in a route. ViewModels read them back out of
 * their `SavedStateHandle` using these same keys, so they live here rather than
 * as loose strings on both sides.
 */
object NavArgs {
    const val WORKER_ID = "workerId"
    const val CHANNEL_ID = "channelId"

    /** Home opens offering the first-run tour. */
    const val START_TUTORIAL = "startTutorial"

    /** Manage Channel opens with the "your team ready" card over it. */
    const val SHOW_TEAM_READY = "showTeamReady"
}

sealed class Screen(val route: String) {
    /** The mark, held while the database says whether this install is set up. */
    data object Splash : Screen("splash")

    /** Onboarding: name the first team channel. Nothing works without one. */
    data object FirstChannel : Screen("first_channel")

    data object Home : Screen("home?${NavArgs.START_TUTORIAL}={${NavArgs.START_TUTORIAL}}") {
        fun createRoute(startTutorial: Boolean = false) =
            "home?${NavArgs.START_TUTORIAL}=$startTutorial"
    }

    data object Notifications : Screen("notifications")

    data object Settings : Screen("settings")

    /** [NavArgs.CHANNEL_ID] is optional: set, the roster opens filtered to that channel. */
    data object ManageMembers : Screen("manage_members?${NavArgs.CHANNEL_ID}={${NavArgs.CHANNEL_ID}}") {
        fun createRoute(channelId: String? = null) =
            if (channelId == null) "manage_members" else "manage_members?${NavArgs.CHANNEL_ID}=$channelId"
    }

    data object ManageChannels :
        Screen("manage_channels?${NavArgs.SHOW_TEAM_READY}={${NavArgs.SHOW_TEAM_READY}}") {
        fun createRoute(showTeamReady: Boolean = false) =
            "manage_channels?${NavArgs.SHOW_TEAM_READY}=$showTeamReady"
    }

    data object DirectCall : Screen("direct_call/{${NavArgs.WORKER_ID}}") {
        fun createRoute(workerId: String) = "direct_call/$workerId"
    }

    data object MemberDetail : Screen("member_detail/{${NavArgs.WORKER_ID}}") {
        fun createRoute(workerId: String) = "member_detail/$workerId"
    }
}
