# Handover

Last updated: 2026-07-13

## Latest user-reported blocker: Top/Bottom reader padding

The user reports that changing the EPUB reader's Top and Bottom padding values still does not visibly move the rendered chapter content or change the usable reading area. The reader content is now expected to render, but this padding behavior remains unresolved and blocks meaningful padding validation.

Reproduction on the latest debug APK:

1. Open a readable EPUB.
2. Tap the center to open reader options.
3. Move Top or Bottom a large distance away from the 15% default.
4. Observe the chapter text and repagination.

Expected: the selected edge changes independently, the text inset changes visibly, and the chapter repaginates while the options remain open. Actual: the Top/Bottom change is not reflected on the reading surface. Do not mark this item complete until it is verified on the target device with visible content.

Relevant implementation locations are `EpubReaderView` and `styleEpubHtml` in `BookOrbitApp.kt`, plus `EpubReaderPaddingStore.kt`. Padding persistence and padding application are separate concerns: reopening a book may retain the stored value while the rendered Top/Bottom inset still fails to change.

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
  - login is done through native username/password fields and the BookOrbit login API
- Session handling is implemented at a basic level:
  - WebView and API client share cookies
  - login completion is inferred by probing authenticated endpoints
  - explicit sign-out now waits for cookie clearing to complete and stays on login instead of bouncing into cached browser state
  - app restart now preserves the authenticated session in manual testing and returns to the app without forcing a fresh login
  - reachable-but-unauthenticated startup now opens Login instead of incorrectly treating cached Home as an offline fallback
  - explicit Sign in from cached Home disables cached fallback until authentication succeeds, preventing the login screen from immediately bouncing away
- Library browsing is implemented:
  - libraries load from `GET /api/v1/libraries`
  - books load from `POST /api/v1/libraries/{id}/books`
  - live payload parsing has been validated against a working BookOrbit deployment
  - the post-login browser now opens on a native Home feed with an integrated search bar and hamburger drawer
  - the drawer contains Home, Libraries with library children, Series, Authors, Options, and sign-in/log-out
  - Home derives Keep Reading, On Deck, recent book, and recent series shelves from optional progress, series, read-state, and timestamp metadata
  - Home shelves are scoped to the selected library page, while search uses the global BookOrbit book-query endpoint
  - authenticated book covers load through the shared cookie-aware API client
  - card covers use thumbnails, background downsampling, serialized initial loading, and a shared 16 MB bitmap cache to avoid scroll jank and unbounded decoded-image memory
  - series shelf cards open series details, and books open details with explicit Read/Continue, Preview, and download actions
  - book details now load BookOrbit's full detail payload and render subtitle, creators, synopsis, genres/tags, publication data, identifiers, rating, library, format, and file metadata when available
  - series details now load the complete ordered server series with authors, book/read counts, completion, possible gaps, and first-book synopsis instead of relying on the current shelf page
  - series requests now respect BookOrbit's 100-item maximum and merge additional pages; the previous 200-item request was rejected by the server and caused blank series details
  - Accel World pagination now returns the complete 27-book series in the expected order, with distinct-ID and mismatch coverage in the pagination tests
  - series details also show genre/tag context from the first book
  - Back from a book opened inside a series now returns to that series before leaving the detail flow
  - Series Details and Author Details now use adaptive poster-card grids with full-width headers, series numbers, progress strips, read/in-progress states, and offline markers
  - Book and Series descriptions now collapse to four rendered lines when needed, expose an inline Expand/Collapse affordance, and toggle from taps on the description text or affordance
  - shelf cards were reduced to roughly two-thirds of the original candidate size after device feedback
  - the Android status bar is hidden for an immersive app window with transient swipe reveal
- Reading and listening paths are implemented:
  - audio uses ExoPlayer
  - audio UI includes play/pause, skip, speed, and resume support
  - PDF uses `PdfRenderer`
  - PDF reader includes zoom, pan, and reset controls
  - EPUB uses local extraction plus OPF spine parsing and chapter-by-chapter `WebView` rendering
  - EPUB reader includes chapter picking plus theme and font-size controls
  - EPUB now uses fullscreen viewport pagination rather than vertical chapter scrolling
  - EPUB pagination now uses an explicit full-viewport page strip and translates one viewport at a time; the previous CSS column geometry that could render only one line per page has been removed
  - pagination is recalculated after fonts, images, and viewport changes, and backward chapter-boundary navigation remains pinned to the previous chapter's final page while resources finish loading
  - left/right outer-quarter taps and left/right swipes navigate pages, while the center toggles transient reader controls; swipe clicks are suppressed to avoid double navigation
  - EPUB progress percentage includes the current paginated screen, but reopen still restores to the chapter rather than the exact in-chapter page
  - downloaded EPUB local images now render correctly in the reader WebView
  - CBZ comics can be opened from local downloads or authenticated cache copies
  - active reader state is persisted and can be restored after recreation or restart
  - reader reopen now falls back to last-synced progress after queue replay, so tested resume state survives successful sync
  - restore precedence now prefers the furthest-ahead local active-reader progress over older queued or last-synced progress snapshots
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
  - login recovery UI and change-server routing
  - populated live-browser and browser-loading states
  - cached offline browser UI behavior
- The Android debug build is passing on this machine with `.\gradlew.bat assembleDebug`
- The local JVM unit test task is passing on this machine with `.\gradlew.bat testDebugUnitTest`
- The Android instrumentation test target compiles on this machine with `.\gradlew.bat assembleDebugAndroidTest`
- The EPUB pagination fix passes `.\gradlew.bat assembleDebug`, `.\gradlew.bat testDebugUnitTest`, and `.\gradlew.bat assembleDebugAndroidTest`; real-device EPUB validation remains pending because no Android device was attached during the fix
- The Android release build is passing on this machine with `.\gradlew.bat assembleRelease`
- The latest reader fix and swipe regression coverage pass `testDebugUnitTest`, `assembleDebug`, `assembleDebugAndroidTest`, and `assembleRelease`; real-device EPUB validation remains pending
- A manual app test matrix now exists in [testing.md](C:/Users/vangeaux/Desktop/.git_projects/bookorbit-android/docs/testing.md)
- Recent manual device validation results:
  - physical-device launcher icon is correct
  - downloads work and tested local reopen works in airplane mode
  - progress sync after reconnect is visible in the BookOrbit web app
  - tested reader resume now restores the last position
  - downloaded EPUB images now render correctly
  - the previous sign-out cached-browser fallback bug was reproduced and then fixed in code after testing
  - explicit sign-out now returns to login with the `Change server` action visible
  - app relaunch after sign-in now returns to the app without forcing a fresh login
  - closing and reopening the tested EPUB, and fully closing and relaunching the app, both restore the last reading session correctly after the restore-precedence fix
  - audiobook, PDF, and CBZ-specific testing is deferred because representative sample files are not currently available

## UI/UX workstream status

- UI/UX discussion can begin now; the functional baseline is no longer a blocker.
- A dark-first, system-aware Audiobookshelf/Plex-inspired direction is now approved for the next workstream; the detailed behavior and visual constraints are recorded in [native-app-expansion-plan.md](C:/Users/vangeaux/Desktop/.git_projects/bookorbit-android/docs/native-app-expansion-plan.md).
- Checkpoint 1 is product direction and shared design-system tokens.
- An editorial-observatory design-system candidate is implemented with explicit light/dark palettes, typography, shapes, shared top bars, and shared status surfaces.
- Setup, login, and browser screens use the candidate; it still needs on-device visual review before Checkpoint 1 is marked complete.
- A native mobile Home/search/drawer candidate based on the requested Komga information hierarchy and Audiobookshelf-style mobile interaction pattern is implemented and awaiting device review.
- Rich native book/series detail candidates based on the main BookOrbit content hierarchy are implemented and ready for device review and UI adjustment.
- Checkpoints 2-4 cover setup/login/app shell, library browsing, and the EPUB reader.
- Audiobook, PDF, and CBZ-specific UI work remains deferred until representative samples are available.
- Detailed checkpoints and regression guardrails are in [ui-ux.md](C:/Users/vangeaux/Desktop/.git_projects/bookorbit-android/docs/ui-ux.md).

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
  - `GET /api/v1/books/{id}`
  - `GET /api/v1/series/{seriesId}/books`
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
- [docs/ui-ux.md](C:/Users/vangeaux/Desktop/.git_projects/bookorbit-android/docs/ui-ux.md)
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

1. Fix and validate Top/Bottom EPUB reader padding on the real sample, including visible text/image inset changes and repagination; then recheck full-viewport pagination, left/right swipes, chapter boundaries, offline images, progress sync, and chapter restore.
2. Run the complete automated and device regression matrix for native login/session recovery, offline catalogs, Preview isolation, and server-forced session expiry.
3. Continue UI refinement for the approved dark-first design direction, including description density, accessibility, and large-text behavior.
4. Defer format-specific audiobook, PDF, and CBZ visual validation until representative samples are available.

## Suggested next UI validation pass

1. Review four-line expandable descriptions and adaptive Series/Author grids on a real device.
2. Validate setup, native login, Home, Libraries, Series, Authors, Options, details, and EPUB at narrow width and large font scale.
3. Recheck offline indicators, session actions, Preview isolation, download actions, and EPUB swipe/resume after UI changes.
4. Defer format-specific audiobook, PDF, and CBZ visual validation until sample files are available.

## Known limitations

- The fullscreen EPUB pagination display regression has been addressed in code by replacing fragile document-level column geometry with an explicit translated full-viewport page strip. Do not treat the reader checkpoint as device-valid until visible content, page totals, tap/swipe navigation, images, and repagination are confirmed against the real sample.
- Exact in-chapter page restore and RTL direction controls also remain unimplemented.
- Comic support is limited to local or authenticated-cache CBZ rendering; CBR is still not implemented.
- Native login and catalog/device flows still need real-server and real-device validation after the expansion work.
- Queue replay behavior is validated for the tested real content flow, but it has not yet been broadened across every media type and restart path.
- The local-only bootstrap/open logic is covered by JVM tests, but audiobook, PDF, and CBZ device validation is deferred until representative files are available.
- Parsing hardening is improved but not complete across every nullable or variant BookOrbit payload shape.
