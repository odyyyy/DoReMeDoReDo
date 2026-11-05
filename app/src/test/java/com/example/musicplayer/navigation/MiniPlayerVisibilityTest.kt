package com.example.musicplayer.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the destinations that keep the Mini Player visible. */
class MiniPlayerVisibilityTest {

    @Test
    fun `mini player is shown on browsing destinations`() {
        assertTrue(shouldShowMiniPlayer(Routes.LIBRARY))
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
}