# Release Checklist

Task 20 stabilization record for the minimal offline music player.

## Build configuration

| Setting | Value |
| --- | --- |
| `applicationId` | `com.example.musicplayer` |
| `versionCode` / `versionName` | `1` / `1.0` |
| `minSdk` | 26 (Android 8.0) |
| `targetSdk` / `compileSdk` | 37 |
| Release optimization | Enabled through the AGP 9 `optimization` block; R8 + resource shrinking |
| Custom keep rules | None. Hilt, Room, Media3, Coil, and AndroidX ship consumer rules. |
| Signing | Optional, read from an untracked root `keystore.properties` |

Without `keystore.properties`, `assembleRelease` and `bundleRelease` still
succeed but produce unsigned artifacts. The file is intentionally ignored by
Git; never commit the keystore or its passwords.

Expected `keystore.properties` keys:

```properties
storeFile=.signing/release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

## Build commands

```powershell
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest
./gradlew assembleDebug
./gradlew assembleRelease bundleRelease
```

`assembleRelease` and `bundleRelease` also run `lintVitalRelease` and R8.

## Automated verification performed

All results below were produced on 2026-11-08 from the current working tree.

| Check | Environment | Result |
| --- | --- | --- |
| JVM unit tests | JDK, `testDebugUnitTest` | **104 tests / 18 suites, 0 failures** |
| Instrumented suite | API 26 emulator (Android 8.0) | **77 tests, 0 failures** |
| Instrumented suite | API 37 emulator (Android 17) | **77 tests, 0 failures** |
| Room migration test | API 26 and API 37, `AppDatabaseMigrationTest` | 1→4 and 2→4 pass; legacy row and artwork survive |
| Large-library budget | API 26, 10,000 tracks | query 250 ms, mapping 5 ms, sort 3 ms, search 35 ms, group 29 ms |
| Large-library budget | API 37, 10,000 tracks | query 362 ms, mapping 36 ms, sort 13 ms, search 213 ms, group 35 ms |
| Debug build | `assembleDebug assembleDebugAndroidTest` | Success |
| Release build | `assembleRelease bundleRelease` | Success, including `lintVitalRelease` and R8 |
| Release APK | `app/build/outputs/apk/release/app-release.apk` | 3.94 MB, signed |
| Release AAB | `app/build/outputs/bundle/release/app-release.aab` | 7.21 MB, signed |
| Clean release install | API 26 emulator | Installs, cold-launches, first-run UI visible |
| Clean release install | API 37 emulator | Installs, cold-launches, first-run UI visible |

The release APK signature was verified with `apksigner`; the local test
certificate is `CN=MusicPlayer Local Release`.

## Manual smoke matrix performed

Dataset: 368 indexed tracks in 413 scanned files (365 MP3, 3 WAV), 26 albums,
including nested folders, untagged files, UTF-16 tags, unsupported files,
empty folders, and corrupt/truncated audio.

- [x] First launch with no folder selected.
- [x] SAF folder selection and persisted access.
- [x] Initial scan completes and shows 368 tracks.
- [x] Unchanged rescan keeps 368 tracks and does not duplicate rows.
- [x] Missing/renamed folder: rescan reports `Last scan failed: Selected folder
      does not exist or is not a directory.`, keeps the existing 368 rows, and
      recovers cleanly after the folder is restored.
- [x] Search filters to `60 of 368 tracks`.
- [x] Sorting by title, artist, album, duration, date added, and shuffle.
- [x] Album grid (26 albums) and album detail (15 tracks in disc/track order).
- [x] Playback from the Tracks list builds a 368-item queue.
- [x] Shuffle, repeat, seek, previous/next, and track transitions.
- [x] Queue screen reorder/remove controls and 368-item badge.
- [x] Mini player, Now Playing artwork/metadata/controls.
- [x] MediaSession and media notification with three actions.
- [x] Background playback with the app in the background.
- [x] Release build installs and launches on API 26 and API 37.

## Pre-publish steps

1. Bump `versionCode` / `versionName` and confirm the production signing key.
2. Run the build commands above from a clean checkout.
3. Verify the release signature with `apksigner verify --print-certs`.
4. Install the release APK on at least one clean API 26 device and one current
   device; run the smoke matrix.
5. Re-test the low-FPS scenario on physical hardware (see limitations).
6. Upload the AAB with a store listing; store assets are out of Task 20 scope.

## Known limitations

- **Real-device scroll FPS is not measured.** The available emulators use
  SwiftShader software rendering, whose baseline jank makes frame statistics
  non-representative (Settings itself measured ~38% janky frames). The list
  work was moved off the main thread, per-row artwork placeholder overdraw was
  removed, and the 10,000-track budget test passes, but the 300+ track fast
  scroll should still be spot-checked on physical hardware before release.
- **SAF grant revocation was not triggered through adb.** The unavailable
  folder path was tested instead. The app checks the persisted read grant and
  folder readability and keeps indexed rows when access fails; manually
  revoking the grant in system settings remains a device-level check.
- **Corrupt/unsupported audio is still indexed** with fallback metadata so the
  user can see the file. Playback of such a file is expected to fail through
  the normal playback error path rather than crash; this was not exhaustively
  tested for every corrupt-file shape.
- **Third-party document providers are not covered.** The SAF tests use the
  AOSP `DocumentsUI`/external-storage provider. Providers with unusual
  traversal or metadata behavior may differ.
- **The manual dataset has 368 tracks**, not 5,000–10,000. The large-library
  target is covered by the synthetic 10,000-row database performance test.
- **The release artifact is signed with a local self-signed test key**, not a
  production or Play App Signing key.
- **No crash reporting, analytics, or telemetry** is configured; this is
  intentional and out of scope.
- **No automated test covers actual headphone-disconnect hardware events**;
  playback interruption behavior is covered by instrumented audio-focus tests.
