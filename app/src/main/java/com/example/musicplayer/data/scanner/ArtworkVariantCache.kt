package com.example.musicplayer.data.scanner

/**
 * Bounded LRU cache of decoded artwork variants used within one metadata batch.
 *
 * Album tracks commonly embed the same cover picture, and every track in a
 * folder commonly resolves to the same `cover.jpg`. Decoding that source and
 * re-encoding both JPEG variants once per track is the most expensive part of a
 * (re)scan, so the extractor reuses the result while a batch is running.
 *
 * The cache belongs to one batch rather than to the [MetadataExtractor]
 * singleton, so images are released when extraction finishes. The byte cap
 * keeps peak memory bounded even when a library has many large covers; the
 * entry cap and access-order eviction keep small caches cheap to scan.
 */
internal class ArtworkVariantCache(
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
) {

    private val entries = object : LinkedHashMap<Key, ArtworkVariants>(
        /* initialCapacity = */ 16,
        /* loadFactor = */ 0.75f,
        /* accessOrder = */ true,
    ) {}

    private var totalBytes = 0L

    /** Lookups answered from the cache; exposed for unit tests. */
    var hits: Int = 0
        private set

    /** Lookups that required extraction; exposed for unit tests. */
    var misses: Int = 0
        private set

    /** Returns the cached variants for [key], or `null` on a miss. */
    @Synchronized
    fun get(key: Key): ArtworkVariants? {
        val cached = entries[key]
        if (cached == null) {
            misses++
        } else {
            hits++
        }
        return cached
    }

    /**
     * Stores [variants] for [key], evicting the least recently used entries
     * until both the entry and byte budgets are satisfied.
     */
    @Synchronized
    fun put(key: Key, variants: ArtworkVariants) {
        entries[key]?.let { totalBytes -= it.byteSize() }
        entries[key] = variants
        totalBytes += variants.byteSize()

        val iterator = entries.entries.iterator()
        while ((entries.size > maxEntries || totalBytes > maxBytes) && iterator.hasNext()) {
            val eldest = iterator.next()
            totalBytes -= eldest.value.byteSize()
            iterator.remove()
        }
    }

    private fun ArtworkVariants.byteSize(): Long {
        val thumbnailBytes = thumbnail?.size ?: 0
        // The two variants may share one instance when the source image is
        // already thumbnail-sized; count those bytes only once.
        val fullBytes = if (full === thumbnail) 0 else full?.size ?: 0
        return (thumbnailBytes + fullBytes).toLong()
    }

    /**
     * Identity of one artwork source within a batch.
     *
     * @property embedded True for a picture carried by the audio file itself,
     *   false for a folder cover document.
     * @property value Content hash for embedded artwork, document URI for a
     *   folder cover.
     */
    data class Key(
        val embedded: Boolean,
        val value: String,
    )

    private companion object {
        const val DEFAULT_MAX_ENTRIES = 32

        /**
         * Room for two maximum-sized full variants (about 4.5 MB together each)
         * plus small thumbnails. The values are already compressed JPEG bytes,
         * not decoded bitmaps, so this cap stays modest next to the one or two
         * bitmaps a worker decodes.
         */
        const val DEFAULT_MAX_BYTES = 12L * 1024L * 1024L
    }
}
