package com.example.musicplayer.data.artwork

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the content-addressed file naming used by [FileArtworkStore].
 *
 * Kept free of Android framework types so the identity of a stored image can be
 * verified on the JVM (this is the cache key the database indexes).
 */
class FileArtworkStoreTest {

    @Test
    fun `fileNameFor is deterministic for the same bytes`() {
        val artwork = byteArrayOf(1, 2, 3, 4)

        assertEquals(
            FileArtworkStore.fileNameFor(artwork),
            FileArtworkStore.fileNameFor(artwork.copyOf()),
        )
    }

    @Test
    fun `fileNameFor changes when the artwork changes`() {
        assertNotEquals(
            FileArtworkStore.fileNameFor(byteArrayOf(1, 2, 3)),
            FileArtworkStore.fileNameFor(byteArrayOf(1, 2, 4)),
        )
    }

    @Test
    fun `fileNameFor is a bounded jpeg name`() {
        val name = FileArtworkStore.fileNameFor(byteArrayOf(42))

        assertTrue(name.endsWith(".jpg"))
        // 16 hash bytes rendered as hex, plus the extension.
        assertEquals(36, name.length)
    }
}
