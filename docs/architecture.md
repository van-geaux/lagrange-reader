# Architecture

## Goal

`Lagrange Reader` is a native Android client for BookOrbit focused on reading and listening only. It does not manage metadata or server settings beyond initial server selection and login.

## App flow

The current app flow is:

1. User enters a BookOrbit server URL.
2. The app opens the server login page in a WebView.
3. The app probes the authenticated API to determine whether login has succeeded.
4. After authentication, the app loads libraries and books.
5. The user can stream content, download it, reopen local files offline, and queue progress updates for later sync.

## Main components

### UI and navigation

- `MainActivity` boots the app and wires the graph.
- `AppCoordinator` owns screen state and orchestrates transitions.
- `AppScreen` defines the app-level screens.
- `BookOrbitApp` renders the UI for setup, login, library browsing, and reader/player screens.

### Data and API layer

- `BookOrbitRepository` is the main integration layer with the BookOrbit server.
- It stores the selected server URL and selected library.
- It loads libraries and books from the live API.
- It resolves stream and download URLs for files.
- It translates local progress events into the server DTO shapes.

### Local persistence

- `DataStore` stores the configured server URL and selected library ID.
- `DownloadStore` stores downloaded file records.
- `ProgressQueueStore` stores pending progress updates that still need to be synced.

### Background sync

- `ProgressSyncWorker` runs queued progress sync when network is available.
- Sync is currently event-based and timestamped.
- Conflict handling is currently newest-progress-wins.

## Live BookOrbit contract currently assumed

Validated against the live server and BookOrbit source:

- `POST /api/v1/auth/login`
- `GET /api/v1/auth/me`
- `GET /api/v1/libraries`
- `POST /api/v1/libraries/{id}/books`
- `GET /api/v1/books/files/{fileId}/serve`
- `GET /api/v1/books/files/{fileId}/download`
- `POST /api/v1/books/files/{fileId}/progress`
- `PATCH /api/v1/books/{id}/audio-progress`

## Known architectural gaps

- EPUB and comic reading are still placeholders.
- Login completion detection is still indirect and based on API probing.
- Sync queue behavior needs compaction and stronger failure classification.
- Reader state restoration is basic.
