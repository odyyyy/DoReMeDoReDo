package com.example.musicplayer.data.database

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Change detection used to decide whether a rescanned file needs its metadata
 * re-read.
 */
class TrackEntityTest {

    @Test
    fun isSameFile_matchesWhenSizeAndModifiedAreIdentical() {
        val track = track(fileSize = 4_096L, lastModified = 1_700_000_000_000L)

        assertTrue(track.isSameFile(size = 4_096L, modifiedAt = 1_700_000_000_000L))
    }

    @Test
    fun isSameFile_detectsChangedSize() {
        val track = track(fileSize = 4_096L, lastModified = 1_700_000_000_000L)

        assertFalse(track.isSameFile(size = 5_120L, modifiedAt = 1_700_000_000_000L))
    }

    @Test
    fun isSameFile_detectsChangedModifiedTime() {
        val track = track(fileSize = 4_096L, lastModified = 1_700_000_000_000L)

        assertFalse(track.isSameFile(size = 4_096L, modifiedAt = 1_700_000_000_001L))
    }

    @Test
    fun isSameFile_neverMatchesUnreportedStamps() {
        // A provider that reports neither size nor modified time must not make
        // every rescan look like an unchanged file.
        val track = track(fileSize = 0L, lastModified = 0L)

        assertFalse(track.isSameFile(size = 0L, modifiedAt = 0L))
    }

    @Test
    fun isSameCover_matchesWhenStampIsUnchanged() {
        val track = track(artworkFileLastModified = 1_700_000_000_000L)

        assertTrue(track.isSameCover(coverLastModified = 1_700_000_000_000L))
    }

    @Test
    fun isSameCover_detectsReplacedCover() {
        val track = track(artworkFileLastModified = 1_700_000_000_000L)

        assertFalse(track.isSameCover(coverLastModified = 1_700_000_000_001L))
    }

    @Test
    fun isSameCover_detectsRemovedCover() {
        // The row was indexed with a cover that no longer exists.
        val track = track(artworkFileLastModified = 1_700_000_000_000L)

        assertFalse(track.isSameCover(coverLastModified = 0L))
    }

    @Test
    fun isSameCover_reExtractsWhenRowHasNoStampButFolderHasCover() {
        // A row written before the cover stamp was persisted must be re-read
        // once so its cover is refreshed.
        val track = track(artworkFileLastModified = 0L)

        assertFalse(track.isSameCover(coverLastModified = 1_700_000_000_000L))
    }

    @Test
    fun isSameCover_matchesWhenNeitherRowNorFolderHasCover() {
        val track = track(artworkFileLastModified = 0L)

        assertTrue(track.isSameCover(coverLastModified = 0L))
    }

    private fun track(
        fileSize: Long = 4_096L,
        lastModified: Long = 1_700_000_000_000L,
        artworkFileLastModified: Long = 0L,
    ) = TrackEntity(
        uri = "content://music/track.mp3",
        title = "Title",
        artist = "Artist",
        album = "Album",
        durationMs = 210_000L,
        albumId = null,
        artworkUri = null,
        folderUri = "content://music/folder",
        fileSize = fileSize,
        lastModified = lastModified,
        dateAdded = 1_700_000_100_000L,
        artworkFileLastModified = artworkFileLastModified,
    )
}