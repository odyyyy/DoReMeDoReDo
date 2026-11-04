package com.example.musicplayer.domain.model

/**
 * Record that a folder was fully scanned and its result was reconciled into
 * Room.
 *
 * Persisted outside the process so "this folder is already indexed" survives
 * app restarts; the in-memory scan state alone must never be used as proof of
 * indexing across process death.
 *
 * @property folderUri Tree URI of the folder whose scan completed.
 * @property completedAt Completion time in milliseconds since epoch.
 */
data class CompletedScan(
    val folderUri: String,
    val completedAt: Long,
)