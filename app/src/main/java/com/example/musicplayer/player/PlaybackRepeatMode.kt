package com.example.musicplayer.player

import androidx.media3.common.Player

/**
 * Repeat modes offered by the player UI.
 *
 * The values map one-to-one onto Media3's repeat modes and are always read back
 * from the player, so the UI reflects the real player state rather than a
 * separate copy of it. Shuffle has no equivalent enum: Media3 exposes it as a
 * single boolean (`Player.shuffleModeEnabled`).
 */
enum class PlaybackRepeatMode {
    /** Stop after the last queue item. */
    OFF,

    /** Continue from the first item after the last one. */
    ALL,

    /** Repeat the current item indefinitely. */
    ONE;

    /** The Media3 repeat mode that implements this value. */
    fun toPlayerRepeatMode(): Int = when (this) {
        OFF -> Player.REPEAT_MODE_OFF
        ALL -> Player.REPEAT_MODE_ALL
        ONE -> Player.REPEAT_MODE_ONE
    }

    /** The next value in the Off -> All -> One -> Off cycle used by the toggle. */
    fun next(): PlaybackRepeatMode = when (this) {
        OFF -> ALL
        ALL -> ONE
        ONE -> OFF
    }

    companion object {
        /** Projects a Media3 repeat mode onto its [PlaybackRepeatMode] value. */
        fun fromPlayerRepeatMode(repeatMode: Int): PlaybackRepeatMode = when (repeatMode) {
            Player.REPEAT_MODE_ALL -> ALL
            Player.REPEAT_MODE_ONE -> ONE
            else -> OFF
        }
    }
}