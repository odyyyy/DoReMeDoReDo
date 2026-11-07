package com.example.musicplayer.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure checks of the [PlaybackQueue] projection: an empty queue has no current
 * item, and the current item follows [PlaybackQueue.currentIndex].
 */
class PlaybackQueueTest {

    private fun item(id: String) = PlaybackTrack(
        uri = "content://music/$id",
        mediaId = "content://music/$id",
        title = id,
        artist = "Artist",
        album = "Album",
        artworkUri = null,
        durationMs = 1_000L,
    )

    @Test
    fun emptyQueue_hasNoCurrentItem() {
        val queue = PlaybackQueue()

        assertTrue(queue.isEmpty)
        assertEquals(0, queue.size)
        assertEquals(-1, queue.currentIndex)
        assertNull(queue.currentItem)
    }

    @Test
    fun currentItem_followsCurrentIndex() {
        val queue = PlaybackQueue(
            items = listOf(item("a"), item("b"), item("c")),
            currentIndex = 1,
        )

        assertFalse(queue.isEmpty)
        assertEquals(3, queue.size)
        assertEquals("b", queue.currentItem?.mediaId?.substringAfterLast('/'))
    }

    @Test
    fun outOfBoundsIndex_hasNoCurrentItem() {
        val queue = PlaybackQueue(items = listOf(item("a")), currentIndex = 5)

        assertNull(queue.currentItem)
    }

    @Test
    fun unshuffledProjection_usesIdentityPlaylistMapping() {
        val queue = PlaybackQueue(
            items = listOf(item("a"), item("b")),
            currentIndex = 1,
        )

        assertEquals(0, queue.playlistIndexAt(0))
        assertEquals(1, queue.playlistIndexAt(1))
    }

    @Test
    fun shuffledProjection_mapsDisplayPositionsToPlaylistIndices() {
        val queue = PlaybackQueue(
            items = listOf(item("c"), item("a"), item("b")),
            currentIndex = 0,
            playlistIndices = listOf(2, 0, 1),
        )

        assertEquals("c", queue.currentItem?.mediaId?.substringAfterLast('/'))
        assertEquals(2, queue.playlistIndexAt(0))
        assertEquals(0, queue.playlistIndexAt(1))
        assertEquals(1, queue.playlistIndexAt(2))
    }
}
