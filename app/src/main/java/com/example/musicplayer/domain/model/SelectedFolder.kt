package com.example.musicplayer.domain.model

/**
 * Represents a user-selected music folder and its display details.
 *
 * @property uri String representation of the Storage Access Framework tree URI.
 * @property displayName Human-readable display name of the selected folder.
 * @property isAccessPersisted Whether read access to this URI was persisted.
 */
data class SelectedFolder(
    val uri: String,
    val displayName: String,
    val isAccessPersisted: Boolean = true,
)
