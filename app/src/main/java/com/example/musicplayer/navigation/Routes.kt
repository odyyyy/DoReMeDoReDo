package com.example.musicplayer.navigation

import android.net.Uri

/**
 * Central definition of navigation destinations.
 */
object Routes {
    /** Bottom-navigation tab: the library's track list. */
    const val TRACKS = "tracks"

    /** Bottom-navigation tab: the album grid. */
    const val ALBUMS = "albums"

    /** Bottom-navigation tab: folder selection, rescan, and library status. */
    const val SETTINGS = "settings"

    const val ALBUM_DETAIL = "album_detail/{albumId}"
    const val NOW_PLAYING = "now_playing"
    const val QUEUE = "queue"

    fun albumDetail(albumId: String): String = "album_detail/${Uri.encode(albumId)}"
}