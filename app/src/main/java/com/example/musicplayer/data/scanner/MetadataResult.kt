package com.example.musicplayer.data.scanner

import com.example.musicplayer.domain.model.DiscoveredAudioFile
import com.example.musicplayer.domain.model.TrackMetadata

/**
 * Typed result of reading metadata for a single discovered audio file.
 *
 * A [Failure] only affects the file it belongs to; batch extraction continues
 * with the remaining files.
 */
sealed interface MetadataResult {
    /** The file this result belongs to. */
    val file: DiscoveredAudioFile

    /**
     * Metadata was read successfully.
     *
     * @property metadata Resolved track metadata with fallbacks applied.
     */
    data class Success(
        override val file: DiscoveredAudioFile,
        val metadata: TrackMetadata,
    ) : MetadataResult

    /**
     * Metadata could not be read, for example because the file is corrupt,
     * unsupported, or no longer accessible.
     *
     * @property error Underlying exception, when available.
     * @property message Human-readable error description.
     */
    data class Failure(
        override val file: DiscoveredAudioFile,
        val error: Throwable?,
        val message: String,
    ) : MetadataResult
}
