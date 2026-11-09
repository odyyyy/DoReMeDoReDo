package com.example.musicplayer.player

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.musicplayer.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrackMediaItemTest {

    @Test
    fun regularTrack_convertsToMediaItem() {
        val track = Track(
            uri = "content://music/song.flac",
            title = "Song Title",
            artist = "Artist Name",
            album = "Album Name",
            durationMs = 200_000L,
            albumId = "album-1",
            artworkUri = null,
            dateAdded = 1000L,
            cueStartOffsetMs = 0L,
        )

        val mediaItem = track.toMediaItem()

        assertEquals("content://music/song.flac", mediaItem.mediaId)
        assertEquals("content://music/song.flac", mediaItem.localConfiguration?.uri?.toString())
        assertEquals("Song Title", mediaItem.mediaMetadata.title?.toString())
        assertEquals(200_000L, mediaItem.mediaMetadata.durationMs)
    }

    @Test
    fun cueSplitTrack_carriesCueOffsetExtraAndBaseMediaUri() {
        val track = Track(
            uri = "content://music/album.flac#cue/2",
            title = "Cue Track 2",
            artist = "Cue Artist",
            album = "Cue Album",
            durationMs = 240_000L,
            albumId = "album-cue",
            artworkUri = null,
            dateAdded = 1000L,
            cueStartOffsetMs = 180_000L,
        )

        val mediaItem = track.toMediaItem()

        assertEquals("content://music/album.flac#cue/2", mediaItem.mediaId)
        assertEquals("content://music/album.flac", mediaItem.localConfiguration?.uri?.toString())
        assertEquals("Cue Track 2", mediaItem.mediaMetadata.title?.toString())
        assertEquals(240_000L, mediaItem.mediaMetadata.durationMs)
        assertNotNull(mediaItem.mediaMetadata.extras)
        assertEquals(180_000L, mediaItem.mediaMetadata.extras?.getLong(EXTRA_CUE_START_OFFSET_MS))
    }
}
