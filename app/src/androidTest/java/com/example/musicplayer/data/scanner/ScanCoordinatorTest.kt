package com.example.musicplayer.data.scanner

import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.musicplayer.data.database.AppDatabase
import com.example.musicplayer.data.artwork.ArtworkStore
import com.example.musicplayer.data.database.TrackDao
import com.example.musicplayer.data.database.TrackEntity
import com.example.musicplayer.data.repository.TrackRepository
import com.example.musicplayer.data.repository.TrackRepositoryImpl
import com.example.musicplayer.domain.model.CompletedScan
import com.example.musicplayer.domain.model.DiscoveredAudioFile
import com.example.musicplayer.domain.model.TrackMetadata
import com.example.musicplayer.domain.repository.ScanStatusRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies Task 8: connecting folder scanning to Room with safe rescanning.
 *
 * Runs against a real in-memory SQLite database with fake discovery and
 * metadata steps, so the reconciliation rules are exercised end to end:
 * duplicates, added/changed/removed files, and the guarantee that a failed
 * scan never wipes existing records.
 */
@RunWith(AndroidJUnit4::class)
class ScanCoordinatorTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var database: AppDatabase
    private lateinit var dao: TrackDao
    private lateinit var repository: TrackRepository

    private val metadata = FakeMetadataExtraction()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        dao = database.trackDao()
        repository = TrackRepositoryImpl(dao)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun firstScan_populatesDatabase() = runBlocking {
        val coordinator = coordinator(FakeDiscovery(success(file("a.mp3"), file("b.mp3"), file("c.mp3"))))

        val outcome = coordinator.scanFolder(FOLDER)

        val completed = (outcome as ScanOutcome.Executed).state as ScanState.Completed
        assertEquals(ScanPersistence(added = 3, updated = 0, unchanged = 0, removed = 0), completed.persistence)
        assertEquals(3, dao.count())
        assertEquals("a", dao.getByFolder(PARENT).first { it.uri.endsWith("a.mp3") }.title)
        assertTrue(coordinator.scanState.value is ScanState.Completed)
    }

    @Test
    fun unchangedRescan_createsNoDuplicatesAndSkipsMetadataReads() = runBlocking {
        val discovery = FakeDiscovery(success(file("a.mp3"), file("b.mp3")))
        val coordinator = coordinator(discovery)

        coordinator.scanFolder(FOLDER)
        val firstPassDateAdded = dao.getAll().associate { it.uri to it.dateAdded }
        metadata.reads = 0

        val outcome = coordinator.scanFolder(FOLDER)

        val completed = (outcome as ScanOutcome.Executed).state as ScanState.Completed
        assertEquals(ScanPersistence(added = 0, updated = 0, unchanged = 2, removed = 0), completed.persistence)
        assertEquals(2, dao.count())
        // Unchanged files are not re-read and their rows are left untouched.
        assertEquals(0, metadata.reads)
        assertEquals(firstPassDateAdded, dao.getAll().associate { it.uri to it.dateAdded })
        assertEquals(2, discovery.calls)
    }

    @Test
    fun rescan_addsNewUpdatesChangedAndRemovesMissingFiles() = runBlocking {
        val discovery = FakeDiscovery(
            success(file("keep.mp3"), file("changed.mp3", size = 4_096L), file("gone.mp3")),
        )
        val coordinator = coordinator(discovery)
        coordinator.scanFolder(FOLDER)
        val keepDateAdded = dao.getAll().first { it.uri.endsWith("keep.mp3") }.dateAdded
        Thread.sleep(5)

        metadata.titles = mapOf("changed.mp3" to "Renamed Title")
        discovery.result = success(
            file("keep.mp3"),
            file("changed.mp3", size = 8_192L),
            file("new.mp3"),
        )

        val outcome = coordinator.scanFolder(FOLDER)

        val completed = (outcome as ScanOutcome.Executed).state as ScanState.Completed
        assertEquals(ScanPersistence(added = 1, updated = 1, unchanged = 1, removed = 1), completed.persistence)

        val tracks = dao.getAll()
        assertEquals(3, tracks.size)
        assertEquals(setOf("keep.mp3", "changed.mp3", "new.mp3"), tracks.map { it.uri.substringAfterLast('/') }.toSet())

        val changed = tracks.first { it.uri.endsWith("changed.mp3") }
        assertEquals("Renamed Title", changed.title)
        assertEquals(8_192L, changed.fileSize)

        // Unchanged file keeps its original first-indexed time.
        assertEquals(keepDateAdded, tracks.first { it.uri.endsWith("keep.mp3") }.dateAdded)
        assertNull(tracks.firstOrNull { it.uri.endsWith("gone.mp3") })
    }

    @Test
    fun failedTraversal_keepsExistingLibraryAndReportsFailure() = runBlocking {
        val discovery = FakeDiscovery(success(file("a.mp3"), file("b.mp3")))
        val coordinator = coordinator(discovery)
        coordinator.scanFolder(FOLDER)

        discovery.result = ScanResult.Failure(
            folderUri = FOLDER,
            error = SecurityException("revoked"),
            message = "Read permission for the selected folder is no longer available.",
        )

        val outcome = coordinator.scanFolder(FOLDER)

        val failed = (outcome as ScanOutcome.Executed).state as ScanState.Failed
        assertEquals(FOLDER.toString(), failed.folderUri)
        assertEquals(2, dao.count())
        val state = coordinator.scanState.value
        assertTrue(state is ScanState.Failed)
        assertEquals(
            "Read permission for the selected folder is no longer available.",
            (state as ScanState.Failed).message,
        )
    }

    @Test
    fun metadataFailure_keepsIndexedRowAndAddsFallbackForNewFile() = runBlocking {
        val discovery = FakeDiscovery(success(file("keep.mp3")))
        val coordinator = coordinator(discovery)
        coordinator.scanFolder(FOLDER)

        Thread.sleep(5)
        // keep.mp3 now looks changed but cannot be read; broken.mp3 is new and unreadable.
        metadata.failingUris = setOf("keep.mp3", "broken.mp3")
        discovery.result = success(
            file("keep.mp3", size = 9_999L),
            file("broken.mp3"),
        )

        val outcome = coordinator.scanFolder(FOLDER)

        val completed = (outcome as ScanOutcome.Executed).state as ScanState.Completed
        assertEquals(ScanPersistence(added = 1, updated = 0, unchanged = 1, removed = 0), completed.persistence)

        val tracks = dao.getAll()
        assertEquals(2, tracks.size)
        // Existing row keeps its previously indexed metadata.
        assertEquals("keep", tracks.first { it.uri.endsWith("keep.mp3") }.title)
        // Unreadable new file is visible with fallback values.
        val fallback = tracks.first { it.uri.endsWith("broken.mp3") }
        assertEquals("broken", fallback.title)
        assertEquals(TrackMetadata.UNKNOWN_ARTIST, fallback.artist)
        assertEquals(TrackMetadata.UNKNOWN_ALBUM, fallback.album)
        assertEquals(0L, fallback.durationMs)
    }

    @Test
    fun persistenceFailure_reportsFailureWithoutWipingLibrary() = runBlocking {
        val failing = FailingRepository(repository)
        val coordinator = coordinator(
            discovery = FakeDiscovery(success(file("a.mp3"), file("b.mp3"))),
            repository = failing,
        )
        coordinator.scanFolder(FOLDER)
        assertEquals(2, dao.count())

        Thread.sleep(5)
        failing.failWrites = true

        val outcome = coordinator.scanFolder(FOLDER)

        val failed = (outcome as ScanOutcome.Executed).state as ScanState.Failed
        assertTrue(failed.message.startsWith("Could not save scan results"))
        assertTrue(coordinator.scanState.value is ScanState.Failed)
        // The library survived the failed persistence step.
        assertEquals(2, dao.count())
    }

    @Test
    fun successfulScan_persistsIndexedFolderMarker() = runBlocking {
        val status = FakeScanStatusRepository()
        val coordinator = coordinator(
            FakeDiscovery(success(file("a.mp3"))),
            scanStatus = status,
        )

        coordinator.scanFolder(FOLDER)

        val marker = status.lastCompletedScan.first()
        assertNotNull(marker)
        assertEquals(FOLDER.toString(), marker!!.folderUri)
        assertTrue(marker.completedAt > 0L)
        assertEquals(1, status.markCount)

        // A later successful rescan of the same folder refreshes the marker.
        Thread.sleep(5)
        coordinator.scanFolder(FOLDER)
        val refreshed = status.lastCompletedScan.first()
        assertEquals(2, status.markCount)
        assertTrue(refreshed!!.completedAt >= marker.completedAt)
    }

    @Test
    fun failedScan_neverMarksFolderAsIndexed() = runBlocking {
        val status = FakeScanStatusRepository()
        val discovery = FakeDiscovery(success(file("a.mp3")))
        // Writes fail, so reconciliation cannot complete for this scan.
        val failing = FailingRepository(repository, failWrites = true)
        val coordinator = coordinator(discovery, repository = failing, scanStatus = status)

        val outcome = coordinator.scanFolder(FOLDER)

        assertTrue((outcome as ScanOutcome.Executed).state is ScanState.Failed)
        assertNull(status.lastCompletedScan.first())
        assertEquals(0, status.markCount)
    }

    @Test
    fun failedTraversalWithoutMarker_keepsLibraryUnmarked() = runBlocking {
        val status = FakeScanStatusRepository()
        val discovery = FakeDiscovery(
            ScanResult.Failure(
                folderUri = FOLDER,
                error = SecurityException("revoked"),
                message = "Read permission for the selected folder is no longer available.",
            ),
        )
        val coordinator = coordinator(discovery, scanStatus = status)

        val outcome = coordinator.scanFolder(FOLDER)

        assertTrue((outcome as ScanOutcome.Executed).state is ScanState.Failed)
        assertNull(status.lastCompletedScan.first())
        assertEquals(0, status.markCount)
    }

    @Test
    fun overlappingScansOfSameFolder_areSkipped() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val discovery = FakeDiscovery(success(file("a.mp3")))
        discovery.beforeResult = {
            started.complete(Unit)
            gate.await()
        }
        val coordinator = coordinator(discovery)

        val first = async(Dispatchers.Default) { coordinator.scanFolder(FOLDER) }
        started.await()

        assertEquals(ScanOutcome.Skipped, coordinator.scanFolder(FOLDER))

        gate.complete(Unit)
        val outcome = first.await()
        assertTrue((outcome as ScanOutcome.Executed).state is ScanState.Completed)
        assertEquals(1, discovery.calls)
        assertEquals(1, dao.count())
    }

    // --- artwork pipeline ----------------------------------------------------

    @Test
    fun newTrackWithArtwork_isStoredAndIndexed() = runBlocking {
        val artwork = byteArrayOf(1, 2, 3)
        val store = FakeArtworkStore()
        metadata.artwork = mapOf("a.mp3" to artwork)
        val coordinator = coordinator(FakeDiscovery(success(file("a.mp3"))), artworkStore = store)

        coordinator.scanFolder(FOLDER)

        val uri = dao.getAll().single().artworkUri
        assertNotNull(uri)
        assertEquals(listOf(artwork.toList()), store.stored.values.map { it.toList() })
        assertTrue(store.stored.containsKey(uri))
    }

    @Test
    fun trackWithoutArtwork_indexesNoArtworkReference() = runBlocking {
        val store = FakeArtworkStore()
        val coordinator = coordinator(FakeDiscovery(success(file("a.mp3"))), artworkStore = store)

        coordinator.scanFolder(FOLDER)

        assertNull(dao.getAll().single().artworkUri)
        assertTrue(store.stored.isEmpty())
    }

    @Test
    fun unchangedTrackIndexedBeforeArtworkPipeline_isReReadAndBackfilled() = runBlocking {
        // A row written before artwork extraction existed: same file stamp as the
        // discovered file, but its artwork was never read.
        dao.upsert(
            TrackEntity(
                uri = "$ROOT/a.mp3",
                title = "a",
                artist = "Artist",
                album = "Album",
                durationMs = 210_000L,
                albumId = "album-key",
                artworkUri = null,
                artworkScanned = false,
                folderUri = PARENT,
                fileSize = 1_024L,
                lastModified = 1_700_000_000_000L,
                dateAdded = 1L,
            ),
        )
        val artwork = byteArrayOf(4, 2)
        metadata.artwork = mapOf("a.mp3" to artwork)
        val store = FakeArtworkStore()
        val coordinator = coordinator(FakeDiscovery(success(file("a.mp3"))), artworkStore = store)

        coordinator.scanFolder(FOLDER)

        val row = dao.getAll().single()
        assertNotNull(row.artworkUri)
        assertTrue("the backfill must be recorded so it is not repeated", row.artworkScanned)
        assertEquals(1L, row.dateAdded)
        assertEquals(1, metadata.reads)
        assertEquals(listOf(artwork.toList()), store.stored.values.map { it.toList() })
    }

    @Test
    fun sharedArtworkAcrossTracks_isStoredOnce() = runBlocking {
        val cover = byteArrayOf(7, 7, 7)
        val store = FakeArtworkStore()
        metadata.artwork = mapOf("a.mp3" to cover, "b.mp3" to cover)
        val coordinator = coordinator(
            FakeDiscovery(success(file("a.mp3"), file("b.mp3"))),
            artworkStore = store,
        )

        coordinator.scanFolder(FOLDER)

        assertEquals(1, store.stored.size)
        assertEquals(1, dao.getAll().mapNotNull { it.artworkUri }.toSet().size)
        // The shared image is still referenced by the library after cleaning.
        assertEquals(1, store.stored.size)
    }

    @Test
    fun changedFile_replacesArtworkAndRemovesTheStaleImage() = runBlocking {
        metadata.artwork = mapOf("a.mp3" to byteArrayOf(1))
        val store = FakeArtworkStore()
        val discovery = FakeDiscovery(success(file("a.mp3")))
        val coordinator = coordinator(discovery, artworkStore = store)
        coordinator.scanFolder(FOLDER)
        val oldArtworkUri = dao.getAll().single().artworkUri
        assertNotNull(oldArtworkUri)
        Thread.sleep(5)

        metadata.artwork = mapOf("a.mp3" to byteArrayOf(9))
        discovery.result = success(file("a.mp3", size = 4_096L))

        coordinator.scanFolder(FOLDER)

        val newArtworkUri = dao.getAll().single().artworkUri
        assertNotNull(newArtworkUri)
        assertNotEquals(oldArtworkUri, newArtworkUri)
        assertFalse("stale artwork must be deleted", store.stored.containsKey(oldArtworkUri))
    }

    @Test
    fun removedTrack_dropsItsArtwork() = runBlocking {
        metadata.artwork = mapOf("gone.mp3" to byteArrayOf(5))
        val store = FakeArtworkStore()
        val discovery = FakeDiscovery(success(file("gone.mp3")))
        val coordinator = coordinator(discovery, artworkStore = store)
        coordinator.scanFolder(FOLDER)
        assertEquals(1, store.stored.size)

        // The folder is now empty, so the indexed track and its artwork go away.
        discovery.result = success()

        coordinator.scanFolder(FOLDER)

        assertEquals(0, dao.count())
        assertTrue(store.stored.isEmpty())
    }

    @Test
    fun failedScan_neverCleansUpArtwork() = runBlocking {
        metadata.artwork = mapOf("a.mp3" to byteArrayOf(1))
        val store = FakeArtworkStore()
        val discovery = FakeDiscovery(success(file("a.mp3")))
        val coordinator = coordinator(discovery, artworkStore = store)
        coordinator.scanFolder(FOLDER)
        val written = store.stored.keys.toList()
        val cleanupsAfterSuccessfulScan = store.cleanups

        discovery.result = ScanResult.Failure(
            folderUri = FOLDER,
            error = SecurityException("revoked"),
            message = "Read permission for the selected folder is no longer available.",
        )

        coordinator.scanFolder(FOLDER)

        assertEquals(written, store.stored.keys.toList())
        assertEquals(cleanupsAfterSuccessfulScan, store.cleanups)
    }

    // --- helpers -------------------------------------------------------------

    private fun coordinator(
        discovery: AudioFileDiscovery,
        metadataExtraction: MetadataExtraction = metadata,
        repository: TrackRepository = this.repository,
        scanStatus: ScanStatusRepository = FakeScanStatusRepository(),
        artworkStore: ArtworkStore = FakeArtworkStore(),
    ) = ScanCoordinator(
        discovery = discovery,
        metadataExtraction = metadataExtraction,
        trackRepository = repository,
        scanStatusRepository = scanStatus,
        artworkStore = artworkStore,
        ioDispatcher = Dispatchers.IO,
    )

    private fun file(
        name: String,
        size: Long = 1_024L,
        modified: Long = 1_700_000_000_000L,
    ) = DiscoveredAudioFile(
        uri = Uri.parse("$ROOT/$name"),
        name = name,
        mimeType = "audio/mpeg",
        size = size,
        lastModified = modified,
        parentFolderUri = Uri.parse(PARENT),
    )

    private fun success(vararg files: DiscoveredAudioFile) = ScanResult.Success(
        folderUri = FOLDER,
        audioFiles = files.toList(),
        totalFilesScanned = files.size,
        durationMs = 7L,
    )

    private class FakeDiscovery(var result: ScanResult) : AudioFileDiscovery {
        var calls = 0
        var beforeResult: suspend () -> Unit = {}

        override suspend fun scanFolder(folderUri: Uri): ScanResult {
            calls++
            beforeResult()
            return result
        }
    }

    private class FakeMetadataExtraction : MetadataExtraction {
        /** Titles to return per file name; anything else falls back to the name. */
        var titles: Map<String, String> = emptyMap()

        /** Files whose metadata read fails. */
        var failingUris: Set<String> = emptySet()

        /** Embedded artwork to return per file name. */
        var artwork: Map<String, ByteArray> = emptyMap()

        /** Number of files pushed through metadata extraction. */
        var reads = 0

        override suspend fun extractAll(files: List<DiscoveredAudioFile>): List<MetadataResult> {
            reads += files.size
            return files.map { file ->
                if (file.name in failingUris) {
                    MetadataResult.Failure(
                        file = file,
                        error = IllegalStateException("unreadable"),
                        message = "Failed to read metadata for '${file.name}'",
                    )
                } else {
                    MetadataResult.Success(
                        file = file,
                        metadata = TrackMetadata(
                            title = titles[file.name] ?: file.name.substringBefore('.'),
                            artist = "Artist",
                            album = "Album",
                            durationMs = 210_000L,
                            albumId = "album-key",
                            artworkBytes = artwork[file.name],
                        ),
                    )
                }
            }
        }
    }

    /** In-memory stand-in for the content-addressed artwork store. */
    private class FakeArtworkStore : ArtworkStore {
        /** Stored artwork by URI, mimicking content addressing via the bytes. */
        val stored = LinkedHashMap<String, ByteArray>()

        /** Number of cleanup passes requested after a completed scan. */
        var cleanups = 0

        override suspend fun store(artworkBytes: ByteArray): String? {
            if (artworkBytes.isEmpty()) return null
            stored.entries.firstOrNull { it.value.contentEquals(artworkBytes) }?.let { return it.key }
            val uri = "file:///artwork/${stored.size}.jpg"
            stored[uri] = artworkBytes
            return uri
        }

        override suspend fun deleteUnreferenced(referencedArtworkUris: Collection<String>) {
            cleanups++
            stored.keys.retainAll(referencedArtworkUris.toSet())
        }
    }

    /** In-memory stand-in for the DataStore-backed scan marker. */
    private class FakeScanStatusRepository : ScanStatusRepository {
        private val state = MutableStateFlow<CompletedScan?>(null)

        var markCount = 0
            private set

        override val lastCompletedScan: Flow<CompletedScan?> = state

        override suspend fun markCompleted(folderUri: String, completedAt: Long) {
            markCount++
            state.value = CompletedScan(folderUri = folderUri, completedAt = completedAt)
        }
    }

    private class FailingRepository(
        private val delegate: TrackRepository,
        var failWrites: Boolean = false,
    ) : TrackRepository by delegate {

        override suspend fun upsertAll(tracks: List<TrackEntity>) {
            check(!failWrites) { "simulated write failure" }
            delegate.upsertAll(tracks)
        }

        override suspend fun deleteByUris(uris: List<String>) {
            check(!failWrites) { "simulated write failure" }
            delegate.deleteByUris(uris)
        }
    }

    private companion object {
        val FOLDER: Uri = Uri.parse("content://com.example.musicplayer.documents/tree/music")
        const val PARENT: String =
            "content://com.example.musicplayer.documents/tree/music/document/music"
        const val ROOT: String = PARENT
    }
}