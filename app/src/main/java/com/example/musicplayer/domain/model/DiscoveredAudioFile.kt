package com.example.musicplayer.domain.model

import android.net.Uri

/**
 * Represents a candidate audio file discovered during Storage Access Framework folder traversal.
 *
 * @property uri Document URI of the audio file.
 * @property name File display name.
 * @property mimeType File MIME type reported by document provider, if available.
 * @property size File size in bytes.
 * @property lastModified Last modified timestamp in milliseconds since epoch.
 * @property parentFolderUri Document URI of the parent folder.
 * @property artworkFileUri Optional image file in the same folder (cover.jpg,
 *   folder.jpg, …) used when the audio file has no embedded artwork.
 * @property artworkFileLastModified Last modified timestamp of the folder cover
 *   document, or `0` when the folder has no cover. Changing the cover image while
 *   the audio file itself is untouched forces a re-extraction on the next scan
 *   so replaced covers appear in the library.
 * @property cueTrackNumber Track position taken from a cue sheet in the same
 *   folder, or `null` when no matching cue entry exists. Used only as an
 *   ordering hint when the audio tags do not carry a track number.
 * @property cueDiscNumber Disc number declared by the cue sheet, or `null`.
 */
data class DiscoveredAudioFile(
    val uri: Uri,
    val name: String,
    val mimeType: String?,
    val size: Long,
    val lastModified: Long,
    val parentFolderUri: Uri,
    val artworkFileUri: Uri? = null,
    val artworkFileLastModified: Long = 0,
    val cueTrackNumber: Int? = null,
    val cueDiscNumber: Int? = null,
)
