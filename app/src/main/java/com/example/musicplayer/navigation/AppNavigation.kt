package com.example.musicplayer.navigation

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