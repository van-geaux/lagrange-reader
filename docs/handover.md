# Handover

Last updated: 2026-07-09

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
  - CBZ comics can be opened from local downloads or authenticated cache copies
  - active reader state is persisted and can be restored after recreation or restart
  - reader controls and status surfaces have improved accessibility semantics
- Offline support is partially implemented:
  - downloads are stored locally
  - local files are preferred when present
  - stale or zero-byte local download records are pruned before reuse
  - EPUB, PDF, and CBZ can be reopened from authenticated cache copies before full download
  - cached library and book browser state is used for offline fallback
  - cached offline browser states disable live-only actions for non-downloaded titles
  - progress updates are queued locally and replayed later
  - sync queue compaction is implemented
- Queue and storage hardening is implemented:
  - transient sync failures use WorkManager retry backoff
  - auth-blocked sync queues remain persisted without burning retries
  - last-synced progress markers suppress duplicate or stale submissions
  - malformed library/book payloads are surfaced as user-facing errors
  - stored progress percentages are normalized to a consistent 0-100 scale across media types
  - reader opening fails earlier with explicit user-facing messages when no readable local or cached content can be prepared
- Focused JVM unit coverage exists for:
  - `ActiveReaderStore`
  - `DownloadStore`
  - `ProgressQueueStore`
  - repository helper and payload normalization paths
- The Android debug build is passing on this machine with `.\gradlew.bat assembleDebug`
- The local JVM unit test task is passing on this machine with `.\gradlew.bat testDebugUnitTest`
- The Android release build is passing on this machine with `.\gradlew.bat assembleRelease`
- A manual app test matrix now exists in [testing.md](C:/Users/vangeaux/Desktop/.git_projects/bookorbit-android/docs/testing.md)

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
- [app/src/main/java/com/bookorbit/android/BookOrbitRepository.kt](C:/Users/vangeaux/Desktop/.git_projects/bookorbit-android/app/src/main/java/com/bookorbit/android/BookOrbitRepository.kt)
- [app/src/main/java/com/bookorbit/android/BookOrbitApp.kt](C:/Users/vangeaux/Desktop/.git_projects/bookorbit-android/app/src/main/java/com/bookorbit/android/BookOrbitApp.kt)
- [app/src/main/java/com/bookorbit/android/AppCoordinator.kt](C:/Users/vangeaux/Desktop/.git_projects/bookorbit-android/app/src/main/java/com/bookorbit/android/AppCoordinator.kt)
- [app/src/main/java/com/bookorbit/android/ActiveReaderStore.kt](C:/Users/vangeaux/Desktop/.git_projects/bookorbit-android/app/src/main/java/com/bookorbit/android/ActiveReaderStore.kt)
- [app/src/main/java/com/bookorbit/android/ProgressQueueStore.kt](C:/Users/vangeaux/Desktop/.git_projects/bookorbit-android/app/src/main/java/com/bookorbit/android/ProgressQueueStore.kt)
- [app/src/main/java/com/bookorbit/android/DownloadStore.kt](C:/Users/vangeaux/Desktop/.git_projects/bookorbit-android/app/src/main/java/com/bookorbit/android/DownloadStore.kt)
- [app/src/test/java/com/bookorbit/android/ProgressQueueStoreTest.kt](C:/Users/vangeaux/Desktop/.git_projects/bookorbit-android/app/src/test/java/com/bookorbit/android/ProgressQueueStoreTest.kt)
- [app/src/test/java/com/bookorbit/android/DownloadStoreTest.kt](C:/Users/vangeaux/Desktop/.git_projects/bookorbit-android/app/src/test/java/com/bookorbit/android/DownloadStoreTest.kt)

## Highest-priority next steps

1. Run end-to-end offline tests against real content.
2. Verify progress replay to the live BookOrbit server after reconnect.
3. Verify reader behavior has no accidental API dependency during offline local reopen.
4. Confirm session persistence and login recovery behavior after app restart.
5. Add integration or instrumentation coverage for login, browser loading, and reader flows.

## Suggested next manual validation pass

1. Log into the live server.
2. Open a real EPUB without downloading it first.
3. Download the same EPUB and reopen it from local storage.
4. Put the device offline and continue reading.
5. Reconnect and verify the server reflects the latest progress.
6. Repeat the same flow for one PDF, one audiobook, and one CBZ if available.

## Known limitations

- EPUB support is still basic despite theme, font, and chapter controls.
- Comic support is limited to local or authenticated-cache CBZ rendering; CBR is still not implemented.
- Login completion detection is still based on polling authenticated APIs.
- Queue replay behavior has not yet been fully verified end to end on real reading activity.
- Parsing hardening is improved but not complete across every nullable or variant BookOrbit payload shape.
