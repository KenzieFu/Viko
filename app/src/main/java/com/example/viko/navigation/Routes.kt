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
    data object Home : Screen("home")

    data object Notifications : Screen("notifications")

    data object ManageMembers : Screen("manage_members")

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
