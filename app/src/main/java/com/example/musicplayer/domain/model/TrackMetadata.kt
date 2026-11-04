package com.example.musicplayer.domain.model

/**
 * Metadata read from an audio file during library scanning.
 *
 * All human-readable fields are already resolved: when a tag is missing or blank,
 * a sensible fallback is supplied so that callers never have to deal with null
 * title, artist, or album values.
 *
 * @property title Display title, falling back to the file name without extension.
 * @property artist Track artist, falling back to [UNKNOWN_ARTIST].
 * @property album Album name, falling back to [UNKNOWN_ALBUM].
 * @property durationMs Track duration in milliseconds, or `0` when unavailable.
 * @property albumId Stable album-grouping key derived from normalized album and
 *   artist tags, or `null` when the file has no album tag.
 * @property artworkBytes Bounded embedded artwork (downscaled JPEG), or `null`
 *   when the file has no artwork or the artwork cannot be decoded.
 */
data class TrackMetadata(
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val albumId: String?,
    val artworkBytes: ByteArray? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TrackMetadata) return false
        return title == other.title &&
            artist == other.artist &&
            album == other.album &&
            durationMs == other.durationMs &&
            albumId == other.albumId &&
            artworkBytes.contentEquals(other.artworkBytes)
    }

    override fun hashCode(): Int {
        var result = title.hashCode()
        result = 31 * result + artist.hashCode()
        result = 31 * result + album.hashCode()
        result = 31 * result + durationMs.hashCode()
        result = 31 * result + albumId.hashCode()
        result = 31 * result + (artworkBytes?.contentHashCode() ?: 0)
        return result
    }

    companion object {
        const val UNKNOWN_TITLE = "Unknown Track"
        const val UNKNOWN_ARTIST = "Unknown Artist"
        const val UNKNOWN_ALBUM = "Unknown Album"
    }
}
