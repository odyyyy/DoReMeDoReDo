package com.example.musicplayer.player

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.core.net.toUri
import com.example.musicplayer.domain.model.Track

/**
 * Converts a library track into a Media3 [MediaItem].
 *
 * The track's document URI is both the media URI and the stable media ID, so
 * the player playlist and the library share one identity and queue operations
 * can be reconciled later. The indexed duration is carried in the media
 * metadata so every queue item can be rendered without querying the library.
 */
internal fun Track.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setMediaId(uri)
        .setUri(uri.toUri())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(artworkUri?.toUri())
                .setDurationMs(durationMs.takeIf { it > 0L })
                .build(),
        )
        .build()
