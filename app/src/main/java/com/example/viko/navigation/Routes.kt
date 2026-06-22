package com.example.viko.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Notifications : Screen("notifications")
    object DirectCall : Screen("direct_call/{workerId}") {
        fun createRoute(workerId: String) = "direct_call/$workerId"
    }
    object ManageMembers : Screen("manage_members")
    object MemberDetail : Screen("member_detail/{workerId}") {
        fun createRoute(workerId: String) = "member_detail/$workerId"
    }
    object SetupMember : Screen("setup_member/{deviceId}") {
        fun createRoute(deviceId: String) = "setup_member/$deviceId"
    }
    object MemberPairing : Screen("member_pairing/{channelId}") {
        fun createRoute(channelId: String) = "member_pairing/$channelId"
    }
    object SelectChannel : Screen("select_channel/{workerId}") {
        fun createRoute(workerId: String) = "select_channel/$workerId"
    }
}
