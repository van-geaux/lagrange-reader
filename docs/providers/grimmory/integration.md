# Grimmory native integration

## Status

Grimmory is a distinct provider backed by its native `/api/v1` API. The provider implementation and its comic-streaming, multi-format, audiobook playback, and chapter-projection regression suites passed automated verification; detailed evidence is recorded in the [Grimmory validation checklist](../../provider-checklists/grimmory.md). Authenticated Grimmory status/progress round-trips, live CBX and alternate-file server requests, and device validation remain pending; do not infer a released or device-validated feature from automated results alone.

## Authentication and account identity

The adapter uses Grimmory's primary-account JSON login, not the Komga-compatible API:

- `POST /api/v1/auth/login` with JSON `username` and `password`.
- `POST /api/v1/auth/refresh` with JSON `refreshToken`.
- Authenticated requests use `Authorization: Bearer <access-token>`.
- The response `expires` field is a duration in seconds; refresh responses rotate the token pair.
- After login and when an account scope must be restored, `GET /api/v1/users/me` supplies the stable root `id` used for profile-scoped local data. The upstream `UserController` route and `BookLoreUser.id` DTO were source-checked.

Access/refresh tokens are encrypted with an Android Keystore AES-GCM key before storage. Lagrange stores a derived account scope rather than the raw user ID. Logout deactivates that scope without deleting completed downloaded media.

Live sanitized evidence: login returned HTTP 200; `/api/v1/users/me` returned HTTP 200 and included an `id` field. A live refresh attempt returned HTTP 401, so successful refresh-token rotation remains unverified and must be checked on-device/server-side before claiming full expiry recovery.

## Native data and media routes

The Grimmory module set owns its transport and mapping. It does not reuse Komga DTOs, Komga authentication, or BookOrbit endpoints.

- Libraries: `GET /api/v1/libraries`.
- App catalog and detail: `GET /api/v1/app/books` and `GET /api/v1/app/books/{bookId}`. Catalog summaries expose only primary-file metadata; detail `files[]`/`fileTypes[]` supplies the attached formats and file IDs. Returned catalog/Home/search summaries enrich labels through cached, bounded-concurrency detail lookups (maximum four in flight, up to 512 LRU entries scoped by profile, server URL, and book ID). Metadata failure falls back to the primary format; book content is never downloaded for labels.
- Search: `GET /api/v1/app/books/search` with `q`, `page`, and `size`.
- Home shelves: `GET /api/v1/app/books/continue-reading?limit=20` supplies active ebook/PDF/comic reads and `GET /api/v1/app/books/continue-listening?limit=20` supplies active audiobooks. The Home Currently Reading shelf merges these routes with catalog entries whose normalized `readProgress` is finite, greater than 0, and below 99.5%, independent of `readStatus`; a later `UNREAD` volume is excluded when a lower-numbered volume in the same library/series is `READING` or `REREADING`. It scans unfiltered `GET /api/v1/app/books` pages of 50 ordered by `lastReadTime` descending, up to 100 pages; a catalog requiring more pages is rejected instead of silently truncated, while native route items remain available. Matching entries are merged by stable `(libraryId, id)` and sorted by activity. Continue Reading See all applies the same progress and series-order rules and scopes the source query to the requested library before slicing the complete result into 50-book pages; each See all page reloads the bounded catalog scan. Array-valued `status` and `fileType` filters use comma-delimited query values (for example, `status=READING,RE_READING`); JSON-array strings are rejected by the server. Continue Reading See all and Recently read use the generic app-book catalog. Recently read is ordered by `lastReadTime` descending; the `recently-added?limit=50` route supplies the book preview, while its `See all` destination uses paginated app books ordered by `addedOn` descending. `Recently added series` uses `GET /api/v1/app/series?page=…&size=20&sort=recentlyAdded&dir=desc`, whose `latestAddedOn`, authors, counts, and `coverBooks[].bookId` are projected to series cards. Grimmory computes `latestAddedOn` as the maximum book `addedOn` in each series, so a newly added volume can promote an older series; this does not match BookOrbit's ordering by the earliest book addition. Selecting a series loads its books from `/api/v1/app/series/{seriesName}/books` in `seriesNumber` order, bounded to 20 pages of 50 books; generic series filters remain unavailable.
- On Deck: derived only from read-status books within a bounded recent-read window, then resolved per series through `GET /api/v1/app/series/{seriesName}/books` ordered by `seriesNumber` ascending. Grimmory may omit `readStatus` when a book has no user-progress row or return `UNSET`; the adapter maps either case to `UNREAD`, so untouched books qualify. Explicit unsupported statuses remain unmapped. The adapter selects the first `UNREAD` book after the last `READ` book only when no `READING`/`REREADING` volume precedes that candidate; if an earlier volume is active, the series is omitted from On Deck. Home scans at most 10 pages (up to 500 recently-read books) and resolves at most 16 distinct series; each series request is capped at 20 pages of 50 books. `See all` is capped at 50 projected books and 100 series detail attempts.
- `Recently released books` remains hidden because the provider `publishedDate` field is not yet validated as a reliable release date for all media/catalog records or an agreed recency window. `Recently updated series` remains hidden because `coverUpdatedOn` describes cover metadata and does not establish a general series/book update timestamp. `Want to read` remains hidden because unread status is not equivalent to a user-curated Want to read shelf.
- Each shelf is isolated from non-auth failures; bounded diagnostics report only the section and HTTP status/error class, never response bodies or credentials. Authentication failures continue to the coordinator's session-recovery path.
- Cover: generic `/api/v1/media/book/{bookId}/cover`. When loading an `AUDIO` book, the provider first requests `/api/v1/media/book/{bookId}/audiobook-cover` and falls back to the generic cover only on HTTP 404; authentication and other failures continue through the normal authenticated request/session-recovery path. Automated contract coverage is present; live-server and device validation remain pending.
- CBX page list: [`GET /api/v1/cbx/{bookId}/pages`](https://grimmory.org/api/operations/listpages_1/), returning an array of contiguous one-based page numbers. Comic images use [`GET /api/v1/media/book/{bookId}/cbx/pages/{pageNumber}`](https://grimmory.org/api/operations/getcbxpage/). The reader maps its zero-based page index to Grimmory's one-based `pageNumber`; page-list metadata and page images use the native authenticated transport, with `application/json` and `image/*` Accept headers respectively. Opening a streamed comic does not create a durable download.
- Content: `/api/v1/books/{bookId}/content` for supported EPUB/PDF content; online CBX uses the page routes above. Audiobooks use `/api/v1/audiobooks/{bookId}/stream`, with `bookType` selected for an alternate audio file. Grimmory audio playback is wired through the existing player and uses the Grimmory authenticated-media module for request headers and authentication recovery.
- Primary-file download: `GET /api/v1/books/{bookId}/download`.
- Attached-file download: `GET /api/v1/books/{bookId}/files/{fileId}/download` for the exact non-primary file.
- Alternate-audiobook metadata: `/api/v1/audiobooks/{bookId}/info?bookType=…`; the matching stream route uses the same selector. Primary audio keeps the default route. The adapter maps documented `chapters[]` titles and millisecond start offsets into the shared chapter model. For folder-based audiobooks with no explicit chapters, it falls back to `tracks[]` titles and `cumulativeStartMs`; explicit chapter metadata takes precedence. Chapter selection and progress use the projected metadata, and the full player disables chapter seeking and withholds the chapter percentage when usable bounds are unavailable.

The download module streams response bytes, uses authenticated provider transport and shared profile-scoped download records/attempts, validates a staged file before atomic promotion, and deletes only the matching local record. Each file keeps its Grimmory `fileId`; primary downloads use the book-level route, while alternate selections use the documented file-specific route. The selected media kind, filename/format, download URL, audiobook info, and stream URL are projected from that file rather than inherited from the primary file. A sanitized live primary-download probe returned HTTP 200 with `application/octet-stream`; no live alternate-file round-trip has been performed.

The reader constructs Grimmory-native content URLs for supported online media and supports CBX page streaming through Grimmory's documented native page-list and page-image routes. Remote comic preparation relies on the provider page source, not a local archive suffix, so generic `CBX` catalog metadata does not block streaming. Offline-only restoration still requires a local comic file; that missing-local-file error is classified as local-reader-unavailable so an online failure is not replaced by a misleading offline-download message. Missing-cover HTTP 404 is treated as no image; authentication and other HTTP failures are not silently swallowed. Comic streaming has automated contract coverage, while live-server and device behavior remain pending.

## Coordinator routing and cache boundaries

The initial-browser, remaining-library, and selected-library catalog-refresh paths now call the resolved `BookCatalogModule` before the legacy repository fallback. `AppCoordinatorTest` verifies that the provider module receives these refreshes and that the compatibility repository refresh is not called when the module resolves. The production `AppGraph` wires Grimmory's module resolvers.

This is not a full fail-closed guarantee for alternate or misconfigured compositions: the resolver contract remains nullable, so an omitted resolver can still reach the repository compatibility path. Cached catalog, Home, and browser-state reads also remain backed by shared local compatibility stores. Those are local reads rather than Grimmory network calls, but may expose stale compatible projections; provider-local cache isolation and broader fallback hardening remain unverified.

## Native reading-status mutation

Grimmory status changes use the native single-book `PUT /api/v1/app/books/{bookId}/status` route with a JSON `status` value from Grimmory's `ReadStatus` enum. The request is authenticated, and Grimmory checks that the caller can access the book's library. A successful response has no status body; the client treats the successful HTTP response as completion. The app-specific route does not clear or update `dateFinished` when a book moves away from `READ`, so that server-side field must not be inferred from this mutation.

The adapter advertises and writes only the exact shared subset:

| BookOrbit status | Grimmory wire value |
|---|---|
| `UNREAD` | `UNREAD` |
| `READING` | `READING` |
| `REREADING` | `RE_READING` |
| `READ` | `READ` |
| `ABANDONED` | `ABANDONED` |

`WANT_TO_READ`, `ON_HOLD`, and `SKIMMED` have no exact shared Grimmory counterpart and remain unavailable. Grimmory now advertises this mapped status set, so the capability-driven Book Detail `Mark as...` action is available for Grimmory. Authenticated live status round-trip and device validation remain pending.

## Native progress and audiobook synchronization

Grimmory now provides native bidirectional book progress through `GET` and `PUT /api/v1/app/books/{bookId}/progress`. `ReaderModule` delegates `loadReaderProgress` to the same `GrimmoryProgressModule`, so reader hydration and progress writes use one provider-owned contract.

- EPUB progress stores and restores the Readium CFI and resource `href` alongside the server percentage.
- PDF and CBX progress converts Grimmory's one-based page numbers to the reader's zero-based page indices when loading, and converts them back to one-based values when writing.
- Audiobook metadata is loaded from `/api/v1/audiobooks/{bookId}/info`. Single-file playback uses `/api/v1/audiobooks/{bookId}/stream`; folder-based tracks use the same route with a zero-based `trackIndex` query parameter. Playback item IDs are distinct per track, while the provider `bookFileId` remains stable.
- App/core tracks audiobook absolute timeline milliseconds, zero-based `trackIndex`, and track-local milliseconds separately. On Grimmory's wire, folder-based audiobook `positionMs`/`fileProgress.positionData` is track-local, `trackIndex`/`fileProgress.positionHref` identifies the track, and `percentage` represents overall progress. `trackPositionMs` may be included only as an optional supplemental local field; it is not an absolute server coordinate, and no server round-trip is claimed here.

This synchronization is online-only for Grimmory. Offline device-local audiobook checkpoint persistence or progress replay is not implemented in this provider integration; cached catalog progress must not be treated as a durable or current reader position. Focused coverage includes `GrimmoryApiContractTest`, `GrimmoryProgressModuleTest`, `GrimmoryReaderModuleTest`, `AudiobookTimelineTest`, `ReadiumAudioPlaybackTest`, and the relevant `AppCoordinator` tests. Automated coverage is not a live-server or device result; authenticated Grimmory progress/status round-trips and online/offline device validation remain pending.

## Capability boundaries

The adapter currently exposes library/catalog, search, implemented Home shelves, the bounded Recently added series catalog and its series-detail route, book detail/covers, supported native content, downloads, local books, native online progress synchronization, and the mapped native reading-status mutation subset described above. General series filters and discovery remain unavailable. The following remain explicitly unavailable in this integration until verified and implemented: collections, authors, Smart Scopes, ratings, reading sessions/attempts, annotations, EPUB bookmarks, achievements, statistics, and provider offline/background-cache management. Unsupported calls must not be converted into successful empty/no-op results or fall through to BookOrbit.

## Verification evidence

The focused progress/reader/audio test evidence and full automated verification results are recorded in the [Grimmory validation checklist](../../provider-checklists/grimmory.md). Authenticated Grimmory progress GET-after-PUT server round-trip and UI/device behavior remain pending.
