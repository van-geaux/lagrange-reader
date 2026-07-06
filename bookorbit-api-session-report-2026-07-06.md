# BookOrbit API Session Report

Date: 2026-07-06

## Objective

Check the API docs at `http://ryzen9:42200/api/docs` to determine whether the server supports:

1. Reading the library catalog
2. Downloading books locally
3. Reading locally and syncing progress to the server
4. Streaming books when not downloaded

## Findings

The API does support the core pieces needed for this workflow.

### Library access

- `GET /api/v1/libraries`
- `GET /api/v1/libraries/{id}`
- `POST /api/v1/libraries/{id}/books`

These endpoints let an app discover libraries and query the books in a library.

### Local download and streaming

- `GET /api/v1/books/files/{fileId}/download`
- `GET /api/v1/books/files/{fileId}/serve`

These endpoints support fetching a file for offline use and serving it directly when local storage is not available.

### Reading progress sync

- `POST /api/v1/books/files/{fileId}/progress`
  - Updates ebook reading progress
  - Uses `SaveProgressDto`
- `PATCH /api/v1/books/{id}/audio-progress`
  - Updates audiobook progress
  - Uses `UpsertAudioProgressDto`

### Reading sessions

- `POST /api/v1/books/files/{fileId}/sessions`

This can be used to store reading session data, which may complement progress sync.

## Conclusion

Yes, the API is sufficient for building an app that:

1. Reads the library catalog
2. Downloads books locally
3. Reads locally and syncs progress back to the server
4. Streams books when they are not downloaded

## Implementation Notes

- Progress sync is file-based for ebooks, so the app should maintain a `book -> fileId` mapping.
- Audio progress uses the book-level audio endpoint.
- The docs do not show a dedicated offline sync queue, so the app would need to store local progress updates and replay them when back online.
