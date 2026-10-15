# Kavita modules

The Kavita adapter uses provider-specific modules under `provider/kavita/`. `KavitaRepository` remains a compatibility adapter for the current coordinator data-source seam and delegates supported operations to these modules.

## Implemented modules

- `KavitaAuthModule`
  - probes `GET /api/Library/libraries`;
  - authenticates an Auth Key through `POST /api/Plugin/authenticate`;
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

## Unsupported capabilities

Kavita EPUB reader routing, chapter-unread mutation, audiobook downloads, Home shelves, authors, annotations, statistics, achievements, Smart Scopes, provider cache warming, and server reading-session history remain unavailable. The module set exposes these as unavailable rather than returning successful empty results.

## Composition

`KavitaModuleSet` is the composition root for Kavita capability modules. `ProviderRepositoryResolver` selects `KavitaRepository` only when the active profile has provider ID `kavita`; it does not infer BookOrbit after a failed Kavita request.

Auth Keys are never stored in ordinary profiles, source files, logs, fixtures, or documentation. Authenticated demo-server behavior still requires a locally supplied Auth Key and separate manual validation.
