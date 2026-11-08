package com.example.musicplayer.data.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ArtworkVariantCacheTest {

    @Test
    fun get_returnsTheStoredVariantsForTheSameKey() {
        val cache = ArtworkVariantCache()
        val key = ArtworkVariantCache.Key(embedded = true, value = "cover-hash")
        val variants = variants(thumbnailSize = 8, fullSize = 16)

        cache.put(key, variants)

        assertSame(variants, cache.get(key))
        assertEquals(1, cache.hits)
        assertEquals(0, cache.misses)
    }

    @Test
    fun get_countsAMissingKeyAsAMiss() {
        val cache = ArtworkVariantCache()

        assertNull(cache.get(ArtworkVariantCache.Key(embedded = false, value = "uri")))
        assertEquals(1, cache.misses)
        assertEquals(0, cache.hits)
    }

    @Test
    fun put_evictsTheLeastRecentlyUsedEntry() {
        val cache = ArtworkVariantCache(maxEntries = 2, maxBytes = Long.MAX_VALUE)
        val first = key("first")
        val second = key("second")
        val third = key("third")

        cache.put(first, variants(1, 1))
        cache.put(second, variants(1, 1))
        // Touch first so second becomes the least recently used entry.
        assertNotNull(cache.get(first))
        cache.put(third, variants(1, 1))

        assertNotNull(cache.get(first))
        assertNull(cache.get(second))
        assertNotNull(cache.get(third))
    }

    @Test
    fun put_evictsEntriesUntilTheByteBudgetFits() {
        // Each entry weighs 6 bytes: 3-byte thumbnail + 3-byte full variant.
        val cache = ArtworkVariantCache(maxEntries = 10, maxBytes = 10)

        cache.put(key("first"), variants(3, 3))
        cache.put(key("second"), variants(3, 3))

        assertNull(cache.get(key("first")))
        assertNotNull(cache.get(key("second")))
    }

    @Test
    fun put_countsSharedFullAndThumbnailBytesOnce() {
        val shared = ByteArray(8)
        val variants = ArtworkVariants(thumbnail = shared, full = shared)
        val cache = ArtworkVariantCache(maxEntries = 10, maxBytes = 8)

        cache.put(key("shared"), variants)

        assertNotNull(cache.get(key("shared")))
    }

    private fun key(value: String) = ArtworkVariantCache.Key(embedded = true, value = value)

    private fun variants(thumbnailSize: Int, fullSize: Int) = ArtworkVariants(
        thumbnail = ByteArray(thumbnailSize),
        full = ByteArray(fullSize),
    )
}
