package com.example.musicplayer.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.musicplayer.presentation.album.AlbumScreen
import com.example.musicplayer.presentation.library.AlbumsScreen
import com.example.musicplayer.presentation.library.LibraryViewModel
import com.example.musicplayer.presentation.library.TracksScreen
import com.example.musicplayer.presentation.player.MiniPlayerHost
import com.example.musicplayer.presentation.player.NowPlayingScreen
import com.example.musicplayer.presentation.queue.QueueScreen
import com.example.musicplayer.presentation.settings.SettingsScreen

/**
 * Root navigation host for the application.
 *
 * The shared [Scaffold] owns the two pieces of persistent chrome:
 * - the bottom navigation bar with the Tracks, Albums and Settings tabs,
 * - the Mini Player, which sits directly above the tabs.
 *
 * Both are decided per destination (see [shouldShowBottomBar] and
 * [shouldShowMiniPlayer]) and the Scaffold reserves their height as content
 * padding, so screen content is never covered.
 *
 * The library [LibraryViewModel] is created once at this level and shared by the
 * tabs, so switching tabs never re-triggers a folder check or a scan.
 */
@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
    libraryViewModel: LibraryViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        // imePadding keeps the Mini Player and the tabs reachable while the
        // search field has the keyboard open.
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        bottomBar = {
            Column(
                modifier = Modifier.windowInsetsPadding(
                    WindowInsets.navigationBars.only(WindowInsetsSides.Bottom),
                ),
            ) {
                if (shouldShowMiniPlayer(currentRoute)) {
                    MiniPlayerHost(
                        onOpenNowPlaying = {
                            navController.navigate(Routes.NOW_PLAYING) {
                                // Repeated taps on the panel must not stack screens.
                                launchSingleTop = true
                            }
                        },
                    )
                }

                if (shouldShowBottomBar(currentRoute)) {
                    AppBottomBar(
                        currentRoute = currentRoute,
                        onNavigate = navController::navigateToTab,
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.TRACKS,
            modifier = Modifier.padding(innerPadding),
            // Pushed screens slide in from the right while the screen below
            // shifts slightly left, so opening a screen reads as forward motion.
            // Top-level tab switches are lateral moves instead: they cross-fade
            // with a subtle scale so Tracks, Albums and Settings feel like one
            // surface changing content rather than a stack of pushed screens.
            enterTransition = {
                if (isTabSwitch(
                        initialState.destination.route,
                        targetState.destination.route,
                    )
                ) {
                    fadeIn(animationSpec = tween(TAB_ENTER_ANIMATION_MS)) +
                        scaleIn(
                            animationSpec = tween(TAB_ENTER_ANIMATION_MS),
                            initialScale = TAB_INITIAL_SCALE,
                        )
                } else {
                    slideInHorizontally(animationSpec = tween(NAVIGATION_ANIMATION_MS)) { it } +
                        fadeIn(animationSpec = tween(NAVIGATION_FADE_MS))
                }
            },
            exitTransition = {
                if (isTabSwitch(
                        initialState.destination.route,
                        targetState.destination.route,
                    )
                ) {
                    fadeOut(animationSpec = tween(TAB_EXIT_ANIMATION_MS)) +
                        scaleOut(
                            animationSpec = tween(TAB_EXIT_ANIMATION_MS),
                            targetScale = TAB_TARGET_SCALE,
                        )
                } else {
                    slideOutHorizontally(animationSpec = tween(NAVIGATION_ANIMATION_MS)) { it } +
                        fadeOut(animationSpec = tween(NAVIGATION_FADE_MS))
                }
            },
            // Back returns to the previous screen immediately, with no
            // transition, so the system Back button and gesture feel instant.
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
        ) {
            composable(Routes.TRACKS) {
                TracksScreen(
                    viewModel = libraryViewModel,
                    onOpenQueue = { navController.navigate(Routes.QUEUE) },
                )
            }

            composable(Routes.ALBUMS) {
                AlbumsScreen(
                    viewModel = libraryViewModel,
                    onAlbumSelected = { albumId -> navController.navigate(Routes.albumDetail(albumId)) },
                    onOpenQueue = { navController.navigate(Routes.QUEUE) },
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(viewModel = libraryViewModel)
            }

            composable(
                route = Routes.ALBUM_DETAIL,
                arguments = listOf(
                    navArgument("albumId") { type = NavType.StringType },
                ),
            ) {
                AlbumScreen(
                    onBackClicked = { navController.popBackStack() },
                )
            }

            composable(Routes.NOW_PLAYING) {
                NowPlayingScreen(
                    onBackClicked = { navController.popBackStack() },
                    onOpenQueue = { navController.navigate(Routes.QUEUE) },
                    onOpenAlbum = { albumKey ->
                        navController.navigate(Routes.albumDetail(albumKey))
                    },
                )
            }

            composable(Routes.QUEUE) {
                QueueScreen(
                    onBackClicked = { navController.popBackStack() },
                )
            }
        }
    }
}

/**
 * Switches between the bottom-navigation tabs.
 *
 * Tab state is saved and restored, so returning to a tab keeps its scroll
 * position, and the back stack never grows while hopping between tabs.
 */
private fun NavHostController.navigateToTab(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * True when both destinations are bottom-navigation tabs, i.e. the navigation
 * is a lateral tab switch rather than opening a detail screen.
 */
private fun isTabSwitch(initialRoute: String?, targetRoute: String?): Boolean {
    val tabs = TopLevelDestination.entries
    return tabs.any { it.route == initialRoute } && tabs.any { it.route == targetRoute }
}

/** Duration of the horizontal screen slide used for push navigation. */
private const val NAVIGATION_ANIMATION_MS = 120

/** Duration of the cross-fade layered on top of the push slide. */
private const val NAVIGATION_FADE_MS = 80

/** Incoming tab fade/scale duration. */
private const val TAB_ENTER_ANIMATION_MS = 140

/** Outgoing tab fades out faster so the new tab becomes readable sooner. */
private const val TAB_EXIT_ANIMATION_MS = 70

/** Incoming tab starts slightly smaller and settles to full size. */
private const val TAB_INITIAL_SCALE = 0.95f

/** Outgoing tab drifts slightly larger as it fades away. */
private const val TAB_TARGET_SCALE = 1.02f