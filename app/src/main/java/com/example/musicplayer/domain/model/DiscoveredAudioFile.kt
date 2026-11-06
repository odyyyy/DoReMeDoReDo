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
 */
data class DiscoveredAudioFile(
    val uri: Uri,
    val name: String,
    val mimeType: String?,
    val size: Long,
    val lastModified: Long,
    val parentFolderUri: Uri,
    val artworkFileUri: Uri? = null,
)
