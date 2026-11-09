package com.example.musicplayer.player

import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.example.musicplayer.domain.model.Track

/**
 * Key under which the track's album grouping key travels with the media item.
 *
 * The player surfaces need it to open the album of the currently playing track.
 * It is carried in [MediaMetadata] extras because Media3 has no dedicated album
 * ID field and the media ID is already the track URI.
 */
internal const val EXTRA_ALBUM_ID = "com.example.musicplayer.extra.ALBUM_ID"

/**
 * Key under which the track's full-resolution artwork URI travels with the
 * media item.
 *
 * Media3 only carries one artwork reference, and the system notification needs
 * the small thumbnail. Now Playing reads this extra to draw the sharp cover
 * instead of the thumbnail.
 */
internal const val EXTRA_FULL_ARTWORK_URI = "com.example.musicplayer.extra.FULL_ARTWORK_URI"

/**
 * Key under which a CUE split track's start offset (ms within the shared audio
 * file) travels with the media item.
 *
 * Playback seeks here when this item begins so the whole source file does not
 * play from the start for every split track.
 */
internal const val EXTRA_CUE_START_OFFSET_MS = "com.example.musicplayer.extra.CUE_START_OFFSET_MS"

/**
 * Converts a library track into a Media3 [MediaItem].
 *
 * The track's document URI is both the media URI and the stable media ID, so
 * the player playlist and the library share one identity and queue operations
 * can be reconciled later. The indexed duration is carried in the media
 * metadata so every queue item can be rendered without querying the library.
 * The optional album ID is copied into metadata extras so Now Playing can
 * navigate back to the album the current track belongs to, and the
 * full-resolution cover URI is carried there too so Now Playing can draw a sharp
 * image while Media3 keeps the thumbnail for the notification.
 *
 * CUE split tracks carry a `#cue/<n>` fragment on their URI so each split track
 * persists as a unique Room row. The fragment is dropped before playback so
 * ExoPlayer opens the underlying audio file.
 */
internal fun Track.toMediaItem(): MediaItem {
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setAlbumTitle(album)
        .setArtworkUri(artworkUri?.toUri())
        .setDurationMs(durationMs.takeIf { it > 0L })

    val extras = Bundle().apply {
        if (!albumId.isNullOrBlank()) putString(EXTRA_ALBUM_ID, albumId)
        if (!fullArtworkUri.isNullOrBlank()) {
            putString(EXTRA_FULL_ARTWORK_URI, fullArtworkUri)
        }
        putLong(EXTRA_CUE_START_OFFSET_MS, cueStartOffsetMs)
    }
    if (!extras.isEmpty()) metadata.setExtras(extras)

    // CUE split tracks carry a `#cue/<n>` fragment for uniqueness; drop it so
    // ExoPlayer opens the underlying audio file.
    val mediaUri = uri.takeIf { it.contains('#') }
        ?.substringBefore('#')
        ?: uri

    return MediaItem.Builder()
        .setMediaId(uri)
        .setUri(mediaUri.toUri())
        .setMediaMetadata(metadata.build())
        .build()
}
