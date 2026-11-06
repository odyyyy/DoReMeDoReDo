package com.example.musicplayer.data.scanner

import android.net.Uri
import com.example.musicplayer.data.artwork.ArtworkStore
import com.example.musicplayer.data.database.TrackEntity
import com.example.musicplayer.data.database.isSameFile
import com.example.musicplayer.data.database.toTrackEntity
import com.example.musicplayer.data.repository.TrackRepository
import com.example.musicplayer.domain.model.DiscoveredAudioFile
import com.example.musicplayer.domain.repository.ScanStatusRepository
import com.example.musicplayer.domain.model.TrackMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs the full scan pipeline: SAF traversal, metadata extraction, and
 * reconciliation of the Room library with what was found.
 *
 * Rules enforced here:
 *
 *  - Nothing is written to the database before a traversal has [ScanResult.Success].
 *    A failed or unreadable folder leaves the library exactly as it was.
 *  - Records are only removed after a completed scan no longer reports their
 *    files; deletion always runs last, so a failure during persistence cannot
 *    wipe indexed tracks.
 *  - Unchanged files (same URI, size, and modified time) are not re-read, so
 *    rescanning a large library is cheap. A row that was indexed before its
 *    artwork was ever read (`artworkScanned == false`) is the exception: it is
 *    re-read once so covers appear without the user clearing the library.
 *  - Extracted artwork is stored as a content-addressed file before the row is
 *    written, and stored images that the reconciled library no longer references
 *    are deleted afterwards, so the artwork store tracks the library exactly.
 *  - Only one scan runs at a time: concurrent requests are dropped with
 *    [ScanOutcome.Skipped] instead of stacking up.
 *  - The persistent "folder indexed" marker ([ScanStatusRepository]) is written
 *    only after traversal succeeded *and* the database was reconciled, so a
 *    failed or interrupted scan never makes a folder look indexed and a
 *    successful one survives process death.
 *  - All work runs on [ioDispatcher]; [scanState] is the single observable
 *    source of truth for Idle / Scanning / Completed / Failed.
 */
@Singleton
class ScanCoordinator @Inject constructor(
    private val discovery: AudioFileDiscovery,
    private val metadataExtraction: MetadataExtraction,
    private val trackRepository: TrackRepository,
    private val scanStatusRepository: ScanStatusRepository,
    private val artworkStore: ArtworkStore,
    private val ioDispatcher: CoroutineDispatcher,
) {

    private val scanMutex = Mutex()

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)

    /** Current state of the running or last finished scan. */
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    /**
     * Scans [folderUri] and reconciles the library with the result.
     *
     * @return The resulting state, or [ScanOutcome.Skipped] when another scan
     *   is already running.
     */
    suspend fun scanFolder(folderUri: Uri): ScanOutcome {
        // tryLock: a second request for the same folder is dropped instead of
        // queueing a duplicate scan behind the running one.
        if (!scanMutex.tryLock()) return ScanOutcome.Skipped
        try {
            return runScan(folderUri)
        } finally {
            scanMutex.unlock()
        }
    }

    private suspend fun runScan(folderUri: Uri): ScanOutcome {
        val folderString = folderUri.toString()
        _scanState.value = ScanState.Scanning(folderString)

        return try {
            when (val scanResult = discovery.scanFolder(folderUri)) {
                // Traversal did not complete: the existing library must survive.
                is ScanResult.Failure -> fail(folderString, scanResult.message)
                is ScanResult.Success -> persist(folderString, scanResult)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Unexpected traversal error: fail instead of getting stuck in
            // Scanning, with the library left untouched.
            fail(
                folderUri = folderString,
                message = "Scan failed: ${e.localizedMessage ?: e.message ?: "unknown error"}",
            )
        }
    }

    private suspend fun persist(
        folderUri: String,
        scanResult: ScanResult.Success,
    ): ScanOutcome {
        val persistence = try {
            withContext(ioDispatcher) { reconcile(scanResult) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Persistence failed partway: keep reporting failure, never wipe.
            return fail(
                folderUri = folderUri,
                message = "Could not save scan results: ${e.localizedMessage ?: e.message ?: "unknown error"}",
            )
        }

        // The folder counts as "indexed" only now, after reconciliation wrote
        // the library. If this marker cannot be stored, report failure so the
        // next launch performs the (cheap, unchanged-files-skipping) scan again
        // instead of assuming the folder is up to date.
        try {
            scanStatusRepository.markCompleted(
                folderUri = folderUri,
                completedAt = System.currentTimeMillis(),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return fail(
                folderUri = folderUri,
                message = "Could not record the completed scan: ${e.localizedMessage ?: e.message ?: "unknown error"}",
            )
        }

        val completed = ScanState.Completed(
            folderUri = folderUri,
            scanResult = scanResult,
            persistence = persistence,
        )
        _scanState.value = completed
        return ScanOutcome.Executed(completed)
    }

    private fun fail(folderUri: String, message: String): ScanOutcome {
        val failed = ScanState.Failed(folderUri = folderUri, message = message)
        _scanState.value = failed
        return ScanOutcome.Executed(failed)
    }

    /**
     * Brings the library in line with a completed scan: upserts new and
     * changed tracks, keeps unchanged rows untouched, and deletes rows whose
     * files disappeared.
     *
     * Insertions run before deletions: a failure between the two steps leaves
     * extra rows at worst, never a library without the files just scanned.
     */
    private suspend fun reconcile(scan: ScanResult.Success): ScanPersistence {
        val existingByUri = trackRepository.getAll().associateBy { it.uri }
        val discovered = scan.audioFiles

        var added = 0
        var updated = 0
        var unchanged = 0

        // Files whose stamp changed (or that were never indexed) are the only
        // ones worth the cost of another metadata read, plus rows indexed before
        // artwork extraction ran: those still have artworkScanned == false and
        // get their cover backfilled by this scan.
        val needsExtraction = discovered.filter { file ->
            val existing = existingByUri[file.uri.toString()]
            existing == null ||
                !existing.artworkScanned ||
                !existing.isSameFile(size = file.size, modifiedAt = file.lastModified)
        }

        val results = if (needsExtraction.isEmpty()) {
            emptyList()
        } else {
            metadataExtraction.extractAll(needsExtraction)
        }

        val now = System.currentTimeMillis()
        val rowsToWrite = ArrayList<TrackEntity>(results.size)

        for (result in results) {
            val file = result.file
            val existing = existingByUri[file.uri.toString()]
            when (result) {
                is MetadataResult.Success -> {
                    rowsToWrite += file.toTrackEntity(
                        metadata = result.metadata,
                        // Preserve first-indexed time across rescans.
                        dateAdded = existing?.dateAdded ?: now,
                        // This file is being (re)read, so its artwork reference is
                        // rebuilt from the freshly extracted bytes. Re-storing is
                        // content addressed: identical bytes keep the same URI and
                        // a changed cover resolves to a new file, while the stale
                        // one is dropped by the cleanup below.
                        artworkUri = result.metadata.artworkBytes?.let { artworkStore.store(it) },
                    )
                    if (existing == null) added++ else updated++
                }

                is MetadataResult.Failure -> {
                    if (existing == null) {
                        // Show the file with fallbacks rather than hiding it.
                        rowsToWrite += fallbackEntity(file, indexedAt = now)
                        added++
                    } else {
                        // Existing row: keep the previously indexed metadata.
                        unchanged++
                    }
                }
            }
        }

        val unchangedStamps = discovered.size - needsExtraction.size
        unchanged += unchangedStamps

        trackRepository.upsertAll(rowsToWrite)

        // Deletions run only after a completed traversal, and only last.
        val discoveredUris = discovered.mapTo(HashSet(discovered.size)) { it.uri.toString() }
        val removedUris = existingByUri.keys - discoveredUris
        trackRepository.deleteByUris(removedUris.toList())

        // Only once the library is final: every stored image the library no
        // longer references belongs to a removed track or a replaced cover. This
        // is best effort by contract (ArtworkStore never throws), so an artwork
        // problem can never fail an otherwise successful scan.
        artworkStore.deleteUnreferenced(trackRepository.getArtworkUris())

        return ScanPersistence(
            added = added,
            updated = updated,
            unchanged = unchanged,
            removed = removedUris.size,
        )
    }

    /**
     * Row for a newly discovered file whose metadata could not be read, using
     * the same fallbacks the extractor applies to missing tags.
     */
    private fun fallbackEntity(file: DiscoveredAudioFile, indexedAt: Long) = TrackEntity(
        uri = file.uri.toString(),
        title = MetadataExtractor.resolveTitle(rawTitle = null, fileName = file.name),
        artist = TrackMetadata.UNKNOWN_ARTIST,
        album = TrackMetadata.UNKNOWN_ALBUM,
        durationMs = 0L,
        albumId = null,
        artworkUri = null,
        artworkScanned = true,
        folderUri = file.parentFolderUri.toString(),
        fileSize = file.size,
        lastModified = file.lastModified,
        dateAdded = indexedAt,
    )
}