package com.example.musicplayer.navigation

import android.net.Uri

/**
 * Central definition of navigation destinations.
 */
object Routes {
    const val LIBRARY = "library"
    const val ALBUM_DETAIL = "album_detail/{albumId}"
    const val NOW_PLAYING = "now_playing"
    const val QUEUE = "queue"

    fun albumDetail(albumId: String): String = "album_detail/${Uri.encode(albumId)}"
}