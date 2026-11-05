package com.example.musicplayer.presentation.player

import com.example.musicplayer.player.PlaybackState
import com.example.musicplayer.player.PlaybackTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the seek-bar state projection and its math. */
class PlaybackProgressTest {

    private val track = PlaybackTrack(
        uri = "content://music/track-1",
        mediaId = "content://music/track-1",
        title = "Song",
        artist = "Artist",
        album = "Album",
        artworkUri = null,
        durationMs = 200_000L,
    )

    @Test
    fun `no loaded track maps to null`() {
        assertNull(PlaybackState().toPlaybackProgress())
    }

    @Test
    fun `position duration and play state are projected`() {
        val progress = PlaybackState(
            currentTrack = track,
            positionMs = 50_000L,
            durationMs = 200_000L,
            isPlaying = true,
        ).toPlaybackProgress()

        requireNotNull(progress)
        assertEquals(track.uri, progress.trackUri)
        assertEquals(50_000L, progress.positionMs)
        assertEquals(200_000L, progress.durationMs)
        assertTrue(progress.isPlaying)
        assertTrue(progress.isSeekable)
        assertEquals(0.25f, progress.fraction, 0.0001f)
    }

    @Test
    fun `unknown duration is not seekable`() {
        val progress = PlaybackState(
            currentTrack = track,
            positionMs = 10_000L,
            durationMs = 0L,
        ).toPlaybackProgress()

        requireNotNull(progress)
        assertFalse(progress.isSeekable)
        assertEquals(0f, progress.fraction, 0.0001f)
        assertEquals(0L, progress.positionFor(0.5f))
    }

    @Test
    fun `fraction clamps to the track bounds`() {
        assertEquals(0f, seekPositionFraction(positionMs = -5_000L, durationMs = 1_000L), 0.0001f)
        assertEquals(0.5f, seekPositionFraction(positionMs = 500L, durationMs = 1_000L), 0.0001f)
        assertEquals(1f, seekPositionFraction(positionMs = 2_000L, durationMs = 1_000L), 0.0001f)
        assertEquals(0f, seekPositionFraction(positionMs = 500L, durationMs = 0L), 0.0001f)
    }

    @Test
    fun `fraction maps back to a position`() {
        assertEquals(500L, positionForFraction(0.5f, 1_000L))
        assertEquals(0L, positionForFraction(-1f, 1_000L))
        assertEquals(1_000L, positionForFraction(2f, 1_000L))
        assertEquals(0L, positionForFraction(0.5f, 0L))
    }

    @Test
    fun `fraction and position round-trip`() {
        val fraction = seekPositionFraction(123_456L, 200_000L)
        val position = positionForFraction(fraction, 200_000L)
        assertEquals(123_456L, position)
    }
}