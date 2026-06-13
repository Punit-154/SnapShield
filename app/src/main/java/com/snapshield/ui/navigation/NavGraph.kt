package com.snapshield.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.snapshield.ui.detail.DetailScreen
import com.snapshield.ui.inbox.InboxScreen

sealed class Screen(val route: String) {
    data object Inbox : Screen("inbox")
    data object Detail : Screen("detail/{smsId}") {
        fun createRoute(smsId: String) = "detail/$smsId"
    }
}

@Composable
fun SnapShieldNavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Inbox.route
    ) {
        composable(Screen.Inbox.route) {
            InboxScreen(
                onMessageClick = { smsId ->
                    navController.navigate(Screen.Detail.createRoute(smsId))
                }
            )
        }

        composable(
            route = Screen.Detail.route,
            arguments = listOf(
                navArgument("smsId") { type = NavType.StringType }
            )
        ) {
            DetailScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
