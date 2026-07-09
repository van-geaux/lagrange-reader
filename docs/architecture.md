# Architecture

## Goal

`Lagrange Reader` is a native Android client for BookOrbit focused on reading and listening only. It does not manage metadata or server settings beyond initial server selection and login.

## App flow

The current app flow is:

1. User enters a BookOrbit server URL.
2. The app opens the server login page in a WebView.
3. The app probes the authenticated API to determine whether login has succeeded.
4. After authentication, the app loads libraries and books.
5. The user can stream content, download it, reopen local files offline, and queue progress updates for later sync.
6. EPUB and PDF titles are opened from a local readable copy when the native reader requires file access.
7. EPUB titles are extracted into app cache and rendered chapter by chapter from the EPUB spine.

## Main components

### UI and navigation

- `MainActivity` boots the app and wires the graph.
- `AppCoordinator` owns screen state and orchestrates transitions.
- `AppScreen` defines the app-level screens.
- `BookOrbitApp` renders the UI for setup, login, library browsing, and reader/player screens.
- Library browsing renders explicit loading, empty, and error states for library and book lists, with a refresh action.
- Book cards show active download progress, failed-download retry, and cancel controls while a download is running.
- Downloaded book cards also expose a delete-local-copy action that removes the stored file and its persisted download record.
- Reader startup has an explicit loading screen, and unsupported reader types render a user-facing message instead of falling through to a generic WebView.
- Coordinator UI messages are normalized from typed auth, HTTP, TLS, timeout, DNS, and generic network failures instead of exposing raw exception text.
- When auth probing fails during bootstrap or login refresh, cached browser state is used as the offline fallback instead of forcing an immediate return to login.
- Cached offline browser states mark non-downloaded titles as unavailable offline and suppress live-only actions like open-stream and download.

### Data and API layer

- `BookOrbitRepository` is the main integration layer with the BookOrbit server.
- It stores the selected server URL and selected library.
- It loads libraries and books from the live API.
- It resolves stream and download URLs for files.
- It prepares readable local copies for offline-first reader flows, including EPUB/PDF cache copies for authenticated reads before download.
- It translates local progress events into the server DTO shapes.
- It maps server `readingProgress` page and time-position fields back into reader resume state.
- Progress queue writes are dispatched on `Dispatchers.IO`; `AppCoordinator` debounces noisy reader/player progress events before calling the repository.

### Local persistence

- `DataStore` stores the configured server URL and selected library ID.
- `DownloadStore` stores downloaded file records.
- Download targets use sanitized book titles plus file ids, with extensions derived from BookOrbit format/MIME hints where available.
- Missing local download files are pruned from persisted download records before records are returned, so offline snapshots do not continue to show removed files as downloaded.
- Zero-byte local download and reader-cache files are discarded and refetched instead of being treated as valid local content.
- `BrowserSnapshotStore` persists the last successful library list plus per-library book snapshots for offline/browser-failure fallback.
- `ProgressQueueStore` stores pending progress updates that still need to be synced.
- Debug builds show the current pending progress queue count directly in the browser screen.

### Background sync

- `ProgressSyncWorker` runs queued progress sync when network is available.
- Sync is currently event-based and timestamped.
- Conflict handling is currently newest-progress-wins.
- Duplicate queued updates for the same server/book/file target are compacted to the newest event.
- Audio progress is throttled before persistence; page/chapter updates are queued only when the target position changes meaningfully.
- Worker retries now use WorkManager backoff only for transient sync failures; auth-blocked queues remain persisted without consuming retries.
- The repository persists the last successfully synced progress per target and skips re-queueing or re-posting stale/equivalent updates.

### Reader implementations

- Audio uses ExoPlayer against a local file or authenticated stream URL.
- PDF uses `PdfRenderer` with simple page-by-page navigation against local downloads or authenticated cache copies.
- EPUB uses a local extraction flow:
  - the `.epub` is resolved from downloads or fetched into app cache
  - `META-INF/container.xml` is parsed to locate the OPF package
  - the OPF manifest and spine are parsed
  - HTML/XHTML spine items are rendered in a `WebView` chapter by chapter
  - progress is currently tracked at chapter granularity and translated into percentage
- Unsupported formats show an explicit unsupported-format message.

## Live BookOrbit contract currently assumed

Validated against the live server and BookOrbit source:

- `POST /api/v1/auth/login`
- `GET /api/v1/auth/me`
- `GET /api/v1/libraries`
- `POST /api/v1/libraries/{id}/books`
- `GET /api/v1/books/files/{fileId}/serve`
- `GET /api/v1/books/files/{fileId}/download`
- `POST /api/v1/books/files/{fileId}/progress`
- `PATCH /api/v1/books/{id}/audio-progress`

## Known architectural gaps

- EPUB support is functional but still minimal: no pagination, no in-chapter scroll restore, no theme or typography controls.
- Comic/CBZ reading is not implemented yet.
- Login completion detection is still indirect and based on API probing.
- Sync retry/backoff behavior still needs hardening and live replay verification.
- Reader state restoration uses queued local progress first, then server-reported page/time/percentage progress.
- Progress throttling is implemented in coordinator memory and needs focused unit coverage.
