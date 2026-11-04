package com.example.musicplayer.domain.repository

import com.example.musicplayer.domain.model.CompletedScan
import kotlinx.coroutines.flow.Flow

/**
 * Persistent marker for the last successful scan-to-Room reconciliation of the
 * selected music folder.
 *
 * Read at startup to decide whether the folder needs its initial indexing;
 * written only after a scan has completed and the database was reconciled.
 */
interface ScanStatusRepository {

    /**
     * The last completed scan, or `null` when no folder has been fully
     * indexed yet (or the stored marker is unreadable).
     */
    val lastCompletedScan: Flow<CompletedScan?>

    /**
     * Records that the scan of [folderUri] completed and its results were
     * written to the database at [completedAt].
     *
     * Callers must invoke this only after reconciliation succeeded.
     */
    suspend fun markCompleted(folderUri: String, completedAt: Long)
}