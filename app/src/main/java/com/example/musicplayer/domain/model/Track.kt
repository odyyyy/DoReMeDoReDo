package com.example.musicplayer.domain.model

/**
 * A track of the user-facing library, expressed as a plain domain model.
 *
 * The presentation layer works exclusively with this type; Room rows
 * ([com.example.musicplayer.data.database.TrackEntity]) are mapped at the data
 * boundary so storage details never leak into the UI.
 *
 * @property uri Stable document/content URI identifying the track.
 * @property title Display title.
 * @property artist Track artist.
 * @property album Album name.
 * @property durationMs Duration in milliseconds, or `0` when unavailable.
 * @property trackNumber Track position within its disc, or `null` when the
 *   library has no reliable number (tag, cue sheet, or file name).
 * @property discNumber Disc number for multi-disc releases, or `null`.
 * @property albumId Optional album-grouping key derived from tags.
 * @property artworkUri Optional bounded artwork thumbnail used by lists and
 *   notifications, or `null` when the track has no artwork.
 * @property fullArtworkUri Optional full-resolution artwork used by Now Playing,
 *   or `null` when unavailable.
 * @property dateAdded Timestamp (ms since epoch) when the track was first
 *   indexed; used for sorting.
 */
data class Track(
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val albumId: String?,
    val artworkUri: String?,
    val dateAdded: Long,
    val fullArtworkUri: String? = null,
)