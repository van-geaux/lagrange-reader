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

### Current user

Endpoint:

```text
GET /api/v1/auth/me
```

Used to confirm authenticated session state after login.

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

Current client request body:

```json
{}
```

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
  "page": 0,
  "size": 50
}
```

Important notes:

- The client should use `files[].role == "primary"` when selecting a file.
- `authors` is an array, not a scalar string.
- `readingProgress` is the progress object for ebook progress display.

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
- Confirm whether `/api/v1/auth/me` should be used directly in the app bootstrap
- Confirm whether session cookies alone are sufficient in all OIDC flows
