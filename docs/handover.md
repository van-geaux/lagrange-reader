# Handover

Last updated: 2026-07-10

## Repository

- GitHub: `https://github.com/van-geaux/lagrange-reader`
- Local path: `C:\Users\vangeaux\Desktop\.git_projects\bookorbit-android`
- Main branch: `main`

## Current implementation state

- Server bootstrap is implemented:
  - first open asks for the BookOrbit server URL
  - server reachability is checked before entering login
  - malformed URL, unreachable host, TLS, timeout, redirect, and generic network failures show distinct setup messages
  - failed setup attempts preserve the entered server URL for direct retry
  - login is done through an in-app WebView
- Session handling is implemented at a basic level:
  - WebView and API client share cookies
  - login completion is inferred by probing authenticated endpoints
  - explicit sign-out now waits for cookie clearing to complete and stays on login instead of bouncing into cached browser state
- Library browsing is implemented:
  - libraries load from `GET /api/v1/libraries`
  - books load from `POST /api/v1/libraries/{id}/books`
  - live payload parsing has been validated against a working BookOrbit deployment
- Reading and listening paths are implemented:
  - audio uses ExoPlayer
  - audio UI includes play/pause, skip, speed, and resume support
  - PDF uses `PdfRenderer`
  - PDF reader includes zoom, pan, and reset controls
  - EPUB uses local extraction plus OPF spine parsing and chapter-by-chapter `WebView` rendering
  - EPUB reader includes chapter picking plus theme and font-size controls
  - downloaded EPUB local images now render correctly in the reader WebView
  - CBZ comics can be opened from local downloads or authenticated cache copies
  - active reader state is persisted and can be restored after recreation or restart
  - reader reopen now falls back to last-synced progress after queue replay, so tested resume state survives successful sync
  - reader controls and status surfaces have improved accessibility semantics
- Offline support is partially implemented:
  - downloads are stored locally
  - local files are preferred when present
  - stale or zero-byte local download records are pruned before reuse
  - EPUB, PDF, and CBZ can be reopened from authenticated cache copies before full download
  - cached library and book browser state is used for offline fallback
  - cached offline browser states disable live-only actions for non-downloaded titles
  - offline-first browser opens and startup reader restore now suppress live stream fallback when a local-only reopen is intended
  - progress updates are queued locally and replayed later
  - sync queue compaction is implemented
  - manual device testing confirmed downloaded content reopens correctly in airplane mode for the tested content set
- Queue and storage hardening is implemented:
  - transient sync failures use WorkManager retry backoff
  - auth-blocked sync queues remain persisted without burning retries
  - last-synced progress markers suppress duplicate or stale submissions
  - pending progress counts shown in debug browser UI are scoped to the active server instead of counting saved queues for every server
  - malformed library/book payloads are surfaced as user-facing errors
  - stored progress percentages are normalized to a consistent 0-100 scale across media types
  - reader opening fails earlier with explicit user-facing messages when no readable local or cached content can be prepared
- Focused JVM unit coverage exists for:
  - `ActiveReaderStore`
  - `DownloadStore`
  - `LastSyncedProgressStore`
  - `ProgressQueueStore`
  - repository helper and payload normalization paths
  - coordinator bootstrap, login recovery, and sign-out behavior
- Basic Compose instrumentation coverage exists for:
  - server setup validation UI
  - cached offline browser UI behavior
- The Android debug build is passing on this machine with `.\gradlew.bat assembleDebug`
- The local JVM unit test task is passing on this machine with `.\gradlew.bat testDebugUnitTest`
- The Android instrumentation test target compiles on this machine with `.\gradlew.bat assembleDebugAndroidTest`
- The Android release build is passing on this machine with `.\gradlew.bat assembleRelease`
- A manual app test matrix now exists in [testing.md](C:/Users/vangeaux/Desktop/.git_projects/bookorbit-android/docs/testing.md)
- Recent manual device validation results:
  - physical-device launcher icon is correct
  - downloads work and tested local reopen works in airplane mode
  - progress sync after reconnect is visible in the BookOrbit web app
  - tested reader resume now restores the last position
  - downloaded EPUB images now render correctly
  - the previous sign-out cached-browser fallback bug was reproduced and then fixed in code after testing

## Handover maintenance rule

- Update this handover only when explicitly told to do so before ending a session.
- A general instruction to update docs means update the relevant project docs, excluding this handover.

## Live BookOrbit assumptions already validated

- Authentication:
  - `POST /api/v1/auth/login` accepts `username` and `password`
  - successful login sets auth cookies and returns `accessToken` plus `user`
- Library and book loading:
  - `GET /api/v1/libraries`
  - `POST /api/v1/libraries/{id}/books`
- File access:
  - `GET /api/v1/books/files/{fileId}/serve`
  - `GET /api/v1/books/files/{fileId}/download`
- Progress sync:
  - ebook progress uses `POST /api/v1/books/files/{fileId}/progress`
  - audiobook progress uses `PATCH /api/v1/books/{id}/audio-progress`
  - progress DTOs use `percentage` and optionally `pageNumber`, `positionSeconds`, and `currentFileId`

## Important local environment notes

- JDK 17 and Android SDK are already installed and working.
- `local.properties` is present locally and points at the Android SDK.
- Gradle builds may need access outside the workspace because the wrapper cache lives under `C:\Users\vangeaux\.gradle`.

## Files to inspect first next session

- [CHECKLIST.md](C:/Users/vangeaux/Desktop/.git_projects/bookorbit-android/CHECKLIST.md)
- [docs/testing.md](C:/Users/vangeaux/Desktop/.git_projects/bookorbit-android/docs/testing.md)
- [docs/architecture.md](C:/Users/vangeaux/Desktop/.git_projects/bookorbit-android/docs/architecture.md)
- [app/src/main/java/com/bookorbit/android/BookOrbitRepository.kt](C:/Users/vangeaux/Desktop/.git_projects/bookorbit-android/app/src/main/java/com/bookorbit/android/BookOrbitRepository.kt)
- [app/src/main/java/com/bookorbit/android/BookOrbitApp.kt](C:/Users/vangeaux/Desktop/.git_projects/bookorbit-android/app/src/main/java/com/bookorbit/android/BookOrbitApp.kt)
- [app/src/main/java/com/bookorbit/android/AppCoordinator.kt](C:/Users/vangeaux/Desktop/.git_projects/bookorbit-android/app/src/main/java/com/bookorbit/android/AppCoordinator.kt)
- [app/src/main/java/com/bookorbit/android/ActiveReaderStore.kt](C:/Users/vangeaux/Desktop/.git_projects/bookorbit-android/app/src/main/java/com/bookorbit/android/ActiveReaderStore.kt)
- [app/src/main/java/com/bookorbit/android/LastSyncedProgressStore.kt](C:/Users/vangeaux/Desktop/.git_projects/bookorbit-android/app/src/main/java/com/bookorbit/android/LastSyncedProgressStore.kt)
- [app/src/main/java/com/bookorbit/android/ProgressQueueStore.kt](C:/Users/vangeaux/Desktop/.git_projects/bookorbit-android/app/src/main/java/com/bookorbit/android/ProgressQueueStore.kt)
- [app/src/test/java/com/bookorbit/android/BookOrbitRepositoryHelpersTest.kt](C:/Users/vangeaux/Desktop/.git_projects/bookorbit-android/app/src/test/java/com/bookorbit/android/BookOrbitRepositoryHelpersTest.kt)
- [app/src/test/java/com/bookorbit/android/AppCoordinatorTest.kt](C:/Users/vangeaux/Desktop/.git_projects/bookorbit-android/app/src/test/java/com/bookorbit/android/AppCoordinatorTest.kt)
- [app/src/test/java/com/bookorbit/android/LastSyncedProgressStoreTest.kt](C:/Users/vangeaux/Desktop/.git_projects/bookorbit-android/app/src/test/java/com/bookorbit/android/LastSyncedProgressStoreTest.kt)
- [app/src/main/java/com/bookorbit/android/DownloadStore.kt](C:/Users/vangeaux/Desktop/.git_projects/bookorbit-android/app/src/main/java/com/bookorbit/android/DownloadStore.kt)
- [app/src/test/java/com/bookorbit/android/ProgressQueueStoreTest.kt](C:/Users/vangeaux/Desktop/.git_projects/bookorbit-android/app/src/test/java/com/bookorbit/android/ProgressQueueStoreTest.kt)
- [app/src/test/java/com/bookorbit/android/DownloadStoreTest.kt](C:/Users/vangeaux/Desktop/.git_projects/bookorbit-android/app/src/test/java/com/bookorbit/android/DownloadStoreTest.kt)

## Highest-priority next steps

1. Retest explicit sign-out on device after commit `863b659` to confirm the cached-browser fallback is fully fixed.
2. Confirm session persistence and login recovery behavior after app restart on a live server.
3. Confirm local-only reopen behavior on a real offline audiobook flow after process restart.
4. Add broader integration or instrumentation coverage for login, browser loading, and reader flows.
5. Verify queue replay and resume behavior again on additional real media types beyond the already-tested content.

## Suggested next manual validation pass

1. Install the latest debug APK built from commit `863b659`.
2. Log into the live server.
3. Tap `Sign out` and confirm the app remains on login instead of opening the cached browser snapshot.
4. Re-sign in and relaunch the app to confirm session persistence behavior.
5. Repeat the tested offline resume/sync flow for an audiobook after process restart.
6. Repeat the same offline reopen and sync flow for a PDF and CBZ if available.

## Known limitations

- EPUB support is still basic despite theme, font, and chapter controls.
- Comic support is limited to local or authenticated-cache CBZ rendering; CBR is still not implemented.
- Login completion detection is still based on polling authenticated APIs.
- Queue replay behavior is validated for the tested real content flow, but it has not yet been broadened across every media type and restart path.
- The local-only bootstrap/open logic is covered by JVM tests, but it still needs device-side validation with real offline audio content after restart.
- Parsing hardening is improved but not complete across every nullable or variant BookOrbit payload shape.
