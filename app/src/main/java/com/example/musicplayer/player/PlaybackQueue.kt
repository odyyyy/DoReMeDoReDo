package com.example.musicplayer.player

/**
 * Immutable snapshot of the player's queue.
 *
 * The queue *is* the Media3 playlist owned by [PlaybackService]; this type is
 * only a read-only projection of it, rebuilt from the player whenever the
 * playlist or the current item changes. The player stays the single source of
 * truth: screens and ViewModels observe this snapshot and send commands through
 * [PlayerController], but never hold an editable second copy of the queue.
 *
 * @property items The queued items in playback order.
 * @property currentIndex Index of the item loaded by the player, or `-1` when
 *   nothing is queued.
 */
data class PlaybackQueue(
    val items: List<PlaybackTrack> = emptyList(),
    val currentIndex: Int = -1,
) {
    /** The item currently loaded by the player, or `null` for an empty queue. */
    val currentItem: PlaybackTrack? get() = items.getOrNull(currentIndex)

    /** Number of items in the queue. */
    val size: Int get() = items.size

    /** True when nothing is queued. */
    val isEmpty: Boolean get() = items.isEmpty()
}
