package com.example.musicplayer

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import coil3.request.crossfade
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point that bootstraps Hilt dependency injection and
 * configures artwork loading.
 */
@HiltAndroidApp
class DoReMeDoReDoApp : Application(), SingletonImageLoader.Factory {

    /**
     * Artwork decoding is configured once for the whole app:
     *  - The in-memory cache keeps recently decoded covers in RAM, so scrolling a
     *    large library and reopening a screen do not re-decode the same image. It
     *    is bounded to 15% of the app's available heap instead of the library
     *    default, which is plenty for a screenful of thumbnails.
     *  - Coil's disk cache is disabled (and removed): every artwork image is
     *    already stored as a bounded, content-addressed JPEG by
     *    `FileArtworkStore`, so a second on-disk copy would only double storage
     *    and add a second cache that the library does not control.
     *  - Image requests keep Coil's default size resolution, so a 44 dp list row
     *    decodes a thumbnail-sized bitmap out of the 512 px thumbnail, while Now
     *    Playing decodes the larger full-resolution variant for its cover.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, MEMORY_CACHE_PERCENT)
                    .build()
            }
            .diskCache(null)
            .diskCachePolicy(CachePolicy.DISABLED)
            .crossfade(false)
            .build()

    private companion object {
        /** Share of the app's available heap the artwork memory cache may use. */
        const val MEMORY_CACHE_PERCENT = 0.15
    }
}
