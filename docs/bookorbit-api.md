# BookOrbit API Contract

This document records the BookOrbit API contract currently used by the Android client.

## Authentication

### Login

Endpoint:

```text
POST /api/v1/auth/login
```

Request body:

```json
{
  "username": "string",
  "password": "string"
}
```

Server behavior:

- Returns `200 OK`
- Returns JSON with `accessToken` and `user`
- Sets `access_token` cookie on `/api`
- Sets `refresh_token` cookie on `/api/v1/auth`

The Android client persists `accessToken` for the configured server and sends it as a Bearer credential on authenticated API and media requests, while retaining the shared cookie jar for cookie-based and refresh-capable server flows.

When an authenticated request receives `401` or `403`, the client closes that response, attempts `POST /api/v1/auth/refresh` with the current bearer/cookie credentials, then falls back to `POST /api/v1/auth/token/renew` when the first path is not available. It stores a returned `accessToken` when present and retries the original request once. Concurrent expired requests share the refresh lock and reuse a token refreshed by the first request. These renewal paths are client fallbacks pending confirmation against the target deployment; physical-device testing must record the actual server contract if both are unavailable.

### Current user

Endpoint:

```text
GET /api/v1/auth/me
```

Used to confirm authenticated session state after login.
The app also uses this endpoint during bootstrap and login polling instead of inferring auth state from library loading.

The Android login screen retains native username/password credentials and now also uses the server's web login through the implemented interim WebView path. Native OIDC provider discovery and custom-scheme callback handling are not implemented; current stock server `main` only accepts its web callback. The interim WebView and final AppAuth contracts are recorded in [OIDC / SSO Authentication](./oidc-authentication.md).

### OIDC / SSO discovery and callback

Verified public endpoints:

```text
GET  /api/v1/app-settings/oidc/providers/public
POST /api/v1/auth/oidc/{slug}/state
POST /api/v1/auth/oidc/callback
```

The callback request contains `code`, `codeVerifier`, `redirectUri`, `nonce`, and `state`. A successful login returns the normal BookOrbit access-token/user response and sets the normal access and refresh cookies, so the client can reuse its existing authenticated-request, refresh, `/auth/me`, and pending-destination recovery paths.

Current stock BookOrbit server `main` accepts only `APP_URL/oauth2-callback`. Native AppAuth therefore requires upstream PR [#554](https://github.com/bookorbit/bookorbit/pull/554) or equivalent server support plus registration of Lagrange's exact `com.vangeaux.lagrange:/oauth2-callback` URI with the deployed server and identity-provider client.

## Libraries

### List libraries

Endpoint:

```text
GET /api/v1/libraries
```

Unauthenticated behavior:

- Returns `401 Unauthorized`

Authenticated behavior:

- Returns an array of library objects
- The current client uses `id` and `name`

### Query library books

Endpoint:

```text
POST /api/v1/libraries/{id}/books
```

Current client request body for the first page:

```json
{
  "sort": [],
  "pagination": { "page": 0, "size": 100 }
}
```

Subsequent pages use the same body with an incremented `pagination.page` value. For a reported catalog of at least four pages, the client requests remaining pages in ordered batches with at most four concurrent calls, then validates the merged count and retries once if the catalog changed during traversal.

When Library Browse filters are applied, the client adds BookOrbit's standard filter group and sort fields while keeping the same pagination contract. For example:

```json
{
  "filter": {
    "type": "group",
    "join": "AND",
    "rules": [
      { "type": "rule", "field": "title", "operator": "contains", "value": "Dune" },
      { "type": "rule", "field": "author", "operator": "includesAny", "value": ["Frank Herbert"] },
      { "type": "rule", "field": "genre", "operator": "includesAny", "value": ["Science Fiction"] },
      { "type": "rule", "field": "readProgress", "operator": "isInProgress" },
      { "type": "rule", "field": "format", "operator": "includesAny", "value": ["epub"] }
    ]
  },
  "sort": [{ "field": "lastReadAt", "dir": "desc" }],
  "pagination": { "page": 0, "size": 100 }
}
```

The Android filter sheet exposes title/author/series matching, unread/in-progress/finished progress, common formats, and the server sort fields most useful on a phone. BookOrbit's current source defines book relation rules as the singular `genre` or `author` field with operator `includesAny` and an array value. The client now follows that exact shape; the previous genre-chip request incorrectly used `genres`, `contains`, and a scalar value. Tapping a book-detail genre bypasses the local summary-only filter and opens a fully paginated server-filtered Books list scoped to the selected library. Local books use the standard controls against cached `BookSummary` metadata instead of sending a request; tags remain informational because the documented contract has no verified tag filter and tag chips are non-clickable. A real-repository MockWebServer regression asserts the exact genre payload and result parsing; target-server compatibility and result scope remain pending device validation.

Current response shape:

```json
{
  "items": [
    {
      "id": 15662,
      "title": "Book title",
      "authors": ["Author"],
      "files": [
        {
          "id": 15663,
          "format": "epub",
          "role": "primary"
        }
      ],
      "readingProgress": null
    }
  ],
  "total": 5012,
  "seriesCount": 321,
  "page": 0,
  "size": 100
}
```

Important notes:

- The client should use `files[].role == "primary"` when selecting a file.
- If no file is marked `primary`, the client now ranks supported reading/listening formats ahead of unknown attachments.
- `authors` is an array, not a scalar string.
- `readingProgress` is the progress object for ebook progress display.
- The Android client also tolerantly maps optional series identity/order, read state, and created/updated/last-read timestamps when present. These fields drive native Home shelves but are not assumed to exist on every server payload.
- A registered file or book may expose availability as `missing` through a boolean or one of the status/state/availability fields. The Android client treats that as server availability metadata, keeps the server-returned book visible, and does not treat it as a reading status.
- Cover metadata may arrive as `hasCover`, `coverUrl`, `cover.path`, or `coverImage.path`; when a cover is indicated without a direct URL, the client falls back to `/api/v1/books/{id}/cover`.
- Library responses may include `seriesCount` (or an equivalent `totalSeries`/`seriesTotal` field); the Android client uses it for the full Browse header while book pages are loaded incrementally.

### Book detail files

`GET /api/v1/books/{bookId}` returns every attached file in `files[]`, not only the server-primary file. Each entry may include `id`, `format`, `role`, `filename`, `sizeBytes`, `durationSeconds`, and update metadata. The Android client retains the primary file as the normal `BookSummary` and exposes supported alternate media through the Book Detail `Available file` control and bottom-sheet picker. Each option retains its file metadata and file-specific `fileId`; the selected file ID is preferred during detail hydration so selecting an audiobook such as M4B does not fall back to the primary EPUB.

The picker includes supported EPUB/KEPUB, PDF, comic, and audio formats. Labels show format, filename, size, and `Primary`/`Alternate` role. If those visible fields are identical, a short suffix of the stable file ID is appended for differentiation. Unknown attachments such as JSON remain outside the selectable reader/media options. Existing `Other versions` navigation continues to refer only to separate same-series, same-index book records.

### Global book search

Endpoint:

```text
POST /api/v1/books/query
```

The Android client sends `q`, an empty `sort` list, and pagination capped at 100 results. Search results retain their returned `libraryId` so details and reading actions target the correct library.

### Series catalog filters

Endpoint:

```text
GET /api/v1/series?q=&page=0&size=100&sort=name&order=asc
```

The Series filter sheet uses BookOrbit's `completionStatus`, `author`, `libraryId`, `genre`, `sort`, and `order` query parameters. A Series-detail genre chip opens a fully paginated catalog with `genre=<value>`. Completion values are `not_started`, `in_progress`, and `complete`; catalog sort values are `name`, `bookCount`, `lastAddedAt`, and `readProgress`. Genre query compatibility and exact result scope still require validation against the target server.

### Book cover

Endpoint:

```text
GET /api/v1/books/{id}/cover
```

Cover requests use the same authenticated cookie-aware HTTP client as the rest of the API and are cached in memory for the active app process.

### Book detail

Endpoint:

```text
GET /api/v1/books/{id}
```

The native detail screen maps the returned title, subtitle, authors, narrators, description, publisher, publication date/year, language, page count, ISBN-10, ISBN-13, genres, tags, library name, series identity/order, file metadata, and authenticated user's `rating`. This rating is a whole value from 1 through 5 or null; it is not decimal or aggregate metadata. Detail loading is network-first when available, with a version-matching detail-cache fallback so reader and offline actions remain usable. The cache stores `userRating` and accepts a legacy `rating` only when it is an exact whole value from 1 through 5.

The Metadata sources row renders one link per `providerIds` entry. The Android client now links all 14 of BookOrbit's provider keys (`google`, `goodreads`, `amazon`, `hardcover`, `openLibrary`, `itunes`, `audible`, `librofm`, `kobo`, `lubimyczytac`, `ranobedb`, opening `https://ranobedb.org/book/{bookId}`; `audnexus`, opening `https://api.audnex.us/books/{bookId}`; `comicvine`, opening `https://comicvine.gamespot.com/issue/{bookId}/`; and `aladin`, opening `https://www.aladin.co.kr/shop/wproduct.aspx?ItemId={bookId}`). Audnexus opens a provider API endpoint rather than a human-facing page.

### Personal rating

Authenticated endpoint:

```text
POST /api/v1/books/bulk-set-rating
```

Set or clear the signed-in user's rating:

```json
{
  "bookIds": [123],
  "rating": 5
}
```

Use JSON null to clear:

```json
{
  "bookIds": [123],
  "rating": null
}
```

After a successful write, the client re-fetches `GET /api/v1/books/{id}` and uses that response as authoritative. If the returned rating differs from the requested integer or null, the client treats the write as rejected, including the server's metadata-locked behavior, and rolls back the optimistic UI state.

### Series detail

Endpoint:

```text
GET /api/v1/series/{seriesId}/books?page=0&size=100&sort=seriesIndex&order=asc
```

The response contains `items` plus `seriesInfo`. BookOrbit limits `size` to 100, so the client requests and merges additional pages until `seriesInfo.bookCount` is reached. It maps the complete ordered book list, series name, book/read counts, authors, and possible index gaps, then loads the first book detail for synopsis and genre/tag context.

### Achievements

Authenticated endpoint:

```text
GET /api/v1/achievements
```

The contract is verified against the official current BookOrbit source. The response's `iconName` uses BookOrbit's official achievement icon vocabulary; the Android UI maps those values to semantic Material equivalents instead of displaying raw icon names. The client preserves server-censored secret fields, shows award dates for earned achievements, and shows locked current/threshold progress only when both values are provided. HTTP 404 is treated as an older server without Achievements support; other failures remain retryable.

## Files

### Stream a file

Endpoint:

```text
GET /api/v1/books/files/{fileId}/serve
```

Notes:

- Supports byte ranges.
- Intended for direct reading or listening without local download.

### Download a file

Endpoint:

```text
GET /api/v1/books/files/{fileId}/download
```

Notes:

- Requires download permission.
- Used by the app for offline local storage.
- The client streams the response and reports per-file byte progress to `BrowserState`; completed downloads and authentication-interrupted downloads clear the active state, while failures expose retry guidance.

## Progress

### Ebook progress

Write endpoint:

```text
POST /api/v1/books/files/{fileId}/progress
```

Authoritative reader-hydration endpoint:

```text
GET /api/v1/books/{bookId}/progress
```

The response is a per-file progress collection. During a normal online book open, the client selects the entry whose `fileId` matches the selected file and reads its `percentage` and `pageNumber`. For EPUB, the legacy chapter/page fallback treats BookOrbit's `pageNumber` as one-based and stores the corresponding reader page index as zero-based. For non-EPUB media, the client preserves BookOrbit's upstream zero-based `pageNumber` semantics. BookOrbit may also return an exact EPUB `cfi`; Phase 1 does not consume or upload that field, so exact CFI interoperability remains deferred. An empty response or an entry with omitted fields leaves the existing local fields intact. Reader hydration is kept separate from the metadata detail cache.

DTO shape:

```json
{
  "percentage": 50,
  "pageNumber": 12,
  "positionSeconds": 120.5,
  "cfi": "optional string",
  "koreaderProgress": "optional string"
}
```

Minimum field currently required by the client:

- `percentage`

Optional fields currently relevant to the client:

- `pageNumber`
- `positionSeconds`

For EPUB specifically, `percentage` is the strongest signal BookOrbit exposes: it is format-independent and does not depend on any client-side pagination assumptions. Server `pageNumber` is only a one-based chapter/page fallback derived from BookOrbit's own reading-position bookkeeping, not an exact in-book location. The exact Readium `Locator`/CFI and the client's rendered-page state (margins, font scale, theme, continuous vs. paginated layout) are never uploaded to BookOrbit, so the server cannot reconstruct the precise on-screen position a device last showed. Practically, this means same-device local Readium `Locator` resume (see [Reader implementations](./architecture.md)) is usually more precise than a fresh-install or cross-device hydration from server `percentage`/`pageNumber`, which can only approximate the original position. Progress uploads are also queued and throttled rather than sent immediately, and the best-effort flush on reader close is not guaranteed to reach the server before the app is fully closed offline; see [Background sync](./architecture.md#background-sync) for the queueing/throttle/close-flush behavior.

If this non-audio progress endpoint returns 404 for a queued event, the client treats the recorded file ID as potentially stale. It fetches `GET /api/v1/books/{bookId}`, resolves the current primary file, and retries progress once only when the replacement ID differs. After a successful remapped write it patches the normal `reading`/`read` status. A missing book/current file, unchanged ID, or second 404 is terminal `INVALID`; the event is acknowledged rather than retried forever. Authentication and other transient failures keep their existing retry behavior.

### Audiobook progress

Write endpoint:

```text
PATCH /api/v1/books/{id}/audio-progress
```

Authoritative reader-hydration endpoint:

```text
GET /api/v1/books/{bookId}/audio-progress
```

The response supplies `currentFileId`, `positionSeconds`, and `percentage`. The client applies the audio progress only when `currentFileId` matches the selected file; otherwise it preserves the selected file's existing progress. Incomplete responses preserve fields that were already present locally. Fractional `positionSeconds` values are converted to the reader's millisecond position, and this volatile progress remains outside the metadata detail cache.

DTO shape:

```json
{
  "percentage": 50,
  "currentFileId": 123,
  "positionSeconds": 120.5
}
```

Position sampling and network delivery are decoupled: the player samples the current playback position every 1.5 seconds locally, but that does not mean a write reaches the server every 1.5 seconds. Audio-progress writes are throttled by `ProgressQueuePolicy` so a new value is only queued for sync once roughly 15 seconds of elapsed time or 15 seconds of position movement has passed (or the percentage moves by at least 1 point), plus the usual debounce/backoff and connectivity-gated retry behavior of the sync worker. Active server-side audio progress should therefore be read as an approximate periodic snapshot, not an exact wall-clock timestamp of playback.

On a clean reader close, the app immediately publishes the final known position and attempts one flush of the pending queue in addition to durably persisting it locally, but that flush is best-effort: while offline or on request failure, delivery at close time is not guaranteed and instead relies on the existing connectivity-gated retry queue to deliver it once the device is back online.

When a book is reopened, the client compares its own queued-or-last-synced local progress against the server-hydrated progress and keeps whichever position is further ahead (by page/chapter index, then position, then percentage), rather than unconditionally preferring one source.

## Sessions and reading attempts (verified — 2026-07-26)

```text
GET /api/v1/books/{bookId}/sessions
GET /api/v1/books/{bookId}/reading-attempts
```

`sessions` returns analytics sessions for the book with `startedAt`, `endedAt`, `durationSeconds`, `progressDelta`, `endProgress`, `format`, `source`, and `stats`. `reading-attempts` returns date-level attempts with `outcome`, `totalSessions`, and `totalSeconds`. Neither response carries an exact audio position or file ID; both endpoints are analytics-level summaries, not exact playback state. Lagrange's audiobook-only Server reading history section (Book Detail) is authenticated and consumes both endpoints with loading/unsupported/error/empty states. Server session/attempt data is never treated as an exact audio position; the local Room `AudiobookSessionHistoryStore` remains authoritative for seeking, as it retains exact play/pause position and seek behavior. See `docs/architecture.md` for the separation of concerns between the two history sources.

## User statistics (integrated — 2026-07-26)

Verified endpoint family under `/api/v1/user-statistics/`. Lagrange currently consumes the summary and daily-reading endpoints in the lazy-loaded profile-menu Statistics destination; the remaining metrics are available for future expansion:

```text
GET /api/v1/user-statistics/summary
GET /api/v1/user-statistics/reading-heatmap
GET /api/v1/user-statistics/reading-source-distribution
GET /api/v1/user-statistics/peak-hours
GET /api/v1/user-statistics/favorite-days
GET /api/v1/user-statistics/session-timeline
GET /api/v1/user-statistics/completion-timeline
GET /api/v1/user-statistics/goal-trajectory
GET /api/v1/user-statistics/progress-funnel
GET /api/v1/user-statistics/completion-latency
GET /api/v1/user-statistics/genre-reading-time
GET /api/v1/user-statistics/reading-pace
GET /api/v1/user-statistics/session-archetypes
```

The Android client parses `summary` fields `trackedBooks`, `startedBooks`, `inProgressBooks`, `completedBooks`, and `meanProgressPercent`, plus daily-reading fields `day`, `readingSeconds`, `progressDelta`, and `eventsCount`. A 404 is treated as an unsupported older server; other request failures produce a retryable error state. Missing or empty fields are tolerated and shown as partial/empty data rather than preventing the screen from loading.

## Open items

- Confirm multi-file audiobook handling in the Android client
- Confirm whether session cookies alone are sufficient in all OIDC flows
- Expand the Statistics screen with additional validated metrics such as heatmap, source distribution, and peak-hours when their UI requirements are prioritized
