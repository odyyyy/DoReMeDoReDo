package com.example.musicplayer.player

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for the repeat-mode mapping used by the player controls. */
class PlaybackRepeatModeTest {

    @Test
    fun `media3 repeat modes map onto the enum`() {
        assertEquals(
            PlaybackRepeatMode.OFF,
            PlaybackRepeatMode.fromPlayerRepeatMode(Player.REPEAT_MODE_OFF),
        )
        assertEquals(
            PlaybackRepeatMode.ALL,
            PlaybackRepeatMode.fromPlayerRepeatMode(Player.REPEAT_MODE_ALL),
        )
        assertEquals(
            PlaybackRepeatMode.ONE,
            PlaybackRepeatMode.fromPlayerRepeatMode(Player.REPEAT_MODE_ONE),
        )
    }

    @Test
    fun `unknown media3 repeat mode falls back to off`() {
        assertEquals(
            PlaybackRepeatMode.OFF,
            PlaybackRepeatMode.fromPlayerRepeatMode(Int.MIN_VALUE),
        )
    }

    @Test
    fun `enum maps onto media3 repeat modes`() {
        assertEquals(Player.REPEAT_MODE_OFF, PlaybackRepeatMode.OFF.toPlayerRepeatMode())
        assertEquals(Player.REPEAT_MODE_ALL, PlaybackRepeatMode.ALL.toPlayerRepeatMode())
        assertEquals(Player.REPEAT_MODE_ONE, PlaybackRepeatMode.ONE.toPlayerRepeatMode())
    }

    @Test
    fun `toggle cycle is off all one off`() {
        assertEquals(PlaybackRepeatMode.ALL, PlaybackRepeatMode.OFF.next())
        assertEquals(PlaybackRepeatMode.ONE, PlaybackRepeatMode.ALL.next())
        assertEquals(PlaybackRepeatMode.OFF, PlaybackRepeatMode.ONE.next())
    }

    @Test
    fun `mapping round-trips`() {
        PlaybackRepeatMode.entries.forEach { mode ->
            assertEquals(
                mode,
                PlaybackRepeatMode.fromPlayerRepeatMode(mode.toPlayerRepeatMode()),
            )
        }
    }
}