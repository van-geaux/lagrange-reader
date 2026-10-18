# Kavita modules

The Kavita adapter uses provider-specific modules under `provider/kavita/`. `KavitaRepository` remains a compatibility adapter for the current coordinator data-source seam and delegates supported operations to these modules.

## Implemented modules

- `KavitaAuthModule`
  - probes `GET /api/Library/libraries`;
  - authenticates username/password through `POST /api/Account/login`;
  - stores the returned JWT in encrypted, profile-scoped storage;
  - sends bearer headers for authenticated requests.
- `KavitaLibraryModule`
  - loads libraries from `GET /api/Library/libraries`.
- `KavitaBookCatalogModule`
  - loads paginated series from `POST /api/Series/v2`;
  - loads series chapters from `GET /api/Series/series-detail`;
  - projects chapters into the shared book catalog model;
  - preserves series identity, chapter identity, volume identity during provider requests, file metadata, formats, and page progress.
- `KavitaSeriesCatalogModule`
  - groups projected chapter books by Kavita series ID;
  - applies library, query, author, genre, completion, and sort filters through the shared series projection helpers;
  - loads ordered series detail and a representative chapter detail.
- `KavitaBookDetailModule`
  - loads `GET /api/Series/chapter`;
  - maps metadata, files, pages, genres, tags, authors, and sizes.
- `KavitaCoverModule`
  - loads authenticated series covers from `/api/Image/series-cover`.
- `KavitaReaderModule`
  - loads `GET /api/Reader/chapter-info`;
  - routes comic pages through `/api/Reader/image`;
  - routes PDFs through `/api/Reader/pdf`.
- `KavitaReadingProgressModule` and `KavitaReadingStatusModule`
  - read and write chapter page progress;
  - mark a chapter read;
  - report chapter unread as unavailable because Kavita exposes unread at series scope.
- `KavitaDownloadModule`
  - downloads chapters from `/api/Download/chapter`;
  - stores files in app-private storage and shared `DownloadStore` metadata;
  - supports progress callbacks and local deletion.
- `KavitaRepository` local-books and cache integration
  - expose completed Kavita downloads as the Local books catalog;
  - restore the cached browser snapshot when the server is unavailable;
  - report app storage usage and clear disposable cache files;
  - update, cancel, inspect, and clear the Kavita offline download cache.

## Unsupported capabilities

Kavita EPUB reader routing now downloads the EPUB through the authenticated chapter download endpoint into app-private storage before opening it with the shared local EPUB reader. Comic and PDF chapters continue to use Kavita reader endpoints. Chapter downloads, Local books, browser snapshots, storage reporting, offline-cache operations, and background cache warming are supported. Audiobook downloads, authors, annotations, statistics, achievements, Smart Scopes, and server reading-session history remain unavailable or unsupported as described by their modules.

## Composition

`KavitaModuleSet` is the composition root for Kavita capability modules. `ProviderRepositoryResolver` selects `KavitaRepository` only when the active profile has provider ID `kavita`; it does not infer BookOrbit after a failed Kavita request.

Kavita usernames and passwords are never stored in ordinary profiles, source files, logs, fixtures, or documentation. Authenticated demo-server behavior requires locally supplied credentials through the secure local mechanism and separate manual validation.
