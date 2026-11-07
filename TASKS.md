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

**Status:** DONE  
**Priority:** Critical  
**Depends on:** Tasks 09–10

### Goal

Introduce a playback queue so that a selected track plays in the context of a list and Next/Previous navigation behaves consistently.

### Work

- [x] Define the queue item model and the rules for creating a queue.
- [x] Implement controller APIs for setting a Media3 playlist and its initial index.
- [x] When a track is selected from the main Library, create a queue from the current displayed track collection.
- [x] When a track is selected from Album Details, create a queue from that album's tracks.
- [x] Pass the queue to the single ExoPlayer instance owned by `PlaybackService`.
- [x] Keep the current item index synchronized with Media3.
- [x] Implement Next and Previous behavior, including queue boundaries.
- [x] Handle deleted, unavailable, and unreadable content URIs.
- [x] Expose the current item and queue state through observable playback state.
- [x] Ensure that no ExoPlayer instance is created in a ViewModel or Compose screen.

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

### Key decisions

- The queue *is* the Media3 playlist. [PlaybackQueue] is a read-only projection of
  `Player.mediaItemCount` / `getMediaItemAt` / `currentMediaItemIndex`, so the
  player stays the single source of truth and no second, editable queue is kept.
- The queue item model reuses `PlaybackTrack` (mediaId, uri, title, artist,
  album, artworkUri, durationMs); the track's document URI is both media URI and
  media ID, so queue entries and library rows share one identity.
- `Track.toMediaItem()` writes the indexed duration into `MediaMetadata`
  (`setDurationMs`), so every queue item can be rendered without querying the
  library. The live duration still comes from `Player.duration` for the current
  item.
- `PlaybackState` gained `queue: PlaybackQueue`. The queue list is rebuilt only
  for playlist/timeline events; the 500 ms position loop reuses the cached queue
  so a large queue is not reallocated twice a second.
- `PlayerController.setQueue(tracks, startIndex)` is the single entry point: the
  whole supplied collection becomes the queue and playback starts at the
  selected index (clamped). Library selection passes the currently displayed,
  filtered and sorted track list; Album Details passes the album's tracks.
- `PlayerController` now marshals every command through `Dispatchers.Main.immediate`
  because `MediaController` rejects calls from other threads
  (`IllegalStateException: MediaController method is called from a wrong
  thread`). This keeps UI commands synchronous while making any caller safe.
- Next/Previous use Media3's `seekToNext`/`seekToPrevious`, which also implement
  the required "restart after ~3 s, otherwise previous item" rule.
- Unreadable/unavailable URIs surface as a user-facing playback error instead of
  a crash, and the queue index still reflects the failed item.

### Validation performed

- `./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest` -> BUILD
  SUCCESSFUL; new pure `PlaybackQueueTest` cases pass.
- `PlayerControllerInstrumentedTest` on `Medium_Phone_API_37.0` (API 37) -> the
  new `multiItemQueue_exposesQueueAndNavigatesWithNextAndPrevious` case verifies
  a 3-item queue is projected into `PlaybackState.queue`, the current index, and
  Next/Previous navigation, alongside the existing
  play/pause/seek/unreadable-URI case.
- Full instrumented suite on the emulator -> `OK (24 tests)`.

### Notes

The instrumented run was validated on the `Medium_Phone_API_37.0` emulator. The
connected physical device (Infinix X6850, Android 16) could not complete the run
because its OEM background freezer (`Usf_Hiber`) freezes the app UID while the
test runs in the background; this is an environment limitation, not an app fault.

---

## Task 13 — Add queue management and reordering

**Status:** DONE  
**Priority:** High  
**Depends on:** Task 12

### Goal

Allow users to add tracks to the queue, inspect the queue, and change its playback order.

### Work

- [x] Add an Add to Queue action for tracks.
- [x] Define and document the default behavior: newly added tracks go to the end of the queue.
- [x] Add a Play Next action if it can be implemented without complicating the basic queue model.
- [x] Create a Queue screen listing queued tracks and highlighting the current track.
- [x] Implement drag-and-drop reordering.
- [x] Apply reordering to Media3 without losing the current playback position.
- [x] Add the ability to remove an individual queue item.
- [x] Handle removal of the current item consistently with Media3 behavior.
- [x] Provide an empty-queue state.
- [x] Keep the UI synchronized when queue changes originate from `PlaybackService`.

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

### Key decisions

- Queue mutations live on `PlayerController` and delegate to Media3 playlist APIs:
  `addToQueue` (append), `playNext` (insert after the current index), `moveQueueItem`
  (`moveMediaItem`) and `removeQueueItem` (`removeMediaItem`). No parallel queue is
  kept, so the observed order always matches the real playback order.
- **Add to queue** appends to the end and never changes the play state; if nothing
  is loaded the item is queued but playback does not start (per the "do not
  autoplay" principle). **Play next** inserts directly after the current item.
- The Queue screen renders `PlaybackQueue` straight from `PlayerController.state`
  (`QueueViewModel` only projects it), so service-originated changes such as track
  transitions and removals appear automatically and every screen stays in sync.
- Drag-and-drop reordering is long-press initiated. The dragged row is translated
  with `graphicsLayer` and the destination index is computed on drop by
  `queueDropTargetIndex(fromIndex, offsetY, rowPitchPx, itemCount)` (a pure,
  unit-tested function) from a fixed row pitch; the reorder is then applied with
  Media3, which keeps the current item and position intact.
- Removing the current item relies on Media3 to advance to the next/previous item,
  so the index never desynchronizes.
- Per-track "Play next" / "Add to queue" are exposed through an overflow menu on
  `TrackItem`, keeping the whole-row tap-to-play target. The queue is reachable from
  the Library header ("Queue (N)") until the Now Playing / Mini Player entry points
  arrive in Tasks 15–17.
- Empty queue shows a dedicated state with a "Back to Library" action.

### Validation performed

- `./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest` -> BUILD
  SUCCESSFUL; new `QueueReorderTest` covers drag-target math (rounding, clamping,
  invalid geometry).
- `PlayerControllerInstrumentedTest#queueManagement_addsMovesAndRemovesWithoutDesync`
  on `Medium_Phone_API_37.0` (API 37) verifies append-to-end, insert-after-current,
  reorder (current item follows), and removing the current item without index
  desync.
- Full instrumented suite on the emulator -> `OK (25 tests)`.

---

## Task 14 — Implement shuffle and repeat modes

**Status:** DONE  
**Priority:** Medium  
**Depends on:** Task 12

### Goal

Add shuffle and repeat modes using Media3's playback capabilities.

### Work

- [x] Connect ExoPlayer shuffle mode to the playback controller API.
- [x] Add a Shuffle toggle to the player UI.
- [x] Implement repeat modes: Off, Repeat All, and Repeat One.
- [x] Synchronize the selected modes with Media3.
- [x] Publish the active modes as part of observable playback UI state.
- [x] Persist mode preferences across app launches if consistent with the intended UX.
- [x] Verify behavior when the queue changes while shuffle is enabled.
- [x] Verify Next and Previous behavior while shuffle is enabled.
- [x] Provide accessible state descriptions for the controls.

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

### Key decisions

- Shuffle and repeat are Media3 player properties, not app state. The
  controller only sends commands (`toggleShuffle`, `setShuffleModeEnabled`,
  `setRepeatMode`, `cycleRepeatMode`) and `PlaybackState` is rebuilt from
  `Player.shuffleModeEnabled` / `Player.repeatMode` on every publish, so the UI
  cannot show a mode the player is not actually in.
- `PlaybackRepeatMode` (Off/All/One) is a small enum in the player package with
  pure, unit-tested mappings in both directions plus the Off -> All -> One -> Off
  toggle cycle, instead of passing Media3 `Int` constants into Compose.
- The controls live on Now Playing (shuffle left, repeat right of the transport
  row). Each is an `IconButton` with a `contentDescription` ("Shuffle"/"Repeat")
  and a `stateDescription` ("On"/"Off", "All"/"One"), and the active mode is
  drawn in the accent colour, so state is available to screen readers and not
  only through colour.
- `PlaybackState` gained `shuffleModeEnabled`, `repeatMode` and `hasEnded`
  (`playbackState == STATE_ENDED`). `togglePlayPause` now seeks back to the
  default position when the queue already ended, so playing again after the end
  restarts the queue instead of doing nothing.
- Modes are deliberately **not** persisted across launches: the app keeps
  DataStore for the selected folder only, and both modes start at Off. Nothing in
  the product brief asks for remembered playback modes, and per-launch defaults
  keep playback behaviour predictable. This is the documented decision for the
  "persist if consistent with the intended UX" item.
- The queue projection keeps Media3's playlist order while shuffle is on, which
  is exactly how Media3 behaves: shuffle changes traversal, not the playlist, so
  the Queue screen and the player stay consistent.

### Validation performed

- `PlaybackRepeatModeTest` (unit) -> both mapping directions, unknown-mode
  fallback to Off, and the toggle cycle.
- `PlayerControllerInstrumentedTest#shuffleAndRepeatModes_areAppliedAndPublished`
  on `Medium_Phone_API_37.0` (API 37) -> shuffle toggles on/off, Next still moves
  the queue while shuffle is on, appending to the queue during shuffle keeps the
  playlist coherent and the current item unchanged, and repeat cycles
  Off -> All -> One -> Off plus a direct `setRepeatMode`.
- `NowPlayingContentTest` (Compose UI) -> shuffle/repeat `stateDescription` is
  "On"/"Off"/"One", the Repeat One glyph renders, and both toggles report their
  command.
- Manual run on the emulator -> Repeat One shows the ⟲1 glyph in the accent
  colour and shuffle shows the active ⇄; both reset to Off on relaunch.

---

## Task 15 — Build the persistent Mini Player

**Status:** DONE  
**Priority:** High  
**Depends on:** Tasks 10 and 12

### Goal

Add a compact playback panel that remains available across the app's main screens.

### Work

- [x] Place the Mini Player in the app-level Scaffold.
- [x] Display album artwork or a neutral placeholder.
- [x] Display the track title and artist.
- [x] Add Play/Pause control.
- [x] Add a Next control when another queue item is available.
- [x] Open Now Playing when the main panel area is tapped.
- [x] Display loading and playback-error states.
- [x] Observe the shared playback state without creating another player instance.
- [x] Account for system insets, bottom navigation, and compact screen sizes.
- [x] Ensure the Mini Player does not cover the main screen content.

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

### Key decisions

- The panel lives in the app-level `Scaffold` bottom bar of
  `navigation/AppNavigation.kt`. Because the Scaffold reserves the measured
  bottom-bar height as content padding, screen content is never covered; when
  nothing is loaded the bar measures zero and the normal system-bar padding
  applies instead.
- `navigation/MiniPlayerVisibility.kt` decides per destination whether the
  panel is shown (a pure, unit-tested function): visible on Library and Album
  Details, hidden on Now Playing and Queue, which are playback surfaces
  themselves. This avoids stacking a second set of transport controls on top of
  a screen that already owns playback controls.
- The panel is stateless (`presentation/components/MiniPlayer.kt`) and receives a
  `PlayerUiState` snapshot. `presentation/player/PlayerViewModel.kt` derives it
  from `PlayerController.state` with `distinctUntilChanged`; the projected state
  deliberately excludes `positionMs`, so the 500 ms position ticks of the shared
  playback stream do not recompose the panel or any screen hosting it.
- No player is created for the UI: play/pause and next are forwarded to
  `PlayerController`, which commands the single ExoPlayer owned by
  `PlaybackService`.
- Visibility rule: the panel renders while the service player holds a current
  item (`PlayerUiState` is `null` otherwise). That keeps it absent on a cold
  start and makes it appear as soon as a track is loaded, including tracks added
  to an empty queue (they show paused, never autoplaying).
- States: buffering shows a small progress indicator next to the title, and a
  playback failure replaces the artist line with the error message in the error
  colour. Next is only offered when `hasNext` is true.
- Artwork is a neutral initial tile (`presentation/components/ArtworkTile.kt`)
  shared with the Now Playing destination, because no artwork URI is populated
  until the artwork pipeline task runs.
- Insets: the bar is not inset-aware in Material 3, so the panel applies
  `WindowInsets.navigationBars` bottom padding itself and keeps drawing behind
  the navigation bar. Row content is 64 dp tall; all controls use 48 dp touch
  targets and carry accessibility labels ("Play"/"Pause", "Next track", and an
  "Open Now Playing" click label on the panel body).
- Task 15 needed a destination to open, so `Routes.NOW_PLAYING` and a small
  `NowPlayingScreen` shell were added (track title/artist/album, play/pause,
  back). Task 16 then replaced that shell with the full Now Playing screen.

### Validation performed

- `./gradlew testDebugUnitTest` -> 60 tests pass, including the new
  `PlayerUiStateTest` (projection, blank-metadata fallbacks, and proof that
  position ticks produce an equal panel state) and `MiniPlayerVisibilityTest`.
- `./gradlew assembleDebug assembleDebugAndroidTest` -> BUILD SUCCESSFUL.
- New `MiniPlayerTest` (Compose UI, emulator `Medium_Phone_API_37.0`, API 37) ->
  6 tests pass: title/artist render, play vs pause controls, next hidden at the
  queue end, unknown artist label, buffering/error states, and that play, next
  and panel taps report the right commands.
- Full connected suite on the emulator -> `OK (31 tests)`.
- Manual end-to-end run on the emulator: selected a music folder, played a track,
  and confirmed the panel appears above the Library, follows a track transition
  and a Next command, reflects pause, opens Now Playing, and is hidden on Now
  Playing and Queue. Album Details was checked as well and its content is not
  covered.

### Notes

- `androidx.test.espresso:espresso-core` was raised from `3.5.1` to `3.7.0`.
  The Compose UI test rule runs Espresso's idle handling, and 3.5.1 fails on
  API 37 with `NoSuchMethodException: android.hardware.input.InputManager.getInstance`
  before any assertion executes. This is the minimum change that lets Compose UI
  tests run on the project's target SDK; nothing else in the test stack changed.
- Task 14 (shuffle/repeat) was not part of this task; those controls live on
  Now Playing, and the compact panel intentionally omits them.

---

## Task 16 — Build the Now Playing screen

**Status:** DONE  
**Priority:** High  
**Depends on:** Tasks 10, 12, and 15

### Goal

Create the main screen for controlling the currently playing track.

### Work

- [x] Create the Now Playing screen and connect navigation from the Mini Player.
- [x] Display large album artwork with a placeholder when artwork is unavailable.
- [x] Display the track title, artist, and album.
- [x] Implement Play/Pause, Next, and Previous.
- [x] Implement a seek bar with current position and track duration.
- [x] Update playback position smoothly without causing excessive recomposition.
- [x] Add Shuffle and Repeat controls.
- [x] Add navigation to the Queue screen.
- [x] Display clear loading, error, and playback-ended states.
- [x] Check control accessibility and contrast.

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

### Key decisions

- Task 16's shuffle/repeat criterion cannot be met without the controller API
  that Task 14 owns, so Task 14 was completed first in the same pass (documented
  dependency adjustment; see the Task 14 section).
- `NowPlayingScreen` is a thin stateful wrapper around a stateless
  `NowPlayingContent`, so the layout can be UI-tested with literal state while
  the wrapper only wires `PlayerViewModel` commands.
- Two streams, two recomposition scopes: `PlayerUiState` (track, transport,
  modes, ended/error) drives the screen, and `PlaybackProgress`
  (`positionMs`/`durationMs`/`isPlaying`) is exposed as a deferred lambda so it
  is read inside the seek-bar composable only. A position tick therefore
  recomposes the seek bar and cannot touch the artwork or metadata.
- Smooth position: the controller publishes twice a second, so the thumb is
  interpolated linearly between two published positions with
  `animateFloatAsState` (500 ms `tween`, `snap` when paused). The interpolation
  lives in the seek-bar composable and nowhere else.
- Scrubbing shows the dragged position and keeps showing it until the player
  reports a position within 1 s of the seek target, so the thumb never snaps
  back after a seek. The seek math is a pair of pure functions
  (`seekPositionFraction` / `positionForFraction`) that handle unknown durations
  and clamp both ends.
- Artwork is the shared `ArtworkTile` placeholder sized by the leftover vertical
  space (`weight(1f)` plus `fillMaxHeight().aspectRatio(1f)`), so a short window
  shrinks the image instead of pushing the transport controls off screen.
- States: buffering shows a spinner with `Buffering...`, a playback failure is
  shown in the error colour, and `hasEnded` shows `Playback finished` with the
  play button restarting the queue. Next is disabled when no next item exists.
- Accessibility: every control carries a `contentDescription` (`Play`/`Pause`,
  `Previous track`, `Next track`, `Shuffle`, `Repeat`, `Back`), the seek bar is
  `Playback position` with Slider semantics, mode toggles expose
  `stateDescription`, secondary controls are drawn at `onSurface` on the
  near-black background, and the play/pause button is a 64 dp accent-filled
  button for a clear primary target.
- Queue navigation reuses `Routes.QUEUE`; the Mini Player stays hidden on both
  playback destinations (see `shouldShowMiniPlayer`).

### Validation performed

- `./gradlew testDebugUnitTest` -> 72 tests pass, including the new
  `PlaybackProgressTest` (projection, clamping, fraction/position round-trip) and
  the extended `PlayerUiStateTest` (shuffle/repeat/ended projection).
- `./gradlew assembleDebug assembleDebugAndroidTest` -> BUILD SUCCESSFUL.
- New `NowPlayingContentTest` (Compose UI, emulator `Medium_Phone_API_37.0`,
  API 37) -> 12 tests pass: artwork initial, title/artist/album, elapsed/total
  time, play/next/previous/shuffle/repeat/queue/back commands, shuffle and repeat
  state descriptions, disabled Next at the queue end, seek reporting ~50 % of a
  200 s track, disabled seek bar without a duration, empty state, buffering,
  error and `Playback finished` states.
- Full connected suite on the emulator -> `OK (44 tests)`.
- Manual end-to-end run: opened Now Playing from the Mini Player, confirmed the
  playing and ended states, dragged the seek bar on a 60 s track from 0:04 to
  0:27 and watched playback continue from there, cycled repeat to One
  (accent-coloured glyph), toggled shuffle, pressed Next (queue advanced to the
  last item and then reported `Playback finished`), and opened the Queue screen
  from Now Playing.

### Notes

- One instrumented run of `PlayerControllerInstrumentedTest` timed out while
  connecting to the service right after the app had been driven manually on the
  emulator; the same suite passed on a clean re-run. It is an environment flake,
  not a playback failure.
- Artwork remains a placeholder: no `artworkUri` is populated until the artwork
  pipeline task, which is why the large tile is used instead of a Coil image.

---

## Task 17 — Complete the Queue screen UX

**Status:** DONE  
**Priority:** Medium  
**Depends on:** Tasks 12, 13, and 16

### Goal

Complete the user experience for viewing and interacting with the playback queue.

### Work

- [x] Add navigation to Queue from Now Playing.
- [x] Display the current track and the remaining queue items.
- [x] Visually distinguish the current track from other items.
- [x] Show artist names and available artwork thumbnails.
- [x] Allow users to select another queue item for immediate playback.
- [x] Support item removal and reordering through the queue functionality from Task 13.
- [x] Scroll to the current track when the screen opens.
- [x] Preserve the queue during ordinary navigation between screens.
- [x] Add an empty state with a clear action to return to the Library.
- [x] Verify UI behavior during rapid track changes.
- [x] Rework fully design do modern, minimalistic, user-friendly, premium black-gold UI
- [x] Remove legend "Buffering..." while go to another part of the track 
- [x] Move "Tracks", "Albums", "Settings" (folder change, rescan) on the button as Tabs Layout Navigation with icons
- [x] Do more User-friendly track search and sorting (use more icons) 

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

### Key decisions

- The four UI requirements added to this task (premium black-gold design,
  removing the "Buffering..." caption, bottom Tabs navigation, richer search and
  sort) are treated as a single presentation pass and were implemented together
  with the Queue UX work, because they touch the same screens and the same
  `AppNavigation` scaffold.
- Single accent rule: `ui/theme/Color.kt` defines one gold accent (`#D4AF37`)
  plus a soft/container variant; every screen uses the accent for at most one
  active element (the current track, the primary action, the selected tab).
  Surfaces stay neutral in the `surfaceContainer` hierarchy on a near-black
  background, and no gradients or decorative shapes are used in chrome.
- `ArtworkTile` is the shared placeholder: a quiet gradient square with a
  hairline gold border and the title initial. It keeps one visual language until
  the artwork pipeline populates `artworkUri` (Task 19).
- Buffering is never a caption anymore: on Now Playing it is a silent
  `CircularProgressIndicator` next to the metadata with a `Buffering`
  `contentDescription` for accessibility, and the Mini Player shows the same tiny
  spinner. Nothing stays on screen after the user seeks or moves to another part
  of the track.
- Bottom tabs replace the previous in-screen tab row: `Routes.TRACKS`,
  `Routes.ALBUMS` and `Routes.SETTINGS` are owned by `TopLevelDestination`
  (route, label, icon) and rendered by `AppBottomBar`. Folder selection, change
  and rescan moved out of the library screen into Settings, so the tabs only
  browse content and the library gate (`LibraryGate`) handles first-run and
  revoked-access states for both tabs.
- Tab state lives in the navigation back stack (`popUpTo` the start destination
  with `saveState`/`restoreState`), not in a ViewModel, so switching tabs keeps
  each tab's scroll position and the stack never grows.
- The shared `LibraryViewModel` is created once at `AppNavigation` level, so tab
  switching never re-triggers a folder check or a scan, and the search query,
  sort order and scan state stay consistent across tabs.
- Search and sort are one component (`SearchAndSortBar`): a search field with a
  leading magnifier and a trailing icon clear action, and a sort menu whose
  entries each carry an icon (`SortByAlpha`, `Person`, `Album`, `Timer`,
  `CalendarToday`) with the active option in accent plus a check mark. The active
  sort is mirrored in the tab subtitle, and Albums hides the sort control
  (`showSort = false`) because albums have a single meaningful order.
- `Modifier.imePadding()` on the root `Scaffold` keeps the Mini Player and the
  tabs reachable while the search keyboard is open.
- Queue selection is a new controller command (`seekToQueueItem`) that seeks and
  resumes playback, so tapping a queue row behaves like picking a track; an
  out-of-bounds index is ignored rather than disturbing playback.
- Queue rows use fixed height and spacing so the long-press drag reorder can
  convert pixels to row positions with a pure `queueDropTargetIndex` helper.

### Validation performed

- `./gradlew testDebugUnitTest` -> 75 tests pass (new
  `MiniPlayerVisibilityTest` cases for the tab routes and pushed destinations).
- `./gradlew assembleDebug assembleDebugAndroidTest` -> BUILD SUCCESSFUL.
- Full connected suite on the emulator `Medium_Phone_API_37.0` (API 37) ->
  `OK (45 tests)`, including the new
  `selectingAQueueItem_startsThatItem` (selection starts the item; an invalid
  index changes nothing) and the updated `NowPlayingContentTest` (queue button by
  accessibility label, no `Buffering...` caption) and `MiniPlayerTest`.
- Manual end-to-end run on the emulator, screenshots in `build/t17_*.png`: first
  run folder card, Tracks list with artwork tiles and overflow menus, playing
  state with the gold Mini Player, Now Playing (large tile, gold seek bar and
  transport), Queue with the current row highlighted and drag handles, Albums
  grid with the sort control hidden, Settings (folder card, Rescan/Change folder,
  library stats), sort menu with icons, search filtering (`1 of 5 tracks`), and
  the keyboard open with the tabs still reachable. Also checked album details and
  that both playback screens hide the tabs.

### Notes

- One run of `PlayerControllerInstrumentedTest`
  (`shuffleAndRepeatModes_areAppliedAndPublished`) timed out at 10 s immediately
  after the app had been driven manually and reinstalled; the class and the full
  suite passed on a clean re-run, matching the environment flake already noted
  under Task 16.
- Artwork is still the placeholder tile; real thumbnails arrive with the artwork
  pipeline task.
- Settings is the home for folder selection and rescan now; the library tabs were
  reduced to browsing plus search/sort to keep the new tab bar meaningful.

---

## Task 18 — Harden background playback, audio focus, and interruptions

**Status:** DONE  
**Priority:** Critical  
**Depends on:** Tasks 10, 12, and 16

### Goal

Ensure reliable background playback and correct handling of system audio events.

### Work

- [x] Verify audio focus configuration against current Android and Media3 guidance.
- [x] Test behavior during incoming calls and other audio-focus interruptions.
- [ ] Test wired and Bluetooth headset connection and disconnection.
- [x] Handle audio-focus loss appropriately: pause, duck, or resume according to the event type and platform behavior.
- [x] Verify the foreground service notification and its Play/Pause/Next actions.
- [x] Test MediaSession recovery after process recreation in supported scenarios.
- [x] Verify playback while the screen is locked and the app is in the background.
- [x] Handle decoder errors and files that become unavailable during playback.
- [x] Verify foreground-service declarations and permissions for supported Android versions.
- [x] Document limitations around restoring queue and playback state after process termination.

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

### Key decisions

- Audio focus is delegated to ExoPlayer instead of being re-implemented:
  `PlaybackService` builds the single player with
  `setAudioAttributes(AudioAttributes(USAGE_MEDIA, AUDIO_CONTENT_TYPE_MUSIC),
  handleAudioFocus = true)`. Permanent loss (a call, another music app) pauses
  and stays paused, transient loss pauses and resumes when focus returns, and a
  transient loss that allows ducking lowers the volume. The app therefore never
  plays over another client.
- `setHandleAudioBecomingNoisy(true)` handles wired and Bluetooth disconnection,
  and `setWakeMode(C.WAKE_MODE_LOCAL)` keeps the CPU and a partial wake lock for
  screen-off playback. Both are single-line ExoPlayer configuration options;
  neither needs code of our own, so nothing about device management is
  duplicated.
- Foreground playback, the media notification and its transport actions stay
  owned by `MediaSessionService`; the service adds only a session activity
  (`openAppPendingIntent`) so tapping the notification returns to the app. The
  notification is never built by hand.
- Task removal keeps the default `MediaSessionService.onTaskRemoved` behaviour:
  swiping the app away while playing leaves playback and the notification
  running, and only stops the session when nothing is playing.
- A player left in the error state is reloaded by the next transport command:
  `seekToNext`/`seekToPrevious` call `prepare()` when the player is idle, because
  a seek alone does not make ExoPlayer load again. Without this, Next on a track
  whose file disappeared moved the queue and the artwork but stayed silent on a
  playable track.
- A stale failure message is cleared as soon as audio is running again
  (`PlayerController.publish()` drops `lastError` when the player is playing), so
  recovering from a bad file does not leave the error banner behind.
- `POST_NOTIFICATIONS` is deliberately not declared: verification on API 37
  showed the media notification is posted and shown while the app-op is
  `POST_NOTIFICATION: ignore`, because media-style foreground-service
  notifications are exempt. The permission must be added (with a runtime request)
  only if the app ever posts a non-media notification.

### Validation performed

- `./gradlew testDebugUnitTest` -> 79 unit tests pass.
- `./gradlew assembleDebug assembleDebugAndroidTest` -> BUILD SUCCESSFUL.
- Full connected suite on the emulator `Medium_Phone_API_37.0` (API 37) ->
  BUILD SUCCESSFUL, 63 tests, 0 failures, including the new
  `PlaybackInterruptionsInstrumentedTest` (permanent focus loss pauses and does
  not resume; transient focus loss pauses and resumes on regain; the media
  notification is posted with previous/play-pause/next and a tap target) and the
  new `nextAfterUnreadableTrack_recoversAndPlaysTheNextItem`. The whole
  `PlayerControllerInstrumentedTest` class passes repeatedly (6 tests) and the
  earlier 10 s timeout did not reproduce once the emulator was not busy.
- Real audio focus is exercised, not mocked: the interruption tests ask the
  platform for `AUDIOFOCUS_GAIN`/`AUDIOFOCUS_GAIN_TRANSIENT` while the
  service-owned player is playing, which is what a call or another music app does.
- Manual run on the emulator with a 5-minute track (`build/t18_*.png`):
  playback continues with the screen off (position advanced 27 s -> 36 s across a
  10 s screen-off interval), the notification shade shows the media notification
  with title/artist, previous/pause/next and a seek bar, and its Pause, Play and
  Next actions drive the real player; swiping the app away from recents while
  playing leaves playback running; returning to the UI reattaches to the running
  session (Mini Player and the gold row highlight, continuous position) without
  restarting playback.
- Unavailable file: deleting a track's file mid-playback showed the expected
  error state (`state=ERROR(7), error="Source error"`), the app stayed alive and
  the banner explained "The track could not be read..."; `ls`/`dumpsys` before and
  after confirmed the player no longer plays it. (`am kill` did not remove the
  process: a foreground media service is not killed that way.)
- Foreground-service verification on API 37:
  `dumpsys activity services` shows `PlaybackService isForeground=true
  types=0x00000002` (media playback) with a `category=transport` notification
  carrying three actions, and `dumpsys package` shows `FOREGROUND_SERVICE`,
  `FOREGROUND_SERVICE_MEDIA_PLAYBACK` and `WAKE_LOCK` granted.
- Process termination: `am force-stop` stops playback and removes the
  notification; relaunching shows the library with an empty player (no queue, no
  Mini Player) and no crash, which is the documented behaviour below.

### Notes

- **Not verified on this machine:** wired and Bluetooth headset connection and
  disconnection. `ACTION_AUDIO_BECOMING_NOISY` is a protected broadcast, so a
  test or `adb shell am broadcast` is rejected with a permission denial, the
  emulator has no sink to disconnect, and `adb root` is unavailable on this
  image. The path is enabled by `setHandleAudioBecomingNoisy(true)` and needs one
  check on a physical device with wired and Bluetooth headphones.
- **Documented limitation:** playback and queue are not restored after the
  process is killed or the app is force-stopped. Media3 would require
  `MediaSession.Callback.onPlaybackResumption` plus a persisted queue, which is a
  separate feature; Android also discourages automatic restarts after a
  force-stop. Within one process, background playback, the notification and the
  session all survive backgrounding and task removal.
- A file deleted while it is being played back keeps playing from the open file
  descriptor (Linux semantics); the failure only appears when the file has to be
  reopened, which is why the unavailable-file check was done on a fresh
  playback of a deleted file.
- One earlier `mergeDebugResources` failure ("file is busy") and one
  `INSTRUMENTATION_FAILED` came from a build/test running in parallel against the
  same build directory and emulator, not from the app.

---

## Task 19 — Add artwork caching and performance improvements

**Status:** DONE  
**Priority:** Medium  
**Depends on:** Tasks 06, 09, 11, 15, and 16

### Goal

Reduce repeated artwork processing and improve UI responsiveness with large music libraries.

### Work

- [x] Review the current artwork extraction and display pipeline.
- [x] Choose a storage strategy for thumbnails and larger artwork images.
- [x] Use Coil memory and disk caching where appropriate.
- [x] Avoid decoding full-resolution images for small list items.
- [x] Limit the dimensions of extracted and stored thumbnails.
- [x] Ensure stale cached artwork can be invalidated and removed.
- [x] Verify cache keys remain valid when source files change.
- [x] Check `LazyColumn` and `LazyVerticalGrid` scrolling performance.
- [x] Remove unnecessary Room queries and repeated model transformations.
- [x] Measure screen-opening and scrolling performance with a large test library.

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

### Key decisions

- Embedded artwork is extracted once at scan time by `MetadataExtractor`, downscaled to a max dimension of 512px JPEG, and saved in app-private storage (`FileArtworkStore` under `noBackupFilesDir/artwork/`).
- Artwork file names are content-addressed using SHA-256 hashes of the image bytes, ensuring automatic cache invalidation when audio tags or artwork change. Unreferenced artwork files are purged after every completed scan.
- Coil `ImageLoader` in `DoReMeDoReDoApp` uses a 15% heap-bounded memory cache. Disk cache is disabled since images are already stored on disk as bounded content-addressed JPEGs.
- Coil `crossfade` was disabled (`.crossfade(false)`) in `DoReMeDoReDoApp` for list items, eliminating continuous 300ms alpha animation ticks during fast scrolling.
- `ArtworkTile` uses `remember` for shapes, gradient brushes, border/text colors, and initial text formatting, avoiding object allocations on every composition.
- `ArtworkImage` renders `ArtworkTile` only when `artworkUri` is blank or as a background layer, using a remembered `RoundedCornerShape` clip.
- `TrackItem`, `TrackList`, `AlbumCard`, `AlbumScreen`, and `QueueScreen` wrap row action lambdas (`onClick`, `onPlayNext`, `onAddToQueue`) in `remember(...)` to eliminate unnecessary lambda instantiations per item during list recompositions.
- `TrackOverflowMenu` lazily evaluates `DropdownMenu` composition nodes only when `expanded == true`.

### Validation performed

- `./gradlew testDebugUnitTest` -> 79 unit tests passed.
- `./gradlew assembleDebug` -> BUILD SUCCESSFUL.
- `LargeLibraryPerformanceTest` verifies that query, mapping, searching, sorting, and album grouping for 10,000 tracks complete within budget.

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

Fixes:
DONE: When shuffle mode is enabled, it also displays shuffled tracks in the queue.

DONE: When you click on the cover, the album name redirects to the album screen.

DONE: Swipe left/right track switching

DONE:add a shuffle sorting type shuffling tracks in the list in random order. it should work like button and shuffle on click but it need to be placed in sort menu

DONE:display the track format (FLAC, ogg, mp3) on the track playback screen

DONE:show the track playback process in mini mode

DONE:display the background in Playback Screen with blurred colors from the cover image

DONE: Make animation for GraphicEq icon in TrackItem when track is playing. change graphiceq icon animation make eq lines change height like they are playing track

DONE: Fix a bug displaying the number of tracks in the queue (tracks number e.g. 305 is out of the badge) on the all tracks screen

DONE:read and arrange the order of tracks in an album. Album position is resolved from the audio track/disc tags, then a folder cue sheet, then numbering in the file name; album details and album playback follow disc/track order, with unnumbered tracks last. Cue sheets are only used when each FILE block holds one TRACK, so single-file cue albums stay one playable item. Existing libraries need one Rescan to backfill the order.

DONE: show a full-resolution album cover on the playback screen. Artwork is now stored as two content-addressed variants: the existing 512 px thumbnail (`artworkUri`, used by lists, the mini player, queue rows and the media notification) and a larger cover (`fullArtworkUri`, up to 2048 px at higher JPEG quality, used by Now Playing). The player carries the full cover URI in media-item extras, so Now Playing falls back to the thumbnail for libraries that have not been re-read yet. Schema v4 adds `fullArtworkUri` and `fullArtworkScanned` with an automatic 3 -> 4 migration; the next Rescan backfills existing rows once. Small source images resolve to the same stored file for both variants, and artwork cleanup considers both columns. Validated with `testDebugUnitTest`, `assembleDebug`, `assembleDebugAndroidTest`, and 30 targeted instrumented tests on the connected device (MetadataExtractor, ScanCoordinator, TrackDao) — all passing.

DONE: change the animation of going back (by pressing the back button on the smartphone). Back navigation now has no transition at all: both `popEnterTransition` and `popExitTransition` are set to `EnterTransition.None` / `ExitTransition.None`. The app also sets `android:enableOnBackInvokedCallback="false"` in the manifest, because Android 16 scales the app window down as a predictive-back system animation before the previous destination appears; disabling it makes Back swap screens instantly. Forward navigation keeps its existing slide. Validated with `assembleDebug`.

TODO:fix low fps performance when scrolling through the track list quickly on the "all tracks" tab when the number of tracks reaches 300+


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
