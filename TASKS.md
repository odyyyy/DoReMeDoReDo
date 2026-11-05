## Project: Minimal Music Player

This file tracks the next ten implementation tasks for the Android music player.
Tasks 01–10 are summarized below as completed work. Tasks 11–20 are the new
implementation roadmap.

**Status legend:** `TODO` · `IN PROGRESS` · `BLOCKED` · `DONE`

---

## Completed work — Tasks 01–10

**Status:** DONE

The initial project foundation has been completed:

- Created and verified a Kotlin Android project using Jetpack Compose and Material 3.
- Configured Gradle, the Version Catalog, and core libraries: Navigation, Lifecycle, Coroutines, Room, Media3, Coil, Hilt, DataStore, and DocumentFile.
- Established the package structure, application entry point, theme, and basic library screen.
- Prepared the project foundation for music-folder selection, file scanning, metadata extraction, Room-based library storage, track browsing, and Media3 playback.

> Note: Tasks 01–10 are marked as completed at the project owner's request. This summary does not claim that their original acceptance criteria have been independently re-tested in this revision.

---

# Next implementation tasks

## Task 11 — Implement album grouping and album screens

**Status:** DONE  
**Priority:** High  
**Depends on:** Tasks 06–09

### Goal

Group indexed tracks into albums and provide an album library screen and an individual album details screen.

### Work

- [x] Define a stable grouping rule: use an album ID when available; otherwise use normalized album title and artist.
- [x] Avoid merging albums by different artists solely because they share the same title.
- [x] Create a domain `Album` model containing title, artist, track count, and an optional artwork reference.
- [x] Add repository queries for retrieving albums and the tracks belonging to an album.
- [x] Create an `AlbumsViewModel` with immutable UI state.
- [x] Build an Albums screen using an adaptive `LazyVerticalGrid`.
- [x] Build an Album Details screen showing the album's tracks in a predictable order.
- [x] Use stable item keys.
- [x] Handle missing album titles, unknown artists, and unavailable artwork.
- [x] Add navigation between Library, Albums, and Album Details.

### Out of scope

- Creating a separate Room `Album` table unless implementation evidence demonstrates it is necessary.
- Editing album tags or writing metadata back to audio files.
- Online album lookup, music recognition, or remote artwork downloads.
- Artist discography pages or artist-specific browsing.
- Playlist creation or playback-queue management.

### Acceptance criteria

- Albums are grouped consistently without obvious cross-artist merging.
- Track counts match the tracks assigned to each album.
- Opening an album displays only its associated tracks.
- Missing or incomplete metadata does not crash the app.
- The project builds and relevant available tests pass.

### Key decisions

- Domain `Album` model created with `id`, `title`, `artist`, `trackCount`, and `artworkUri`.
- `GetAlbumsUseCase` groups domain tracks by `albumId` (when present) or fallback normalized key (`"$normTitle|$normArtist"`), preventing cross-artist album merges.
- `GetAlbumDetailUseCase` filters and maps tracks belonging to a given album ID.
- Added `AlbumsSection` with an adaptive `LazyVerticalGrid` (`GridCells.Adaptive(minSize = 150.dp)`) and `AlbumCard` component in `LibraryScreen`.
- Created `AlbumScreen` and `AlbumViewModel` for album details with album metadata header, "Play Album" action button, track list, and navigation support. Tapping an album track starts playback with the album's track list as the queue.
- Navigation routes extended with `Routes.ALBUM_DETAIL` (`"album_detail/{albumId}"`) with URI encoding for argument safety.

### Validation performed

- `./gradlew testDebugUnitTest` -> 42 passed (including 7 new unit tests in `GetAlbumsUseCaseTest` and `GetAlbumDetailUseCaseTest`).
- `./gradlew assembleDebug` -> BUILD SUCCESSFUL.

### Notes

Initially, albums may be derived from aggregate queries over `TrackEntity`. Do not add database complexity without a demonstrated requirement.

---

## Task 12 — Implement the playback queue and playlist-based track selection

**Status:** TODO  
**Priority:** Critical  
**Depends on:** Tasks 09–10

### Goal

Introduce a playback queue so that a selected track plays in the context of a list and Next/Previous navigation behaves consistently.

### Work

- [ ] Define the queue item model and the rules for creating a queue.
- [ ] Implement controller APIs for setting a Media3 playlist and its initial index.
- [ ] When a track is selected from the main Library, create a queue from the current displayed track collection.
- [ ] When a track is selected from Album Details, create a queue from that album's tracks.
- [ ] Pass the queue to the single ExoPlayer instance owned by `PlaybackService`.
- [ ] Keep the current item index synchronized with Media3.
- [ ] Implement Next and Previous behavior, including queue boundaries.
- [ ] Handle deleted, unavailable, and unreadable content URIs.
- [ ] Expose the current item and queue state through observable playback state.
- [ ] Ensure that no ExoPlayer instance is created in a ViewModel or Compose screen.

### Out of scope

- Queue editing, drag-and-drop reordering, and removing individual queue items.
- Shuffle and repeat modes.
- Persistent queue restoration after process death, unless required to keep the basic service behavior correct.
- Playlist creation, saving, importing, or exporting.
- Crossfade, gapless-playback customization, and advanced playback effects.

### Acceptance criteria

- Starting a track from Library or Album Details creates the expected queue.
- Next and Previous navigate through the queue correctly.
- The current track and index remain synchronized between the service and UI.
- An unavailable file is handled without crashing the app.
- The app uses exactly one ExoPlayer instance, owned by `PlaybackService`.

---

## Task 13 — Add queue management and reordering

**Status:** TODO  
**Priority:** High  
**Depends on:** Task 12

### Goal

Allow users to add tracks to the queue, inspect the queue, and change its playback order.

### Work

- [ ] Add an Add to Queue action for tracks.
- [ ] Define and document the default behavior: newly added tracks go to the end of the queue.
- [ ] Add a Play Next action if it can be implemented without complicating the basic queue model.
- [ ] Create a Queue screen listing queued tracks and highlighting the current track.
- [ ] Implement drag-and-drop reordering.
- [ ] Apply reordering to Media3 without losing the current playback position.
- [ ] Add the ability to remove an individual queue item.
- [ ] Handle removal of the current item consistently with Media3 behavior.
- [ ] Provide an empty-queue state.
- [ ] Keep the UI synchronized when queue changes originate from `PlaybackService`.

### Out of scope

- Shuffle and repeat modes.
- Saving multiple named queues or playlists.
- Queue history and recently played history.
- Smart queues, recommendations, or automatic track selection.
- Queue synchronization across devices.

### Acceptance criteria

- A track can be added to the end of the queue.
- The displayed order matches the actual playback order.
- Removing an item does not desynchronize the current item index.
- The UI correctly displays an empty queue and the current item.
- Queue changes are reflected by every screen observing playback state.

---

## Task 14 — Implement shuffle and repeat modes

**Status:** TODO  
**Priority:** Medium  
**Depends on:** Task 12

### Goal

Add shuffle and repeat modes using Media3's playback capabilities.

### Work

- [ ] Connect ExoPlayer shuffle mode to the playback controller API.
- [ ] Add a Shuffle toggle to the player UI.
- [ ] Implement repeat modes: Off, Repeat All, and Repeat One.
- [ ] Synchronize the selected modes with Media3.
- [ ] Publish the active modes as part of observable playback UI state.
- [ ] Persist mode preferences across app launches if consistent with the intended UX.
- [ ] Verify behavior when the queue changes while shuffle is enabled.
- [ ] Verify Next and Previous behavior while shuffle is enabled.
- [ ] Provide accessible state descriptions for the controls.

### Out of scope

- Custom shuffle algorithms or weighted/randomized recommendations.
- Smart or context-aware playback.
- Per-playlist shuffle preferences.
- Crossfade and other audio transitions.
- A full redesign of the Now Playing screen.

### Acceptance criteria

- Shuffle changes the selection order using Media3's shuffle functionality.
- Repeat One repeats the current track.
- Repeat All continues playback after the last queue item.
- Repeat Off ends the queue after the last item.
- UI icons and state always reflect the actual ExoPlayer state.

---

## Task 15 — Build the persistent Mini Player

**Status:** TODO  
**Priority:** High  
**Depends on:** Tasks 10 and 12

### Goal

Add a compact playback panel that remains available across the app's main screens.

### Work

- [ ] Place the Mini Player in the app-level Scaffold.
- [ ] Display album artwork or a neutral placeholder.
- [ ] Display the track title and artist.
- [ ] Add Play/Pause control.
- [ ] Add a Next control when another queue item is available.
- [ ] Open Now Playing when the main panel area is tapped.
- [ ] Display loading and playback-error states.
- [ ] Observe the shared playback state without creating another player instance.
- [ ] Account for system insets, bottom navigation, and compact screen sizes.
- [ ] Ensure the Mini Player does not cover the main screen content.

### Out of scope

- Full playback controls, seek bar, and advanced controls; these belong on Now Playing.
- Queue editing or reordering.
- Lock-screen and notification UI customization.
- Floating-window or picture-in-picture playback.
- Audio visualizers or animated artwork effects.

### Acceptance criteria

- The Mini Player is visible on the main app screens when a current track exists.
- Track title, artist, and Play/Pause state update without restarting the screen.
- Controls operate the same ExoPlayer instance in `PlaybackService`.
- The panel is not shown as an active player before playback has started.
- Main screen content remains accessible and is not obscured.

---

## Task 16 — Build the Now Playing screen

**Status:** TODO  
**Priority:** High  
**Depends on:** Tasks 10, 12, and 15

### Goal

Create the main screen for controlling the currently playing track.

### Work

- [ ] Create the Now Playing screen and connect navigation from the Mini Player.
- [ ] Display large album artwork with a placeholder when artwork is unavailable.
- [ ] Display the track title, artist, and album.
- [ ] Implement Play/Pause, Next, and Previous.
- [ ] Implement a seek bar with current position and track duration.
- [ ] Update playback position smoothly without causing excessive recomposition.
- [ ] Add Shuffle and Repeat controls.
- [ ] Add navigation to the Queue screen.
- [ ] Display clear loading, error, and playback-ended states.
- [ ] Check control accessibility and contrast.

### Out of scope

- Lyrics fetching, synchronized lyrics, or karaoke display.
- Equalizer, DSP, audio effects, or sound enhancement.
- Editing track metadata.
- Online artwork or metadata lookup.
- Visualizer animations and complex motion design.
- Queue editing beyond navigation to the Queue screen.

### Acceptance criteria

- All controls operate through `PlaybackService`.
- Seeking changes the playback position.
- Position and duration are displayed correctly.
- Shuffle and Repeat UI states are synchronized with Media3.
- The screen handles track changes and missing artwork correctly.

---

## Task 17 — Complete the Queue screen UX

**Status:** TODO  
**Priority:** Medium  
**Depends on:** Tasks 12, 13, and 16

### Goal

Complete the user experience for viewing and interacting with the playback queue.

### Work

- [ ] Add navigation to Queue from Now Playing.
- [ ] Display the current track and the remaining queue items.
- [ ] Visually distinguish the current track from other items.
- [ ] Show artist names and available artwork thumbnails.
- [ ] Allow users to select another queue item for immediate playback.
- [ ] Support item removal and reordering through the queue functionality from Task 13.
- [ ] Scroll to the current track when the screen opens.
- [ ] Preserve the queue during ordinary navigation between screens.
- [ ] Add an empty state with a clear action to return to the Library.
- [ ] Verify UI behavior during rapid track changes.

### Out of scope

- Creating, saving, or managing named playlists.
- Queue history or playback history.
- Recommendations or automatic queue generation.
- Sharing or exporting a queue.
- Reimplementing queue operations already owned by the playback controller.

### Acceptance criteria

- Queue screen displays the actual Media3 queue.
- The current item is clearly highlighted.
- Selecting an item starts it and updates the screen state.
- Removing and reordering items do not cause state desynchronization.
- The screen remains synchronized when queue changes originate elsewhere.

---

## Task 18 — Harden background playback, audio focus, and interruptions

**Status:** TODO  
**Priority:** Critical  
**Depends on:** Tasks 10, 12, and 16

### Goal

Ensure reliable background playback and correct handling of system audio events.

### Work

- [ ] Verify audio focus configuration against current Android and Media3 guidance.
- [ ] Test behavior during incoming calls and other audio-focus interruptions.
- [ ] Test wired and Bluetooth headset connection and disconnection.
- [ ] Handle audio-focus loss appropriately: pause, duck, or resume according to the event type and platform behavior.
- [ ] Verify the foreground service notification and its Play/Pause/Next actions.
- [ ] Test MediaSession recovery after process recreation in supported scenarios.
- [ ] Verify playback while the screen is locked and the app is in the background.
- [ ] Handle decoder errors and files that become unavailable during playback.
- [ ] Verify foreground-service declarations and permissions for supported Android versions.
- [ ] Document limitations around restoring queue and playback state after process termination.

### Out of scope

- Cloud playback or streaming services.
- Casting to external devices.
- Android Auto or other vehicle integrations.
- Wear OS controls.
- Advanced Bluetooth device management beyond standard Android audio behavior.
- Guaranteeing playback restoration after force-stop; Android may prevent automatic restart.

### Acceptance criteria

- Playback continues in the background in scenarios supported by Android.
- Audio focus and interruptions are handled without unexpected simultaneous playback.
- System notification and MediaSession controls work correctly.
- Headset disconnection and media unavailability are handled safely.
- Tests are performed on target Android versions and results are documented.

---

## Task 19 — Add artwork caching and performance improvements

**Status:** TODO  
**Priority:** Medium  
**Depends on:** Tasks 06, 09, 11, 15, and 16

### Goal

Reduce repeated artwork processing and improve UI responsiveness with large music libraries.

### Work

- [ ] Review the current artwork extraction and display pipeline.
- [ ] Choose a storage strategy for thumbnails and larger artwork images.
- [ ] Use Coil memory and disk caching where appropriate.
- [ ] Avoid decoding full-resolution images for small list items.
- [ ] Limit the dimensions of extracted and stored thumbnails.
- [ ] Ensure stale cached artwork can be invalidated and removed.
- [ ] Verify cache keys remain valid when source files change.
- [ ] Check `LazyColumn` and `LazyVerticalGrid` scrolling performance.
- [ ] Remove unnecessary Room queries and repeated model transformations.
- [ ] Measure screen-opening and scrolling performance with a large test library.

### Out of scope

- Downloading artwork from online services.
- Face recognition, image analysis, or automatic artwork generation.
- A user-facing artwork editor.
- A general-purpose image cache unrelated to music artwork.
- Premature optimization without a measured performance issue.

### Acceptance criteria

- Reopening screens does not trigger unnecessary artwork extraction.
- Missing or corrupt artwork does not crash the app.
- Memory use remains reasonable when scrolling large lists.
- Cached artwork is refreshed when the source file or its relevant metadata changes.
- Basic performance measurements are documented.

---

## Task 20 — Validate large libraries, compatibility, and the release build

**Status:** TODO  
**Priority:** Critical  
**Depends on:** Tasks 11–19

### Goal

Validate the app with large music libraries and target Android versions, fix critical defects, and prepare a release build.

### Work

- [ ] Prepare test datasets: a small library, a large library, nested folders, incomplete metadata, and corrupt files.
- [ ] Test scanning, rescanning, and recovery after access errors.
- [ ] Test search, sorting, album grouping, and screen navigation.
- [ ] Test queue behavior, shuffle, repeat, seeking, and track transitions.
- [ ] Test background playback, MediaSession, and notification controls.
- [ ] Test on the minimum supported API 26 and a current target Android API.
- [ ] Add or complete unit tests for domain logic and repositories.
- [ ] Add Room and critical scanning integration tests where the test infrastructure allows.
- [ ] Fix crashes, resource leaks, ANRs, and critical performance problems.
- [ ] Verify runtime permissions, SAF access, and behavior after permission revocation.
- [ ] Review release configuration, R8/shrinker behavior, and required keep rules.
- [ ] Build a release APK/AAB and verify installation on a clean device.
- [ ] Prepare a concise release checklist and a list of known limitations.

### Out of scope

- Publishing the app to Google Play.
- Creating store listing assets, screenshots, marketing copy, or a website.
- Setting up paid services, analytics, crash-reporting backends, or accounts unless separately approved.
- Adding new product features during the stabilization phase.
- Guaranteeing compatibility with every Android device or third-party document provider.

### Acceptance criteria

- Core user scenarios pass with the prepared test libraries.
- No known blocking crash or ANR remains in tested scenarios.
- The app works on API 26 and the selected current Android version.
- `assembleDebug`, relevant test tasks, and the release build complete successfully.
- The release APK/AAB installs and launches on a clean test device.
- Test results and known limitations are documented.

---

## Execution rules

1. Execute tasks in order unless a dependency requires a documented adjustment.
2. Before starting a task, inspect the relevant code and verify the current Gradle/build state.
3. Do not mark a task `DONE` until its acceptance criteria have been verified.
4. If blocked, document the reason, affected files, and the decision or input required.
5. After each task, run at least `./gradlew assembleDebug`; run relevant test tasks for changes that include testable logic.
6. Do not add dependencies without a clear need. Verify compatibility with the project's current toolchain.
7. Never create a second ExoPlayer instance. The playback engine is owned exclusively by `PlaybackService`.
8. Preserve the project's minimal visual style and existing architectural decisions unless there is a demonstrated reason to change them.
9. Respect each task's **Out of scope** section. Do not implement excluded features as part of the task.
10. If an out-of-scope requirement is necessary to meet an acceptance criterion, stop and document the dependency instead of silently expanding the task.
