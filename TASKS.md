# TASKS.md

## Project: Minimal Music Player

This file tracks the first ten implementation tasks. Complete them in
order unless a dependency or build issue requires a documented
adjustment.

**Status legend:** `TODO` · `IN PROGRESS` · `BLOCKED` · `DONE`

------------------------------------------------------------------------

## Task 01 --- Create and verify the Android project

**Status:** DONE\
**Priority:** Critical\
**Depends on:** None

### Goal

Create a clean Kotlin Android application using Jetpack Compose.

### Work

-   [x] Create an Android Studio project using the **Empty Activity**
    template.
-   [x] Use Kotlin and Kotlin DSL (`.kts`).
-   [x] Set the application ID/package to the chosen project package
    (initial suggestion: `com.example.musicplayer`).
-   [x] Set Minimum SDK to **API 26 (Android 8.0)**.
-   [x] Use the current supported Compile SDK and Target SDK available
    in the installed Android Studio/SDK setup; target API 36 if the
    installed toolchain supports it.
-   [x] Confirm the project uses Compose and Material 3.
-   [x] Keep the initial project in one `app` module.
-   [x] Build and run the starter app on an emulator or physical device.

### Acceptance criteria

-   The project opens without Gradle sync errors.
-   The starter Compose screen launches.
-   `./gradlew assembleDebug` completes successfully.
-   The package name and SDK configuration are recorded.

### Notes

Do not add navigation, database, playback, or other app features in this
task.

### Configuration recorded

-   **Application ID / namespace:** `com.example.musicplayer`
-   **Module:** single `:app` module
-   **Language / DSL:** Kotlin, Kotlin DSL build scripts
-   **UI toolkit:** Jetpack Compose + Material 3
-   **minSdk:** 26
-   **targetSdk:** 37
-   **compileSdk:** 37 (platform `android-37.0` installed in the SDK)
-   **AGP:** 9.5.0, **Gradle:** 9.6.0

### Validation performed

-   `./gradlew assembleDebug` -> BUILD SUCCESSFUL
-   Launched on emulator `Medium_Phone_API_37.0`; the starter Compose
    screen displayed.
-   `./gradlew testDebugUnitTest` -> BUILD SUCCESSFUL

------------------------------------------------------------------------

## Task 02 --- Set up dependency management and core libraries

**Status:** DONE\
**Priority:** Critical\
**Depends on:** Task 01

### Goal

Prepare a maintainable Gradle configuration for the selected technology
stack.

### Work

-   [x] Inspect the generated Gradle files and existing Version Catalog.
-   [x] Centralize dependency and plugin versions in
    `gradle/libs.versions.toml` where practical.
-   [x] Add compatible stable dependencies for:
    -   [x] Jetpack Compose and Material 3
    -   [x] Navigation Compose
    -   [x] Lifecycle ViewModel Compose
    -   [x] Kotlin Coroutines and Flow
    -   [x] Room runtime, KTX, and compiler integration
    -   [x] AndroidX Media3 ExoPlayer and Session
    -   [x] Coil 3 Compose integration
    -   [x] Hilt and its Compose/navigation integration only where
        needed
    -   [x] DataStore Preferences
-   [x] Configure Kotlin/Compose compiler integration according to the
    project's Kotlin and Android Gradle Plugin versions.
-   [x] Add only dependencies needed by the planned architecture.
-   [x] Sync Gradle and build the app.

### Acceptance criteria

-   All dependencies resolve with mutually compatible versions.
-   The app builds successfully.
-   Versions are not duplicated unnecessarily across Gradle files.
-   No unrelated libraries are added.

### Notes

Check current official documentation for compatible versions. Do not
copy version numbers from an old tutorial.

### Versions added

All plugin and dependency versions live in `gradle/libs.versions.toml`.

-   Compose BOM `2026.02.01`; Material 3; Navigation Compose `2.10.2`
-   Lifecycle `2.11.0` (runtime-ktx, runtime-compose,
    viewmodel-compose)
-   Activity Compose `1.13.0`; Core KTX `1.19.1`
-   Coroutines `1.11.0`
-   Room `2.8.5` (runtime, ktx, compiler via KSP)
-   Media3 `1.11.1` (exoplayer, session)
-   Coil 3 `3.3.0` (`coil-compose`)
-   Hilt `2.60.1` (+ `hilt-navigation-compose` `1.4.0`)
-   DataStore Preferences `1.3.0`
-   DocumentFile `1.1.0` (Storage Access Framework traversal)
-   KSP `2.3.13` (AGP 9 requires KSP 2.3.6+)

### Key decisions

-   AGP 9 uses built-in Kotlin, so no `kotlin-android` plugin is applied.
    The bundled Kotlin compiler is 2.2.10; the Compose compiler plugin
    (`org.jetbrains.kotlin.plugin.compose`) is pinned to the same 2.2.10.
-   Coil is pinned to `3.3.0` (Kotlin 2.2.0 metadata) instead of the
    latest `3.6.x` (Kotlin 2.4.x metadata), because the compiled Kotlin
    metadata from Coil 3.6.x is unreadable by the AGP 9 bundled Kotlin
    2.2.10 compiler. `3.3.0` is the newest stable Coil 3 release that
    resolves cleanly without upgrading the whole Kotlin toolchain.
-   Hilt and Room annotation processing use KSP (not kapt), as required
    for AGP 9 built-in Kotlin.

### Validation performed

-   `./gradlew assembleDebug` -> BUILD SUCCESSFUL (KSP and Hilt tasks
    executed, e.g. `kspDebugKotlin`, `hiltAggregateDepsDebug`).
-   `./gradlew testDebugUnitTest` -> BUILD SUCCESSFUL

------------------------------------------------------------------------

## Task 03 --- Establish package structure and app entry point

**Status:** DONE\
**Priority:** High\
**Depends on:** Task 02

### Goal

Create the initial code organization without introducing unnecessary
abstractions.

### Work

-   [x] Create the top-level packages described in `AGENT.md`:
    -   [x] `data`
    -   [x] `domain`
    -   [x] `player`
    -   [x] `presentation`
    -   [x] `navigation`
    -   [x] `di`
    -   [x] `ui.theme`
-   [x] Keep `MainActivity` as the activity entry point.
-   [x] Create or confirm `MusicPlayerApp` as the Compose application
    root if useful.
-   [x] Set up a minimal Material 3 theme.
-   [x] Add a basic app scaffold with a placeholder Library screen.
-   [x] Keep the UI deliberately simple.

### Acceptance criteria

-   The application launches into the placeholder Library screen.
-   Package responsibilities are clear.
-   No empty classes are created merely to fill the proposed folder
    tree.
-   The app still builds.

### Implementation notes

-   `MusicPlayerApp` is the `Application` subclass annotated with
    `@HiltAndroidApp` (the entry point Hilt requires). The Compose root is
    `AppNavigation`, which hosts the app-level `Scaffold` and `NavHost`;
    that scaffold is the future home of the Mini Player.
-   `MainActivity` is annotated with `@AndroidEntryPoint` and renders
    `AppNavigation` inside `MusicPlayerTheme`.
-   Only packages required so far contain files. The remaining
    directories (`data/*`, `domain/*`, `player`, `di`, etc.) exist as
    empty packages to establish responsibilities; no placeholder classes
    were added to fill them.
-   The theme is a single fixed dark, near-black Material 3 palette with
    one restrained accent (`0xFF4DB6AC`). System dynamic colour and
    light/dark switching are intentionally not used.

------------------------------------------------------------------------

## Task 04 --- Implement persistent music-folder selection

**Status:** DONE\
**Priority:** Critical\
**Depends on:** Task 03

### Goal

Allow the user to select one local music folder and retain access to it
across app restarts.

### Work

-   [x] Add a first-run empty state explaining that a music folder must
    be selected.
-   [x] Add a button to open the system document-tree picker.
-   [x] Use the Activity Result API or equivalent modern Android API.
-   [x] Request read access and persist the granted URI permission when
    supported.
-   [x] Store the selected folder URI and display name in DataStore.
-   [x] On app launch, check whether the saved permission is still
    available.
-   [x] If access is unavailable, show a clear recovery state and allow
    folder reselection.
-   [x] Do not request broad storage permissions unless a verified
    platform requirement makes them necessary.

### Acceptance criteria

-   A user can select a folder through the Android system picker.
-   The selected folder name is shown in the app.
-   The selection survives an app restart when the provider supports
    persistent access.
-   Revoked or unavailable access is handled without a crash.
-   The app can return to the folder-selection flow.

### Notes

Folder preferences are stored in DataStore Preferences and managed via `FolderRepositoryImpl`.

### Key decisions

-   `FolderRepository` persists folder tree URI and display name using DataStore Preferences.
-   `takePersistableUriPermission` is called when taking folder permission.
-   `hasValidPermission` checks `persistedUriPermissions` and `DocumentFile.canRead()`. If permission is revoked, UI automatically transitions to `FolderAccessRevoked`.

### Validation performed

-   `./gradlew testDebugUnitTest` -> 4 unit tests passed.
-   `./gradlew assembleDebug` -> BUILD SUCCESSFUL.

------------------------------------------------------------------------

## Task 05 --- Implement audio-file discovery and folder traversal

**Status:** DONE\
**Priority:** Critical\
**Depends on:** Task 04

### Goal

Enumerate supported audio files inside the selected folder and its
accessible subfolders.

### Work

-   [x] Create a `MusicScanner` responsible for SAF tree traversal.
-   [x] Traverse nested directories where supported by the document
    provider.
-   [x] Identify candidate audio files by MIME type and/or supported
    file extension.
-   [x] Initially recognize MP3, FLAC, WAV, M4A, AAC, OGG, and OPUS.
-   [x] Avoid treating folders or unrelated documents as audio tracks.
-   [x] Run scanning off the main thread.
-   [x] Return a structured scan result containing discovered document
    URIs and any recoverable errors.
-   [x] Distinguish a completed scan from a failed or incomplete scan.
-   [x] Handle inaccessible child folders and provider errors without
    crashing.

### Acceptance criteria

-   A test folder with nested directories is traversed.
-   Supported audio candidates are discovered.
-   Non-audio files are ignored.
-   Scanning does not block the main thread.
-   A failed scan is not reported as a successful empty library.

### Notes

Discovery logic operates on `Dispatchers.IO`. Results are returned as `ScanResult.Success` or `ScanResult.Failure`.

### Key decisions

-   `MusicScanner` runs recursive SAF traversal off the main thread (`Dispatchers.IO`) up to a max depth limit of 20 to handle deep directory trees safely.
-   Audio candidacy matches MIME types (`audio/*`) and supported file extensions (`.mp3`, `.flac`, `.wav`, `.m4a`, `.aac`, `.ogg`, `.opus`).
-   Subdirectory listing failures are captured as non-fatal warnings in `ScanResult.Success`.

### Validation performed

-   `MusicScannerTest` -> unit tests verifying extension/MIME filtering passed.
-   `./gradlew testDebugUnitTest` -> 4 tests passed.
-   `./gradlew assembleDebug` -> BUILD SUCCESSFUL.

------------------------------------------------------------------------

## Task 06 --- Extract track metadata and artwork safely

**Status:** DONE\
**Priority:** High\
**Depends on:** Task 05

### Goal

Read the metadata required to display tracks and group albums.

### Work

-   [x] Create a `MetadataExtractor`.
-   [x] Extract title, artist, album, duration, and available album
    identifier.
-   [x] Extract embedded artwork when available.
-   [x] Use `MediaMetadataRetriever` or another appropriate supported
    Android API.
-   [x] Release retriever resources reliably, including on exceptions.
-   [x] Provide sensible fallbacks when title, artist, or album metadata
    is missing.
-   [x] Treat missing artwork as a normal state.
-   [x] Avoid decoding full-resolution artwork unnecessarily.
-   [x] Return a typed metadata result for each discovered file.
-   [x] Record per-file extraction errors without aborting the entire
    scan.

### Acceptance criteria

-   Metadata can be extracted from representative supported files.
-   Missing tags and artwork do not crash the scanner.
-   One corrupt or unsupported file does not stop extraction for other
    files.
-   All metadata work runs off the main thread.

### Notes

Do not implement artwork caching or the final artwork UI yet.

### Key decisions

-   `TrackMetadata` lives in `domain/model` and always exposes resolved,
    non-null `title`, `artist`, and `album` values. Missing tags fall back
    to the file name without its extension, `Unknown Artist`, and
    `Unknown Album`; missing or malformed duration falls back to `0`.
-   `MetadataResult` is a sealed `Success`/`Failure` per discovered file.
    `extractAll` maps every file through the same guarded extraction, so
    one corrupt or inaccessible file produces a `Failure` without
    aborting the rest of the batch.
-   `MediaMetadataRetriever.release()` is called in a `finally` block and
    wrapped so a release failure cannot turn a successful extraction into
    a failure.
-   `MediaMetadataRetriever` does not expose a reliable album ID for SAF
    documents. `albumId` is therefore a stable synthetic key derived from
    normalized album and artist tags (SHA-256), matching the
    normalized-album/artist grouping fallback in `AGENT.md`. It is `null`
    when the file has no album tag.
-   `METADATA_KEY_ALBUMARTIST` is used when `METADATA_KEY_ARTIST` is
    absent.
-   Embedded artwork is decoded with `inJustDecodeBounds`, downsampled
    with a power-of-two `inSampleSize` to at most 512 px, and re-encoded
    as JPEG at quality 85. Results larger than 512 KB are dropped.
    Missing or corrupt artwork resolves to `null` without failing the
    file.
-   `extract` and `extractAll` run on `Dispatchers.IO`. The blocking
    internals are `internal` for focused tests.
-   Metadata extraction is not yet wired into `ScanResult` or Room;
    Task 08 will connect it to persistence. No artwork caching or artwork
    UI was added, per the task notes.

### Validation performed

-   `./gradlew testDebugUnitTest` -> 14 tests passed (4 scanner + 10
    metadata mapping/fallback tests).
-   `./gradlew assembleDebug` -> BUILD SUCCESSFUL.
-   `./gradlew assembleDebugAndroidTest` -> BUILD SUCCESSFUL.
-   `./gradlew connectedDebugAndroidTest` on
    `Medium_Phone_API_37.0` (API 37) -> BUILD SUCCESSFUL. Instrumented
    tests verified real duration extraction from a generated 1-second WAV
    (reported duration within 900--1100 ms), real ID3v2.3
    title/artist/album and embedded JPEG artwork extraction (the returned
    artwork decodes and respects the 512 px bound), fallbacks for absent
    tags and absent artwork, and that `extractAll` continues after a
    missing file.

------------------------------------------------------------------------

## Task 07 --- Add Room database and library entities

**Status:** DONE\
**Priority:** Critical\
**Depends on:** Task 06

### Goal

Persist the indexed music library locally.

### Work

-   [x] Add a Room database.
-   [x] Create `TrackEntity` with at least:
    -   [x] URI as a stable key
    -   [x] Title
    -   [x] Artist
    -   [x] Album
    -   [x] Duration
    -   [x] Optional album ID
    -   [x] Optional artwork reference
    -   [x] Parent folder URI
    -   [x] File size where available
    -   [x] Modified time where available
    -   [x] Indexed/added timestamp if needed for sorting
-   [x] Create `MusicFolderEntity` if folder records need to be queried
    from Room; otherwise keep the selected folder in DataStore
    initially.
-   [x] Create the required DAO methods for insert/update, delete,
    observe all tracks, and query by folder.
-   [x] Add indexes for fields used by common queries where justified.
-   [x] Add database dependency injection.
-   [x] Keep database access off the main thread.
-   [x] Add a simple repository implementation boundary.

### Acceptance criteria

-   Extracted track metadata can be saved and read from Room.
-   Duplicate URIs do not create duplicate tracks.
-   The database can be opened after app restart.
-   The app builds and basic DAO tests pass where test infrastructure is
    available.

### Notes

Do not create a separate Album table unless the current requirements
demonstrate a need.

### Key decisions

-   `TrackEntity` lives in `data/database` with `uri` as the primary key.
    `TrackDao` uses `@Upsert`, so rescanning an existing URI updates the
    row instead of inserting a duplicate. Single-row `@Upsert` and
    `@Delete` are available for future repository logic.
-   `MusicFolderEntity` was not added. Task 04 already persists the
    selected folder in DataStore, and every track stores `folderUri`,
    which is enough to query or diff a folder during rescanning.
-   Artwork is stored as an optional `artworkUri` reference rather than a
    BLOB so track-list queries never load artwork bytes. Populating that
    reference (local artwork file storage) belongs to the scan-to-database
    wiring in Task 08; this task does not add artwork caching.
-   `TrackEntityMapper` maps a `DiscoveredAudioFile` plus its
    `TrackMetadata` into a row. `dateAdded` is supplied by the caller so
    Task 08 can preserve the original first-indexed time on rescan.
-   Indices cover `folderUri` (per-folder scans and diffing) and
    `title`/`artist`/`album`/`dateAdded` (the required work-list sort
    orders). A separate album table was deliberately not created.
-   `TrackRepository`/`TrackRepositoryImpl` form a data-layer boundary
    that currently exposes `TrackEntity`. The domain `Track` model and
    its mapping arrive with Task 09, the task that defines the library UI.
-   Room suspend queries and Flow queries run on Room's own background
    executor; no DAO call is made on the main thread. The repository also
    skips empty upsert/delete batches.
-   The database is provided by Hilt in `DatabaseModule` and the
    repository is bound in `RepositoryModule`.
-   Schema export is enabled through KSP
    (`room.schemaLocation = app/schemas`) so future schema changes can use
    explicit migrations instead of destructive migration.

### Validation performed

-   `./gradlew assembleDebug testDebugUnitTest` -> BUILD SUCCESSFUL
    (14 unit tests passed).
-   `./gradlew connectedDebugAndroidTest` on a physical Infinix X6850
    running Android 16 -> BUILD SUCCESSFUL, 10 instrumented tests passed.
    The 6 new `TrackDaoTest` cases verify full-field round trip, upsert
    deduplication by URI, folder-scoped queries/deletes, delete-by-folder,
    file-backed persistence across a close/reopen, and
    metadata-to-entity mapping.
-   Room exported
    `app/schemas/com.example.musicplayer.data.database.AppDatabase/1.json`.
    The database uses version 1 with no destructive migration fallback.

------------------------------------------------------------------------

## Task 08 --- Connect scanning to Room and implement safe rescanning

**Status:** DONE\
**Priority:** Critical\
**Depends on:** Task 07

### Goal

Make the local database reflect the selected music folder after a
completed scan.

### Work

-   [ ] Create or complete `MusicRepository` and its implementation.
-   [ ] Connect folder traversal to metadata extraction and Room
    persistence.
-   [ ] Insert newly discovered tracks.
-   [ ] Update records when relevant file metadata changes.
-   [ ] Remove records for files that are no longer present only after a
    scan completes successfully for that folder.
-   [ ] Preserve existing records if permission is revoked or the scan
    fails partway through.
-   [ ] Prevent overlapping scans of the same folder.
-   [ ] Expose scan state such as Idle, Scanning, Completed, and Failed.
-   [x] Add a manual Rescan action to the UI.
-   [x] Ensure all expensive work runs off the main thread.

### Acceptance criteria

-   First scan populates the database.
-   Repeating an unchanged scan does not create duplicates.
-   Newly added files appear after rescanning.
-   Changed metadata is updated.
-   Removed files are removed after a successful complete scan.
-   Failed or incomplete scans do not wipe the library.

### Key decisions

-   `ScanCoordinator` (`data/scanner`) owns the pipeline: traversal →
    metadata extraction → Room reconciliation. It exposes
    `scanState: StateFlow<ScanState>` (Idle / Scanning / Completed /
    Failed) plus `scanFolder()` returning `ScanOutcome` (`Executed` or
    `Skipped`).
-   The work item "create or complete `MusicRepository`" is fulfilled by
    the existing `TrackRepository` / `TrackRepositoryImpl` boundary from
    Task 07; it gained `getAll()` (new DAO query) which reconciliation
    uses. A second, overlapping repository was deliberately not created.
-   Nothing is written before traversal returns `ScanResult.Success`, and
    deletions always run after the upserts. A traversal failure or a
    database exception is reported as `Failed` with the library intact,
    so a failed/incomplete scan can never wipe records.
-   A successful scan reconciles the whole library with the scan result
    (the app stores exactly one selected folder), so rows whose files
    disappeared are removed and rows under new URIs are added.
-   Change detection is `TrackEntity.isSameFile(size, modifiedAt)`:
    identical size and modified time means the row is left untouched and
    no metadata is re-read. Unreported stamps (`0`) never match, so such
    files are re-read instead of wrongly skipped.
-   Metadata extraction only runs for new or changed files. Updates keep
    the original `dateAdded` and any existing `artworkUri`. A failed read
    keeps the already-indexed row; a failed read of a *new* file stores a
    fallback row (file-name title, Unknown Artist/Album, duration 0) so
    the file stays visible.
-   Overlapping scans are prevented with `Mutex.tryLock()`: a second
    request returns `Skipped` instead of stacking behind the running one.
-   Narrow `AudioFileDiscovery` and `MetadataExtraction` interfaces are
    implemented by `MusicScanner` / `MetadataExtractor` and bound with
    `@Binds` in `ScannerModule`, so the orchestration can be tested with
    fakes against a real in-memory Room database.
-   `LibraryViewModel` derives UI state from a single `combine` of
    (selected folder + permission) and the canonical `scanState`. A scan
    is started only when no scan belongs to the selected folder, so
    ViewModel recreation or rotation no longer triggers a redundant
    rescan (the coordinator is a process-wide singleton).
-   `LibraryUiState.Ready` now carries `scanState` (with `ScanFailed`
    folded in as `ScanState.Failed`, keeping the previous result visible
    during a rescan through `lastScanResult`). The manual Rescan action
    was retained (header button, empty state, Retry on the failure
    screen), and the header now reports
    "Library updated: N new, N updated, N unchanged, N removed" after
    each completed scan.

### Validation performed

-   `./gradlew testDebugUnitTest` -> 18 tests passed (4 new
    `TrackEntityTest` cases for size/modified change detection).
-   `./gradlew assembleDebug assembleDebugAndroidTest` -> BUILD
    SUCCESSFUL (KSP/Hilt tasks executed).
-   `./gradlew connectedDebugAndroidTest` on `Medium_Phone_API_37.0`
    (API 37) -> 17 tests passed, including the 7 new
    `ScanCoordinatorTest` cases: first scan populates the database;
    unchanged rescan creates no duplicates and performs zero metadata
    reads; added/changed/removed files reconcile with `dateAdded`
    preserved; a failed traversal keeps the library and reports `Failed`;
    metadata failures keep indexed rows and add a fallback row for new
    files; a persistence failure reports `Failed` without wiping rows;
    overlapping scans are skipped.
-   Manual end-to-end run on the emulator through the real SAF picker:
    seeded `/sdcard/Music` with WAV files including a nested
    subdirectory (non-audio file ignored). First scan -> "Found 2 audio
    tracks" + "Library updated: 2 new"; repeat scan -> "0 new,
    0 updated, 2 unchanged, 0 removed"; adding a file -> "1 new";
    deleting a file -> "1 removed"; renaming the containing folder (new
    URIs) -> old rows removed, new added; making the folder unavailable
    -> "Scan Failed" screen while the database kept both rows (verified
    by pulling the DB through `run-as`); Retry after restoring the
    folder -> "2 unchanged" with the original `dateAdded`; cold restart
    after `force-stop` -> recovered without crash (logcat clean).

### Known limitations

-   Changing the selected folder and completing a scan replaces the
    tracks indexed from the previous folder; the app models exactly one
    library folder.
-   A file whose metadata read fails keeps fallback values until its
    size or modified stamp changes; per-file extraction failures are
    surfaced only through the summary counts, not stored.
-   No scan progress percentage (kept simple, per Task 05 note).

### Additional requirement — Database-first startup (mandatory)

**Goal:** Ensure the music library is loaded from Room on every subsequent app launch without automatically traversing the selected music folder.

#### Work

- [x] Persist successful scan completion for the selected folder across process death and app restarts.
- [x] Store the indexed folder URI and the last successful scan timestamp.
- [x] On app startup, load tracks directly from Room using observable Flow queries.
- [x] Do not automatically run a full filesystem scan when a successful scan already exists for the currently selected folder.
- [x] Do not treat an in-memory `ScanCoordinator` state as proof that a folder was previously indexed.
- [x] Run the full scan only when:
    - No successful scan exists for the selected folder.
    - The user explicitly presses `RESCAN`.
    - The selected folder changes and requires initial indexing.
- [x] Preserve the existing library while a manual rescan is running.
- [x] Update the persistent scan state only after a successful scan and database reconciliation.
- [x] Handle revoked folder permissions without deleting existing database records.
- [x] Keep track and album data observable so the UI updates automatically after database changes.

#### Acceptance criteria

- [x] First launch performs a full scan and saves the discovered library to Room.
- [x] Closing and reopening the app loads the existing library from Room without traversing music folders.
- [x] Force-stopping and restarting the app does not trigger an automatic rescan.
- [x] Opening Tracks or Albums does not trigger filesystem scanning or metadata extraction.
- [x] Pressing `RESCAN` performs a full folder traversal and reconciles the database.
- [x] Newly added, modified, and deleted files are reflected after a successful manual rescan.
- [x] A failed or interrupted scan does not erase the previously indexed library.

#### Out of scope

- Automatic background rescanning.
- Periodic filesystem polling.
- Real-time monitoring of file changes.
- Cloud library synchronization.
- Re-extracting metadata for unchanged files.

#### Key decisions (database-first startup)

- `ScanStatusRepository` / `ScanStatusRepositoryImpl` persist the marker in
  the same DataStore as the selected folder (`indexed_folder_uri` +
  `indexed_scan_at`). It survives process death, and an unreadable or absent
  marker resolves to `null`, so the app re-indexes instead of assuming the
  folder is current.
- `ScanCoordinator` writes the marker only after a successful traversal **and**
  a completed database reconciliation; a failed or interrupted scan never
  marks the folder as indexed.
- `LibraryViewModel.observeLibrary()` combines the selected folder (and its
  permission), the coordinator's canonical `scanState`, and the persisted
  marker. A full scan runs only when `scanState.folderUri` matches the folder
  (a scan already running/finished this session), when the persisted marker
  lacks this folder, or on an explicit `RESCAN`. The in-memory coordinator
  state alone is never treated as proof of prior indexing.
- Track rows are exposed to the UI through `TrackRepository.observeAll()`
  (`Flow`) as `LibraryViewModel.indexedTracks`, so the screen renders the Room
  library and updates automatically after scan writes without any filesystem
  traversal. The Library screen shows the Room-backed list on startup when no
  scan result exists for the current session, and validates it once Tasks 11
  (Albums) land. Album grouping is derived from track metadata, so it stays
  observable through the same Flow.
- Revoked folder access only changes the UI to `LibraryUiState.FolderAccessRevoked`;
  no records are deleted, so the library survives until access is restored.

#### Validation performed (database-first startup)

- `./gradlew testDebugUnitTest assembleDebug` -> BUILD SUCCESSFUL; 18 unit
  tests passed.
- `./gradlew connectedDebugAndroidTest` on `Medium_Phone_API_37.0` (API 37)
  -> BUILD SUCCESSFUL; 22 instrumented tests passed, including the 2 new
  `ScanStatusRepositoryImplTest` cases (`marker_survivesProcessDeath`,
  `missingOrUnreadableMarker_resolvesToNull`) and the `ScanCoordinatorTest`
  marker cases (`successfulScan_persistsIndexedFolderMarker`,
  `failedScan_neverMarksFolderAsIndexed`,
  `failedTraversalWithoutMarker_keepsLibraryUnmarked`).
- Fixed a leftover duplicate `init { observeLibrary() }` block in
  `LibraryViewModel` that collected the library state twice.

------------------------------------------------------------------------

## Task 09 --- Build the Tracks library screen

**Status:** DONE\
**Priority:** High\
**Depends on:** Task 08

### Goal

Display all indexed tracks in a usable, searchable, sortable list.

### Work

-   [x] Create a domain `Track` model and map database entities to it.
-   [x] Expose tracks from the repository as a Flow.
-   [x] Create `GetTracksUseCase` if it adds useful separation.
-   [x] Create `LibraryViewModel` with immutable UI state.
-   [x] Build a Tracks list using `LazyColumn`.
-   [x] Use stable keys based on track URI or another stable ID.
-   [x] Display title and artist; show artwork only if available and
    useful at this stage.
-   [x] Add search by title, artist, and album.
-   [x] Debounce search input by approximately 250--300 ms.
-   [x] Add sorting by title, artist, album, duration, and indexed/added
    date where available.
-   [x] Add clear empty, loading, and error states.
-   [x] Keep the visual design minimal and consistent with `AGENT.md`.

### Acceptance criteria

-   All indexed tracks appear in the list.
-   Search matches title, artist, and album.
-   Sorting changes the visible order correctly.
-   Search and sorting do not block the main thread.
-   Large lists use lazy rendering and stable keys.
-   Empty and loading states are handled.

### Notes

Playback is not part of this task. Track rows may be visually ready for
a future click action, but do not add a fake playback implementation.

### Key decisions

-   `domain/model/Track` is a plain immutable model; `TrackEntity` is
    mapped at the data boundary by `data/mapper/TrackMapper.kt`
    (`TrackEntity.toTrack()`), so Room never leaks into the presentation
    layer.
-   `GetTracksUseCase` returns `Flow<List<Track>>` from
    `TrackRepository.observeAll()`, so the list re-emits automatically
    after scan writes without touching the filesystem. The pure
    top-level `filterAndSortTracks(tracks, query, sort)` holds all
    search/sort logic (case-insensitive comparison, stable order that
    keeps Room's storage order for equal keys).
-   `TracksUiState` is a second immutable stream, separate from the
    folder/scan `LibraryUiState`: `Loading` / `Error(message)` /
    `Ready(tracks, totalTracks, query, sort)`. `buildTracksUiState()`
    combines the Room flow, the debounced query, and the sort, runs on
    `Dispatchers.Default` (`flowOn`), and converts a failing source into
    `Error` instead of crashing - Selection, sorting, and filtering
    therefore never run on the main thread.
-   Search debounce is 300 ms (`SEARCH_DEBOUNCE_MS`), applied only to a
    non-blank query, so the first library render is immediate. Sort
    changes bypass the debounce and apply on the next emission.
-   `LibraryViewModel` gained `tracksUiState`, `searchQuery`,
    `onSearchQueryChange()`, `onSortChange()`, and `retryTracks()`; the
    tracks job is restartable so `Error` + Retry re-subscribes cleanly
    instead of leaving a dead stream.
-   `LibraryScreen` renders search field + sort dropdown above a
    `LazyColumn` with `key = { it.uri }` (the stable primary key), a
    "N of M tracks" count line, and dedicated states: `Loading library...`,
    `Couldn't Load Tracks` + Retry, `No Tracks Found` + Rescan Folder
    (empty library, with the scanning variant while a first scan runs),
    and `No Matches` + Clear Search (filter yields nothing).
-   `TrackItem` shows title, artist, and `m:ss` duration. Artwork is a
    muted accent tile with the track's initial; real artwork arrives in
    Task 19 through `Track.artworkUri`. Rows are intentionally not
    clickable, per the task note (playback is Task 10).
-   Track sort orders are the `TrackSort` enum (Title, Artist, Album,
    Duration, Date added), defaulting to Title; text comparators use
    `CASE_INSENSITIVE_ORDER`, duration/date sorts are ascending.

### Validation performed

-   `./gradlew testDebugUnitTest` -> BUILD SUCCESSFUL, 35 unit tests
    passed: the 13 new `GetTracksUseCaseTest` cases (match across
    title/artist/album, case-insensitivity, trimming, no-match result,
    all five sort orders, filter-then-sort) and the 4 new
    `TracksUiStateTest` cases (initial list renders without waiting for
    the debounce, rapid keystrokes debounce and settle on the final
    query, sort changes apply immediately, a failing source becomes
    `Error`), plus the 18 pre-existing tests.
-   `./gradlew assembleDebug` -> BUILD SUCCESSFUL.
-   `./gradlew connectedDebugAndroidTest` on `Medium_Phone_API_37.0`
    (API 37) -> BUILD SUCCESSFUL, 44 instrumented tests, 0 failures.
-   Manual run on `Medium_Phone_API_37.0`: granted `/sdcard/Music`
    through the SAF picker -> "Found 5 audio tracks" and 5 rows listed,
    sorted by title with `key = uri`. Searching `mid` -> "1 of 5
    tracks" with only *Midnight City*; searching `zzz` -> "0 of 5
    tracks" + the `No Matches` / `Clear Search` state; clearing restores
    all 5. Sorting by Duration reordered the list to 0:01, 0:02, 0:03,
    0:04, 0:05. `am force-stop` + relaunch -> "Library loaded from Room -
    5 tracks indexed" with zero scan activity in logcat (no
    filesystem traversal, no rescan).

### Known limitations

-   Artist/album/location metadata of the seeded test WAVs is
    "Unknown Artist/Album", so Album and Artist sorting were verified
    by unit tests rather than visually.
-   Search results count is shown as "N of M tracks"; a dedicated
    album grouping surface arrives in Task 11.

------------------------------------------------------------------------

## Task 10 --- Implement the Media3 playback service and basic playback

**Status:** TODO\
**Priority:** Critical\
**Depends on:** Task 09

### Goal

Play a selected local track through a single service-owned ExoPlayer
instance.

### Work

-   [ ] Create `PlaybackService` extending `MediaSessionService`.
-   [ ] Create exactly one ExoPlayer instance in the service.
-   [ ] Create and expose a MediaSession.
-   [ ] Add the required service and foreground-service declarations to
    `AndroidManifest.xml`, following current Media3 and Android
    guidance.
-   [ ] Implement MediaController connection from the app.
-   [ ] Create a small playback manager/controller API for play, pause,
    seek, next, and previous.
-   [ ] Convert a selected library track into a Media3 media item using
    its content URI and metadata.
-   [ ] Start playback when the user selects a track.
-   [ ] Publish current track, is-playing, duration, and position as
    observable playback state.
-   [ ] Handle unavailable or unreadable media URIs gracefully.
-   [ ] Verify playback continues when the activity is backgrounded and
    the screen is turned off.
-   [ ] Verify system media controls/notification behavior through
    MediaSession.

### Acceptance criteria

-   Selecting a track starts playback.
-   Play/pause and seek work.
-   The app does not create a second ExoPlayer in a screen or ViewModel.
-   Playback continues when the app is backgrounded.
-   The service exposes a working MediaSession.
-   Unreadable media is handled without crashing the app.
-   The app builds and relevant playback checks are documented.

### Notes

Do not implement the full queue screen, shuffle UI, Mini Player, or Now
Playing design in this task. Those are subsequent tasks. Keep the
service/controller API ready for playlist support.

------------------------------------------------------------------------

## After the first ten tasks

The next planned tasks are:

11. Implement album grouping, album grid, and album detail.
12. Add the playback queue and playlist-based track selection.
13. Add "Add to queue" and queue reordering.
14. Add shuffle using ExoPlayer shuffle mode.
15. Build the persistent Mini Player.
16. Build the full Now Playing screen.
17. Build the Queue screen.
18. Polish background playback, audio focus, interruptions, and
    notification behavior.
19. Add artwork caching and performance improvements.
20. Test large libraries and a range of Android versions; prepare a
    release build.

Do not start these later tasks until the first ten are complete and the
project owner approves the next implementation step.
