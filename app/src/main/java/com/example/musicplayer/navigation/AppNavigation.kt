package com.example.musicplayer.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.musicplayer.presentation.album.AlbumScreen
import com.example.musicplayer.presentation.library.LibraryScreen
import com.example.musicplayer.presentation.queue.QueueScreen

/**
 * Root navigation host for the application.
 *
 * The shared [Scaffold] is the app-level surface where cross-screen chrome
 * such as the Mini Player will live.
 */
@Composable
fun AppNavigation(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
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

            composable(Routes.QUEUE) {
                QueueScreen(
                    onBackClicked = { navController.popBackStack() },
                )
            }
        }
    }
}
