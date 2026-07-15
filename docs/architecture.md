# Architecture

## Goal

`Lagrange Reader` is a native Android client for BookOrbit focused on reading and listening only. It does not manage metadata or server settings beyond initial server selection and login.

## App flow

The current app flow is:

1. User enters a BookOrbit server URL.
2. The app presents native username/password credentials and submits them to the BookOrbit login API.
3. The app verifies `GET /api/v1/auth/me` before resuming the pending browser, library, reader, or download destination.
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
- On cold reopen, the coordinator emits the cached Room-backed browser state before session and library network checks finish. Remote-only cards stay in offline-safe mode until authentication is confirmed.
- A complete cached library remains visible while reconciliation runs. An interrupted reconciliation clears the syncing state but keeps the prior complete catalog usable; first-time partial data does not expose the jump rail.
- Catalog loads are cancelable at the coordinator level so selecting another library or starting another refresh cannot let an older long-running reconciliation replace the newer screen.
- When auth state cannot be confirmed during bootstrap or login refresh, cached browser state is used as the offline fallback instead of forcing an immediate return to login.
- A server-unavailable result may use cached offline Home, but an explicit unauthenticated response now opens Login because the reachable server rejected the saved session.
- Cached offline browser states mark non-downloaded titles as unavailable offline and suppress live-only actions like open-stream and download.
- Opening a title from a cached offline browser snapshot now forces a local-only reader build, so offline reopen does not fall back to authenticated cache fetches or stream URLs.
- Offline-first active-reader restore uses the same local-only reader stream suppression, so startup reopen does not quietly fall back to a live audio stream.
- When authentication expires during browser, open-book, or download flows, the coordinator routes back through login and resumes the intended action after the session is restored.
- The login screen no longer uses a WebView or login polling; WebView remains only for reader content that requires it.
- Preview reader launches start at the beginning and do not write active-reader or progress state.

### Data and API layer

- `BookOrbitRepository` is the main integration layer with the BookOrbit server.
- It stores the selected server URL and selected library.
- Server URL validation now requires HTTPS for non-local hosts; cleartext HTTP is reserved for local development targets only.
- It loads libraries and books from the live API.
- It walks every page of the selected library's default listing, deduplicates by book id while preserving server order, and only publishes the result after the full request succeeds. If totals change or the deduplicated count disagrees with the latest total, it retries once from page zero and otherwise preserves the prior cache.
- It reads BookOrbit's `/api/v1/libraries/{id}/books/jump-buckets` response and retains absolute indexes only when its total matches the reconciled listing. Older servers or mismatched snapshots fall back to complete local index derivation.
- BookOrbit currently has no reliable catalog revision/delta contract, so refresh must read all metadata pages to detect removals and remote progress changes. The Room reconciliation still limits local writes to changed/new/reordered rows and deletions.
- It loads rich book details and complete ordered series pages on demand while preserving the selected cached summary as a failure fallback. Opened-book details are persisted for all books and reused only while the catalog `updatedAt` version still matches.
- Stored selected-library ids are validated against the latest available library list before the browser chooses a library to load.
- It resolves stream and download URLs for files.
- It prepares readable local copies for offline-first reader flows, including EPUB/PDF cache copies for authenticated reads before download.
- It translates local progress events into the server DTO shapes.
- It maps BookOrbit's current scalar `readingProgress` card value, nested `readStatus`, and legacy nested page/time progress fields back into browser and reader state.
- Reader callbacks, persisted snapshots, queue entries, last-synced markers, and API payloads all use one canonical 0-100 percentage scale; low values are not reinterpreted as fractions.
- A queued progress event writes both the media progress endpoint and the book status endpoint. Progress below 99.5% sets `reading`; progress at or above 99.5% sets `read`. The event is acknowledged only after both requests succeed because BookOrbit's Currently Reading widget is status-backed and its internal automatic status promotion can fail independently after accepting progress.
- Removing a title from Currently Reading is serialized against progress replay and uses endpoints available to ordinary users. Non-audio titles call `DELETE /api/v1/books/files/{fileId}/progress`; audio titles write zero percentage/position through `PATCH /api/v1/books/{bookId}/audio-progress`. The app then patches the book status to `unread` with cleared lifecycle dates. After both server writes succeed, all pending and last-synced markers for that server/book are removed along with exact EPUB resume, matching active-reader state, cached detail progress, legacy snapshot progress, and Room catalog progress. This clears the primary/current file represented by the app but does not delete BookOrbit reading-session history or progress on additional files; the broader `reset-reading-state` operation requires `LibraryEditMetadata`.
- Progress queue writes are dispatched on `Dispatchers.IO`; the coordinator records the newest reader/player event synchronously before persisting it so an immediate reader close cannot lose the final update.
- Browser bootstrap flushes pending progress before reconciling the complete library catalog, and reader close attempts a foreground sync before clearing active-reader state; WorkManager remains the offline/transient fallback.

### Local persistence

- `DataStore` stores the configured server URL and selected library ID.
- Android network security config blocks cleartext traffic by default and allows it only for localhost and common emulator loopback hosts.
- Session reset now waits for cookie removal to complete before the app settles on the login screen, and explicit sign-out suppresses cached offline-browser fallback until a fresh login succeeds.
- Successful native login persists the returned `accessToken`; authenticated API, cover, download, and reader-cache requests send it as a Bearer credential alongside the shared cookie jar. A 401/403 response triggers one refresh-cookie renewal attempt and rebuilds the original request with refreshed credentials before session recovery is shown. Explicit session clearing removes the token and cookies.
- Refresh requests include the current bearer/cookie credentials and are serialized; a request that waited behind another successful refresh reuses the newly persisted token instead of starting a second renewal.
- Coordinator-side session and server resets also clear in-memory browser, download, and post-login destination state so stale UI targets are not reused after sign-out or server changes.
- `DownloadStore` stores downloaded file records scoped by server URL so server changes do not reuse unrelated local files by `fileId`.
- Download targets use sanitized book titles plus file ids, with extensions derived from BookOrbit format/MIME hints where available.
- Missing local download files are pruned from persisted download records before records are returned, so offline snapshots do not continue to show removed files as downloaded.
- Corrupted local EPUB/PDF/CBZ files are rejected before reader startup, and invalid persisted download records are dropped before falling back to authenticated cache copies when possible.
- Zero-byte local download and reader-cache files are discarded and refetched instead of being treated as valid local content.
- Authenticated reader-cache copies are also scoped by server-derived cache keys so server changes do not reuse unrelated cached files with the same `fileId`.
- `BrowserSnapshotStore` persists the last successful library list and can read legacy first-page book snapshots as a migration/failure fallback; it is no longer the active Browse book store.
- `LibraryCatalogStore` uses Room tables scoped by server URL and library id for ordered book metadata, catalog totals, refresh timestamps, and validated jump buckets. Reconciliation compares rows, deletes missing ids in bounded batches, upserts only changed rows, and commits metadata/buckets in one transaction.
- `ProgressQueueStore` stores pending progress updates that still need to be synced.
- Debug builds show the current pending progress queue count directly in the browser screen.
- `CatalogSnapshotStore` stores versioned raw Series, Authors, and author-book pages keyed by server URL for offline catalog fallback without mixing servers.
- `CoverCacheStore` persists versioned BookOrbit thumbnail bytes per server/book. Per-file locks let foreground and worker repositories read different thumbnails concurrently.

### Background sync

- `ProgressSyncWorker` runs queued progress sync when network is available.
- `CoverCacheWarmWorker` starts after a complete catalog reconciliation and walks the selected-library cache in durable 50-download batches on unmetered connectivity. Existing versioned files are skipped, so later runs fetch only new or changed thumbnails.
- Sync is currently event-based and timestamped.
- Conflict handling is newest-event-wins: equivalent updates are suppressed, while a newer lower-position event is retained so rereads and corrections can reach BookOrbit.
- Duplicate queued updates for the same server/book/file target are compacted to the newest event.
- Audio progress is throttled before persistence through a dedicated `ProgressQueuePolicy`; page/chapter updates are queued only when the target position changes meaningfully.
- Worker retries now use WorkManager backoff only for transient sync failures; auth-blocked queues remain persisted without consuming retries.
- The repository persists the last successfully synced progress per target and skips only equivalent updates; it no longer rejects legitimate lower reread/correction events.
- Foreground and WorkManager repository instances share queue and last-synced file locks. A replay removes only the exact event IDs it processed, preserving any newer same-book event written while the network request was in flight.
- Rapid reader callbacks replace a short-delayed unique worker so the latest compacted event always has a trailing replay. Missing percentages are not serialized as zero-percent server updates.
- Progress and its authoritative `reading`/`read` status form one logical replay operation; a status failure keeps the exact event queued so WorkManager retries it.
- After all current-server queue entries sync and a fresh library page loads, temporary in-memory progress overlays are cleared so BookOrbit or another client can become authoritative on refresh.
- Reader reopen now consults the persisted last-synced progress marker when no newer queued progress exists, so local resume survives successful queue replay.
- Pending progress that targets a different server now remains persisted instead of being silently dropped during sync attempts.
- Changing the configured server now preserves server-scoped downloads, queued progress, and last-synced markers on disk instead of wiping them globally.
- Reader resume now restores queued local progress only from the exact server/media/book/file target instead of loosely matching overlapping ids.
- EPUB reader padding is stored independently for Top, Bottom, Left, and Right per book/file target, so closing and reopening a book keeps its values instead of returning to the 15% defaults. Top/Bottom resize the Android `WebView` outside its HTML content, while Left/Right update the page strip in place; target-device testing confirms visible changes on all four edges.
- Debug queue counts shown in the browser are scoped to the active server, even when pending updates for other saved servers still exist on disk.

### Reader implementations

- Audio uses ExoPlayer against a local file or authenticated stream URL.
- PDF uses `PdfRenderer` with simple page-by-page navigation against local downloads or authenticated cache copies.
- EPUB uses a local extraction flow:
  - the `.epub` is resolved from downloads or fetched into app cache
  - `META-INF/container.xml` is parsed to locate the OPF package
  - the OPF manifest and spine are parsed
  - HTML/XHTML spine items are rendered in a `WebView` chapter by chapter
  - reflowable chapter content uses the last device-known-good single absolute page strip with `overflow: visible`; page turns translate that strip without adding a clipped HTML wrapper around it
  - left and right outer-quarter taps, plus left/right swipes, move one page; the center opens overlay controls, and the top-right Close action is the only way to dismiss them
  - EPUB hides both system bars and permanent app chrome while reading; Back, chapter selection, themes, and text sizing live in transient overlays
  - the reader `WebView` allows local file-backed EPUB resources so extracted images and cover content can resolve offline
  - progress percentage includes the current in-chapter page, and persisted chapter/page identity restores the exact local page after layout
  - Top, Bottom, Left, and Right use independent 0-100% controls and persist per book/file. Top/Bottom are converted to Compose padding around the `WebView`, so Android performs the vertical clipping and viewport resize without changing the known-good HTML renderer; Left/Right update and repaginate the page strip in place. Target-device testing confirms that EPUB content renders and all four controls visibly update the reading surface.
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

- EPUB exact in-chapter page restore is implemented; exact restore, visible-overflow pagination, external vertical padding, and swipe behavior still require real-device validation against the representative sample. RTL direction controls are not implemented.
- Native login has not yet been verified against every server auth flow or real rate-limit response.
- Sync retry/backoff behavior still needs hardening and live replay verification.
- Reader state restoration uses queued local progress first, then server-reported page/time/percentage progress.
- Progress throttling rules are extracted into a small policy object with focused JVM coverage.

## UI/UX phase boundary

The functional architecture is stable enough for UI/UX changes to begin. Theme tokens and shared shell components should be established before screen-specific restyling, followed by setup/login, library browsing, and the EPUB reader. Format-specific audiobook, PDF, and CBZ adjustments remain deferred until representative files are available. See [ui-ux.md](./ui-ux.md) for checkpoints and regression guardrails.

The first design-system candidate uses explicit BookOrbit light/dark color schemes, typography, and shapes instead of platform dynamic colors. Shared `BookOrbitTopBar`, `OrbitMessage`, and `OrbitEyebrow` components establish the initial shell vocabulary while keeping coordinator behavior outside the presentation layer.

The browser presentation now uses the Plex-inspired bottom-navigation shell and starts on a Home feed. Home shelf derivation is deterministic from `BookSummary` progress, series identity/order, read state, and added/updated/read timestamps. Those optional fields are parsed tolerantly and persisted in the Room catalog and active-reader state. Home remains scoped to the selected library, but now derives shelves from its complete cached catalog instead of one server page. Top-level Home and the Library Recommended/Browse tabs share the same pull-to-refresh container and coordinator refresh pipeline. Currently Reading cards provide overflow and long-press reset actions on live data. On Deck requires at least one completed series book, chooses the first unread volume, and suppresses that volume while it has active reading progress.

Home shelves remain selected-library scoped, but interactive search uses BookOrbit's global `/api/v1/books/query` contract. Browse filtering and supported sorting are local operations over the complete cached catalog; Series and Authors catalogs remain server-backed. Covers are fetched with the repository's authenticated cookie-aware client and cached in memory and on disk. Series/Authors catalog image bytes are retried, cached, and decoded off the Compose main thread; Series also has a deterministic `/api/v1/series/{id}/cover` fallback. Browser-local navigation owns series, author, and book detail destinations; book-detail Read/Continue and Preview actions call the coordinator reader flow. Fresh reader progress is merged into the current browser state immediately, including a just-read book not present in an interrupted first-time sync. `MainActivity` uses immersive status-bar hiding with transient swipe reveal.

Book details are enriched on demand with descriptive, creator, publication, identifier, genre/tag, and file metadata. Series details no longer depend on the current shelf page: they request the server's ordered series page, show completion and possible gaps, and optionally use the first book synopsis as series context. Hardware and top-bar Back preserve the series destination when a book was opened from within it.

Card covers use BookOrbit's thumbnail endpoint when available. Compose-owned loads cancel the underlying OkHttp call when a card scrolls away, while short striped locks deduplicate simultaneous requests for the same visible image without preserving stale global jobs. Decoding runs off the Compose main thread with downsampling and `RGB_565`, and decoded bitmaps share a 32 MB LRU cache. Versioned bytes persist on disk, and one low-priority worker request at a time gradually warms the complete selected-library thumbnail set without consuming metered data.
