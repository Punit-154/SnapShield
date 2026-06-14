package com.smssentry.ui.navigation

import android.util.Log
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
import com.smssentry.ui.compose.ComposeSmsScreen
import com.smssentry.ui.detail.DetailScreen
import com.smssentry.ui.inbox.InboxScreen
import com.smssentry.ui.theme.ThemePreferenceRepository

sealed class Screen(val route: String) {
    data object Inbox : Screen("inbox")
    data object Detail : Screen("detail/{smsId}") {
        fun createRoute(smsId: String) = "detail/$smsId"
    }
    data object ModelDownload : Screen("model_download")
    data object Compose : Screen("compose?recipient={recipient}") {
        fun createRoute(recipient: String = "") = "compose?recipient=$recipient"
    }
}

private const val TAG = "NavGraph"
private const val NAV_ANIM_DURATION = 350

@Composable
fun SMSSentryNavGraph(
    navController: NavHostController,
    themeRepository: ThemePreferenceRepository
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Inbox.route,
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
        composable(Screen.Inbox.route) {
            InboxScreen(
                onMessageClick = { smsId ->
                    navController.navigate(Screen.Detail.createRoute(smsId))
                },
                onComposeSms = {
                    navController.navigate(Screen.Compose.createRoute())
                },
                themeRepository = themeRepository
            )
        }

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
                    val backQueue = navController.currentBackStack.value.map { it.destination.route }
                    Log.d(TAG, "backQueue before pop: $backQueue")
                    if (!navController.popBackStack()) {
                        Log.w(TAG, "popBackStack returned false, navigating to Inbox")
                        navController.navigate(Screen.Inbox.route) {
                            popUpTo(Screen.Inbox.route) { inclusive = true }
                        }
                    }
                }
            )
        }

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
    }
}

