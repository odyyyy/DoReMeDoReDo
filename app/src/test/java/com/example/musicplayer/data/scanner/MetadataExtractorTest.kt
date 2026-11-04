package com.example.musicplayer.data.scanner

import com.example.musicplayer.domain.model.TrackMetadata
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MetadataExtractorTest {

    @Test
    fun buildMetadata_mapsAvailableTags() {
        val artwork = byteArrayOf(1, 2, 3)

        val metadata = MetadataExtractor.buildMetadata(
            fileName = "file.mp3",
            rawTitle = "Song Title",
            rawArtist = "Song Artist",
            rawAlbum = "Song Album",
            rawDuration = "123456",
            artworkBytes = artwork,
        )

        assertEquals("Song Title", metadata.title)
        assertEquals("Song Artist", metadata.artist)
        assertEquals("Song Album", metadata.album)
        assertEquals(123456L, metadata.durationMs)
        assertNotNull(metadata.albumId)
        assertArrayEquals(artwork, metadata.artworkBytes)
    }

    @Test
    fun buildMetadata_trimsTagWhitespace() {
        val metadata = MetadataExtractor.buildMetadata(
            fileName = "file.mp3",
            rawTitle = "  Song Title  ",
            rawArtist = "  Song Artist  ",
            rawAlbum = "  Song Album  ",
            rawDuration = " 5000 ",
        )

        assertEquals("Song Title", metadata.title)
        assertEquals("Song Artist", metadata.artist)
        assertEquals("Song Album", metadata.album)
        assertEquals(5000L, metadata.durationMs)
    }

    @Test
    fun buildMetadata_appliesFallbacksForMissingTags() {
        val metadata = MetadataExtractor.buildMetadata(
            fileName = "My Song.mp3",
            rawTitle = null,
            rawArtist = null,
            rawAlbum = null,
            rawDuration = null,
        )

        assertEquals("My Song", metadata.title)
        assertEquals(TrackMetadata.UNKNOWN_ARTIST, metadata.artist)
        assertEquals(TrackMetadata.UNKNOWN_ALBUM, metadata.album)
        assertEquals(0L, metadata.durationMs)
        assertNull(metadata.albumId)
        assertNull(metadata.artworkBytes)
    }

    @Test
    fun buildMetadata_treatsBlankTagsAsMissing() {
        val metadata = MetadataExtractor.buildMetadata(
            fileName = "file.flac",
            rawTitle = "   ",
            rawArtist = "",
            rawAlbum = "\t",
            rawDuration = "",
        )

        assertEquals("file", metadata.title)
        assertEquals(TrackMetadata.UNKNOWN_ARTIST, metadata.artist)
        assertEquals(TrackMetadata.UNKNOWN_ALBUM, metadata.album)
        assertEquals(0L, metadata.durationMs)
        assertNull(metadata.albumId)
    }

    @Test
    fun resolveTitle_prefersTagThenFileName() {
        assertEquals("Tagged", MetadataExtractor.resolveTitle(" Tagged ", "file.mp3"))
        assertEquals("file", MetadataExtractor.resolveTitle(null, "file.mp3"))
        assertEquals("no extension", MetadataExtractor.resolveTitle("  ", "no extension"))
        assertEquals(TrackMetadata.UNKNOWN_TITLE, MetadataExtractor.resolveTitle(null, ".mp3"))
    }

    @Test
    fun parseDurationMs_handlesMissingAndMalformedValues() {
        assertEquals(0L, MetadataExtractor.parseDurationMs(null))
        assertEquals(0L, MetadataExtractor.parseDurationMs("not a number"))
        assertEquals(0L, MetadataExtractor.parseDurationMs("-100"))
        assertEquals(5000L, MetadataExtractor.parseDurationMs(" 5000 "))
    }

    @Test
    fun buildAlbumId_isStableAndNormalized() {
        val first = MetadataExtractor.buildAlbumId("  Kind of Blue ", "Miles   Davis")
        val second = MetadataExtractor.buildAlbumId("kind of blue", "miles davis")

        assertNotNull(first)
        assertEquals(first, second)
    }

    @Test
    fun buildAlbumId_isNullWithoutAlbumTag() {
        assertNull(MetadataExtractor.buildAlbumId(null, "Artist"))
        assertNull(MetadataExtractor.buildAlbumId("   ", "Artist"))
    }

    @Test
    fun buildAlbumId_differsForDifferentArtists() {
        val first = MetadataExtractor.buildAlbumId("Greatest Hits", "Artist One")
        val second = MetadataExtractor.buildAlbumId("Greatest Hits", "Artist Two")

        assertNotNull(first)
        assertNotNull(second)
        assertNotEquals(first, second)
    }

    @Test
    fun calculateInSampleSize_keepsDecodedDimensionsWithinBounds() {
        assertEquals(1, MetadataExtractor.calculateInSampleSize(512, 512, 512))
        assertEquals(2, MetadataExtractor.calculateInSampleSize(513, 513, 512))
        assertEquals(2, MetadataExtractor.calculateInSampleSize(1000, 1000, 512))
        assertEquals(8, MetadataExtractor.calculateInSampleSize(4000, 1000, 512))
        assertEquals(1, MetadataExtractor.calculateInSampleSize(0, 1000, 512))
    }
}
