# Handover

Last updated: 2026-07-22

## Current outcome

The reader/library follow-ups, remote-media work, and audiobook session persistence work are implemented and committed locally.

- Orientation lock now persists the exact portrait or landscape orientation selected in Lagrange instead of inheriting the orientation of the previous app.
- Library Browse statistics and filter/collapse or multi-selection controls are fixed while only the catalog scrolls.
- Local books supports multi-selection and batch Delete local.
- Authors loads the complete catalog and provides the shared represented-label #/A-Z jump rail.
- Libraries, Series, Authors, and Local books have distinct filled destination icons.
- BookOrbit's exact eight reading-state values are parsed and persisted through Room, browser snapshots, detail cache, and active-reader state.
- Currently reading contains Reading and Rereading; Want to read contains Want to Read; Recently read contains Read and Skimmed.
- On Deck is deliberately series-derived rather than mapped from On Hold: a series with at least one Read volume contributes its first non-Read volume unless that candidate is Reading or Rereading. Standalone and unstarted series are excluded.
- Connected CBZ/CBR/CB7 uses lazy authenticated BookOrbit page resources, so opening and navigation fetch only required pages.
- Connected audiobooks now follow BookOrbit's own WebUI delivery model: the authenticated `/api/v1/books/files/{fileId}/serve` URL is passed directly to Media3. Readium is not used to interpret a standalone remote audio file.
- Explicit local audiobook downloads retain the proven Readium 3.0.2 audio navigator. Both local Readium and connected direct Media3 engines share the foreground media service, compact controls, progress, chapters, speed, Preview isolation, and Close behavior.
- EPUB and PDF continue using authenticated complete-file preparation and Readium. MOBI/AZW/AZW3/FB2 remain explicitly unsupported by product choice.
- The requested format-filter follow-up was scrapped.
- Audiobook speed persists globally across books and compact-player close/reopen.
- New audiobook opens prepare in the Browser with a compact-player spinner and autoplay after an explicit Book Detail Play action.
- App/task restart restores the audiobook compact player at the saved position and speed but always paused.

Version 1.1.0 is now set at versionCode 11, and the signed release APK has been built and packaged after the user approved the release-note direction.

## Repository and publishing state

- Repository: `C:\Users\vangeaux\Desktop\.git_projects\bookorbit-android`
- Branch: `main`
- Remote: `origin` via SSH
- Current implementation/documentation HEAD before the release commit: `51c8993`
- The 1.1.0 release metadata and signed artifact are ready to commit and push.

Local commits since `origin/main`, newest first:

- `51c8993 fix: restore audiobook sessions paused`
- `e02c1ba feat: persist audiobook playback sessions`
- `9c7bf63 fix: smooth continuous comic cache reuse`
- `4dba8bf fix: restore on deck and remote audio streaming`
- `c0137bb feat: map BookOrbit reading state shelves`
- `d17e8c7 feat: distinguish library destinations`
- `f5c9a51 feat: add authors jump rail`
- `e566844 feat: delete selected local books`
- `e3ad493 fix: enforce bounded remote media streaming`
- `9260978 feat: load connected comics page by page`
- `3109fb4 feat: stream remote audiobooks with byte ranges` (its Readium remote-audio path is superseded by `4dba8bf`)
- `bc8ea08 fix: pin library browse controls`
- `d04cb35 fix: persist locked app orientation`
- `7d5ffcd docs: drop format filter follow-up`
- `4e89809 docs: queue reader and library follow-ups`

## Current reader and media architecture

### EPUB, PDF, and comics

- EPUB/KEPUB opens with Readium EPUB.
- PDF opens with Readium PDF and the pinned Pdfium adapter.
- Connected CBZ/CBR/CB7 opens as a lazy Readium image publication backed by BookOrbit page endpoints.
- Local/downloaded CBZ remains file-backed.
- Connected CBR/CB7 no longer requires constructing a complete cached CBZ before first display.
- EPUB/PDF retain complete authenticated preparation; explicit downloads remain separate offline copies.
- MOBI, AZW, AZW3, and FB2 remain `UNSUPPORTED_EBOOK`/`MediaKind.UNKNOWN`.

### Audiobooks

- `ReadiumAudioPlaybackService` remains the foreground `MediaSessionService` and owns the active session.
- Explicit local/downloaded audio uses Readium's `AudioNavigator` and ExoPlayer adapter.
- Connected M4B/M4A/MP3/Opus/OGG/FLAC uses a direct Media3 item pointed at BookOrbit's serve route.
- `AuthenticatedMedia3HttpDataSource` resolves current same-origin Bearer/cookie credentials for every open and retries once after a 401/403 session renewal.
- Media3 issues bounded HTTP Range requests; connected playback creates no implicit complete audio copy.
- The compact player operates against the common Media3 `Player` boundary and keeps chapters, seeking, playback speed, background/media-session controls, resume, progress sampling, and explicit Close.
- Normal Read and Preview return to Browser while playback continues; Preview remains progress-isolated.
- Persisted audio progress must not restore a transient fullscreen Reader during bootstrap. Browser opens first and the surviving service supplies the compact player.

### Home shelves

- Home uses the server-wide catalog; Library Recommended uses the selected-library catalog.
- Shelf order is Currently reading, On Deck, then Want to read.
- Currently reading: exact Reading/Rereading.
- On Deck: first non-Read volume from a series containing a Read volume, hidden while the candidate is Reading/Rereading.
- Want to read: exact Want to Read.
- Recently read: exact Read/Skimmed.
- Unread, Abandoned, On Hold, null, and unknown states do not receive their own reading-state shelf.

## User validation completed

The user confirmed on the target device:

- Portrait orientation lock works correctly after returning from another app.
- Local books batch actions work.
- Authors jump rail works.
- Destination icons are good.
- Exact reading-state behavior works.
- Restored series-progression On Deck works correctly.
- Direct remote audiobook streaming opens and plays nicely.

Earlier target-device confirmations still apply for EPUB, CBZ, connected CBR, local audiobook playback, reader Exit/tutorial behavior, compact-player placement/controls, series grouping, profile-menu ordering, and protection against refresh/download ejecting an active reader.

## Verification completed

The final implementation gate passed:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

Results:

- 294 JVM tests across 50 suites
- 0 failures, 0 errors, 0 skipped
- Android lint passed
- Debug APK assembly passed
- Android instrumentation-test APK assembly passed
- Direct Media3 instrumentation coverage compiles exact authenticated byte ranges and one 401 renewal/retry.
- Connected execution of the new Media3 MockWebServer tests remains pending because no Android device/ADB was attached to this environment.

Debug APK:

`app/build/outputs/apk/debug/app-debug.apk`

- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Release APK: `app/build/release-artifacts/Lagrange-1.1.0.apk`

## Highest-priority next work

After publishing the 1.1.0 release artifact, continue the physical validation matrix:

1. Remote audio: verify backward/forward seeking, chapter selection, speed, Preview isolation, background playback, notification/lock-screen/headset/Bluetooth controls, app relaunch with a surviving session, access-token renewal during a long stream, and explicit-download offline reopening.
2. Remote comics: validate representative large CBZ, CBR, and CB7 files, selected-page-only loading, navigation, reconnect/error behavior, and explicit downloads.
3. Library Browse: confirm the statistics and filter/collapse or selection row remains fixed during deep scrolling, downward swipes, pull-to-refresh, collapse/expand, and multi-selection.
4. Reading shelves: repeat On Deck and the exact state shelves in selected-library Recommended and after offline/cold-cache reopening; confirm a candidate disappears from On Deck when it becomes Reading/Rereading.
5. Readers/layout: validate a real BookOrbit PDF, mixed portrait/square cover alignment, compact-player relaunch/layout, accessibility, large text, themes, and responsive widths/orientations.

Before asking the user to test another build, assemble the debug APK and report its exact path. Keep release signing material outside Git and update this handover only for explicit handover requests.

## Architecture guardrails

- Do not route connected standalone audio through Readium publication/asset retrieval again. Match BookOrbit by using the serve URL as direct authenticated Media3 media.
- Keep explicit downloaded/local audio on the proven Readium path unless a future migration is explicitly approved.
- Keep playback foreground-service-owned and compact-only; do not add a fullscreen audiobook player without a user-approved GUI/system decision.
- Resolve streaming credentials only for the configured BookOrbit origin and retain one bounded 401/403 renewal retry.
- Never implicitly download an entire large audio/comic item for ordinary connected Play/Read.
- Keep explicit downloads independent and usable offline.
- Preserve Preview isolation from normal progress and active-reader persistence.
- During bootstrap, skip restoring AUDIO as a fullscreen Reader; open Browser and let the service restore the compact player.
- Preserve the series-derived On Deck rule. Do not map On Hold into On Deck.
- Preserve exact BookOrbit reading states independently from legacy `isRead`.
- Keep shelf order Currently reading -> On Deck -> Want to read.
- Keep MOBI/AZW/AZW3/FB2 explicitly unsupported unless the user revisits conversion.
- Keep reader content above the compact player and the compact player above app/system navigation.
- Preserve per-library cover ownership/aspect ratios and square-cover bottom alignment.
- Pdfium currently requires Jetifier because of its transitive Android support-library dependency.

## Important files for the next session

- `README.md`
- `CHECKLIST.md`
- `docs/roadmap.md`
- `docs/testing.md`
- `docs/architecture.md`
- `docs/ui-ux.md`
- `app/src/main/java/com/bookorbit/android/AppCoordinator.kt`
- `app/src/main/java/com/bookorbit/android/BookOrbitApp.kt`
- `app/src/main/java/com/bookorbit/android/BookOrbitHomeScreen.kt`
- `app/src/main/java/com/bookorbit/android/BookOrbitRepository.kt`
- `app/src/main/java/com/bookorbit/android/AuthenticatedReadiumHttpClient.kt`
- `app/src/main/java/com/bookorbit/android/AuthenticatedMedia3HttpDataSource.kt`
- `app/src/main/java/com/bookorbit/android/ReadiumAudioPlayback.kt`
- `app/src/main/java/com/bookorbit/android/ReadiumAudioPlayerOverlay.kt`
- `app/src/main/java/com/bookorbit/android/ReadiumComicReaderActivity.kt`
- `app/src/main/java/com/bookorbit/android/ReadiumEpubReaderActivity.kt`
- `app/src/main/java/com/bookorbit/android/ReadiumPdfReaderActivity.kt`
- `app/src/androidTest/java/com/bookorbit/android/ReadiumRemoteResourceInstrumentedTest.kt`
- `app/src/test/java/com/bookorbit/android/HomeShelfTest.kt`

## Protected working-tree changes

The following user-owned changes are unrelated to the handover and must not be staged or committed:

- Modified `README.md`
- Modified `app/src/main/res/drawable/ic_launcher_foreground.xml`
- Modified `app/src/main/res/drawable/ic_launcher_monochrome.xml`
- Modified `docs/README.md`
- Untracked `.agents/`
- Untracked `AGENTS.md`
- Untracked `sample/`

## Environment notes

- JDK 17 and the Android SDK are installed and working.
- The Readium PDF route pins `readium-adapter-pdfium:3.0.2`; `android.enableJetifier=true` remains required.
- No Android device/ADB was attached during the latest automated verification.
- The Gradle daemon started during this session was stopped before this handover update.
- Git SSH authentication is configured through `origin`.

## Handover maintenance rule

- Update this file only when the user explicitly requests it.
- Stop session-started project terminals, servers, watchers, emulators, and Gradle daemons before updating it.
