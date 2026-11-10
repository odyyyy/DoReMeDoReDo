package com.example.musicplayer.presentation.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicplayer.data.scanner.ScanOutcome
import com.example.musicplayer.data.scanner.ScanResult
import com.example.musicplayer.data.scanner.ScanState
import com.example.musicplayer.domain.model.CompletedScan
import com.example.musicplayer.domain.model.SelectedFolder
import com.example.musicplayer.domain.model.Track
import com.example.musicplayer.domain.model.TrackSort
import com.example.musicplayer.domain.repository.ScanStatusRepository
import com.example.musicplayer.domain.usecase.CheckFolderPermissionUseCase
import com.example.musicplayer.domain.usecase.GetAlbumsUseCase
import com.example.musicplayer.domain.usecase.GetSelectedFolderUseCase
import com.example.musicplayer.domain.usecase.GetTracksUseCase
import com.example.musicplayer.domain.usecase.ScanMusicFolderUseCase
import com.example.musicplayer.domain.usecase.SetSelectedFolderUseCase
import com.example.musicplayer.player.PlayerController
import com.example.musicplayer.player.TrackArtwork
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel managing folder selection, scan-driven library persistence, the
 * Tracks view, and the Albums view.
 */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val getSelectedFolderUseCase: GetSelectedFolderUseCase,
    private val setSelectedFolderUseCase: SetSelectedFolderUseCase,
    private val checkFolderPermissionUseCase: CheckFolderPermissionUseCase,
    private val scanMusicFolderUseCase: ScanMusicFolderUseCase,
    private val scanStatusRepository: ScanStatusRepository,
    private val getTracksUseCase: GetTracksUseCase,
    private val getAlbumsUseCase: GetAlbumsUseCase,
    private val playerController: PlayerController,
) : ViewModel() {

    private val _uiState = MutableStateFlow<LibraryUiState>(LibraryUiState.Loading)

    /** Folder/scan lifecycle state of the Library screen. */
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")

    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /**
     * Single shared subscription to the indexed library.
     *
     * Both the Tracks state and the Albums state are derived from this stream, so
     * one library change costs one Room query and one entity-to-domain mapping
     * instead of one per screen. `WhileSubscribed` keeps the query alive only
     * while a screen actually collects one of the derived states, and the replay
     * cache hands the current library to a screen that subscribes later (for
     * example when the user switches to the Albums tab).
     */
    private val domainTracks: Flow<List<Track>> = getTracksUseCase().shareIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        replay = 1,
    )

    private val _tracksUiState = MutableStateFlow<TracksUiState>(TracksUiState.Loading)

    val tracksUiState: StateFlow<TracksUiState> = _tracksUiState.asStateFlow()

    val albumsUiState: StateFlow<AlbumsUiState> = combine(
        getAlbumsUseCase(domainTracks),
        _searchQuery,
    ) { albums, query ->
        val needle = query.trim()
        val filtered = if (needle.isEmpty()) {
            albums
        } else {
            // Case-insensitive matching without lowercasing every album title
            // and artist on each keystroke.
            albums.filter { album ->
                album.title.contains(needle, ignoreCase = true) ||
                    album.artist.contains(needle, ignoreCase = true)
            }
        }
        AlbumsUiState.Ready(
            albums = filtered,
            totalAlbums = albums.size,
            isFiltered = needle.isNotEmpty(),
        )
    }
        // Search filtering and the album projection are O(n); keep them off the
        // main thread so a large library does not jank while typing.
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AlbumsUiState.Loading,
        )

    /**
     * Current track + play/pause state for the row highlight. Derived without
     * position so the list does not recompose on every playback tick.
     */
    val playbackHighlight: StateFlow<PlaybackHighlight?> = playerController.state
        .map { playback ->
            playback.currentTrack?.let { track ->
                PlaybackHighlight(trackUri = track.uri, isPlaying = playback.isPlaying)
            }
        }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    /** User-facing playback failure, or `null` when playback is healthy. */
    val playbackError: StateFlow<String?> = playerController.state
        .map { it.playbackError }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    /** Number of items currently in the playback queue. */
    val queueSize: StateFlow<Int> = playerController.state
        .map { it.queue.size }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0,
        )

    private val _trackSort = MutableStateFlow(TrackSort.TITLE)

    /**
     * Seed for the random Shuffle sort. It advances whenever the user picks
     * Shuffle in the sort menu, so choosing the same option again produces a new
     * random order instead of being a no-op.
     */
    private val _shuffleSeed = MutableStateFlow(0L)

    private var tracksJob: Job? = null

    /**
     * Whether this process has already checked the library for artwork files
     * that disappeared (for example after a backup restore). Checked once so a
     * missing image that cannot be regenerated does not trigger a scan on every
     * library re-render.
     */
    private var artworkRepairChecked = false

    init {
        observeLibrary()
        observeTracks()
    }

    /** Whether the selected folder currently exists and is readable. */
    private sealed interface FolderAvailability {
        data object None : FolderAvailability
        data class Revoked(val folder: SelectedFolder) : FolderAvailability
        data class Available(val folder: SelectedFolder) : FolderAvailability
    }

    private fun observeLibrary() {
        viewModelScope.launch {
            combine(
                selectedFolderAvailability(),
                scanMusicFolderUseCase.scanState,
                scanStatusRepository.lastCompletedScan,
            ) { availability, scanState, completedScan ->
                LibrarySnapshot(availability, scanState, completedScan)
            }.collectLatest { (availability, scanState, completedScan) ->
                when (availability) {
                    // Revoked folders keep their indexed records: only the
                    // recovery UI changes, nothing is deleted from Room.
                    is FolderAvailability.None -> _uiState.value = LibraryUiState.NoFolderSelected

                    is FolderAvailability.Revoked -> _uiState.value = LibraryUiState.FolderAccessRevoked(
                        folderUri = availability.folder.uri,
                        displayName = availability.folder.displayName,
                    )

                    is FolderAvailability.Available ->
                        render(availability.folder, scanState, completedScan)
                }
            }
        }
    }

    /**
     * Observes the Room library as domain tracks, applying search + sorting
     * off the main thread. Restartable: a failed stream is replaced wholesale
     * when the user retries.
     */
    private fun observeTracks() {
        tracksJob?.cancel()
        _tracksUiState.value = TracksUiState.Loading
        tracksJob = viewModelScope.launch {
            buildTracksUiState(
                tracks = domainTracks,
                searchQuery = _searchQuery,
                sort = _trackSort,
                shuffleSeed = _shuffleSeed,
            ).collect { state ->
                _tracksUiState.value = state
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onSortChange(sort: TrackSort) {
        // Re-selecting Shuffle must produce a new random order.
        if (sort == TrackSort.SHUFFLE) {
            _shuffleSeed.value += 1
        }
        _trackSort.value = sort
    }

    /**
     * Starts playback from [track] using the currently displayed, filtered and
     * sorted list as the player queue, per the queue rules in
     * [PlayerController.setQueue].
     */
    fun onTrackSelected(track: Track) {
        val tracks = (_tracksUiState.value as? TracksUiState.Ready)?.tracks ?: return
        val index = tracks.indexOfFirst { it.uri == track.uri }
        if (index < 0) return
        playerController.setQueue(tracks, index)
    }

    /** Appends [track] to the end of the playback queue. */
    fun onAddToQueue(track: Track) {
        playerController.addToQueue(track)
    }

    /** Inserts [track] immediately after the current item. */
    fun onPlayNext(track: Track) {
        playerController.playNext(track)
    }

    /** Dismisses the last playback failure message. */
    fun dismissPlaybackError() {
        playerController.dismissPlaybackError()
    }

    /** Restarts the library observation after [TracksUiState.Error]. */
    fun retryTracks() {
        observeTracks()
    }

    private fun selectedFolderAvailability(): Flow<FolderAvailability> =
        getSelectedFolderUseCase()
            .map { folder ->
                when {
                    folder == null -> FolderAvailability.None
                    !checkFolderPermissionUseCase(folder.uri) ->
                        FolderAvailability.Revoked(folder)

                    else -> FolderAvailability.Available(folder)
                }
            }
            // DocumentFile.exists()/canRead() query the document provider. They
            // must never run on the main thread, where a slow provider could
            // block the first frame or trigger an ANR.
            .flowOn(Dispatchers.IO)

    private suspend fun render(
        folder: SelectedFolder,
        scanState: ScanState,
        completedScan: CompletedScan?,
    ) {
        // A scan of this folder is running or finished during this session:
        // mirror the canonical state (covers Rescan and first-run scans).
        if (scanState.folderUri == folder.uri) {
            _uiState.value = LibraryUiState.Ready(
                folder = folder,
                scanState = scanState,
                lastScanResult = retainedScanResult(folder),
            )
            return
        }

        // Persisted proof that this folder was already indexed: the library is
        // loaded from Room, no filesystem scan runs on startup. Rescanning stays
        // a manual action.
        if (completedScan?.folderUri == folder.uri) {
            _uiState.value = LibraryUiState.Ready(
                folder = folder,
                scanState = ScanState.Idle,
                lastScanResult = null,
            )
            // Artwork is stored in app-private space that auto backup excludes,
            // while the database itself is backed up. A restore therefore leaves
            // rows pointing at images that are gone. Detect that once per
            // process and run the incremental scan, which only re-reads the
            // affected tracks, instead of showing placeholders until the user
            // finds Rescan.
            if (!artworkRepairChecked) {
                artworkRepairChecked = true
                if (scanMusicFolderUseCase.hasMissingArtwork()) {
                    startScan(folder)
                }
            }
            return
        }

        // No successful scan exists for this folder (first launch, or a new
        // folder was selected): perform the initial indexing scan.
        _uiState.value = LibraryUiState.Ready(
            folder = folder,
            scanState = ScanState.Scanning(folder.uri),
            lastScanResult = null,
        )
        startScan(folder)
    }

    /**
     * Keeps the previous completed scan visible while a rescan of the same
     * folder is running; results from another folder are never carried over.
     */
    private fun retainedScanResult(folder: SelectedFolder): ScanResult.Success? {
        val current = _uiState.value as? LibraryUiState.Ready ?: return null
        if (current.folder.uri != folder.uri) return null
        return current.scanResult
    }

    private fun startScan(folder: SelectedFolder) {
        // Launched on viewModelScope (not inside the state collector) so a
        // running scan survives further folder/state re-emissions.
        viewModelScope.launch {
            when (val outcome = scanMusicFolderUseCase(Uri.parse(folder.uri))) {
                is ScanOutcome.Executed -> {
                    // A completed scan may have added, replaced, or removed
                    // covers. The queue's media items copied the old artwork
                    // metadata when they were created, so refresh the player's
                    // artwork overlay now. Failed scans leave the indexed
                    // library untouched and therefore need no refresh.
                    if (outcome.state is ScanState.Completed) {
                        refreshQueueArtwork()
                    }
                }

                ScanOutcome.Skipped -> Unit
            }
        }
    }

    /**
     * Re-reads the freshly indexed artwork and hands it to the player.
     *
     * This is a one-shot read instead of [domainTracks] on purpose: the shared
     * observation has a replay cache that may still hold pre-scan rows. Only an
     * active queue needs updating, so an idle player does no work at all.
     */
    private suspend fun refreshQueueArtwork() {
        if (playerController.state.value.queue.items.isEmpty()) return
        val artworkByUri = getTracksUseCase.currentTracks().associate { track ->
            track.uri to TrackArtwork(
                artworkUri = track.artworkUri,
                fullArtworkUri = track.fullArtworkUri,
            )
        }
        playerController.updateQueueArtwork(artworkByUri)
    }

    fun onFolderSelected(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = LibraryUiState.Loading
            val success = setSelectedFolderUseCase(uri)
            if (!success) {
                _uiState.value = LibraryUiState.FolderAccessRevoked(
                    folderUri = uri.toString(),
                    displayName = uri.lastPathSegment ?: "Selected Folder",
                )
            }
            // On success the folder flow re-emits and starts the first scan.
        }
    }

    /** Manual Rescan action: starts a fresh scan of the selected folder. */
    fun rescanCurrentFolder() {
        val folder = (_uiState.value as? LibraryUiState.Ready)?.folder ?: return
        startScan(folder)
    }

    fun onClearFolder() {
        viewModelScope.launch {
            setSelectedFolderUseCase.clearFolder()
            _uiState.value = LibraryUiState.NoFolderSelected
        }
    }

    /** One immutable combination of the three inputs that drive the screen. */
    private data class LibrarySnapshot(
        val availability: FolderAvailability,
        val scanState: ScanState,
        val completedScan: CompletedScan?,
    )
}