package com.example.musicplayer.player

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.core.net.toUri
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
 * Converts a library track into a Media3 [MediaItem].
 *
 * The track's document URI is both the media URI and the stable media ID, so
 * the player playlist and the library share one identity and queue operations
 * can be reconciled later. The indexed duration is carried in the media
 * metadata so every queue item can be rendered without querying the library.
 * The optional album ID is copied into metadata extras so Now Playing can
 * navigate back to the album the current track belongs to.
 */
internal fun Track.toMediaItem(): MediaItem {
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setAlbumTitle(album)
        .setArtworkUri(artworkUri?.toUri())
        .setDurationMs(durationMs.takeIf { it > 0L })

    if (!albumId.isNullOrBlank()) {
        metadata.setExtras(
            Bundle().apply { putString(EXTRA_ALBUM_ID, albumId) },
        )
    }

    return MediaItem.Builder()
        .setMediaId(uri)
        .setUri(uri.toUri())
        .setMediaMetadata(metadata.build())
        .build()
}
