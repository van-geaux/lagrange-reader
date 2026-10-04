# Komga modules

The Komga adapter is implemented as provider-specific feature modules in `komga/`. `KomgaRepository` is a temporary compatibility adapter for the current coordinator data-source seam and delegates supported operations to these modules.

## Implemented modules

- `KomgaAuthModule`
  - validates the server through the Komga library endpoint;
  - signs in with HTTP Basic Auth;
  - verifies session state through the current-user endpoint;
  - clears the active Komga session.
- `KomgaLibraryModule`
  - loads and parses Komga libraries.
- `KomgaBookCatalogModule`
  - loads paginated books for a library;
  - maps Komga metadata into Lagrange book summaries;
  - preserves series identity, title, number, format, size, and authenticated thumbnail URL.
- `KomgaSeriesModule`
  - aggregates books across all Komga libraries;
  - groups books by series identity;
  - applies the selected library filter after aggregation;
  - loads ordered Series detail and representative covers.
- `KomgaHomeShelfModule`
  - loads Home shelves from Komga's authenticated server endpoints;
  - maps Continue reading, On deck, Recently read, Recently added books, Recently released books, and Recently added series;
  - preserves server ordering and does not derive Komga shelves from the full catalog;
  - omits an individual shelf when its endpoint is unavailable or returns no items.
- `KomgaReadingProgressModule`
  - synchronizes EPUB reader locators through Komga's Readium `progression` PUT endpoint and page-based progress through `read-progress` when applicable;
  - maps the shared reader progress callback to the provider-specific payload;
  - uses the server as the source of truth when Home is reloaded.
- `KomgaReadingStatusModule`
  - marks books read with `PATCH /api/v1/books/{bookId}/read-progress` and `{ "completed": true }`;
  - marks books unread with `DELETE /api/v1/books/{bookId}/read-progress`;
  - rejects status values Komga does not support.
- `KomgaBookDetailModule`
  - loads book metadata and media/file metadata;
  - maps available EPUB, PDF, comic, and audio files;
  - prepares the selected reader file.
- `KomgaCoverModule`
  - loads book covers with authenticated requests;
  - loads catalog/Series thumbnails with the active Komga authorization header.
- `KomgaDownloadModule`
  - downloads authenticated Komga files to app-private storage;
  - reports transfer progress;
  - writes shared download metadata so Local books and downloaded status survive process recreation;
  - deletes Komga local copies and their records.

## Verified Komga endpoints

- `GET /api/v1/libraries`
- `GET /api/v2/users/me`
- `GET /api/v1/books?library_id={libraryId}&page={page}&size={size}&sort=metadata.title,asc`
- `GET /api/v1/books/{bookId}`
- `GET /api/v1/books/{bookId}/thumbnail`
- `GET /api/v1/books/{bookId}/file`
- `GET /api/v1/books/latest`
- `POST /api/v1/books/list` with Komga's release-date condition for Recently released books
- `GET /api/v1/books/ondeck`
- `GET /api/v1/books?read_status=IN_PROGRESS`
- `GET /api/v1/books?read_status=READ`
- `GET /api/v1/series/new`
- `PATCH /api/v1/books/{bookId}/read-progress`
- `DELETE /api/v1/books/{bookId}/read-progress`

Komga authentication uses HTTP Basic Auth. A `401` or `403` library response is treated as a reachable Komga server so the login screen can collect credentials.

## Composition and selection

`KomgaModuleSet` is the Komga composition root for the provider-neutral capability modules. It owns the supported and explicitly unsupported Komga adapters, including authentication, library, catalog, Series, detail, cover, reader, download, status, cache, session, and authenticated-media behavior. `KomgaRepository` remains a temporary compatibility adapter for the legacy data-source surface. `ProviderRepositoryResolver` creates it only when the active server profile has provider ID `komga`; it does not infer BookOrbit by falling back from a failed Komga probe.

Active-reader session save/read/clear persistence and EPUB page-position persistence are supplied by the shared `ReaderLifecycleModuleImpl`, not by Komga. Komga remains responsible only for provider-dependent reader preparation and currently does not provide progress synchronization.

The provider ID is stored alongside the canonical server URL in `ServerProfile`. It is not appended to the URL and credentials are not stored in the profile record.

The coordinator and WorkManager worker route Komga transfers and local-copy operations through the provider download capability adapter. Queue persistence and retry policy remain shared application behavior.

The coordinator resolves Komga local downloads through `LocalBooksModule`; unsupported provider-specific actions remain unavailable.

Komga exposes only `Read` and `Unread` reading-status actions. The shared UI hides BookOrbit-only statuses while Komga is active, and successful mutations are sent to the authenticated Komga read-progress endpoints above.

Komga `ReadingProgressModule` routes EPUB progress through the authenticated Readium `progression` endpoint when the reader supplies a serialized locator, and uses the page-based `read-progress` endpoint for other formats.

Komga `ReadingSessionModule` operations explicitly report that reading-session history and synchronization are unavailable; session reporters do not queue successful no-op events.

Komga `AnnotationModule` operations explicitly report that annotations are unavailable; the EPUB reader and annotation worker do not use a BookOrbit annotation repository while Komga is active.

Komga exposes no achievement module. `KomgaModuleSet` returns an unsupported catalogue and shared navigation hides the Achievements destination while Komga is active.
Komga exposes no statistics module. `KomgaModuleSet` returns an explicit unsupported result and shared navigation hides the Statistics destination while Komga is active.
Komga exposes no Smart Scope module. `KomgaModuleSet` returns an explicit unavailable result and shared navigation hides Smart Scopes while Komga is active.
Komga exposes no author module. `KomgaModuleSet` returns an explicit unavailable result and shared navigation hides Authors while Komga is active.
Komga uses its detail implementation for book metadata and cached detail loading. User-rating mutations are explicitly unavailable and the shared detail UI hides rating controls.
Komga exposes authenticated `BookCoverModule` loading for book covers, catalog images, EPUB image previews, and comic pages.
Komga exposes `BookCatalogModule` and `HomeShelfModule` adapters for library catalog retrieval and server-provided Home shelf access; shared UI owns shelf presentation and refresh orchestration, but does not calculate Komga shelf membership.
Komga exposes `LibraryModule` for authenticated library discovery.
Komga authentication and server probing are routed through provider `LoginModule` and `ServerDetectionModule` adapters backed by its Basic Auth/session implementation.
BookOrbit-specific cover warming and offline-cache workers do not run while Komga is active.

## Unsupported capabilities

Komga reading-status mutations support only Read and Unread. Smart Scopes, authors, annotations, achievements, statistics, and server reading-session synchronization are not implemented in this adapter checkpoint. The adapter supports EPUB Readium progression when the reader supplies a real locator, and page-based reader progress for other applicable formats. Unsupported actions must not be presented as working Komga features. Home shelf reads are supported through the server-provided endpoints listed above; Home shelf mutations remain unsupported.

## Compatibility boundary

The Komga modules use Komga-specific endpoint paths and JSON fields. Do not route Komga requests through BookOrbit workers or parsers. In particular, Komga downloads must use the authenticated `KomgaDownloadModule`, not the generic worker path that constructs a `BookOrbitRepository`.
