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

When an authenticated request receives `401` or `403`, the client closes that response, attempts `POST /api/v1/auth/refresh`, then falls back to `POST /api/v1/auth/token/renew` when the first path is not available. It stores a returned `accessToken` when present and retries the original request once. These renewal paths are client fallbacks pending confirmation against the target deployment; physical-device testing must record the actual server contract if both are unavailable.

### Current user

Endpoint:

```text
GET /api/v1/auth/me
```

Used to confirm authenticated session state after login.
The app also uses this endpoint during bootstrap and login polling instead of inferring auth state from library loading.

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
  "pagination": { "page": 0, "size": 50 }
}
```

Subsequent pages use the same body with an incremented `pagination.page` value.

When Library Browse filters are applied, the client adds BookOrbit's standard filter group and sort fields while keeping the same pagination contract. For example:

```json
{
  "filter": {
    "type": "group",
    "join": "AND",
    "rules": [
      { "type": "rule", "field": "title", "operator": "contains", "value": "Dune" },
      { "type": "rule", "field": "readProgress", "operator": "isInProgress" },
      { "type": "rule", "field": "format", "operator": "includesAny", "value": ["epub"] }
    ]
  },
  "sort": [{ "field": "lastReadAt", "dir": "desc" }],
  "pagination": { "page": 0, "size": 50 }
}
```

The Android filter sheet exposes title/author/series matching, unread/in-progress/finished progress, common formats, and the server sort fields most useful on a phone. Local books use the same controls against cached `BookSummary` metadata instead of sending a request.

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
  "size": 50
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

The Series filter sheet uses BookOrbit's `completionStatus`, `author`, `libraryId`, `sort`, and `order` query parameters. Completion values are `not_started`, `in_progress`, and `complete`; catalog sort values are `name`, `bookCount`, `lastAddedAt`, and `readProgress`.

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

The native detail screen maps the returned title, subtitle, authors, narrators, description, publisher, publication date/year, language, page count, ISBN-10, ISBN-13, rating, genres, tags, library name, series identity/order, and file metadata. The selected shelf summary remains the fallback if the detail request fails, so reader and offline actions remain usable.

### Series detail

Endpoint:

```text
GET /api/v1/series/{seriesId}/books?page=0&size=100&sort=seriesIndex&order=asc
```

The response contains `items` plus `seriesInfo`. BookOrbit limits `size` to 100, so the client requests and merges additional pages until `seriesInfo.bookCount` is reached. It maps the complete ordered book list, series name, book/read counts, authors, and possible index gaps, then loads the first book detail for synopsis and genre/tag context.

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
