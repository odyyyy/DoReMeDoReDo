package com.example.musicplayer.presentation.player

import com.example.musicplayer.domain.model.TrackMetadata
import com.example.musicplayer.player.PlaybackRepeatMode
import com.example.musicplayer.player.PlaybackState
import com.example.musicplayer.player.PlaybackTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the projection that feeds the Mini Player. */
class PlayerUiStateTest {

    private val track = PlaybackTrack(
        uri = "content://music/track-1",
        mediaId = "content://music/track-1",
        title = "Song",
        artist = "Artist",
        album = "Album",
        artworkUri = "content://music/art-1",
        durationMs = 120_000L,
    )

    @Test
    fun `no loaded track maps to null`() {
        assertNull(PlaybackState().toPlayerUiState())
    }

    @Test
    fun `connection and playback flags are projected`() {
        val state = PlaybackState(
            isConnected = true,
            currentTrack = track,
            isPlaying = true,
            isBuffering = false,
            hasNext = true,
            hasPrevious = false,
            playbackError = null,
        )

        val uiState = state.toPlayerUiState()

        requireNotNull(uiState)
        assertEquals(track.uri, uiState.trackUri)
        assertEquals("Song", uiState.title)
        assertEquals("Artist", uiState.artist)
        assertEquals("Album", uiState.album)
        assertEquals("content://music/art-1", uiState.artworkUri)
        assertTrue(uiState.isPlaying)
        assertFalse(uiState.isBuffering)
        assertTrue(uiState.hasNext)
        assertFalse(uiState.hasPrevious)
        assertNoModes(uiState)
        assertNull(uiState.errorMessage)
    }

    @Test
    fun `shuffle repeat and ended flags are projected`() {
        val uiState = PlaybackState(
            currentTrack = track,
            shuffleModeEnabled = true,
            repeatMode = PlaybackRepeatMode.ONE,
            hasEnded = true,
        ).toPlayerUiState()

        requireNotNull(uiState)
        assertTrue(uiState.shuffleModeEnabled)
        assertEquals(PlaybackRepeatMode.ONE, uiState.repeatMode)
        assertTrue(uiState.hasEnded)
    }

    @Test
    fun `buffering and errors are projected`() {
        val uiState = PlaybackState(
            currentTrack = track,
            isBuffering = true,
            playbackError = "Playback failed.",
        ).toPlayerUiState()

        requireNotNull(uiState)
        assertTrue(uiState.isBuffering)
        assertEquals("Playback failed.", uiState.errorMessage)
    }

    @Test
    fun `blank metadata falls back to display labels`() {
        val uiState = PlaybackState(
            currentTrack = track.copy(title = "   ", artist = "", album = null, artworkUri = ""),
        ).toPlayerUiState()

        requireNotNull(uiState)
        assertEquals(UNKNOWN_TRACK_TITLE, uiState.title)
        assertEquals(TrackMetadata.UNKNOWN_ARTIST, uiState.artistLabel())
        assertEquals(TrackMetadata.UNKNOWN_ALBUM, uiState.albumLabel())
        assertNull(uiState.album)
        assertNull(uiState.artworkUri)
    }

    /**
     * The Mini Player state is the equality unit used by `distinctUntilChanged`,
     * so position ticks of the shared playback stream must not produce a new
     * value (otherwise the panel would recompose twice a second).
     */
    @Test
    fun `position ticks do not change the panel state`() {
        val first = PlaybackState(
            currentTrack = track,
            isPlaying = true,
            durationMs = 120_000L,
            positionMs = 1_000L,
        ).toPlayerUiState()

        val later = PlaybackState(
            currentTrack = track,
            isPlaying = true,
            durationMs = 120_000L,
            positionMs = 42_000L,
        ).toPlayerUiState()

        assertEquals(first, later)
    }

    private fun assertNoModes(uiState: PlayerUiState) {
        assertFalse(uiState.shuffleModeEnabled)
        assertEquals(PlaybackRepeatMode.OFF, uiState.repeatMode)
        assertFalse(uiState.hasEnded)
    }
}