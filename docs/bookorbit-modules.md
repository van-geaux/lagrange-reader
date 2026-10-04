# BookOrbit modules

`bookorbit/BookOrbitModules.kt` currently composes the provider-neutral module contracts around the existing `BookOrbitDataSource`. This is the BookOrbit adapter during the incremental repository extraction.

## Composition

`BookOrbitModuleSet` receives a `BookOrbitDataSource` and exposes a `ProviderModules` value. Each module delegates to the corresponding existing BookOrbit operation while preserving the current behavior and persistence paths.

`BookOrbitModuleSet` is a provider composition root, not a second core layer. It may contain BookOrbit adapters for authentication, server probing, catalog retrieval, parsing, downloads, local-file resolution, and BookOrbit synchronization semantics. It must not contain shared reader/player UI, EPUB/PDF/comic rendering, TTS playback, Image Library scanning/extraction/viewing, generic worker orchestration, or provider-neutral cache/session/progress presentation.

Implemented composition includes:

- server selection and selected-library state;
- server reachability and validation;
- username/password session login and session clearing;
- libraries, paginated books, recent books, cached catalogs, refresh, and search;
- Series catalog and Series detail;
- cached Home books;
- Smart Scopes and scoped Series catalogs/details;
- authors and author books;
- book details, cached details, and user ratings;
- book covers and catalog images;
- reader progress, reader state, EPUB image-library preparation, provider-backed reader restoration, and EPUB positions;
- downloads, multipart audiobook download files, and local-copy deletion;
- Local books;
- reading-status mutations and progress queue/synchronization;
- reading sessions and reading attempts;
- annotations and pending annotation mutations;
- achievements and statistics;
- app/offline-cache storage, refresh, cancellation, and clearing.

## Compatibility seam

The module set is an adapter, not yet a complete decomposition of the BookOrbit repository. The existing repository remains responsible for the underlying HTTP calls, parsing, caches, download records, workers, and synchronization stores. The module wrappers prevent new callers from depending on the entire broad interface while those responsibilities are extracted.

When extracting another BookOrbit capability, keep the provider-specific part in `provider/bookorbit/` and expose only the smallest provider-neutral operation required by core. For example, a BookOrbit endpoint and response mapper belong in the BookOrbit adapter; reader lifecycle, local validation, UI state, retry policy, and shared persistence orchestration belong in core. Do not solve a provider-specific gap by adding a BookOrbit branch to `AppCoordinator`, a reader activity, a shared worker, or a core feature implementation.

Active-reader session save/read/clear persistence and EPUB page-position persistence are shared by `ReaderLifecycleModuleImpl` in the main application layer. The BookOrbit provider receives the saved session and only prepares provider-dependent reader content.

Do not add Komga or other provider behavior to `BookOrbitRepository` or `BookOrbitModuleSet`. Provider-specific endpoint behavior belongs in that provider's module implementation.

Do not add new provider-specific operations to `BookOrbitDataSource` merely to make a caller convenient. Add or extend a focused contract in `core/ProviderModuleContracts.kt`, implement it in the provider module sets, and retain a legacy repository fallback only while the production caller is being migrated.

The coordinator, in-process scheduler, and WorkManager worker now resolve the selected provider's `DownloadModule` for audiobook-file expansion, physical transfers, and local-copy deletion. Queue persistence, progress callbacks, retry policy, and cancellation remain shared application behavior.

The coordinator resolves BookOrbit local downloads through `LocalBooksModule`; Local Books presentation is shared UI behavior.

BookOrbit reading-status mutations are routed through `ReadingStatusModule`; the coordinator retains shared UI projection and pending-progress cleanup.

BookOrbit reading-progress queueing and synchronization are routed through `ReadingProgressModule`, including progress emitted by EPUB TTS playback and background synchronization.

BookOrbit reading-session history and event synchronization are routed through `ReadingSessionModule`, including reader activity reporting and the background sync worker.

BookOrbit annotation loading, EPUB reader mutations, trash/restore/purge actions, and background mutation synchronization are routed through `AnnotationModule`.

BookOrbit exposes `AchievementModule` and shows the Achievements destination when that provider is active.
BookOrbit also exposes `StatisticsModule` and shows the Statistics destination when that provider is active.
BookOrbit exposes `SmartScopeModule` for Smart Scope loading and scoped Series navigation.
BookOrbit exposes `AuthorModule` for author catalogs and author-book navigation.
BookOrbit exposes `BookDetailModule` for detail loading, cached detail loading, and user-rating mutations.
BookOrbit exposes `BookCoverModule` for book covers and catalog images.
BookOrbit exposes `BookCatalogModule` and `HomeShelfModule` for library catalogs, recent sections, search, and cached Home books.
BookOrbit exposes `LibraryModule` for library discovery.
BookOrbit exposes provider adapters for `LoginModule` and `ServerDetectionModule`, preserving its existing authentication and server-probing behavior.

BookOrbit-specific authenticated media headers, profile-session persistence, OIDC/SSO exchange, and background-cache availability are provider concerns. They are exposed through focused provider capabilities where core needs them; the core reader, player, coordinator, and workers must not import BookOrbit endpoint or authentication details.

## Provider behavior

BookOrbit supports the existing Lagrange feature surface, including BookOrbit username/password authentication, server-hosted sign-in and OIDC flows, catalog browsing, readers, downloads, progress, sessions, annotations, achievements, statistics, and offline cache operations where the underlying implementation provides them.

The active server profile selects the BookOrbit adapter when its provider ID is `bookorbit`. Credentials and sessions are not stored in `ServerProfile` records.

## Future extraction

The next extraction steps should move coordinator call sites from the broad repository to focused modules, then reduce the compatibility surface. Each step should preserve BookOrbit behavior and add tests at the module boundary before removing a legacy method.
