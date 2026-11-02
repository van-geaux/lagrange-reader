# Komga integration checkpoint

This checkpoint documents the Komga provider module boundary and the verified implementation contract. Live-server and device validation remain separate from the static/API contract evidence below.

## Verified contract

The implementation follows Komga's documented REST contract:

- authentication probe: `GET /api/v1/libraries`, with redirects disabled so redirect responses remain classifiable;
- current-user verification: `GET /api/v2/users/me`;
- authentication: HTTP Basic Auth or the Komga web-session cookie;
- libraries: `GET /api/v1/libraries`;
- books: `GET /api/v1/books?library_id={libraryId}&page={page}&size={size}&sort=metadata.title,asc`;
- book search: `POST /api/v1/books/list?page=0&size=100&sort=metadata.title,asc` with a Komga `fullTextSearch` JSON payload;
- series catalog: `POST /api/v1/series/list?unpaged=true&sort=metadata.title,asc`, with server-side query/library/genre/completion conditions and shared local filtering/sorting for the remaining predicates;
- series books: paginated `GET /api/v1/series/{seriesId}/books`;
- Home books: Komga book shelves plus `POST /api/v1/books/list` for recently released books;
- Home series: `GET /api/v1/series/new` and `GET /api/v1/series/updated`, each bounded to eight items;
- book details: `GET /api/v1/books/{bookId}`;
- cover thumbnails: `GET /api/v1/books/{bookId}/thumbnail` and `GET /api/v1/series/{seriesId}/thumbnail`;
- book file: `GET /api/v1/books/{bookId}/file`;
- reading progress: `PATCH /api/v1/books/{bookId}/read-progress` and Readium progression `PUT /api/v1/books/{bookId}/progression`;
- reading status: `PATCH` and `DELETE` on `/api/v1/books/{bookId}/read-progress`.

A `401` or `403` response from the library probe is treated as a reachable Komga server. Basic credentials are stored in the encrypted Android Keystore-backed credential store, and a failed credential write is surfaced as a user-facing persistence error rather than treated as a successful durable login. Web-session cookies are cleared when the active profile has no matching cookie-owner marker, preventing a process-wide WebView cookie from being reused by another authenticated profile. No credential or cookie values are stored in this documentation.

## Modules

- `KomgaAuthModule`: server probing, Basic Auth login, web-session verification, profile-scoped cookie ownership, credential persistence, and session clearing.
- `KomgaLibraryModule`: library loading and parsing.
- `KomgaBookCatalogModule`: paginated library-book loading, native book search, series-book loading, metadata projection, and catalog mapping used by the shared filter and sort layer.
- `KomgaSeriesModule`: native Komga series catalog/detail requests instead of scanning every book in every library for each request.
- `KomgaHomeShelfModule`: bounded provider shelves, recently added/updated series, cancellation-preserving shelf failures, and deferred initial catalog loading.
- `KomgaBookDetailModule`: book metadata, media information, file metadata, and reader URLs.
- `KomgaCoverModule`: authenticated cover-thumbnail loading.
- `KomgaDownloadModule`: authenticated streaming download to app-private storage with progress callbacks.
- Komga downloads are recorded in the shared `DownloadStore`, so completed files appear in Local books and remain marked as downloaded when book detail is reopened.
- Offline metadata-cache controls are explicitly unavailable for Komga. Local catalog snapshots and completed downloads remain distinct shared fallback/local-book behavior, and the UI does not render the unsupported offline-cache lifecycle controls.
- `KomgaRepository`: compatibility adapter implementing the current coordinator data-source seam and delegating supported behavior to the modules. Provider catches rethrow coroutine cancellation instead of converting it into a fallback result.
- `ProviderRepositoryResolver`: selects the adapter from the explicit server-type tag stored with the configured profile; it does not infer BookOrbit by falling back from a failed Komga probe.

The reader can open downloaded Komga files from app-private storage, and the provider exposes book progress/status synchronization through the endpoints above. Annotations, achievements, statistics, Smart Scopes, authors, reading sessions, and audiobook-specific behavior remain unavailable unless the provider module contract changes. Automated tests in this repository do not replace live authenticated Komga-server or Android-device validation.
