package com.example.viko.navigation

/**
 * Names of the arguments carried in a route. ViewModels read them back out of
 * their `SavedStateHandle` using these same keys, so they live here rather than
 * as loose strings on both sides.
 */
object NavArgs {
    const val WORKER_ID = "workerId"
}

sealed class Screen(val route: String) {
    /** The mark, held while the database says whether this install is set up. */
    data object Splash : Screen("splash")

    /** Onboarding: name the first team channel. Nothing works without one. */
    data object FirstChannel : Screen("first_channel")

    data object Home : Screen("home")

    data object ManageChannels : Screen("manage_channels")

    data object Notifications : Screen("notifications")

    data object DirectCall : Screen("direct_call/{${NavArgs.WORKER_ID}}") {
        fun createRoute(workerId: String) = "direct_call/$workerId"
    }

    data object MemberDetail : Screen("member_detail/{${NavArgs.WORKER_ID}}") {
        fun createRoute(workerId: String) = "member_detail/$workerId"
    }

    data object SelectChannel : Screen("select_channel/{${NavArgs.WORKER_ID}}") {
        fun createRoute(workerId: String) = "select_channel/$workerId"
    }
}
