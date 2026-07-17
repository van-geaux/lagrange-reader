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

Working prototype (`0.2.0`, pre-1.0). The app shell, API wiring, local download tracking, sync queue, authenticated-session bootstrap, sign-out/session reset behavior, and EPUB/PDF/audio/CBZ/CBR/CB7 reading paths are in place. The visible app brand is Lagrange; the subtitle “a BookOrbit reader” appears on the splash/loading presentation only. The native browser uses a Plex-inspired Home/Libraries/More bottom bar, a Home-only top logo/search/profile bar, a library-name selector with dropdown affordance, a dedicated search layer, an About destination, a Local books destination, and a Library view split into Recommended and cache-backed Browse tabs. Global search results intentionally use compact list rows and expose Mark as read/unread through both a visible three-dot action and long-press. Library, Series, and Authors use compact adaptive poster cards; Series resolves BookOrbit's `coverBookIds` through the representative book thumbnail endpoint instead of assuming a nonexistent Series image route. Browse renders the complete selected-library catalog from a server-scoped Room cache, applies filters and sorts locally, supports series collapsing ordered by series name with an exact grouped-book count on each representative card, and uses BookOrbit's absolute jump-bucket indexes for its complete #/A–Z rail. Series also retrieves every page for the active server filter into a deduplicated in-memory catalog, replaces Load more with complete navigation, and exposes a local #/A–Z or Z–A rail when sorted by Name. Reopen displays cached metadata before network checks finish; refresh keeps that catalog usable while a full server reconciliation updates only changed rows and removes deleted titles atomically. Swipe-to-refresh is available on both Home and Libraries; versioned local cover thumbnails, versioned on-demand book details, and the raised More menu remain in place. Options is available from the profile menu above Log out. The EPUB reader includes exact local in-chapter resume, an app-wide light/dark/sepia choice that survives reader close and app restart, and four independent percentage padding controls for Top, Bottom, Left, and Right; 15% is the default for each edge, 100% represents one quarter of the relevant screen dimension, and padding values persist per book/file. After the clipped-wrapper candidate rendered the real EPUB blank, the current fix restores the last device-known-good single visible-overflow page strip. Top/Bottom padding resizes the Android WebView outside the EPUB HTML, while Left/Right update the strip in place; target-device testing confirms that chapter content renders and all four padding controls visibly update the reading surface. Home derives its shelves, including Currently reading, from the server-wide cached catalog, while Library Recommended and Browse remain scoped to the selected library. Login access tokens are persisted and sent on authenticated API, cover, download, and reader-cache requests; physical-device session-expiry recovery has been validated. Focused JVM coverage exercises coordinator bootstrap, cache-first reconciliation, catalog pagination, cached offline fallback, post-login resume flows, progress restoration, exact library and Series navigation helpers, alternate progress parsing, token extraction, and recoverable browser-load failures. Compose/instrumentation coverage includes setup, login recovery, live/loading browser states, cached offline behavior, Room reconciliation, the Home pull-to-refresh gesture, global-search list-row actions, Series jump navigation without Load more, collapsed-series count presentation, the profile Options menu, EPUB theme persistence, and WebView reader geometry across external vertical resize and page translation. Comic routing now awaits physical-device validation; audiobook-specific validation remains deferred until a representative sample is available.

Book details now use compact horizontally swipeable actions: Read, Preview, Download, and Delete local retain visible labels beside clear icons. Long titles are constrained to five rows with expansion, series name/index metadata remains visible, and the full-screen cover viewer dismisses from any screen tap or Android Back. Library grids support multi-book selection with overlap-safe bulk read/unread actions and stale-selection pruning. Genre chips open fully paginated server-filtered Books or Series results; tags remain informational. Active per-file downloads expose byte progress, percentage/linear or indeterminate state, cancel guidance, and retry failure status, and completed/auth-interrupted downloads clear active state. Authentication currently remains native username/password; direct OIDC/SSO support is deferred.

Latest device feedback confirms swipeable tiles, Series navigation, wrapped genre/tag metadata, grouped metadata cards, text wrapping, reader options, cache-first opening, exact jumps, thumbnail warnings, rapid-scroll cancellation, detail loading, and airplane-mode behavior. The manga unsupported-format regression is fixed in code: bare `cbz`, `cbr`, and `cb7` format tokens now route to the comic reader instead of UNKNOWN. Online CBZ/CBR/CB7 uses BookOrbit's authenticated comic page endpoints; local ZIP comics, including mislabeled ZIP archives, extract and render offline. Downloaded CBR/CB7 requires a server connection for page extraction in this build because the app does not bundle RAR/7z extraction. Physical-device validation remains pending. Audiobook testing remains deferred until a representative sample is available. Direct OIDC/SSO remains open/deferred.

Home and Library Recommended status messages now dismiss immediately through an explicit X button or horizontal swipe. Recently read contains only completed books, excludes titles still in Currently reading, orders by last-read/updated/title, and is capped at 12 items.

Options now has native grouped Interface and Data sections. Interface persists lock-current-orientation, haptic feedback, app theme (Follow system/Light/Dark), default opening screen (Home/Library/Local books), and Reduce motion settings. Data persists downloads-over-cellular (Always/Never/Ask), reports downloaded/disposable-cache sizes, offers confirmed Clear cache that preserves downloads and downloaded-book metadata, controls current background cover work (and future metadata work) with Any network/Wi-Fi only/Disabled, and confirms local-copy deletion by default. Target-device feedback validates lock-current-orientation, the default opening screen, Reduce motion, downloads-over-cellular behavior, storage/cache clearing, and local-copy delete confirmation. Haptics were not perceptible: the current implementation explicitly invokes them only from Options switch/selection rows and otherwise relies on Android/Foundation haptics for supported long-press interactions; ordinary navigation, book taps, and page turns have no explicit feedback. Haptic perceptibility and consistent interaction coverage remain open, as do theme and background-network-policy device validation.

The browser/local-state work order is implemented: Delete local immediately changes the still-open detail action back to Download, refreshes Local books, and updates Room catalog plus legacy snapshot `localPath` state; Local books can recover cover and related metadata from the latest cached rich detail; and opening Options from a book detail dismisses the retained detail. Home now has a separate server-wide `homeBooks` collection restored from all Room catalogs with legacy snapshot fallback and current download paths. Refresh reconciles the selected library first, then incrementally replaces every other library slice while retaining cached slices and reporting partial-cache failures; Libraries Recommended/Browse remain selected-library scoped. Progress, read/unread, and local-path changes update both collections, restoring genuinely in-progress titles from any library to Currently reading. Physical-device validation remains pending for these browser changes.

The latest comic-routing verification passes 178 JVM tests across 28 suites with zero failures/errors/skips; `lintDebug`, `assembleDebug`, and `assembleDebugAndroidTest` pass, including compiled Compose coverage for remote CBR page navigation. Fresh APK: `app/build/outputs/apk/debug/app-debug.apk`.

Target-device follow-up confirms Read/Preview labels, live download progress, long-title expansion, series-index presentation, multi-selection, and correct layout-derived EPUB whole-book progress. Download/Delete local labels, immediate open-detail reconciliation after both download and local deletion, removal of the misleading Tag tap affordance, dismissible Home messages, and completed-only Recently read are implemented; the deletion refresh still needs target-device confirmation. Remaining EPUB validation covers timing/stability of page measurement on representative devices. Direct OIDC/SSO authentication remains deferred. Audiobook testing remains deferred without a sample. Comic routing is implemented and awaiting device validation; offline CBR/CB7 extraction remains an optional future enhancement.

Progress reconciliation now follows BookOrbit's current API contract: card-level scalar `readingProgress` and nested `readStatus` are parsed, every reader and persisted payload uses one canonical 0-100 percentage scale, and newer reread/backward events can repair an older inflated marker. Foreground and WorkManager sync now share queue locks and acknowledge only the exact posted event IDs, so a newer page event cannot be erased by an older in-flight replay; rapid callbacks debounce into a trailing worker, and an unknown percentage is never submitted as a valid zero-progress update. Every accepted progress event also explicitly writes BookOrbit status as `reading`, or `read` at 99.5% and above; the queue acknowledges the event only after both writes succeed, preventing BookOrbit's status-backed Currently Reading widget from remaining empty when server-side automatic status promotion fails. After successful queue replay and a fresh page load, server progress replaces temporary local overlays so BookOrbit-side changes can flow back into Lagrange. Bidirectional target-server synchronization has been validated.

Book cards now expose visible overflow actions and the same menu on long-press across Home shelves and individual Library, Series, Author, and Local Books posters. Mark as read writes BookOrbit's `read` status, preserves the current position, and clears older queued status work; Mark as unread clears progress through the normal-user reset flow. Currently Reading retains its dedicated removal action. Removing or marking a title unread deletes the primary/current file progress (or writes explicit zero audio progress), sets the status to `unread`, clears its lifecycle dates, then clears matching local queued, last-synced, exact EPUB resume, active-reader, detail, snapshot, and Room-catalog progress so stale local work cannot restore it. Refresh parsing also ignores stale page/position/status timestamps when BookOrbit explicitly reports `unread` with no positive progress, preventing the reset status record's fresh `updatedAt` from repopulating Home. This fallback does not delete BookOrbit reading-session history or progress for additional non-primary files; BookOrbit reserves its broader reading-state reset endpoint for accounts with metadata-edit permission. On Deck now requires at least one completed book in a series, selects the first unread volume, and hides the shelf entry while that volume is actively being read. Mark as read/unread and stale-state refresh behavior have been validated on the target device and server.

EPUB, PDF, and comic readers keep the display awake while their reading screen is visible, then restore the prior device timeout behavior when the user leaves. Audiobook playback continues to allow normal screen sleep. EPUB keeps Android's native top status bar visible for accurate battery and network indicators while the bottom navigation bar remains immersive; status icon contrast follows the selected reader theme. An always-visible, theme-matched footer reports weighted completion, the current chapter page, and a measured whole-book current/total page count; while hidden chapter measurements settle it reports `Book pages calculating`. Counts incorporate viewport, external top/bottom geometry, left/right margins, font scale, theme, and assets, and reset when those inputs change. EPUB options use one rounded, theme-matched bottom sheet with grouped reading-position, appearance, text-size, and page-margin controls. Continue reading dismisses the sheet while remaining in the book; Close book exits the reader. Tapping exposed book content or pressing Android Back dismisses options before Back exits the reader.

Library metadata now uses Room 2.6.1, which fits the project's existing Kotlin 1.9.24/AGP 8.5 toolchain. BookOrbit does not expose a reliable catalog revision/delta contract, so refresh retrieves every metadata page to detect additions, removals, and progress changes. Large refreshes request 100 books per page and use ordered batches of at most four concurrent page requests; total and duplicate checks still retry a catalog that changes during traversal. The local transaction compares rows and writes only additions, removals, ordering changes, or changed metadata. The old first-page JSON snapshot remains a migration fallback, not the active Browse data source.

Large-library images now follow a cache-first pipeline. Visible cover requests are cancellable when cards leave composition, decoded thumbnails use a bounded memory cache, and persisted thumbnail keys include the catalog update version. After successful catalog reconciliation, a durable unmetered-network worker fills only missing or changed thumbnails in 50-image batches. Rich book details are cached for every opened title and reused until that title's catalog version changes; they are not eagerly fetched for all titles because that would add roughly one API request per book.

## Build

The current 0.2.0 pass also retries authenticated requests through a refresh-cookie renewal attempt before returning to login, retries and caches catalog thumbnails, updates Continue reading immediately after reading, flushes reader progress before the browser refreshes, and exposes server-aligned filters in Library Browse and Series with matching local filtering for Local books. Browse filters now run against the complete Room catalog instead of triggering lazy server pages; Series remains server-backed. It uses more compact typography and lays out book cards as title/series/index metadata rows. EPUB reader padding is stored independently per book/file; Top/Bottom change the actual Android WebView bounds and trigger its existing resize repagination, avoiding vertical clipping inside the known-good visible-overflow HTML renderer. The restored reader rendering and visible padding behavior pass target-device testing. JVM tests and Android instrumentation compilation pass, while the refresh endpoint, long-lived session behavior, catalog reconciliation, exact jump behavior, and filter results still need device validation against the target BookOrbit server.

The current book-detail UI pass has passed `testDebugUnitTest`, `lintDebug`, `assembleDebug`, and `assembleDebugAndroidTest`. The debug APK is rebuilt before each manual-test handoff.

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

Server policy: explicit `http://` and `https://` BookOrbit URLs are accepted. Bare remote hostnames default to HTTPS; bare localhost and common Android emulator loopback aliases default to HTTP. Cleartext HTTP does not protect credentials, session tokens, metadata, progress, or streamed content from interception, so HTTPS remains strongly recommended outside a trusted network.

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
