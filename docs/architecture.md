# Architecture

This document describes the current Lagrange Reader architecture. Dated implementation narratives and historical verification records are preserved locally in `docs/architecture-archive.md` (ignored by Git, not part of the public documentation set).

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
- Annotation hub and reader navigation: the global Annotations destination loads authenticated user-scoped `/api/v1/annotations` pages, supports active/trash, note, search, sort, and pagination filters, and preserves BookOrbit book/file identity when opening EPUB CFI or PDF page targets. EPUB selection creates highlights/notes, renders highlight/underline decorations, and queues mutations for retry; PDF/comic annotation creation and publication full-text search remain pending.
- Repository/data layer: authenticated BookOrbit requests, parsing, server switching, cache fallback, progress/status/rating operations, download reconciliation, and reader preparation.
- Room/local persistence: server-scoped catalog/download state, active-reader metadata, queued progress, and local audiobook session history.
- Readium: EPUB, PDF, comic, and local/explicit-download reading paths.
- Media3/foreground service: connected and local audiobook playback, compact controls, chapters, seeking, speed, and process/task restoration.

## Data and synchronization rules

- Server identity is part of local cache and persistence boundaries; do not mix records between configured servers.
- Completed local-copy records are separate from active/failed download attempts. Failed first downloads remain remote-only; failed updates preserve the prior completed copy.
- Local exact-position audiobook history remains authoritative for seeking. Server sessions/reading attempts supplement it as analytics context.
- Pending progress replay is outbound protection. It does not replace authoritative inbound progress hydration on normal online opens.
- Completed reader sessions are a separate durable outbound queue from exact progress. `ReadingSessionTracker` measures active time and progress deltas; `ReadingSessionQueueStore` persists file-scoped POSTs and `ReadingSessionSyncWorker` retries them independently.
- Normal online opens hydrate format-specific server progress before reader-state construction; offline and Preview flows remain isolated from those reads.
- Cached browser content may be shown while reconciliation is in progress, but the reconciled server catalog is authoritative for server-backed visibility.
- Server-missing catalog records remain visible with an explicit unavailable state; stale server-absent records are not resurrected after reconciliation.
- Offline library caching is explicit and library-scoped: selected libraries may cache complete lightweight book details and/or browsing-size cover thumbnails without downloading readable book files. Manual and approximately daily unmetered refresh share one bounded, resumable WorkManager pipeline; automatic refresh is disabled by default.
- Detail cache entries are atomic, server-scoped files with legacy monolithic-cache read compatibility. Cover thumbnails use versioned identities and a 256 MB least-recently-used disk limit. Successful entries survive partial refresh failures and are removed only by an explicit offline-cache clear or server removal.

## Reader and media architecture

- EPUB uses generated Readium positions for percentage-based resume, with an equal-chapter fallback only when positions or percentage are unusable. Exact cross-client CFI interoperability remains deferred.
- Continuous EPUB uses active-resource progression for its right-side rail; paginated EPUB, PDF, and comics retain format-appropriate page navigation.
- PDF uses Readium PDFium. Paginated comics use Readium image navigation; continuous comics use a bounded book-scoped lazy surface with page/read limits and decoded-bitmap limits.
- `ReaderTapZones` is the shared pure region model for Readium and continuous-comic tap handling and tutorial rendering. It stores normalized action rectangles, applies reading-direction and axis-inversion transforms, and keeps Menu handling separate from format-specific navigator progression.
- Connected standalone audio uses authenticated direct Media3 streaming. Explicit downloaded/local audio remains on the local Readium/media path.
- Connected multi-file audiobooks preserve BookOrbit's ordered `files` list with per-file millisecond-precision durations from detail parsing through the cache and into the reader/player session. Playback builds one authenticated Media3 playlist item per ordered file and relies on ExoPlayer's native auto-advance between items; no combining/re-encoding occurs.
- A pure `AudiobookTimeline` mapper converts between the book-level absolute chapter position and Media3's per-item `(mediaItemIndex, positionWithinItem)` coordinates. The compact player's elapsed/remaining time, slider, chapter menu, and ±10/30-second seeks all operate on this absolute timeline rather than the active MediaItem's isolated position.
- Audio-progress `PATCH` requests report the active `currentFileId`, its in-file `positionSeconds`, and an overall percentage derived from the absolute timeline; local exact file/position history stays separate and remains authoritative for seeking. A normal online relaunch reloads book detail and audio progress, rebuilds the playlist, and resumes on the server-reported active file, preferring queued local progress when present.
- Multi-file offline download/storage is out of scope: split-MP3 playlists are not downloadable as a unit, and offline traversal across split files is not supported. Single-file M4B and downloaded/local audio playback are unchanged by the multi-file playlist path.
- Audiobook restoration keeps Browser visible while the compact player prepares; explicit Book Detail Play may autoplay after preparation, while task/app restoration remains paused.
- EPUB, PDF, and comic sessions begin after usable publication open and follow reader lifecycle/page activity. Audiobook sessions follow Media3 `isPlaying` transitions and remain separate from local exact-position audiobook history. Five minutes without active interaction/playback rolls a session over; Preview never queues a server session.
- Preview never writes normal progress, active-reader state, or local session history.

## Annotation and search boundary

- Server annotation search is limited to annotation text and note text through the BookOrbit annotation hub; it is not publication full-text search.
- Publication full-text search is intended to remain local to the loaded publication. EPUB/KEPUB search and selectable-text PDF search are pending implementation; scanned/image-only PDFs are unsupported unless OCR is separately approved.
- Annotation identity must retain the BookOrbit `bookId`, annotation ID, and file identity (`bookFileId`/`jumpFileId`) so reader navigation and future synchronization cannot silently cross files.
- EPUB reader highlight creation/rendering and durable create/update/delete synchronization are implemented through the local annotation mutation queue, WorkManager retry, and local-to-server ID mapping. Tapping an EPUB annotation carries its ID, CFI, quote, chapter, color, and style into an isolated Preview launch; Preview renders only that tapped decoration and does not write normal progress, locator, active-reader, or session state. Restore and conflict-safe server hydration remain pending; PDF/comic creation and publication full-text search remain out of scope.

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

Current verification procedures are recorded in [`docs/testing.md`](testing.md); current worktree-specific evidence remains in local `docs/handover.md` (ignored by Git, not part of the public documentation set). Issue #36's streamed split-MP3 playlist is implemented and the user confirmed it works with the connected split-MP3 sample. Historical test counts and dated device results are preserved locally in `docs/architecture-archive.md`.

### Reader failure recovery

The active-reader record is cleared when normal reader preparation or opening fails. The coordinator returns to the cached browser state with the failure message, and startup restoration removes saved reader entries that cannot be prepared. This prevents a failed EPUB, PDF, comic, audiobook, or unsupported-format open from trapping the user in a restart loop.
