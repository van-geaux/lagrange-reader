# Architecture

## Goal

`Lagrange Reader` is a native Android client for BookOrbit focused on reading and listening only. It does not manage metadata or server settings beyond initial server selection and login.

## App flow

The current app flow is:

1. User enters a BookOrbit server URL.
2. The app opens the server login page in a WebView.
3. The app checks `GET /api/v1/auth/me` during login-page navigation and on a short polling interval to determine whether login has succeeded and whether a persisted session is still valid.
4. After authentication, the app loads libraries and books.
5. The user can stream content, download it, reopen local files offline, and queue progress updates for later sync.
6. EPUB and PDF titles are opened from a local readable copy when the native reader requires file access.
7. EPUB titles are extracted into app cache and rendered chapter by chapter from the EPUB spine.

## Main components

### UI and navigation

- `MainActivity` boots the app and wires the graph.
- `AppCoordinator` owns screen state and orchestrates transitions.
- `AppCoordinator` now depends on a small repository-facing interface so bootstrap, login recovery, and browser fallback behavior can be covered by focused JVM tests without changing runtime wiring.
- `AppScreen` defines the app-level screens.
- `BookOrbitApp` renders the UI for setup, login, library browsing, and reader/player screens.
- Library browsing renders explicit loading, empty, and error states for library and book lists, with a refresh action.
- The browser top bar exposes sign-out for live sessions and a direct sign-in path from cached offline snapshots.
- Book cards show active download progress, failed-download retry, and cancel controls while a download is running.
- Downloaded book cards also expose a delete-local-copy action that removes the stored file and its persisted download record.
- Reader startup has an explicit loading screen, and unsupported reader types render a user-facing message instead of falling through to a generic WebView.
- Coordinator UI messages are normalized from typed auth, HTTP, TLS, timeout, DNS, and generic network failures instead of exposing raw exception text.
- If the first live browser load fails and no cached browser snapshot exists, the coordinator now shows an empty browser state with a recoverable error message so the refresh action can retry cleanly.
- When auth state cannot be confirmed during bootstrap or login refresh, cached browser state is used as the offline fallback instead of forcing an immediate return to login.
- A server-unavailable result may use cached offline Home, but an explicit unauthenticated response now opens Login because the reachable server rejected the saved session.
- Cached offline browser states mark non-downloaded titles as unavailable offline and suppress live-only actions like open-stream and download.
- Opening a title from a cached offline browser snapshot now forces a local-only reader build, so offline reopen does not fall back to authenticated cache fetches or stream URLs.
- Offline-first active-reader restore uses the same local-only reader stream suppression, so startup reopen does not quietly fall back to a live audio stream.
- When authentication expires during browser, open-book, or download flows, the coordinator routes back through login and resumes the intended action after the session is restored.

### Data and API layer

- `BookOrbitRepository` is the main integration layer with the BookOrbit server.
- It stores the selected server URL and selected library.
- Server URL validation now requires HTTPS for non-local hosts; cleartext HTTP is reserved for local development targets only.
- It loads libraries and books from the live API.
- It loads rich book details and complete ordered series pages on demand while preserving the selected cached summary as a failure fallback.
- Stored selected-library ids are validated against the latest available library list before the browser chooses a library to load.
- It resolves stream and download URLs for files.
- It prepares readable local copies for offline-first reader flows, including EPUB/PDF cache copies for authenticated reads before download.
- It translates local progress events into the server DTO shapes.
- It maps server `readingProgress` page and time-position fields back into reader resume state.
- Progress queue writes are dispatched on `Dispatchers.IO`; `AppCoordinator` debounces noisy reader/player progress events before calling the repository.

### Local persistence

- `DataStore` stores the configured server URL and selected library ID.
- Android network security config blocks cleartext traffic by default and allows it only for localhost and common emulator loopback hosts.
- Session reset now waits for cookie removal to complete before the app settles on the login screen, and explicit sign-out suppresses cached offline-browser fallback until a fresh login succeeds.
- Coordinator-side session and server resets also clear in-memory browser, download, and post-login destination state so stale UI targets are not reused after sign-out or server changes.
- `DownloadStore` stores downloaded file records scoped by server URL so server changes do not reuse unrelated local files by `fileId`.
- Download targets use sanitized book titles plus file ids, with extensions derived from BookOrbit format/MIME hints where available.
- Missing local download files are pruned from persisted download records before records are returned, so offline snapshots do not continue to show removed files as downloaded.
- Corrupted local EPUB/PDF/CBZ files are rejected before reader startup, and invalid persisted download records are dropped before falling back to authenticated cache copies when possible.
- Zero-byte local download and reader-cache files are discarded and refetched instead of being treated as valid local content.
- Authenticated reader-cache copies are also scoped by server-derived cache keys so server changes do not reuse unrelated cached files with the same `fileId`.
- `BrowserSnapshotStore` persists the last successful library list plus per-library book snapshots for offline/browser-failure fallback.
- `ProgressQueueStore` stores pending progress updates that still need to be synced.
- Debug builds show the current pending progress queue count directly in the browser screen.

### Background sync

- `ProgressSyncWorker` runs queued progress sync when network is available.
- Sync is currently event-based and timestamped.
- Conflict handling is currently newest-progress-wins.
- Duplicate queued updates for the same server/book/file target are compacted to the newest event.
- Audio progress is throttled before persistence through a dedicated `ProgressQueuePolicy`; page/chapter updates are queued only when the target position changes meaningfully.
- Worker retries now use WorkManager backoff only for transient sync failures; auth-blocked queues remain persisted without consuming retries.
- The repository persists the last successfully synced progress per target and skips re-queueing or re-posting stale/equivalent updates.
- Reader reopen now consults the persisted last-synced progress marker when no newer queued progress exists, so local resume survives successful queue replay.
- Pending progress that targets a different server now remains persisted instead of being silently dropped during sync attempts.
- Changing the configured server now preserves server-scoped downloads, queued progress, and last-synced markers on disk instead of wiping them globally.
- Reader resume now restores queued local progress only from the exact server/media/book/file target instead of loosely matching overlapping ids.
- Debug queue counts shown in the browser are scoped to the active server, even when pending updates for other saved servers still exist on disk.

### Reader implementations

- Audio uses ExoPlayer against a local file or authenticated stream URL.
- PDF uses `PdfRenderer` with simple page-by-page navigation against local downloads or authenticated cache copies.
- EPUB uses a local extraction flow:
  - the `.epub` is resolved from downloads or fetched into app cache
  - `META-INF/container.xml` is parsed to locate the OPF package
  - the OPF manifest and spine are parsed
  - HTML/XHTML spine items are rendered in a `WebView` chapter by chapter
  - reflowable chapter content is split into viewport-width CSS columns instead of vertically scrolling
  - the left and right outer quarters move pages, while the center toggles overlay controls
  - EPUB hides both system bars and permanent app chrome while reading; Back, chapter selection, themes, and text sizing live in transient overlays
  - the reader `WebView` allows local file-backed EPUB resources so extracted images and cover content can resolve offline
  - progress percentage includes the current in-chapter page, while persisted page identity still restores at chapter granularity
- Unsupported formats show an explicit unsupported-format message.

## Live BookOrbit contract currently assumed

Validated against the live server and BookOrbit source:

- `POST /api/v1/auth/login`
- `GET /api/v1/auth/me`
- `GET /api/v1/libraries`
- `POST /api/v1/libraries/{id}/books`
- `GET /api/v1/books/{id}`
- `GET /api/v1/series/{seriesId}/books`
- `GET /api/v1/books/files/{fileId}/serve`
- `GET /api/v1/books/files/{fileId}/download`
- `POST /api/v1/books/files/{fileId}/progress`
- `PATCH /api/v1/books/{id}/audio-progress`

## Known architectural gaps

- EPUB support is functional and paginated, but exact in-chapter page restore and RTL direction controls are not implemented yet.
- Login completion detection now combines navigation-triggered and polling-based `/api/v1/auth/me` checks, but it has not yet been verified against every server auth flow.
- Sync retry/backoff behavior still needs hardening and live replay verification.
- Reader state restoration uses queued local progress first, then server-reported page/time/percentage progress.
- Progress throttling rules are extracted into a small policy object with focused JVM coverage.

## UI/UX phase boundary

The functional architecture is stable enough for UI/UX changes to begin. Theme tokens and shared shell components should be established before screen-specific restyling, followed by setup/login, library browsing, and the EPUB reader. Format-specific audiobook, PDF, and CBZ adjustments remain deferred until representative files are available. See [ui-ux.md](./ui-ux.md) for checkpoints and regression guardrails.

The first design-system candidate uses explicit BookOrbit light/dark color schemes, typography, and shapes instead of platform dynamic colors. Shared `BookOrbitTopBar`, `OrbitMessage`, and `OrbitEyebrow` components establish the initial shell vocabulary while keeping coordinator behavior outside the presentation layer.

The browser presentation now uses a native Compose modal drawer and starts on a Home feed. Home shelf derivation is deterministic from `BookSummary` progress, series identity/order, read state, and added/updated/read timestamps. Those optional fields are parsed tolerantly and persisted in browser snapshots and active-reader state. Home remains scoped to the selected library page because the repository loads one library page at a time.

Home shelves remain selected-library scoped, but interactive search now uses BookOrbit's global `/api/v1/books/query` contract. Covers are fetched with the repository's authenticated cookie-aware client and cached in memory. Browser-local navigation owns series and book detail destinations; only the book-detail Read/Continue action calls the coordinator reader flow. `MainActivity` uses immersive status-bar hiding with transient swipe reveal.

Book details are enriched on demand with descriptive, creator, publication, identifier, genre/tag, and file metadata. Series details no longer depend on the current shelf page: they request the server's ordered series page, show completion and possible gaps, and optionally use the first book synopsis as series context. Hardware and top-bar Back preserve the series destination when a book was opened from within it.

Card covers use BookOrbit's thumbnail endpoint when available. Network fetches are serialized for initial fill, decoding runs off the Compose main thread with downsampling and `RGB_565`, and decoded bitmaps share a 16 MB LRU cache across repeated Home shelves. Missing covers are remembered for the process lifetime to avoid repeated failed work while scrolling.
