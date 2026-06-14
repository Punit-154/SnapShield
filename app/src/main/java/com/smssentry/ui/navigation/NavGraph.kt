package com.smssentry.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.smssentry.deepcheck.ui.ModelDownloadScreen
import com.smssentry.ui.chat.ChatScreen
import com.smssentry.ui.compose.ComposeSmsScreen
import com.smssentry.ui.conversations.ConversationListScreen
import com.smssentry.ui.detail.DetailScreen
import com.smssentry.ui.settings.BlockedNumbersScreen
import com.smssentry.ui.settings.SettingsScreen
import com.smssentry.ui.theme.ThemePreferenceRepository

sealed class Screen(val route: String) {
    data object Conversations : Screen("conversations")
    data object Chat : Screen("chat/{threadId}/{address}") {
        fun createRoute(threadId: Long, address: String) =
            "chat/$threadId/${java.net.URLEncoder.encode(address, "UTF-8")}"
    }
    data object Detail : Screen("detail/{smsId}") {
        fun createRoute(smsId: String) = "detail/$smsId"
    }
    data object Compose : Screen("compose?recipient={recipient}") {
        fun createRoute(recipient: String = "") = "compose?recipient=$recipient"
    }
    data object Settings : Screen("settings")
    data object BlockedNumbers : Screen("blocked_numbers")
    data object ModelDownload : Screen("model_download")
}

private const val NAV_ANIM_DURATION = 350

@Composable
fun SMSSentryNavGraph(
    navController: NavHostController,
    themeRepository: ThemePreferenceRepository
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Conversations.route,
        enterTransition = {
            slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(NAV_ANIM_DURATION, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(NAV_ANIM_DURATION))
        },
        exitTransition = {
            slideOutHorizontally(
                targetOffsetX = { -it / 4 },
                animationSpec = tween(NAV_ANIM_DURATION, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(NAV_ANIM_DURATION / 2))
        },
        popEnterTransition = {
            slideInHorizontally(
                initialOffsetX = { -it / 4 },
                animationSpec = tween(NAV_ANIM_DURATION, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(NAV_ANIM_DURATION))
        },
        popExitTransition = {
            slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(NAV_ANIM_DURATION, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(NAV_ANIM_DURATION / 2))
        }
    ) {
        // ── Home: Conversation List ──
        composable(Screen.Conversations.route) {
            ConversationListScreen(
                onConversationClick = { threadId, address ->
                    navController.navigate(Screen.Chat.createRoute(threadId, address))
                },
                onComposeClick = {
                    navController.navigate(Screen.Compose.createRoute())
                },
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        // ── Chat Thread ──
        composable(
            route = Screen.Chat.route,
            arguments = listOf(
                navArgument("threadId") { type = NavType.LongType },
                navArgument("address") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) {
            ChatScreen(
                onBackClick = { navController.popBackStack() },
                onDeepCheck = { smsId ->
                    navController.navigate(Screen.Detail.createRoute(smsId))
                }
            )
        }

        // ── Message Detail / AI Analysis ──
        composable(
            route = Screen.Detail.route,
            arguments = listOf(
                navArgument("smsId") { type = NavType.StringType }
            )
        ) {
            DetailScreen(
                onBackClick = { navController.popBackStack() },
                onNavigateToDownload = {
                    navController.navigate(Screen.ModelDownload.route)
                }
            )
        }

        // ── Compose SMS ──
        composable(
            route = Screen.Compose.route,
            arguments = listOf(
                navArgument("recipient") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) {
            ComposeSmsScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        // ── Settings ──
        composable(Screen.Settings.route) {
            SettingsScreen(
                onBackClick = { navController.popBackStack() },
                onBlockedNumbersClick = {
                    navController.navigate(Screen.BlockedNumbers.route)
                },
                onModelDownloadClick = {
                    navController.navigate(Screen.ModelDownload.route)
                }
            )
        }

        // ── Blocked Numbers ──
        composable(Screen.BlockedNumbers.route) {
            BlockedNumbersScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        // ── Model Download ──
        composable(
            route = Screen.ModelDownload.route,
            enterTransition = {
                slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(NAV_ANIM_DURATION, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(NAV_ANIM_DURATION))
            },
            popExitTransition = {
                slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(NAV_ANIM_DURATION, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(NAV_ANIM_DURATION / 2))
            }
        ) {
            ModelDownloadScreen(
                onBackClick = {
                    if (!navController.popBackStack()) {
                        navController.navigate(Screen.Conversations.route) {
                            popUpTo(Screen.Conversations.route) { inclusive = true }
                        }
                    }
                }
            )
        }
    }
}
