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

The Android login screen currently uses native username/password credentials only. Direct OIDC/SSO provider discovery, redirects, and callback handling are deferred pending a confirmed target-server contract.

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
- Cover metadata may arrive as `hasCover`, `coverUrl`, `cover.path`, or `coverImage.path`; when a cover is indicated without a direct URL, the client falls back to `/api/v1/books/{id}/cover`.
- Library responses may include `seriesCount` (or an equivalent `totalSeries`/`seriesTotal` field); the Android client uses it for the full Browse header while book pages are loaded incrementally.

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

Endpoint:

```text
POST /api/v1/books/files/{fileId}/progress
```

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

If this non-audio progress endpoint returns 404 for a queued event, the client treats the recorded file ID as potentially stale. It fetches `GET /api/v1/books/{bookId}`, resolves the current primary file, and retries progress once only when the replacement ID differs. After a successful remapped write it patches the normal `reading`/`read` status. A missing book/current file, unchanged ID, or second 404 is terminal `INVALID`; the event is acknowledged rather than retried forever. Authentication and other transient failures keep their existing retry behavior.

### Audiobook progress

Endpoint:

```text
PATCH /api/v1/books/{id}/audio-progress
```

DTO shape:

```json
{
  "percentage": 50,
  "currentFileId": 123,
  "positionSeconds": 120.5
}
```

## Open items

- Confirm multi-file audiobook handling in the Android client
- Confirm whether session cookies alone are sufficient in all OIDC flows
