# Handover

Last updated: 2026-07-06

## Repository

- GitHub: `https://github.com/van-geaux/lagrange-reader`
- Local path: `C:\Users\vangeaux\Desktop\.git_projects\bookorbit-android`
- Main branch: `main`

## Current implementation state

- Server bootstrap is implemented:
  - first open asks for the BookOrbit server URL
  - server reachability is checked before entering login
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
  - PDF uses `PdfRenderer`
  - EPUB uses local extraction plus OPF spine parsing and chapter-by-chapter `WebView` rendering
- Offline support is partially implemented:
  - downloads are stored locally
  - local files are preferred when present
  - progress updates are queued locally and replayed later
  - sync queue compaction is implemented
- The Android debug build is passing on this machine with `.\gradlew.bat assembleDebug`

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
- [app/src/main/java/com/bookorbit/android/BookOrbitRepository.kt](C:/Users/vangeaux/Desktop/.git_projects/bookorbit-android/app/src/main/java/com/bookorbit/android/BookOrbitRepository.kt)
- [app/src/main/java/com/bookorbit/android/BookOrbitApp.kt](C:/Users/vangeaux/Desktop/.git_projects/bookorbit-android/app/src/main/java/com/bookorbit/android/BookOrbitApp.kt)
- [app/src/main/java/com/bookorbit/android/EpubSupport.kt](C:/Users/vangeaux/Desktop/.git_projects/bookorbit-android/app/src/main/java/com/bookorbit/android/EpubSupport.kt)
- [docs/bookorbit-api.md](C:/Users/vangeaux/Desktop/.git_projects/bookorbit-android/docs/bookorbit-api.md)

## Highest-priority next steps

1. Run end-to-end offline tests against real content.
2. Verify progress replay to the live BookOrbit server after reconnect.
3. Improve EPUB progress restoration beyond chapter-level resume.
4. Add reader loading/error states and unsupported-format handling.
5. Add tests for parsing, progress mapping, and queue behavior.

## Suggested next manual validation pass

1. Log into the live server.
2. Open a real EPUB without downloading it first.
3. Download the same EPUB and reopen it from local storage.
4. Put the device offline and continue reading.
5. Reconnect and verify the server reflects the latest progress.
6. Repeat the same flow for one PDF and one audiobook if available.

## Known limitations

- EPUB support is basic and chapter-based.
- Comic/CBZ support is not implemented.
- Login completion detection is still based on polling authenticated APIs.
- Queue replay behavior has not yet been fully verified end to end on real reading activity.
