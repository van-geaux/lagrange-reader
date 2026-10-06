# Komga integration checkpoint

This checkpoint adds the Komga provider modules needed to connect, inspect, download, and open a Komga book.

## Verified contract

The implementation follows Komga's documented REST contract:

- authentication probe: `GET /api/v1/libraries`;
- current-user verification: `GET /api/v2/users/me`;
- authentication: HTTP Basic Auth or the Komga web session cookie;
- libraries: `GET /api/v1/libraries`;
- books: `GET /api/v1/books?library_id={libraryId}&page={page}&size={size}&sort=metadata.title,asc`; filtered catalog requests load the authenticated library pages and apply the shared catalog predicates and sort order to the provider-owned book projections.
- book details: `GET /api/v1/books/{bookId}`;
- cover thumbnail: `GET /api/v1/books/{bookId}/thumbnail`;
- book file: `GET /api/v1/books/{bookId}/file`.

A `401` or `403` response from the library probe is treated as a reachable Komga server. Basic credentials are stored in the encrypted Android Keystore-backed credential store. When the user selects `Open server sign-in`, the existing in-app WebView loads Komga's `/login` page, so Komga-configured OAuth2/OIDC providers can complete their normal web flow. The WebView supports JavaScript-created windows used by identity providers and keeps those windows inside the sign-in dialog while sharing the Komga session cookies. The resulting Komga session cookie is reused for API, cover, download, streaming, and reader requests. Native AppAuth/Custom Tabs and provider-specific native redirects are not included.

## Modules

- `KomgaAuthModule`: server probing, Basic Auth login, web-session verification, session-cookie request headers, and session clearing.
- `KomgaLibraryModule`: library loading and parsing.
- `KomgaBookCatalogModule`: paginated library-book loading, genre/read-progress/date projection, and catalog mapping used by the shared filter and sort layer.
- `KomgaBookDetailModule`: book metadata, media information, file metadata, and reader URLs.
- `KomgaCoverModule`: authenticated cover-thumbnail loading.
- `KomgaDownloadModule`: authenticated streaming download to app-private storage with progress callbacks.
- Komga downloads are recorded in the shared `DownloadStore`, so completed files appear in Local books and remain marked as downloaded when book detail is reopened.
- Komga Series derives an aggregate catalog across all libraries from authenticated book metadata (`seriesId`, `seriesTitle`, and `number`); the Series library filter is applied to that aggregate before grouping. Series thumbnails use authenticated Komga book-thumbnail requests.
- Komga book and series catalog filters and sorts are applied to the mapped provider data, including genre, read status, read progress, last read, series completion, and date/name/count ordering. Author navigation remains unavailable because Komga does not provide the corresponding module capability.
- `KomgaRepository`: temporary compatibility adapter implementing the current coordinator data-source seam and delegating supported behavior to the modules.
- `ProviderRepositoryResolver`: selects the adapter from the explicit server-type tag stored with the configured profile; it does not infer BookOrbit by falling back from a failed Komga probe.

The reader currently downloads the selected Komga file to app-private storage before opening it, so the first reader checkpoint works offline after the download completes. Reader position synchronization, annotations, achievements, statistics, and progress synchronization remain unavailable and must not be treated as verified Komga behavior.
