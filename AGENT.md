# AGENT.md

## 1. Project Overview

**Project name:** Music Player\
**Platform:** Android\
**Primary language:** Kotlin\
**UI toolkit:** Jetpack Compose with Material 3\
**Minimum Android version:** Android 8.0 (API 26)\
**Compile / target SDK:** Use the current project configuration; the
initial target is API 36 where supported by the installed Android Gradle
Plugin and SDK.

Build a small, reliable, offline-first music player for locally stored
audio files. The app should focus on browsing a user-selected music
folder, viewing albums and tracks, and playing music with a simple
queue.

The product should feel calm and minimal. Do not add features simply
because they are common in other music players.

## 2. Product Principles

1.  **Local-first:** Music is read from folders selected by the user. No
    account, cloud library, or online streaming is required.
2.  **Minimal scope:** Implement only the features listed in this
    document and explicitly requested project tasks.
3.  **Reliable playback:** Playback must continue when the app is
    backgrounded or the screen is off.
4.  **Clear state ownership:** The playback service owns the player. UI
    components observe state and send commands; they must not create
    their own players.
5.  **Responsive UI:** Never scan folders, extract metadata, decode
    artwork, or perform database work on the main thread.
6.  **Graceful failure:** Missing files, unsupported formats,
    inaccessible folders, and absent artwork must not crash the app.
7.  **Incremental development:** Prefer small, compiling changes over
    large rewrites.

## 3. Required Features

Implement these features and no unrelated feature set:

### 3.1 Music folder access and scanning

-   Let the user select a folder using Android's Storage Access
    Framework (SAF).
-   Use the document-tree picker (`ACTION_OPEN_DOCUMENT_TREE` or the
    Activity Result API equivalent).
-   Persist read access using `takePersistableUriPermission` when the
    provider grants it.
-   Scan the selected folder recursively where the document provider
    supports traversal.
-   Discover supported local audio files, initially including:
    -   MP3
    -   FLAC
    -   WAV
    -   M4A
    -   AAC
    -   OGG
    -   OPUS
-   Treat actual decoding support as dependent on Android and the
    available Media3 decoders.
-   Extract title, artist, album, duration, and available artwork.
-   Store the indexed library in Room.
-   Support rescanning so newly added or changed tracks appear and
    removed tracks are no longer shown.
-   Provide a manual rescan action. Do not continuously poll the
    filesystem.
-   Handle revoked permissions and unavailable document providers with a
    clear message and a way to select the folder again.

### 3.2 Library

-   Provide an Albums view with album artwork and album names.
-   Provide a Tracks view containing all indexed tracks.
-   Support search by track title, artist, and album.
-   Support sorting tracks by:
    -   Title
    -   Artist
    -   Album
    -   Duration
    -   Date added or indexed, if available
-   Use lazy Compose lists and grids with stable item keys.
-   Avoid loading every artwork image into memory at once.

### 3.3 Playback

-   Use AndroidX Media3 ExoPlayer.
-   Host one ExoPlayer instance in a `MediaSessionService`.
-   Expose playback through a `MediaSession` and connect the UI using a
    `MediaController`.
-   Support:
    -   Play and pause
    -   Next and previous
    -   Seek / scrubbing
    -   Sequential playback
    -   Shuffle playback
    -   Background playback
    -   System media controls and media notification
-   Previous-button behavior:
    -   If the current track has played for more than approximately 3
        seconds, restart it.
    -   Otherwise, move to the previous item when one exists.
-   Handle audio focus changes, headphone disconnection, and
    interruptions using Media3/platform-supported behavior.
-   Do not autoplay when the app starts unless a future requirement
    explicitly asks for it.

### 3.4 Queue

-   Keep the playback queue separate from the library.
-   Selecting a track in an album should start playback with that
    album's track list as the queue.
-   Selecting a track in the Tracks view should start playback using the
    currently displayed, sorted/filtered track list as the queue.
-   "Add to queue" should append a track without interrupting the
    current track.
-   Support moving queue items.
-   Keep the queue UI synchronized with the actual ExoPlayer playlist
    and shuffle order.
-   Use ExoPlayer playlist APIs rather than maintaining a second,
    conflicting playback queue.
-   Shuffle should use ExoPlayer's shuffle mode. Do not manually shuffle
    the Compose list as a substitute for player shuffle state.

### 3.5 Player UI

-   Show a persistent Mini Player at the bottom of the main app screens.
-   The Mini Player should show artwork, track title, artist, and
    play/pause control. Tapping it opens Now Playing.
-   Provide a Now Playing screen with:
    -   Artwork
    -   Track title
    -   Artist
    -   Seek bar and elapsed/total time
    -   Previous, play/pause, and next controls
    -   Sequential/shuffle control
    -   Queue button near the lower-right area
-   Provide a Queue screen with reorder support.
-   Keep the UI uncluttered and consistent.

## 4. Explicitly Out of Scope

Do not implement these unless the project owner explicitly changes
scope: - Online streaming or radio - User accounts, cloud sync, or
social features - Recommendations or discovery feeds - Lyrics -
Equalizer or audio effects - Artist, genre, or mood sections - Complex
playlist management - Podcast features - Downloads from online
services - Ads, analytics, or telemetry - Multiple themes or elaborate
personalization settings - Unrequested settings screens or decorative
features

## 5. Technology Stack

Use the following stack unless a compatibility issue requires a
documented change:

-   Kotlin
-   Jetpack Compose
-   Material 3
-   AndroidX Media3 ExoPlayer
-   Media3 MediaSession / MediaSessionService
-   Room
-   Kotlin Coroutines and Flow
-   Hilt for dependency injection
-   Navigation Compose
-   Coil 3 for artwork loading
-   DataStore for small preferences such as selected folder URI and
    simple settings
-   Android Storage Access Framework for folder access

Use stable, mutually compatible dependency versions. Prefer the
project's Version Catalog (`gradle/libs.versions.toml`) for dependency
and plugin versions. Do not invent versions or mix incompatible major
versions. Check official AndroidX/Media3 documentation when a version or
API is uncertain.

## 6. Architecture

Keep the project in a single `app` Gradle module initially. Use clear
package boundaries rather than creating many Gradle modules.

Suggested package structure:

``` text
com.example.musicplayer/
  data/
    database/
      AppDatabase.kt
      TrackDao.kt
      MusicFolderDao.kt
    entity/
      TrackEntity.kt
      MusicFolderEntity.kt
    scanner/
      MusicScanner.kt
      MetadataExtractor.kt
      ScanResult.kt
    repository/
      MusicRepositoryImpl.kt

  domain/
    model/
      Track.kt
      Album.kt
    repository/
      MusicRepository.kt
    usecase/
      GetTracksUseCase.kt
      GetAlbumsUseCase.kt
      SearchTracksUseCase.kt
      ScanMusicUseCase.kt

  player/
    PlaybackService.kt
    PlayerManager.kt
    PlayerController.kt
    PlaybackState.kt

  presentation/
    library/
      LibraryScreen.kt
      LibraryViewModel.kt
      components/
    album/
      AlbumScreen.kt
      AlbumViewModel.kt
    player/
      NowPlayingScreen.kt
      PlayerViewModel.kt
      components/
    queue/
      QueueScreen.kt
      QueueViewModel.kt
    folders/
      FolderSelectionScreen.kt
    components/
      MiniPlayer.kt
      TrackItem.kt
      AlbumCard.kt
      ArtworkImage.kt

  navigation/
    AppNavigation.kt
    Routes.kt

  di/
    DatabaseModule.kt
    RepositoryModule.kt
    PlayerModule.kt

  ui/
    theme/

  MainActivity.kt
  MusicPlayerApp.kt
```

Adapt filenames when useful, but keep responsibilities clear.

### 6.1 Layer responsibilities

**Presentation** - Compose screens and reusable UI components. -
ViewModels expose immutable UI state using `StateFlow`. - UI sends user
actions to ViewModels or a playback controller. - Composables must not
access Room DAOs or instantiate ExoPlayer.

**Domain** - Plain Kotlin models and repository interfaces. - Use cases
for meaningful operations such as scanning, retrieving albums, and
retrieving tracks. - Avoid adding use-case classes that only wrap a
single trivial call without adding value.

**Data** - Room entities and DAOs. - SAF folder traversal and metadata
extraction. - Repository implementations that map entities to domain
models. - File and permission error handling.

**Playback** - `MediaSessionService` owns the single ExoPlayer and
MediaSession. - A controller/manager connects the UI to the service
through MediaController. - Publish playback state as observable flows. -
Do not expose the raw ExoPlayer instance to Compose screens.

## 7. Data Model Guidance

A practical initial `TrackEntity` may contain:

-   `uri` --- stable document/content URI; primary key where appropriate
-   `title`
-   `artist`
-   `album`
-   `duration`
-   `albumId` --- optional metadata identifier
-   `artworkUri` --- optional artwork reference or local cached-artwork
    reference
-   `folderUri`
-   `fileSize`
-   `modifiedTime`
-   `dateAdded` or indexed timestamp, if needed for sorting

A `MusicFolderEntity` may contain:

-   `uri`
-   `displayName`
-   `dateAdded`

Start by grouping albums from track metadata, using `albumId` when
reliable and a normalized album/artist fallback when it is not. Do not
create a separate album table until a real requirement justifies it.

Use Room migrations when the schema changes. Do not use destructive
migrations for normal development once user data needs to be preserved.

## 8. Scanning and Metadata Rules

-   Run traversal and metadata extraction off the main thread, using an
    IO dispatcher.
-   Use SAF `DocumentFile` or the appropriate document-provider APIs for
    tree traversal.
-   Do not assume every provider supports identical filesystem behavior
    or efficient recursive listing.
-   Use `MediaMetadataRetriever` or a suitable supported API for
    metadata and embedded artwork.
-   Release metadata retrievers and other resources reliably.
-   Do not decode full-size artwork unnecessarily. Prefer bounded image
    sizes and cached thumbnails.
-   Avoid duplicate tracks when rescanning.
-   Compare stable URI and available modification metadata/file size to
    decide whether a record needs updating.
-   Remove database entries that are no longer present in a completed
    scan of an accessible folder.
-   Do not delete indexed records merely because a scan failed or
    permission was lost. Distinguish a completed scan from a
    failed/incomplete scan.
-   Report scan progress only if it materially improves the user
    experience; keep the initial implementation simple.

## 9. Playback State and Queue Rules

Keep distinct observable state streams where practical:

-   **Library state:** albums, tracks, search, sorting, scan status.
-   **Playback state:** current item, is-playing, duration, position,
    shuffle state, repeat state if used.
-   **Queue state:** playlist items, current item index, and order
    exposed by the player.

Do not force the whole library screen to recompose every second because
playback position changes. Update the seek position at a sensible
interval and collect only the state each component needs.

Use Media3 playlist operations such as setting, adding, moving, and
seeking to media items. Make sure queue reordering updates the player
playlist, not just a local UI list.

## 10. UI and Visual Direction

-   Dark, near-black foundation.
-   One restrained accent color.
-   Strong, readable typography.
-   Album artwork is the primary visual element.
-   Minimal cards, borders, shadows, and decoration.
-   Moderate corner radii.
-   Subtle animations only when they clarify a state change.
-   Consistent spacing and touch targets.
-   Use Material 3 components where appropriate, but customize them to
    maintain the minimal visual direction.
-   Support loading, empty, permission-denied, and error states.
-   Do not add bottom navigation destinations or screens that are not
    required.

## 11. Navigation and Screen Structure

Initial screens:

1.  Folder selection / first-run state.
2.  Library with Albums and Tracks sections.
3.  Album detail.
4.  Now Playing.
5.  Queue.

Search and sorting belong within the Library experience; they do not
need separate navigation destinations.

Place the Mini Player in a shared app-level scaffold so it appears
consistently on the main library and album screens. Avoid duplicating it
in every screen. Decide deliberately whether it should be hidden on Now
Playing and Queue screens.

## 12. Android Platform Requirements

-   Declare only the permissions and service types actually needed.
-   Use the correct foreground service configuration for media playback
    and the applicable Media3 service declarations.
-   Ensure the MediaSessionService can continue playback while the
    activity is stopped.
-   Use Android's media notification and system media controls through
    MediaSession.
-   Test behavior on Android 8.0 (API 26) and a current Android release.
-   Check current Android requirements for foreground services,
    notifications, and media permissions before release; do not copy
    outdated manifest snippets blindly.
-   Use SAF-granted URI access rather than requesting broad storage
    access without a clear need.

## 13. Engineering Standards

-   Keep Kotlin code idiomatic and straightforward.
-   Prefer immutable UI state and unidirectional data flow.
-   Use structured concurrency; do not launch unmanaged global
    coroutines.
-   Cancel or supersede obsolete scans/searches where appropriate.
-   Handle expected failures explicitly; do not silently swallow
    exceptions.
-   Do not use `!!` unless a non-null invariant is genuinely guaranteed
    and documented.
-   Keep functions focused and avoid premature abstraction.
-   Add comments for non-obvious platform behavior, not for
    self-explanatory code.
-   Avoid speculative frameworks and unnecessary dependencies.
-   Do not introduce a new dependency without explaining why it is
    needed.
-   Keep secrets, signing credentials, and local machine paths out of
    source control.

## 14. Testing and Acceptance

Test at least:

-   First launch with no folder selected.
-   Granting and persisting folder access.
-   Revoking folder access.
-   Empty folder.
-   Folder with nested directories.
-   Duplicate or malformed metadata.
-   Missing artwork.
-   Unsupported or corrupt audio files.
-   New, modified, and deleted files after a rescan.
-   Search and sorting.
-   Starting playback from an album and from the Tracks list.
-   Add to queue without interrupting playback.
-   Queue reorder.
-   Shuffle and next/previous behavior.
-   Background playback, screen off, notification controls, and
    headphone disconnection.
-   Large libraries (target test: 5,000--10,000 tracks).

Prefer unit tests for pure logic and repository/scanner behavior where
practical, plus targeted instrumented tests for Android-specific SAF and
playback integration.

## 15. Agent Workflow and Rules

When working on this repository:

1. Read this file and `TASKS.md` before making changes.
2.  Inspect the existing project before assuming files, package names,
    dependency versions, or build configuration.
3.  Identify the smallest task that satisfies the request.
4.  Implement one task at a time. Do not silently implement future
    tasks.
5.  Keep the project compiling after each meaningful change.
6.  Use existing conventions and dependencies before introducing
    alternatives.
7.  Do not overwrite user changes or replace project files wholesale
    without checking their contents.
8.  Do not fabricate successful build or test results. Run the relevant
    Gradle tasks when tools are available and report exactly what passed
    or failed.
9.  If a requirement is ambiguous, choose the simplest behavior
    consistent with this document. Ask a question only when the
    ambiguity blocks implementation or could cause data loss.
10. Update `TASKS.md` when a task is completed, blocked, or split into
    smaller tasks.
11. Report changed files, key implementation decisions, validation
    performed, and known limitations.
12. Do not expand product scope without explicit approval.

## 16. Definition of Done

A task is complete only when:

-   Its acceptance criteria are met.
-   The implementation follows the architecture and scope in this
    document.
-   Relevant build and tests have been run, or the inability to run them
    is clearly stated.
-   Expected error and empty states are handled.
-   No unrelated features or dependencies were added.
- `TASKS.md` reflects the task's actual status.
