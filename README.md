# Lagrange Reader

Android client for BookOrbit focused on reading and listening.

## Current scope

- Connect to a user-provided BookOrbit server
- Authenticate through the server login flow
- Browse libraries and books
- Stream supported content
- Download books for offline reading or listening
- Queue progress updates offline and sync them later

## Project status

Working prototype (`0.2.0`, pre-1.0). The app shell, API wiring, local download tracking, sync queue, authenticated-session bootstrap, sign-out/session reset behavior, and EPUB/PDF/audio/CBZ reading paths are in place. The visible app brand is Lagrange; the subtitle “a BookOrbit reader” appears on the splash/loading presentation only. The native browser uses a Plex-inspired Home/Libraries/More bottom bar, a Home-only top logo/search/profile bar, a library-name selector with dropdown affordance, a dedicated search layer, an About destination, a Local books destination, and a Library view split into Recommended and cache-backed Browse tabs. Library, Series, and Authors use compact adaptive poster cards. Browse renders the complete selected-library catalog from a server-scoped Room cache, applies filters and sorts locally, supports series collapsing ordered by series name, and uses BookOrbit's absolute jump-bucket indexes for its complete #/A–Z rail. Reopen displays cached metadata before network checks finish; refresh keeps that catalog usable while a full server reconciliation updates only changed rows and removes deleted titles atomically. Swipe-to-refresh, locally cached cover thumbnails, cached downloaded-book metadata, and the raised More menu remain in place. Options is available from the profile menu above Log out. The EPUB reader includes exact local in-chapter resume and four independent percentage padding controls for Top, Bottom, Left, and Right; 15% is the default for each edge, 100% represents one quarter of the relevant screen dimension, and padding values persist per book/file. After the clipped-wrapper candidate rendered the real EPUB blank, the current fix restores the last device-known-good single visible-overflow page strip. Top/Bottom padding resizes the Android WebView outside the EPUB HTML, while Left/Right update the strip in place; target-device testing confirms that chapter content renders and all four padding controls visibly update the reading surface. Home derives an in-progress Continue reading shelf from tolerant server progress fields, while Library Recommended omits the duplicate Home/library heading. Login access tokens are persisted and sent on authenticated API, cover, download, and reader-cache requests; physical-device session-expiry validation remains pending. Focused JVM coverage exercises coordinator bootstrap, cache-first reconciliation, catalog pagination, cached offline fallback, post-login resume flows, progress restoration, exact library navigation helpers, alternate progress parsing, token extraction, and recoverable browser-load failures. Compose/instrumentation coverage includes setup, login recovery, live/loading browser states, cached offline behavior, Room reconciliation, the profile Options menu, and WebView reader geometry across external vertical resize and page translation. Other media-specific validation remains deferred until representative samples are available.

Progress reconciliation now follows BookOrbit's current API contract: card-level scalar `readingProgress` and nested `readStatus` are parsed, every reader and persisted payload uses one canonical 0-100 percentage scale, and newer reread/backward events can repair an older inflated marker. Foreground and WorkManager sync now share queue locks and acknowledge only the exact posted event IDs, so a newer page event cannot be erased by an older in-flight replay; rapid callbacks debounce into a trailing worker, and an unknown percentage is never submitted as a valid zero-progress update. After successful queue replay and a fresh page load, server progress replaces temporary local overlays so BookOrbit-side changes can flow back into Lagrange. Target-server bidirectional validation remains pending.

Library metadata now uses Room 2.6.1, which fits the project's existing Kotlin 1.9.24/AGP 8.5 toolchain. BookOrbit does not expose a reliable catalog revision/delta contract, so refresh retrieves every metadata page to detect additions, removals, and progress changes; the local transaction compares rows and writes only additions, removals, ordering changes, or changed metadata. The old first-page JSON snapshot remains a migration fallback, not the active Browse data source.

## Build

The current 0.2.0 pass also retries authenticated requests through a refresh-cookie renewal attempt before returning to login, retries and caches catalog thumbnails, updates Continue reading immediately after reading, flushes reader progress before the browser refreshes, and exposes server-aligned filters in Library Browse and Series with matching local filtering for Local books. Browse filters now run against the complete Room catalog instead of triggering lazy server pages; Series remains server-backed. It uses more compact typography and lays out book cards as title/series/index metadata rows. EPUB reader padding is stored independently per book/file; Top/Bottom change the actual Android WebView bounds and trigger its existing resize repagination, avoiding vertical clipping inside the known-good visible-overflow HTML renderer. The restored reader rendering and visible padding behavior pass target-device testing. JVM tests and Android instrumentation compilation pass, while the refresh endpoint, long-lived session behavior, catalog reconciliation, exact jump behavior, and filter results still need device validation against the target BookOrbit server.

The progress and catalog reconciliation fixes pass JVM tests and Android instrumentation-test compilation. The debug APK is rebuilt before each manual-test handoff.

From the project root:

```powershell
.\gradlew.bat assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Test

Run the local JVM unit suite with:

```powershell
.\gradlew.bat testDebugUnitTest
```

The release build also compiles locally with:

```powershell
.\gradlew.bat assembleRelease
```

The repository now also includes a GitHub Actions workflow that runs `testDebugUnitTest`, `lintDebug`, `assembleDebugAndroidTest`, and `assembleDebug` on pushes to `main` and on pull requests.

Server policy: production-style server URLs must use `https://`. Plain `http://` is only accepted for local development targets such as `localhost` and common Android emulator loopback aliases.

## Manual app testing

The current manual test entry point is documented in [docs/testing.md](./docs/testing.md).

Minimum baseline before manual app testing:

- `.\gradlew.bat assembleDebug` passes
- `.\gradlew.bat testDebugUnitTest` passes
- a reachable BookOrbit server is available
- a test account can sign in and access real content

## Local setup

Machine-specific SDK setup and environment notes are in [docs/setup.md](./docs/setup.md).

## Documentation

- [docs/README.md](./docs/README.md)
- [docs/architecture.md](./docs/architecture.md)
- [docs/setup.md](./docs/setup.md)
- [docs/privacy.md](./docs/privacy.md)
- [docs/release.md](./docs/release.md)
- [docs/bookorbit-api.md](./docs/bookorbit-api.md)
- [docs/testing.md](./docs/testing.md)
- [docs/ui-ux.md](./docs/ui-ux.md)
- [docs/roadmap.md](./docs/roadmap.md)
- [docs/handover.md](./docs/handover.md)
