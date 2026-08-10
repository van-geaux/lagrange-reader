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
- Audiobook restoration keeps Browser visible while the compact player prepares; explicit Book Detail Play may autoplay after preparation, while task/app restoration remains paused.
- EPUB, PDF, and comic sessions begin after usable publication open and follow reader lifecycle/page activity. Audiobook sessions follow Media3 `isPlaying` transitions and remain separate from local exact-position audiobook history. Five minutes without active interaction/playback rolls a session over; Preview never queues a server session.
- Preview never writes normal progress, active-reader state, or local session history.

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

The current release and recent cleanup gates are recorded in [`docs/testing.md`](testing.md) and [`docs/handover.md`](handover.md). BOOX/Android e-ink physical validation remains deferred pending access to the device; the download, reader, media, navigation, and server-session validation described in the current testing matrix has been user-confirmed. Historical test counts and dated device results are in [`docs/architecture-archive.md`](architecture-archive.md).
