# Provider module separation

This document defines the provider-neutral module boundary in Lagrange. Each provider supplies implementations for the capabilities it supports. The coordinator and Compose UI consume these capability contracts instead of calling provider-specific endpoints directly.

## Why the boundary exists

Book servers expose different authentication methods, endpoint shapes, metadata, media delivery rules, and feature sets. A provider must not pretend to support a capability by returning an empty result or a successful no-op. Unsupported capabilities remain unavailable and their UI actions must not be rendered.

The current broad `BookOrbitDataSource` is a compatibility seam. New provider behavior belongs behind the module contracts in `core/ProviderModuleContracts.kt`; callers can migrate module by module without changing the provider-neutral UI contract.

## Ownership decision rule

Keep behavior in core when it is provider-neutral orchestration or local application behavior: Compose UI, `AppCoordinator`, readers and players, EPUB Image Library scanning/extraction/viewing, TTS playback and locator handling, local media validation, shared cache/session/progress orchestration, worker scheduling, download queue state, retry policy, and capability presentation.

Keep behavior in `provider/<provider>/` when it depends on a provider endpoint, JSON field, authentication mechanism, URL, payload, server ordering rule, provider-specific download/local-file lookup, synchronization semantics, or unsupported-capability rule. Expose that behavior through a focused contract in `core/ProviderModuleContracts.kt` rather than branching in core.

Concrete composition roots are `provider/bookorbit/BookOrbitModules.kt` and `provider/komga/KomgaModuleSet.kt`. `ProviderModuleResolver.kt` selects the composition for the active provider; it should not become a second provider implementation layer. `BookOrbitDataSource` and repository fallbacks are temporary compatibility seams only and must not gain new provider-specific operations.

Image Library and TTS are core features. A provider may resolve the selected local file for Image Library or synchronize provider-specific progress for TTS, but scanning, extraction, viewing, speech playback, highlighting, lifecycle, and locator tracking remain in core.

When a provider does not support a capability, its module must expose an explicit unavailable/unsupported result or the established user-facing unavailable error. Empty data and successful no-ops are not valid substitutes. Provider-exclusive UI must be gated by the same capability contract.

## Module contracts

- `ServerSelectionModule`: server URL and selected-library state.
- `ServerDetectionModule`: reachability and server validation.
- `LoginModule`: session state, sign-in, and session clearing.
- `LibraryModule`: library discovery.
- `BookCatalogModule`: library catalogs, paging, recent books, search, and cached catalogs.
- `SeriesCatalogModule`: Series catalog and Series detail.
- `HomeShelfModule`: cached Home content.
- `SmartScopeModule`: Smart Scope discovery, catalogs, and details.
- `AuthorModule`: author catalog and author books.
- `BookDetailModule`: detail hydration, cached detail, and user rating.
- `BookCoverModule`: book covers and catalog images.
- `ReaderModule`: provider-dependent progress hydration, reader preparation, and restoration of provider-backed reader state.
- `ReaderLifecycleModule`: shared active-reader persistence and lifecycle cleanup.
- `DownloadModule`: remote downloads, audiobook download files, and local-copy deletion.
- `LocalBooksModule`: completed local-book discovery.
- `ReadingStatusModule`: read/unread and reading-status mutations.
- `ReadingProgressModule`: progress queueing and synchronization.
- `ReadingSessionModule`: reading-session/attempt loading, queueing, and synchronization.
- `AnnotationModule`: annotation CRUD, restoration, deletion, and mutation synchronization.
- `AchievementModule`: achievement loading.
- `StatisticsModule`: statistics loading.
- `CacheModule`: storage usage, offline-cache lifecycle, and background refresh configuration.
- `AuthenticatedMediaModule`: origin-scoped provider media headers and authentication recovery for remote reader/player requests.
- `ProviderSessionModule`: provider/profile session save and restore.
- `BackgroundCacheModule`: provider-specific background cache warming, including explicit availability when unsupported.
- `EpubTtsModule`: provider-neutral EPUB text-to-speech session, request, and playback-service boundary. This is a reader feature module, not a server-provider module.

`ProviderModules` groups these contracts into one provider capability set.

Provider implementations are grouped under `provider/`: BookOrbit composition lives in `provider/bookorbit/`, Komga composition and provider contracts live in `provider/komga/`, and cross-provider selection/resolution lives in `provider/`. The broad `BookOrbitRepository` compatibility seam remains at the application package boundary until its callers are fully migrated.

Configured server profiles persist an optional display name together with the untouched server URL and provider ID. The Change Server menu displays all three values, while credentials remain in protected provider-specific storage.

Provider sessions are now preserved per profile in encrypted Android Keystore-backed storage. Switching profiles restores and validates that profile's session; an invalid session is cleared only for that profile and the user is returned to its sign-in screen.

The login screen Change server action, the main server setup screen, and the browser Change Server menu all use the configured profile list. Selecting a profile routes through that profile's provider and sign-in/session flow. The browser menu offers New server, which opens the main server setup screen instead of collecting server details inline.

Download queueing, progress projection, cancellation, and durable scheduling remain shared application behavior. In-process scheduling and the WorkManager worker now route physical transfers through the selected provider's `DownloadModule`; persistence cleanup remains shared download-state behavior.

Local Books loading is provider-resolved through `LocalBooksModule`; the screen and presentation remain shared application behavior.

Reading-status mutations are provider-resolved through `ReadingStatusModule`; shared code updates the browser projection and clears local pending progress. Unsupported providers return an explicit unavailable error rather than a successful no-op.

Reading-progress queueing and synchronization are provider-resolved through `ReadingProgressModule`. The coordinator, progress worker, and EPUB TTS playback path use the selected provider module; pending-count projection and retry orchestration remain shared behavior.

Reading-session history, event queueing, and synchronization are provider-resolved through `ReadingSessionModule`. The reporter and background worker use the selected provider; unsupported providers return explicit unavailable errors.

Annotation loading and mutations are provider-resolved through `AnnotationModule`. Reader annotations, coordinator actions, and the background mutation worker use the selected provider; unsupported providers return explicit unavailable errors.

Achievements are provider-resolved through `AchievementModule`. BookOrbit uses the achievement implementation, while Komga returns an explicit unsupported catalogue. Shared navigation hides the Achievements destination and redirects stale achievement navigation when the selected provider does not expose this module.

Statistics are provider-resolved through `StatisticsModule`. BookOrbit uses its statistics implementation, while Komga returns an explicit unsupported result. Shared navigation hides the Statistics destination and redirects stale statistics navigation when the selected provider does not expose this module.

Smart Scopes are provider-resolved through `SmartScopeModule`. BookOrbit owns the server-backed scope catalog and series operations, while Komga returns an explicit unavailable result. Shared navigation hides Smart Scopes and redirects stale Smart Scope navigation when the selected provider does not expose this module.

Authors are provider-resolved through `AuthorModule`. BookOrbit owns author catalog and author-book loading, while Komga returns an explicit unavailable result. Shared navigation hides Authors and redirects stale author navigation when the selected provider does not expose this module.

Book Details are routed through `BookDetailModule` for detail loading, cached detail loading, and user-rating mutations. Komga supports detail loading but reports user-rating mutations as unavailable; the shared detail UI hides rating controls for unsupported providers.

Book covers and catalog images are routed through `BookCoverModule`. Both providers retain their own authenticated/image-loading behavior, while shared UI, audio playback, EPUB image-library, and comic-page callers use the coordinator capability path.

Home shelf cache access and library/catalog loading are routed through `HomeShelfModule` and `BookCatalogModule`. Provider adapters own catalog retrieval and cache access; shared application code retains shelf projection, pagination orchestration, filtering UI, and refresh state.

Library discovery is routed through `LibraryModule`. Provider adapters own server-specific library retrieval, while selected-library persistence, profile switching, and shared navigation remain application concerns.

Authentication and server reachability are routed through provider `LoginModule` and `ServerDetectionModule` adapters. Shared coordinator code retains login-screen state, session recovery, cached fallback, profile selection, and navigation; provider adapters own credentials, authentication transport, and server-specific probing.

Background cache workers now respect provider selection: BookOrbit-only cover/offline-cache workers exit without attempting BookOrbit operations while Komga is active, and automatic refresh obtains its server URL from the active server profile rather than constructing a BookOrbit repository.

The active-reader session and EPUB reader-position persistence lifecycle is now shared in `core/ReaderLifecycleModule.kt` and implemented by `ReaderLifecycleModuleImpl`. Provider reader modules receive the persisted session for restoration rather than reading `ActiveReaderStore` themselves. They remain responsible for provider-dependent reader preparation and progress hydration.

Reader capabilities that do not depend on a server, such as EPUB text-to-speech, may use independent feature-module contracts alongside `ProviderModules`. They must still preserve the same lifecycle, persistence, and unsupported-capability rules.

## Runtime composition

1. The configured server profile stores a canonical URL and provider ID separately from credentials.
2. `ProviderRepositoryResolver` selects the provider adapter from the explicit provider ID.
3. The adapter composes its feature modules.
4. The coordinator invokes the selected adapter and exposes only supported behavior to the UI.
5. Provider/profile state remains scoped to the active server profile. Downloaded media is device-local, while credentials and sessions remain provider-specific.

The provider ID is stored as profile metadata rather than appended to the URL. The URL therefore remains valid for server requests.

## Migration rule

`BookOrbitDataSource` may remain at the coordinator boundary while extraction continues. It must not be expanded with new provider-specific behavior. New capabilities should be added as a focused module contract, implemented by each supported provider, and covered by provider-specific tests.
