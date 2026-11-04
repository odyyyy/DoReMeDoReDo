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

**Status:** TODO\
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
-   [ ] Add a manual Rescan action to the UI.
-   [ ] Ensure all expensive work runs off the main thread.

### Acceptance criteria

-   First scan populates the database.
-   Repeating an unchanged scan does not create duplicates.
-   Newly added files appear after rescanning.
-   Changed metadata is updated.
-   Removed files are removed after a successful complete scan.
-   Failed or incomplete scans do not wipe the library.

------------------------------------------------------------------------

## Task 09 --- Build the Tracks library screen

**Status:** TODO\
**Priority:** High\
**Depends on:** Task 08

### Goal

Display all indexed tracks in a usable, searchable, sortable list.

### Work

-   [ ] Create a domain `Track` model and map database entities to it.
-   [ ] Expose tracks from the repository as a Flow.
-   [ ] Create `GetTracksUseCase` if it adds useful separation.
-   [ ] Create `LibraryViewModel` with immutable UI state.
-   [ ] Build a Tracks list using `LazyColumn`.
-   [ ] Use stable keys based on track URI or another stable ID.
-   [ ] Display title and artist; show artwork only if available and
    useful at this stage.
-   [ ] Add search by title, artist, and album.
-   [ ] Debounce search input by approximately 250--300 ms.
-   [ ] Add sorting by title, artist, album, duration, and indexed/added
    date where available.
-   [ ] Add clear empty, loading, and error states.
-   [ ] Keep the visual design minimal and consistent with `AGENT.md`.

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
