# Architecture

This document describes the current Lagrange Reader architecture. Dated implementation narratives and historical verification records are preserved in [`docs/architecture-archive.md`](architecture-archive.md).

## Goal and boundaries

Lagrange is a native Android client for BookOrbit focused on reading, listening, offline use, and synchronization. BookOrbit owns accounts, libraries, catalog metadata, media delivery, server progress, statuses, ratings, statistics, and server-side analytics. Lagrange owns the Android UI, local caches, downloads, reader/player state, exact local audiobook history, and offline behavior.

## App flow

1. The user enters a BookOrbit server URL.
2. The app authenticates with native credentials or the interim server-hosted sign-in WebView.
3. The coordinator confirms `GET /api/v1/auth/me` and resumes the pending destination.
4. Libraries and catalog data load into browser state, with cached/offline fallback where allowed.
5. Readers and the audiobook player open from selected catalog/download identity and preserve format-specific state.
6. Progress is persisted locally, queued/throttled, and synchronized when the server is reachable.

## Main components

- Compose UI and navigation: setup, login, Home, Libraries, Search, Series, Authors, Local books, Book Detail, Statistics, Achievements, Options, and reader/player entry points.
- `AppCoordinator`: session/bootstrap state, navigation, refresh lifecycle, download actions, active-reader restoration, progress orchestration, and user-facing state reconciliation.
- Repository/data layer: authenticated BookOrbit requests, parsing, server switching, cache fallback, progress/status/rating operations, download reconciliation, and reader preparation.
- Room/local persistence: server-scoped catalog/download state, active-reader metadata, queued progress, and local audiobook session history.
- Readium: EPUB, PDF, comic, and local/explicit-download reading paths.
- Media3/foreground service: connected and local audiobook playback, compact controls, chapters, seeking, speed, sleep timers, and process/task restoration. The service/controller owns playback state and commands; Compose renders the compact or full player without creating a second playback session.
- Full audiobook player overlay: `AppCoordinator.fullAudioPlayerBook` overlays the retained browser composition, so minimize and Close dismiss the player without implicitly navigating or losing the exact browser route. Metadata navigation is an explicit separate action.

## Data and synchronization rules

- Server identity is part of local cache and persistence boundaries; do not mix records between configured servers.
- Explicit logout, server change, and server removal close the Media3 audiobook service before credentials are cleared, disable late audio-progress callbacks, cancel outbound sync workers, and purge queued progress, reading-session, annotation, catalog, browser-snapshot, detail-cache, and exact-reader state. Downloaded media files remain device-shared assets and are not removed by session cleanup; Local books may therefore appear for another account on the same server/device, without carrying over the previous account's reading state or metadata.
- Completed local-copy records are separate from active/failed download attempts. Failed first downloads remain remote-only; failed updates preserve the prior completed copy.
- Local exact-position audiobook history remains authoritative for seeking. Server sessions/reading attempts supplement it as analytics context.
- Pending progress replay is outbound protection. It does not replace authoritative inbound progress hydration on normal online opens.
- Completed reader sessions are a separate durable outbound queue from exact progress. `ReadingSessionTracker` measures active time and progress deltas; `ReadingSessionQueueStore` persists file-scoped POSTs and `ReadingSessionSyncWorker` retries them independently.
- Normal online opens hydrate format-specific server progress before reader-state construction; offline and Preview flows remain isolated from those reads.
- If a normal detail or reader-preparation attempt fails, the coordinator retries reader preparation against the completed local copy using local-only resolution, independent of a possibly stale connectivity snapshot. Missing local content preserves the original online error; invalid local files and comic extraction failures remain explicit reader errors.
- Detail hydration preserves the selected file identity and completed local path when the returned server representation omits or replaces those fields, so Local books can fall back to the exact downloaded copy.
- Cached browser content may be shown while reconciliation is in progress, but the reconciled server catalog is authoritative for server-backed visibility.
- Browse `BookSummary` and `SeriesSummary` records retain normalized, deterministic `availableFormats` projections parsed from each book's `files` array or series format metadata. Completed `DownloadRecord` entries are separately projected by book ID and exact file ID into `downloadedFormats`, so an alternate M4B download marks `AUDIO` instead of the primary EPUB record. Series projections aggregate downloaded formats from member books. Available-format data is persisted in the server-scoped Room catalog and browser snapshot for book records, with legacy cache rows falling back to the existing primary format when possible; downloaded-format state is reconstructed from the download store, and browse-card presentation does not issue per-card detail requests.
- Home's `Recently added series` preview and `See all` path load the complete `/api/v1/series` catalog for identity, name, and cover, while deriving ordering from the minimum `BookSummary.addedAtMillis` across the complete local book catalog for each series. This prevents a later volume from promoting an old series. Home caps only the preview projection. If the complete server request is unavailable, the existing book-derived grouping remains an offline/request fallback.
- Server-missing catalog records remain visible with an explicit unavailable state; stale server-absent records are not resurrected after reconciliation.
- Offline library caching is explicit and library-scoped: selected libraries may cache complete lightweight book details and/or browsing-size cover thumbnails without downloading readable book files. Manual and approximately daily unmetered refresh share one bounded, resumable WorkManager pipeline; automatic refresh is disabled by default.
- User-started book downloads enqueue unique WorkManager work keyed by server URL plus file ID, run by a foreground `CoroutineWorker` that owns the transfer and posts a `dataSync` foreground-service download notification. `AppCoordinator` is a UI projection over that work: it persists a `DownloadStore` attempt before enqueueing, reconciles active WorkManager downloads after browser loading/reopen, and projects per-file progress from WorkManager state rather than owning the transfer itself. The existing repository staged `.part` transfer, integrity validation, atomic replacement, `DownloadStore` completed/attempt persistence, cancellation, retry, and failed-update preservation remain the transfer path underneath the worker. Cellular policy (ALWAYS/NEVER/ASK_FOR_CONFIRMATION) consent is captured at the foreground trigger and carried into the work item; the worker rechecks current policy/network and never proceeds without the required grant. Byte-range resume remains out of scope. This download path is distinct from the offline library cache above, which never fetches readable book files.
- Download display identity is projected separately from catalog state. The initiating `BookSummary` is published to `BrowserState` before enqueue, and reconciliation returns active identities restored from persisted `DownloadAttempt` metadata before considering catalog fallback. `AppCoordinator` merges those identities with active file IDs in one state update, preventing a numeric catalog/file ID from replacing the initiating title after app switching or process recreation. `DownloadStore` serializes all in-process instances and atomically replaces its JSON files so concurrent attempt writes cannot lose another active download.
- Active download notification UI is a per-file projection of foreground WorkManager work: stable file-based notification IDs group active notifications, retain the initiating title, expose determinate/indeterminate progress and per-file Cancel, and use app-launch content taps. When more than one download is active, a grouped summary reports the count; worker terminal cleanup removes individual and summary notifications. Notification posting is optional when `POST_NOTIFICATIONS` is denied and does not control download execution, preserving issue #77 background/screen-off WorkManager behavior.
- Detail cache entries are atomic, server-scoped files with legacy monolithic-cache read compatibility. Cover thumbnails use versioned identities and a 256 MB least-recently-used disk limit. Successful entries survive partial refresh failures and are removed only by an explicit offline-cache clear or server removal.
- Smart Scope discovery payloads are cached per server. Each successful `/api/v1/smart-scopes/:id/books` page is cached separately by server, Smart Scope ID, and page; offline catalog and Series-detail views can reconstruct from those cached scoped book pages. This content is best-effort and refreshes when online.

## Reader and media architecture

- EPUB uses generated Readium positions for percentage-based resume, with an equal-chapter fallback only when positions or percentage are unusable. Exact cross-client CFI interoperability remains deferred.
- Continuous EPUB uses active-resource progression for its right-side rail; paginated EPUB, PDF, and comics retain format-appropriate page navigation.
- PDF uses Readium PDFium. Paginated comics use Readium image navigation; continuous comics use a bounded book-scoped lazy surface with page/read limits and decoded-bitmap limits.
- Comic page images support bounded pinch zoom and long-press page download in both paginated and continuous CBZ/CBR reading. Downloads reuse the existing image export path and work from the decoded/local page data. A narrowly scoped Readium JavaScript image listener gives image-only EPUB documents bounded in-reader pinch/pan without enabling WebView zoom on mixed text-and-image content. At 1×, ordinary EPUB taps and swipes remain available to the reader's pagination and menu controls; EPUB image long-presses open the shared fullscreen image viewer because Readium 3.0.2 does not expose a public image-level callback or bridge registration surface.
- `ReaderTapZones` is the shared pure region model for Readium and continuous-comic tap handling and tutorial rendering. It stores normalized action rectangles, applies reading-direction and axis-inversion transforms, and keeps Menu handling separate from format-specific navigator progression.
- Connected standalone audio uses authenticated direct Media3 streaming. Explicit downloaded/local audio remains on the local Readium/media path.
- Connected multi-file audiobooks preserve BookOrbit's ordered audio-file list and per-file durations. Media3 receives one authenticated item per streamed file and advances through the playlist automatically.
- `AudiobookTimeline` converts between absolute book-level offsets and Media3 item/in-file coordinates. Chapter selection, compact-player time, slider movement, and relative seeks therefore operate across the complete audiobook.
- Audio progress reports the overall percentage together with the active file ID and in-file position. Online restoration rebuilds the ordered playlist and resolves file-aware server or queued-local progress; local exact-position history remains the seeking authority.
- Multi-file offline download/storage is out of scope. Existing single-file M4B/MP3 and downloaded/local playback remain unchanged.
- Audiobook restoration keeps Browser visible while the compact player prepares; explicit Book Detail Play may autoplay after preparation, while task/app restoration remains paused.
- The full audiobook player keeps Chapter, Speed, and Sleep visible in both orientations. Portrait and landscape derive one height-based group scale for spacing, typography, cover allocation, seek rows, and control artwork while preserving usable interactive minimums. The shared chapter list uses a Material 3 `ModalBottomSheet` so sheet/window geometry owns Android navigation-bar handling.
- EPUB, PDF, and comic sessions begin after usable publication open and follow reader lifecycle/page activity. Audiobook sessions follow Media3 `isPlaying` transitions and remain separate from local exact-position audiobook history. Five minutes without active interaction/playback rolls a session over; Preview never queues a server session.
- Preview never writes normal progress, active-reader state, or local session history.
- Browser drill-down state is one saveable route snapshot containing the selected identity and return path; restored identities resolve against current catalog/search data with bounded fallback metadata.
- Reader launchers persist a stable launch token across `MainActivity` recreation and accept only an explicit user-close result as permission to clear coordinator active-reader state.
- EPUB/PDF/comic activities use normal saved-instance recreation. A stable fragment container ID is created before replacing any dummy-restored navigator; the publication is reopened at the Bundle locator, and exactly one real navigator is installed. The reading-session reporter is retained through configuration recreation and is paused only for a real background stop.

## UI, cache, and security guardrails

- Keep foreground audiobook playback service-owned and compact-only unless a user-approved product decision changes that boundary.
- Preserve exact BookOrbit statuses independently from legacy completion flags.
- Preserve per-library reader profiles, cover ownership/aspect ratios, reader-direction behavior, and tap-zone layout/inversion. Reader profiles use a versioned, per-library JSON envelope; legacy flat profiles remain readable, and malformed profiles do not invalidate other libraries.
- Bound source responses, decoded image sizes, cache scope, and reader preparation; clean incomplete temporary outputs.
- Keep authentication-origin, TLS, cookie, token, and redirect checks separate from general URL helpers.
- Do not route connected standalone audio through Readium publication retrieval.
- Do not implicitly download entire large media for ordinary connected reading/listening.
- Do not expose native AppAuth until the upstream mobile-redirect contract is deployed and verified.

## Current validation status

The current release and recent cleanup gates are recorded in [`docs/testing.md`](testing.md) and the local ignored `docs/handover.md`. BOOX/Android e-ink physical validation remains deferred pending access to the device; the download, reader, media, navigation, server-session, and issue #47 repeated orientation/fold behavior described in the current testing matrix have been user-confirmed. Historical test counts and dated device results are in [`docs/architecture-archive.md`](architecture-archive.md).
