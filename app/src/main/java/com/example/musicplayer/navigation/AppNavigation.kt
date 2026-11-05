package com.example.musicplayer.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.musicplayer.presentation.album.AlbumScreen
import com.example.musicplayer.presentation.library.LibraryScreen
import com.example.musicplayer.presentation.player.MiniPlayerHost
import com.example.musicplayer.presentation.player.NowPlayingScreen
import com.example.musicplayer.presentation.queue.QueueScreen

/**
 * Root navigation host for the application.
 *
 * The shared [Scaffold] is the app-level surface for cross-screen chrome: the
 * Mini Player lives in its bottom bar, which also reserves the space the panel
 * needs so screen content is never covered by it. Destinations decide whether
 * the panel is shown (see [shouldShowMiniPlayer]).
 */
@Composable
fun AppNavigation(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        bottomBar = {
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
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.LIBRARY,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.LIBRARY) {
                LibraryScreen(
                    onAlbumSelected = { albumId ->
                        navController.navigate(Routes.albumDetail(albumId))
                    },
                    onOpenQueue = { navController.navigate(Routes.QUEUE) },
                )
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