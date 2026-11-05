package com.example.musicplayer.navigation

/**
 * Decides which parts of the bottom chrome belong on the current destination.
 *
 * - The **bottom navigation** shows on the three top-level tabs only.
 * - The **Mini Player** shows on the browsing destinations (the three tabs and
 *   album details) so playback is always one tap away. It is deliberately hidden
 *   on Now Playing and Queue, which are playback surfaces themselves and would
 *   otherwise stack a second set of transport controls.
 */
internal fun shouldShowBottomBar(route: String?): Boolean = route in TopLevelRoutes

/** True when the Mini Player belongs on [route]. */
internal fun shouldShowMiniPlayer(route: String?): Boolean = when (route) {
    Routes.ALBUM_DETAIL -> true
    else -> shouldShowBottomBar(route)
}

private val TopLevelRoutes: Set<String> = TopLevelDestination.entries.mapTo(mutableSetOf()) { it.route }