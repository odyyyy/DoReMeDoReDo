package com.example.musicplayer.data.artwork

/**
 * Persists the bounded artwork images (thumbnail and full-resolution variants)
 * extracted during a scan so the UI never has to re-read embedded pictures from
 * the audio files.
 *
 * Implementations must never throw: artwork is optional decoration, so a failure
 * to store or delete an image is reported as `null` (or ignored) instead of
 * failing the scan that produced it.
 */
interface ArtworkStore {

    /**
     * Stores [artworkBytes] and returns the URI that should be indexed as the
     * track's `artworkUri`.
     *
     * Storage is content addressed: identical bytes always resolve to the same
     * file, so the shared cover of every track on an album is written once, and a
     * changed source file (with changed artwork) produces a new file rather than
     * overwriting the old one.
     *
     * @return A loadable `file://` URI for the stored image, or `null` when the
     *   bytes are empty or the image could not be written.
     */
    suspend fun store(artworkBytes: ByteArray): String?

    /**
     * Deletes every stored image that is not referenced by
     * [referencedArtworkUris].
     *
     * Called after a completed scan has reconciled the library, so the
     * unreferenced files are exactly the images of removed tracks and of tracks
     * whose artwork was replaced.
     */
    suspend fun deleteUnreferenced(referencedArtworkUris: Collection<String>)
}
