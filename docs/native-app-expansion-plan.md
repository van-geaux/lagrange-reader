# Native App Expansion and Plex/Audiobookshelf UI Plan

> Historical planning document: the implemented current shell supersedes this plan's drawer-based navigation and placeholder Options destination.

## Goal

Implement a native credential login, non-syncing book Preview mode, global Series and Authors catalogs, Plex-style card layouts, reliable complete-series pagination, an Audiobookshelf/Plex-inspired visual refresh, an Android splash screen, and an empty Options destination in the main drawer.

Preserve existing offline reading, downloads, progress synchronization, session recovery, and reader behavior.

## Product decisions

- Login uses native username/password fields only. OIDC/SSO and magic links are out of scope for this phase.
- Series and Authors catalogs include all accessible libraries.
- Preview opens the normal reader but never restores, saves, queues, or synchronizes progress.
- The redesign is dark-first and inspired by Audiobookshelf/Plex hierarchy and density while still supporting a polished system-selected light theme.
- The splash uses the existing application mark rather than a new illustration.
- Options is a placeholder destination in the main burger drawer, not a reader menu.

## 1. Native login and authentication

- Replace the login WebView with a Compose screen containing username and password fields, password visibility control, Sign in button, loading state, inline error surface, server hostname, and Change server action.
- Add `login(username, password)` to `BookOrbitDataSource` and implement `POST /api/v1/auth/login` with a JSON body. Continue using the existing cookie jar so response cookies remain available to every API and media request.
- After login succeeds, verify `/api/v1/auth/me` before resuming the pending browser, library, reader, or download destination.
- Add `AppCoordinator.submitLogin`; prevent duplicate submissions and distinguish invalid credentials, rate limiting, network, TLS, and server failures.
- Keep credentials in local Compose state only. Never store or log the password. Enable Android autofill and IME submission.
- Remove login polling and WebView navigation authentication checks. Retain WebView only where readers require it.

## 2. Reader Preview mode

- Add Preview beside Read/Continue and Download on Book Details.
- Introduce `ReaderLaunchMode.NORMAL` and `ReaderLaunchMode.PREVIEW`, carried by reader screen state and `ReaderState` with `NORMAL` as the compatibility default.
- Preview prepares and opens the same primary file and format-specific reader as Read.
- Preview always starts at the beginning and must not save or replace `ActiveReaderStore`, enqueue or submit progress, update last-synced progress, change read state, or alter normal resume state.
- Discard all Preview position changes on exit. Label visible reader controls and accessibility semantics as Preview.
- Reuse normal content-preparation errors without mutating reading state.

## 3. Global Series and Authors destinations

- Extend the drawer to Home, Libraries and existing library children, Series, Authors, Options, and Sign in/Log out. Do not render Series or Authors children inside the drawer.
- Add paginated repository support for:
  - `GET /api/v1/series?page={page}&size=100&sort=name&order=asc`
  - `GET /api/v1/authors?page={page}&size=100&sort=name&order=asc`
  - the upstream author-books endpoint for an opened author
- Add page envelopes plus `SeriesSummary`, `AuthorSummary`, and author-book detail models. Resolve relative cover/photo URLs against the configured server and use authenticated image loading.
- Series uses an adaptive poster-card grid showing a representative cover, series name, authors, read/total count, and completion. Add debounced server search and incremental loading. A card opens Series Details.
- Authors uses an adaptive card grid showing the author photo, initial fallback, name, and book count. Add debounced server search and incremental loading. A card opens Author Details, whose books use the shared poster grid.
- Cache successful catalogs and opened author/series book lists per server. Use them as offline fallback without mixing server identities.
- Preserve destination, detail back stack, and list/grid scroll position.

## 4. Fix incomplete series results

- Parse `items`, `total`, `page`, `size`, and `seriesInfo` independently from series-book responses.
- Paginate against response `total`, not `seriesInfo.bookCount`. Continue until distinct accumulated IDs reach `total`; stop safely on an empty page or a page adding no new IDs.
- Deduplicate by book ID after collection, then sort by series index and title.
- Keep `seriesInfo.bookCount` for metadata. When it differs from `total`, prefer `total` for list completeness and emit debug-only mismatch diagnostics.
- Validate Accel World specifically: Series Details must show 27 distinct books in the correct order.
- Cover mismatched totals, partial pages, cross-page duplicates, empty terminal pages, and fractional or missing series indexes with fixtures.

## 6. Audiobookshelf/Plex-inspired design system

- Replace the editorial-observatory styling with a dark-first media-library system while respecting device light/dark mode.
- Use a near-black/navy dark background, charcoal elevated surfaces, restrained amber/gold accent, bright text, muted metadata, and high-contrast progress states. Provide a neutral light companion palette.
- Use sans-serif typography, stronger poster imagery, compact metadata, modest corner radii, subtle elevation, and dense but touch-safe spacing.
- Standardize poster/series/author cards, shelf headers, drawer selection, actions, loading placeholders, empty states, offline banners, and error surfaces.
- Maintain 48 dp touch targets, TalkBack semantics, large-font behavior, and narrow-screen support.
- Do not copy proprietary assets or exact branding.

## 7. Splash screen and Options

- Add AndroidX Core SplashScreen support and Android 12+ plus legacy launch themes.
- Center the existing launcher foreground mark on the new dark brand background.
- Call `installSplashScreen()` before activity initialization and dismiss when the first Compose frame is ready. Do not add an artificial delay.
- Add Options as a drawer destination containing only its title and: `Reader and app options will appear here later.` Do not add settings or persistence yet.

## Interfaces and compatibility

- Add repository operations for login, paginated series, paginated authors, author books, and catalog image loading.
- Expand browser destinations to cover Home, Library, Series, Authors, Author Details, Series Details, Book Details, and Options.
- Keep passwords out of models, logs, saved state, DataStore, and restoration.
- Version new catalog snapshots so existing cached browser data remains readable and absent new fields default safely.

## Verification

### Automated

- Native login request/cookies, `/me` verification, invalid credentials, throttling, and network errors.
- Coordinator recovery of every pending destination after login.
- Preview never writes active-reader, progress-queue, or last-synced stores.
- Global catalog pagination, search parameters, URL resolution, cache fallback, and server isolation.
- Accel World-style 27-book pagination and mismatch cases.
- Compose coverage for login, drawer destinations, adaptive grids, synopsis expansion, Options, Preview action, and Preview reader labeling.
- EPUB coverage for full-viewport text layout, swipe navigation, tap/swipe interaction suppression, and chapter-boundary transitions.
- Run `testDebugUnitTest`, `assembleDebug`, `assembleDebugAndroidTest`, `assembleRelease`, and `git diff --check`.

### Manual device acceptance

- Native login, restart session restoration, logout, and forced session expiry.
- Preview each available format and confirm normal resume/progress is unchanged.
- Open the representative EPUB on a real device and confirm text fills the viewport rather than rendering one line per page.
- Swipe left and right through EPUB pages, including chapter boundaries, and confirm a swipe advances exactly one page without a duplicate tap navigation.
- Confirm Accel World contains all 27 books.
- Navigate global Series and Authors and return without losing position.
- Check splash behavior on Android 11 and Android 12+.
- Review dark/light themes, narrow width, large font, TalkBack, offline catalogs, downloads, and progress indicators.

## Remaining implementation order

1. Finish the remaining shared design-system refinements and accessibility/large-text review.
2. Run the full automated and device regression matrix, then update architecture/API/testing docs.
