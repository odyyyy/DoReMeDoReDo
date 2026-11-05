package com.example.musicplayer.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the destinations that keep the bottom chrome visible. */
class MiniPlayerVisibilityTest {

    @Test
    fun `bottom bar shows on the three tabs`() {
        TopLevelDestination.entries.forEach { destination ->
            assertTrue(destination.route, shouldShowBottomBar(destination.route))
        }
    }

    @Test
    fun `bottom bar is hidden on pushed screens`() {
        assertFalse(shouldShowBottomBar(Routes.ALBUM_DETAIL))
        assertFalse(shouldShowBottomBar(Routes.NOW_PLAYING))
        assertFalse(shouldShowBottomBar(Routes.QUEUE))
        assertFalse(shouldShowBottomBar(null))
    }

    @Test
    fun `mini player is shown on browsing destinations`() {
        assertTrue(shouldShowMiniPlayer(Routes.TRACKS))
        assertTrue(shouldShowMiniPlayer(Routes.ALBUMS))
        assertTrue(shouldShowMiniPlayer(Routes.SETTINGS))
        assertTrue(shouldShowMiniPlayer(Routes.ALBUM_DETAIL))
    }

    @Test
    fun `mini player is hidden on playback destinations`() {
        assertFalse(shouldShowMiniPlayer(Routes.NOW_PLAYING))
        assertFalse(shouldShowMiniPlayer(Routes.QUEUE))
    }

    @Test
    fun `mini player is hidden before the back stack resolves`() {
        assertFalse(shouldShowMiniPlayer(null))
        assertFalse(shouldShowMiniPlayer("unknown"))
    }

    @Test
    fun `tab destinations are discoverable by route`() {
        assertTrue(Routes.TRACKS in TopLevelDestination.entries.map { it.route })
        assertTrue(Routes.ALBUMS in TopLevelDestination.entries.map { it.route })
        assertTrue(Routes.SETTINGS in TopLevelDestination.entries.map { it.route })
    }
}