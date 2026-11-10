package com.example.musicplayer.domain.usecase

import android.net.Uri
import com.example.musicplayer.data.scanner.ScanCoordinator
import com.example.musicplayer.data.scanner.ScanOutcome
import com.example.musicplayer.data.scanner.ScanState
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Scans the selected music folder and reconciles the Room library with the
 * result, delegating to the shared [ScanCoordinator].
 *
 * The coordinator guarantees a single scan at a time and never lets a failed
 * scan delete indexed records.
 */
class ScanMusicFolderUseCase @Inject constructor(
    private val scanCoordinator: ScanCoordinator,
) {

    /**
     * Canonical scan state (Idle / Scanning / Completed / Failed). Survives
     * ViewModel recreation because the coordinator is a process-wide singleton.
     */
    val scanState: StateFlow<ScanState> = scanCoordinator.scanState

    /**
     * Starts a scan of [folderUri], or returns [ScanOutcome.Skipped] when a
     * scan is already running.
     */
    suspend operator fun invoke(folderUri: Uri): ScanOutcome =
        scanCoordinator.scanFolder(folderUri)

    /**
     * True when the indexed library points at artwork files that are no longer
     * stored, for example when a backup restore brought back the database but
     * not the excluded artwork directory. Running the scan then regenerates the
     * covers.
     */
    suspend fun hasMissingArtwork(): Boolean = scanCoordinator.hasMissingArtwork()
}