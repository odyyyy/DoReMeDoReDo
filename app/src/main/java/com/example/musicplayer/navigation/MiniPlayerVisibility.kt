package com.example.musicplayer.navigation

/**
 * Decides whether the persistent Mini Player belongs on the current
 * destination.
 *
 * The panel is part of the browsing experience: it stays visible on the Library
 * and Album Details screens so playback is one tap away. It is deliberately
 * hidden on Now Playing and Queue, which are playback surfaces themselves and
 * would otherwise stack two player controls on top of each other.
 *
 * @param route The `route` of the current back stack destination, or `null`
 *   before the first destination is resolved.
 */
internal fun shouldShowMiniPlayer(route: String?): Boolean = when (route) {
    Routes.LIBRARY,
    Routes.ALBUM_DETAIL,
    -> true

    else -> false
}