# Roadmap

This roadmap summarizes the next practical engineering sequence for the project.

## Completed foundation

- Android project scaffold exists
- Live BookOrbit login contract validated
- Live library and book payloads validated
- Local build is working on this machine
- Download and sync scaffolding are implemented
- EPUB fallback replaced with a basic real EPUB reader path
- Queue compaction and auth-vs-transient sync failure handling implemented
- Reader/player progress events are throttled before queue persistence
- Reader startup loading, reader error messages, unsupported-format messaging, and PDF cache-open support are implemented
- Server-reported page/time progress is mapped into reader resume state
- Library and book browsing now has loading, empty, error, and refresh UI states
- Active download progress, retry, and cancel controls are implemented
- Download filenames preserve more specific format extensions from BookOrbit metadata
- User-facing error messages are normalized from typed repository/network failures
- Last successful libraries and per-library book lists are cached locally for browser fallback
- Downloaded items can now remove their local copy and persisted download record
- Missing local download files are now pruned from persisted download state and cached browser snapshots
- Zero-byte local download and reader-cache files are now treated as invalid and redownloaded
- Debug builds expose the pending sync queue count in the browser view
- Progress sync worker now applies exponential backoff for transient server failures
- Last-synced progress markers now suppress duplicate or stale queue submissions
- Offline startup and login-refresh paths now fall back to cached browser state when available
- Cached offline browser views now disable live-only actions for books without a local copy

## Next execution order

Version 0.2.7 includes the July 20 reader/detail follow-ups. Target-device feedback confirms the reader remains open during sync/refresh/download activity, Exit works, and the tutorial timing/dismissal and revised label readability behave correctly. Series details persist Library/Format/none grouping globally; target-device feedback confirms cross-library separation, persistence, and the inline section label/divider layout. The profile dropdown now places Achievements, Options, and About above a divider before session actions, and its ordering is device-validated. Readium PDF migration is implemented, and its generated three-page connected opening test passes. Square covers now bottom-align with portrait covers and card labels. The full gate passes 265 JVM tests across 46 suites plus lint and both APK assemblies; target-device PDF checks, mixed-library cover alignment, the Options cog icon, divider semantics, About routing, accessibility, and large text remain.

### 1. UI/UX direction and design system

- Agree on the product's visual character, density, and accessibility goals
- Define Compose theme tokens for color, typography, spacing, shape, elevation, and motion
- Establish reusable app-shell, loading, error, offline, and session-state components
- Use [ui-ux.md](./ui-ux.md) as the checkpoint source of truth

### 2. Primary screen refinement

- Refine server setup and the login WebView container
- Refine library selection, book-card hierarchy, and download/offline actions
- Refine the EPUB reader using the currently available sample content
- Validate the fullscreen paginated EPUB candidate and add exact in-chapter page restore after layout behavior is stable
- Preserve existing session, offline, progress-sync, and reader-resume behavior

### 3. Automated coverage and recovery hardening

- Completed in androidTest: real-repository/MockWebServer integration coverage for login bootstrap and library/book loading
- Completed in androidTest: offline queue retention plus explicit progress/status replay and acknowledgement
- Execute the new repository integration tests and remaining Compose instrumentation tests on a usable connected emulator or device; current adb enumeration did not yield a runnable target
- Validate server-forced session expiry and return-to-intended-action recovery on a real deployment

### 4. Media-specific validation

- Completed in code: recognize bare CBZ/CBR/CB7 BookOrbit format tokens, include CB7 in filters/download naming, and route all three through the comic reader instead of unsupported-format handling
- Completed in code: open local/readable CBZ with Readium's image navigator; for connected CBR/CB7, fetch authenticated BookOrbit pages and build/reuse a cached CBZ before opening Readium
- Treat RAR4/RAR5/7z signatures as valid downloaded comics so they are not deleted as corrupt; downloaded/local CBR/CB7 still requires server page extraction in this build
- Completed and target-device validated: give comics the established novel-reader interaction model with a black fullscreen fitted-image surface, always-visible page footer, outer tap zones and horizontal swipes for page changes, center-tap options, exposed-content/Back dismissal, and Back-to-exit only after options close
- Device-validate the Readium CBZ path and connected CBR/CB7 cache preparation; offline downloaded CBR/CB7 remains unsupported without the server, and client-side RAR/7z extraction is optional future work
- Use the available local M4B/chapter-metadata fixture for audiobook work and obtain a representative PDF

### 5. Remote media streaming

- Keep whole-file authenticated preparation for EPUB and PDF.
- Completed in code: replace complete M4B/MP4/audiobook preparation with authenticated Readium HTTP byte-range playback while preserving service-owned background playback, seeking, chapters, progress, Preview isolation, local playback, and explicit offline downloads. Authentication is same-origin only and retries once after session renewal on 401/403.
- Completed in code through the safe page-endpoint path: connected CBZ/CBR/CB7 become authenticated Readium image publications after loading only page-count metadata and the first page for media detection. Readium fetches the selected BookOrbit-rendered page lazily; it no longer downloads every page or builds a complete cached CBZ. Local/downloaded CBZ remains file-backed. Direct remote-CBZ archive ranges remain an optional future optimization.
- Completed hardening in code: remote audio requires a genuine 206 response to an authenticated one-byte probe and refuses a server that ignores Range rather than accepting a whole-file transfer. One 401/403 renews credentials and retries once; unavailable probes fail cleanly with explicit-download guidance. Connected media creates no persistent full-media cache, and obsolete full-audio/CBZ/readium-comics caches are pruned without touching EPUB/PDF caches or Local books downloads. Optional direct-CBZ archive ranges and representative large-file device/server validation remain.
- Product direction selected: retain Readium and the existing service/progress model while supplying authenticated remote resources with bounded read-through caches.

### 6. Reader and library follow-ups

- Fix portrait orientation lock after returning from a landscape-oriented app; Lagrange must enter and remain in portrait instead of inheriting the previous app's landscape orientation.
- Completed in code: keep the Library Browse statistics and filter/collapse or multi-selection action row fixed while only the book catalog scrolls or handles a downward swipe/pull-to-refresh gesture; collapse anchors and jump targets now use direct grid indexes.
- Completed in code: add a Local books-only multi-select `Delete local` action governed by the existing confirmation preference. Cancel retains selection; confirmation runs one batch, reconciles successful files in one browser/local-list revision, preserves failed files, and reports partial failures. Focused JVM and compiled Compose coverage pass; target-device validation remains.
- Completed in code: Authors loads every distinct server page in deterministic name order and uses the shared #/A-Z jump rail with exact represented-label targets, disabled unavailable labels, Reduce motion, header-offset correction, and responsive trailing gutter. Load more is removed; focused JVM and compiled Compose coverage pass, with target-device validation pending.
- Completed in code with the user-selected filled semantic direction: Libraries uses a bookshelf, Series a bookmarked collection, Authors a group, and Local books an offline-download icon. Destination-specific accessibility descriptions and compiled Compose assertions cover all four; target-device visual validation remains.
- Completed in code: preserve BookOrbit's exact eight read-state wire values in the catalog model and every cache. Home and Library Recommended now map only reading/rereading to Currently reading, want_to_read to its own shelf, on_hold to On deck, and read/skimmed to Recently read. Unread, abandoned, null, unknown, and other values are excluded from those four shelves. Deterministic parser/shelf/cache/coordinator coverage passes; representative target-device/server and offline-reopen validation remains.
- Add more reading-direction options, including right-to-left and continuous scrolling with configurable space between pages for PDF and CBZ/CBR.
- Allow a book to be moved to read status from Preview.
- Add support for more book formats.

### 7. Release readiness

- Complete accessibility, large-text, narrow-screen, rotation, and theme checks
- Run unit, lint, debug, instrumentation-compile, and release build gates
- Completed: audit the tracked tree and history for sensitive filenames, high-confidence key signatures, hardcoded credential assignments, and unexplained production/internal URLs
- Create the first tagged release

## User feedback workplan â€” 2026-07-12

The latest debug APK passed login, session relaunch, browsing, EPUB reading, download/offline reopen, and progress-sync testing on the target device. The remaining device feedback is ordered as follows:

1. Completed: put currently reading books in the first Home shelf, with completed/recently read titles remaining available as a separate shelf.
2. Completed: replace the hamburger/drawer browser shell with a Plex-inspired native bottom-navigation layout. Libraries open at the top-level library view, with a library-change control at the top of that screen; More expands to Series, Authors, and Options.
3. Completed: add breathing room below the system status bar on Home, replace the persistent large search field with a top search icon, and open search in a dedicated layer. The standard Android status bar remains visible.
4. Completed: add Compact, Comfortable, and Wide reader padding options, using Comfortable as the more generous default and repaginating when the setting changes.
5. Implemented: keep the branded splash state through bootstrap and replace the post-splash spinner with the branded loading mark; validate the result on a physical device.

Each item must preserve session recovery, offline behavior, progress sync, Preview isolation, and reader resume.

## Latest device feedback workplan â€” 2026-07-13

1. Implemented: restore the exact EPUB page within the current chapter on close/reopen and full app restart, while preserving the existing server progress percentage and chapter-boundary behavior; physical-device validation remains required.
2. Implemented: change the selected-library book browser from list rows to the adaptive poster-card grid used by Series and Authors, preserving metadata, offline-disabled cards, and detail actions; physical-device validation remains required.
3. Implemented: rename the visible app brand to `Lagrange` and add the subtitle `a BookOrbit reader` to the logo/launch presentation. BookOrbit remains the connected server/product name; physical-device branding validation remains required.
4. Implemented: add an About destination with placeholder app/version/server information until the About content is reviewed. Its original More-menu placement was later superseded by the profile-dropdown revision.

## Latest device feedback workplan â€” 2026-07-13 (continued)

1. Implemented: show the `a BookOrbit reader` subtitle only on the splash/loading presentation; the opened app top bar and About screen now show the Lagrange name without the subtitle. Physical-device presentation validation remains required.
2. Implemented: add a Libraries content control that collapses books belonging to the same series into one representative series card, with a Show all action to restore the full book grid; physical-device validation remains required.
3. Implemented: add Local books to More immediately before Options, loading valid downloaded titles from local storage and presenting them with the same poster-card grid.

## Latest device feedback workplan â€” 2026-07-13 (library refinement)

1. Implemented: reduce the current book poster-card minimum width to 75% of its current value, which is half of the original candidate size; physical-device density validation remains required.
2. Implemented: add paginated library loading with automatic near-end page fetching in the Browse tab using the library response total/page metadata.
3. Implemented: show the Lagrange logo only on Home; Library now opens the first/selected library directly, and its library name replaces the logo as a tappable library selector.
4. Implemented: split the selected Library view into Recommended, reusing the Home-style shelves for that library, and Browse, containing the complete paginated book grid.

## Latest device feedback workplan â€” 2026-07-13 (final browser polish)

1. Implemented: make the Library, Series, and Authors poster-card grids visibly smaller on narrow phones by lowering their adaptive minimum width; physical-device density validation remains required.
2. Implemented: add a downward-triangle affordance beside the selected Library name so its tap-to-select behavior is discoverable.
3. Implemented: remove the Library refresh arrow and add swipe-down-to-refresh to the Library content tabs.
4. Implemented: persist fetched book cover thumbnails locally and read that cache before attempting the server, including for Local books.

## Latest device feedback workplan â€” 2026-07-13 (follow-up polish)

1. Implemented: rename the collapsed-series action from Show all to Expand series while preserving the full-grid behavior.
2. Implemented: improve cover-thumbnail decoding and retry behavior so slow or failed cover loads do not permanently remain as first-letter placeholders.
3. Implemented: add extra bottom spacing to the More sheet so About remains comfortably above the Android navigation controls.
4. Implemented: show the full library book total in the paginated Browse header, with optional server-provided series totals instead of only the currently loaded page count.
5. Implemented: make the top-left Libraries title tappable while the picker is open so it returns to the selected library page.
6. Implemented: cache full BookDetailInfo metadata for downloaded titles and prefer that cache when opening Local books.

## Latest device feedback workplan â€” 2026-07-13 (lazy library navigation)

1. Implemented: replace Browse's Load more button with automatic page loading as the user approaches the end of the current grid.
2. Implemented: add a right-side title-initial jump rail for fast movement through loaded library content.
3. Superseded by the complete Room catalog workplan below; Browse no longer lazy-loads as the grid scrolls.

## Latest device feedback workplan Ã¢â‚¬â€ 2026-07-13 (reader spacing)

1. Implemented: keep the existing overall Compact/Comfortable/Wide reader padding presets and add independent Top and Bottom controls using the same levels.
2. Implemented: keep Comfortable as the default for all three controls and repaginate the current EPUB chapter whenever any padding value changes.

## Latest device feedback workplan Ã¢â‚¬â€ 2026-07-13 (percentage padding and recovery)

1. Implemented: replace named reader padding presets with four independent percentage sliders for Top, Bottom, Left, and Right. Each slider ranges from 0â€“100%, where 100% maps to one quarter of the relevant viewport dimension. Fresh books now default Top to 30% and Bottom/Left/Right to 15%; saved per-book values remain unchanged.
2. Implemented: make edge changes independent and apply the new values when the slider is released, then repaginate the current EPUB chapter.
3. Implemented, then refined: jump rails now show only #/letter buckets represented in the complete result; missing letters are no longer exposed as forward-fallback labels.
4. Implemented: alphabetize collapsed library representatives by their series name and restore the nearest book/series scroll anchor when switching between collapsed and expanded views.
5. Implemented: route authentication failures from background search, cover, catalog, detail, and paginated library loads through the login recovery screen instead of silently returning empty content. Physical-device expiry testing remains required.

## Latest device feedback workplan â€” 2026-07-13 (reader, Home, and session follow-up)

1. Implemented: apply Top, Bottom, Left, and Right reader padding changes continuously with a short debounce and an explicit page-strip height so slider changes visibly repaginate the chapter.
2. Implemented, then superseded by later feedback: change the reader overlay's top-right action to Close and initially keep outside taps from dismissing it.
3. Implemented: restore the Home Continue reading shelf for active progress even when the server marks the item read, and tolerate alternate progress container/field names.
4. Implemented: remove the duplicate Home/library heading from Library Recommended.
5. Implemented: suppress a derived Browse series count until all books are loaded when the server does not provide a complete series total.
6. Implemented: persist the login access token and attach it to authenticated API, cover, download, and reader-cache requests; explicit session clearing removes the token.
7. Implemented: move Options from More into the profile menu above Log out and add extra vertical spacing to the More sheet.

### Latest device feedback workplan - 2026-07-13 (session, catalog, and density follow-up)

1. Implemented: make session persistence the highest-priority fix by retrying 401/403 API, cover, download, and reader-cache requests after a refresh-cookie renewal attempt. The endpoint contract and long-lived behavior still require physical-device validation against the target server.
2. Implemented and corrected after target feedback: parse Series `coverBookIds` and load the first representative book's thumbnail, retry failed catalog image loads, and cache successful catalog image bytes for scroll-back reuse. The earlier assumed `/series/{id}/cover` route does not exist in BookOrbit.
3. Implemented: merge freshly observed reader progress into the browser immediately and include a recently read book even when it was outside the first server page, so Continue reading does not wait for a full reload.
4. Implemented: remove redundant in-content Home/library headings from Home, Library Browse, Series, Authors, and Local books while preserving destination identity in the app bar/detail screens.
5. Implemented: reduce shared Compose typography tokens by approximately 10% and reorganize book card metadata into book title, optional series, and series-index rows.
6. Completed and device-validated: apply all four reader padding values during slider movement. The final implementation resizes the WebView for Top/Bottom and updates the known-good page strip for Left/Right.
7. Physical-device validation remains next for session expiry, series thumbnails, Continue reading latency, card density, and card row wrapping.

### Latest device feedback workplan - 2026-07-13 (progress and reader persistence)

1. Implemented: capture reader progress synchronously and flush it before closing the reader; browser bootstrap now syncs queued progress before its first library load so the server and first Home render agree.
2. Implemented and device-validated: persist independent EPUB Top, Bottom, Left, and Right percentage padding per book/file, with visible changes on the reading surface.
3. Implemented: add a server-aligned filter button to Library Browse and Series, with title/author/series, read-status, format, and sort controls applied locally to Local books as well.

### Latest device feedback workplan - 2026-07-13 (session refresh follow-up)

1. Implemented: align refresh requests with the BookOrbit client by sending the current bearer/cookie credentials and reusing a token refreshed by another concurrent request instead of renewing repeatedly.
2. Physical-device validation remains required: allow the debug APK to run past the target server's short access-token lifetime and confirm browsing, covers, downloads, and reader progress stay authenticated without repeated login prompts.

### Latest device feedback workplan - 2026-07-13 (reader rendering regression)

1. Completed and device-validated: restore EPUB chapter content by returning to the previously validated translated page-strip geometry while retaining independent padding persistence.
2. Completed and device-validated: verify that all four independent reader padding controls visibly update the reading surface after normal EPUB rendering is restored.

### Latest device feedback workplan - 2026-07-13 (padding regression)

1. Previous device blocker: Top and Bottom reader padding values did not visibly affect EPUB text position or repagination even when the sliders moved away from the 15% default.
2. Superseded after device feedback: clipping the fixed WebView body still produced a blank real EPUB. The current candidate restores the last device-known-good single visible-overflow page strip and moves Top/Bottom padding entirely outside the HTML renderer by resizing the Android `WebView`; Left/Right still update the strip in place. JVM coverage passes, and the Android WebView regression now models the external resize plus translated-page visibility.
3. Device result on July 13, 2026: the restored EPUB content renders and the independent padding changes are visible. Reader padding is complete; broader reader regression checks remain covered by the manual matrix.

### Latest device feedback workplan - 2026-07-13 (bidirectional progress reconciliation)

1. Implemented: parse BookOrbit's current scalar `readingProgress` card field and nested `readStatus` so server-side progress can populate Lagrange Home and reader state.
2. Implemented: use one canonical 0-100 percentage scale across EPUB, PDF, comic, audiobook, persistence, conflict checks, and BookOrbit request payloads; low progress values are no longer multiplied twice.
3. Implemented: suppress only equivalent submissions, allowing newer reread/backward events to repair stale or accidentally inflated server markers.
4. Implemented: after successful queue replay and a fresh page load, clear temporary local progress overlays so refreshed BookOrbit progress can flow back into Lagrange.
5. Implemented after device feedback: serialize queue/marker access across foreground and WorkManager repository instances, acknowledge only posted snapshot IDs, and schedule a trailing debounced worker so an in-flight replay cannot erase the reader's newer page update.
6. Implemented: retain the known book percentage when a callback omits it and reject any still-unknown percentage instead of submitting an accidental zero-percent update that can remove BookOrbit's reading status.
7. Implemented after target-device feedback: explicitly pair every accepted progress write with `reading`, or `read` at 99.5% and above, and retain the queue event until both BookOrbit requests succeed. This prevents the server's status-backed Currently Reading widget from remaining empty when automatic status promotion does not occur.
8. Physical-device validation remains required in both directions against the target BookOrbit server.

### Latest device feedback workplan - 2026-07-13 (cache-first catalog and exact jumps)

1. Implemented: replace the active first-page JSON book snapshot with a server-scoped Room 2.6.1 metadata catalog for every selected library; retain the JSON data only as a legacy fallback.
2. Implemented: render a complete cached catalog before cold-start session/library network checks finish, then keep it usable while a background refresh reconciles the server.
3. Implemented: retrieve every BookOrbit metadata page during reconciliation because the server has no reliable catalog delta/revision contract. Retry once from page zero if page totals shift or the deduplicated count disagrees, then compare the stable result with Room and atomically write only new, removed, reordered, or changed rows; an interrupted refresh leaves the previous complete generation intact.
4. Implemented: remove Browse's near-end lazy loading. Browse title/author/series/read-status/format filters and supported sorts now operate against the complete local catalog.
5. Implemented: request `/api/v1/libraries/{id}/books/jump-buckets`, persist valid default-sort bucket indexes, and scroll directly to the server's absolute index. Only represented labels are shown; complete-cache local indexes cover filters, author/title sorts, collapsed series, Series, and older servers without inventing forward-fallback labels.
6. Implemented: hide the rail while an initial full catalog is incomplete and for non-letter sort modes, preventing a rail tap from degenerating into end-of-list lazy loading.
7. Added JVM coverage for pagination termination, duplicate handling, jump parsing/index mapping, and cache-first coordinator failure recovery. Added a compiled Android Room transaction test for changed rows and deletions.
8. Physical-device validation remains required for first sync, instant reopen, pull refresh, additions/deletions/progress changes, offline browsing, and #/Aâ€“Z jumps across a large target-server library.

### Latest device feedback workplan - 2026-07-13 (large-library thumbnails and details)

1. Implemented: replace process-global cover jobs with Compose-owned cancellable OkHttp calls so rapidly scrolled-off cards stop consuming server and client request capacity.
2. Implemented: version memory/disk thumbnail keys from catalog metadata and use per-file cache locks, allowing visible foreground reads to proceed independently of background writes.
3. Implemented: after complete catalog reconciliation, use WorkManager on unmetered connectivity to scan the selected library and download up to 50 missing/changed thumbnails per durable chained batch.
4. Implemented: persist rich `BookDetailInfo` for every opened title, not only downloaded books, and invalidate it when the title's catalog update version changes.
5. Deliberate boundary: keep the complete catalog and thumbnails locally, but do not prefetch one rich-detail endpoint per title; a 5k-book library would otherwise generate roughly 5k extra API calls. The summary detail screen renders immediately and the rich supplement is fetched once per changed/opened title.
6. Verification passes: 127 JVM tests across 21 suites, Android lint, debug APK assembly, and Android instrumentation-test compilation. Physical-device validation against the 5k-book library remains required.

### New user feedback workplan - 2026-07-15

- [x] Add visible overflow and long-press actions to Currently reading cards. Removal uses BookOrbit's normal-user file/audio progress and status APIs to clear primary/current progress, mark the title unread, and clear matching local queued, synced, resume, and cached progress so stale work cannot restore it. Reading-session history and additional-file progress remain an admin-reset limitation.
- [x] Implement the former series-progression On Deck heuristic; this was later superseded by the exact BookOrbit On Hold state mapping.
- [x] Add pull-down refresh to Home using the same refresh indicator and cache-preserving catalog reconciliation as Libraries.
- [x] Fix missing thumbnails on Series cards by mapping BookOrbit's `coverBookIds` to representative book thumbnail URLs.
- [x] Fetch every filtered Series page into a deduplicated in-memory catalog, remove Load more, and add #/Aâ€“Z or Zâ€“A direct navigation for Name sorting.
- [x] Show the number of books in collapsed Libraries series cards, matching the Series screen.
- [x] Persist the reader background choice (light, dark, or sepia) across close/reopen and app sessions.

### Additional reader feedback workplan - 2026-07-15

- [x] Prevent the reading screen from closing unexpectedly after an idle period.
- [x] Simplify the reader options overlay to one Close action; tapping the visible book content should also close the options overlay.
- [x] Show battery and signal indicators in the reader's top-right area using Android's native status bar while keeping the bottom navigation bar immersive.
- [x] Show book completion percentage, chapter page progress, and a measured whole-book page location in an always-visible EPUB footer.

### Device validation follow-up and new work order â€” 2026-07-15

- [x] Validate explicit HTTP server setup and authenticated browsing/reading.
- [x] Validate Mark as read/unread from supported book-card menus.
- [x] Validate Android/BookOrbit progress synchronization in both directions.
- [x] Validate large-library browsing, fast #/Aâ€“Z jumps, and airplane-mode behavior; undownloaded books correctly require a connection for full details.
- [x] Validate session-expiry recovery, Series navigation, and Series thumbnails.
- [x] Add the book context menu to global search results for both long-press and three-dot actions.
- [x] Keep global search results as list rows rather than converting them to poster cards.
- [x] Fix stale Currently Reading state after refresh: explicit `unread` records with no positive progress now discard stale page/position and status timestamps, so a server-confirmed removal cannot be reconstructed as reading activity.
- [x] Reduce Home sync/loading time for large libraries by requesting BookOrbit's 100-book page limit and loading large known page ranges in ordered batches with at most four concurrent requests. Existing cached content remains usable, and unstable totals/duplicates still trigger the full consistency retry.
- [x] Redesign reader options as a bottom sheet with separate actions for Continue reading (close options and remain in the book) and Close book (exit the reader).
- [x] Improve reader-options typography with reliable text/background contrast in Light, Sepia, and Dark modes.
- [x] Allow explicitly entered HTTP BookOrbit server URLs and Android cleartext traffic; bare remote hostnames still default to HTTPS, while bare local development hosts retain HTTP shorthand.
- [x] Add Mark as read and Mark as unread to the book-card three-dot and long-press menus. Read status is written directly while preserving position; unread uses the normal-user reset flow so completed progress cannot immediately restore Read.
- [x] Redesign book-detail actions as compact wrapping tiles with selective icons and persistent labels, using a `FlowRow` so narrow screens do not hide actions horizontally.
- [x] Make the tapped series title in book details navigate to that Series detail page.
- [x] Wrap Genre and Tag chips separately and group lower metadata into compact Publication, Identifiers, and Library/file cards.
- [x] Open the book-detail cover in a full-screen image viewer, dismissible by tapping the cover again or pressing Android Back.
- [x] Redesign the reader options window as a rounded bottom sheet with clearer hierarchy, spacing, and grouped controls.

The book-detail follow-up includes a Compose instrumentation regression for the action area, Genre/Tag content, cover-viewer tap dismissal, and Series navigation. `testDebugUnitTest`, `lintDebug`, `assembleDebug`, and `assembleDebugAndroidTest` pass; physical-device layout, horizontal swiping, and Android Back dismissal remain to be validated.

### Latest device feedback work order â€” 2026-07-17

- [x] Dismiss the full-screen cover viewer when any part of the screen is tapped; retain Android Back dismissal.
- [x] Replace oversized book-detail action cards with compact controls: Read/Preview labels beside clear icons and an icon-only Download action.
- [x] Clamp long book-detail titles to five rows with an expand/collapse affordance.
- [x] Support selecting multiple books in library grids and applying bulk Mark as read/unread actions.
- [x] Make genre selections navigate to filtered Books or Series results through the catalog filter model; book queries now use BookOrbit's current singular `genre` field with `includesAny` and an array value, and the adjacent author relation filter uses `author`/`includesAny`/array. Keep tags informational.
- [x] Keep a book's series name and series index visible as distinct metadata rows.
- [x] Validate genre-filter query support and result scope against the target BookOrbit server.
- [ ] Add and validate direct OIDC/SSO authentication after the provider and redirect contract are confirmed; native username/password remains current.
- [x] Complete the revised detail density, title expansion, multi-selection, genre navigation, and series-index implementation in code/tests.
- [ ] Run remaining device validation for revised detail density and interrupted-download recovery; multi-selection and genre filtering are validated.
- [ ] Use the available local M4B/chapter-metadata fixture for audiobook implementation/validation; device-validate the implemented online CBZ/CBR/CB7 and offline ZIP/CBZ paths.
- [x] Revise detail actions so Read and Preview show text beside clear icons, while Download uses an unmistakable download symbol.
- [x] Expose active per-file download progress, percentage/linear or indeterminate state, cancel guidance, and retry failure status from book details.

### Latest detail-action feedback â€” 2026-07-17

The full-screen cover viewer, long-title expansion, series-index presentation, revised action labels/icons, and download status presentation are implemented. Multi-select header overlap and stale-selection pruning are fixed. Genre chips navigate to fully paginated server-filtered Books or Series results and are target-server validated; tags remain informational. Interrupted and large downloads are target-device validated; direct OIDC/SSO remains deferred.

### Target-device follow-up work order â€” 2026-07-17

- [x] Confirm Read/Preview labels, live download progress, long-title expansion, series-index layout, and multi-selection behavior.
- [x] Add labels beside Download and Delete local, retaining their clear icons and compact layout.
- [x] Reconcile the selected book detail immediately after a download completes so Download becomes Delete local without leaving and reopening the screen.
- [x] Remove the misleading Tag tap affordance because BookOrbit has no verified tag filter; keep tags informational.
- [x] Keep native username/password as the current authentication flow; defer direct OIDC/SSO until the provider and redirect contract are confirmed.
- [x] Make red Home/Library Recommended message cards dismissible by horizontal swipe and an explicit close button, clearing the BrowserState message immediately.
- [x] Replace the normalized `Book x/1000` EPUB footer with sequential hidden-WebView measurement, layout-derived current/total pages, weighted completion, and a `Book pages calculating` fallback while counts settle.
- [x] Restrict Recently read to books that have actually finished, excluding every title that remains in progress; order by last-read/updated/title and cap at 12.

### Latest target-device validation and work order - 2026-07-17

Validated on the target device:

- [x] Lock current orientation.
- [x] Default opening screen.
- [x] Downloads-over-cellular behavior.
- [x] Reduce motion.
- [x] Storage accounting/cache clearing and delete-local confirmation.
- [x] Layout-derived whole-book progress correctness.

The former haptic preference and explicit app haptics were removed by user direction, including preference storage, Options UI, custom provider, manual requests, and obsolete tests.

Implement next, in order:

1. [x] Reconcile the still-open book-detail state immediately after Delete local succeeds so the action returns to Download, Local books refreshes, and catalog/snapshot offline state updates without backing out and reopening. Target-device validation passed.
2. [x] Restore cached thumbnails and related metadata in Local books, including while offline, by filling incomplete catalog/snapshot summaries from the latest cached rich detail. Target-device validation passed.
3. [x] Fix navigation to the main Options screen from book details by dismissing retained detail state before selecting Options. Target-device validation passed.
4. [x] Restore Currently reading for genuinely in-progress books by deriving top-level Home from the server-wide collection rather than only the selected library. Target-device/server validation passed.
5. [x] Aggregate Home shelves across every library on the connected server. Restore cached slices from every Room catalog and refresh the selected library first so its screen becomes usable/current before nonselected work. Refresh remaining libraries in deterministic ordered batches with at most three concurrent library reconciliations, merging successful slices after each batch; normal failures retain cached slices and contribute names to the partial-cache message, while cancellation/auth failures propagate immediately. Keep Libraries Recommended/Browse selected-library scoped and clear `isRefreshing` after the final batch. Aggregation is target-device/server validated; latency and partial-failure messaging remain open.
6. [x] Fix the comic unsupported-format regression by recognizing bare CBZ/CBR/CB7 formats. Use authenticated server page extraction for all three online and local ZIP extraction for offline CBZ/mislabeled ZIP. General comic reading is target-device validated; validation of online CBZ/CBR/CB7 and offline downloaded comic formats remains pending, and offline CBR/CB7 extraction remains optional.
7. [x] Replace the comic reader's visible buttons with a fullscreen fitted-image interaction model: left/right tap zones and horizontal swipes turn pages, center tap opens a dark rounded options sheet with title/page, Continue reading, Close book, and page slider, exposed-content tap or Android Back closes options first, and Android Back exits only when options are closed. Target-device validation passed.
8. [x] Replace the former three-dot overflow with a directly labeled live Mark as read or Mark as unread tile. It reuses selected-library/server-wide Home status logic, remains disabled for offline snapshots, and now participates in the wrapping action `FlowRow` with Read, Preview, and Download/Update/Cancel/Delete local.
9. [x] Make Close book synchronously restore cached Browser state before coroutine/network/storage work, immediately merge captured progress into selected-library `books` and server-wide `homeBooks`, and show refreshing/loading state. Background work flushes the captured progress, attempts pending sync, clears non-preview active-reader state, and refreshes the browser; Preview still neither persists nor clears. If no cached browser exists, leave the reader for Loading while the browser loads. Target-device validation passed.
10. [x] Fix foreground book-detail cover gaps such as `your name.` with ordered distinct candidates: normalize explicit `/cover` metadata to `/thumbnail` first, then try canonical `{server}/api/v1/books/{bookId}/thumbnail`. Missing metadata uses canonical directly; empty/failed explicit candidates fall through; cancellation still propagates; successful bytes retain the existing `updatedAt` memory/disk cache identity. The low-priority library-wide warmer remains unchanged, avoiding a request-per-missing-cover catalog scan. Physical-device/server validation remains pending.
11. [x] Add the approved current-chapter page slider beneath the unchanged Choose chapter button and chapter-chip selector in Reading position. It shows `Page X of Y`, uses the primary WebView's current chapter page count rather than whole-book measurement, disables for single-page chapters, jumps immediately while dragging, and cannot cross chapter boundaries. `BookOrbitReaderLayout.goToPage` clamps to `0..pageCount-1`, keeps page rendering, and publishes normal `pageChanged` progress. Target-device validation passed.
12. [x] Add version-aware downloaded-book updates. Persist `sourceUpdatedAtMillis`; show online-only Update local when catalog `updatedAt` is newer, and give legacy versionless downloads one baseline-establishing update. Reuse cellular policy/confirmation and progress/cancel/retry UI with update wording. Download into a sibling `.part`, validate non-empty and EPUB/PDF/comic structure while preserving nonempty MOBI/AZW3 compatibility, then atomically replace the old file. Cancellation, failure, or malformed content deletes only staging and preserves the working local copy. The target-device Update local flow is validated.
13. [x] Add the approved stable Previous/Next row directly below book identity/series metadata rather than in the action strip or at the bottom. Derive immediate candidates from selected-library books, server-wide Home books, and the current detail; online details load the existing complete paginated Series detail once and retain that result while navigating within the series, while offline snapshots make no server request. Filter by series ID with name fallback, deduplicate IDs, sort numeric series indexes ascending with null indexes last and deterministic title/ID ties, then expose adjacent books. Show the row only when more than one series book is known. Use two equal-width transparent borderless 46 dp controls matching detail actions, with arrow, adjacent `#index`, single-line title, and stable disabled Start/End positions. Selection replaces the current book in the existing detail screen. Basic navigation is device-validated; the new visuals and remaining responsive/long-title/loading/offline states still need validation.
14. [x] Replace the old app Dark choice with the approved single flat Theme list in this order: Follow system, Light, Charcoal, Warm black, OLED black. Follow system maps Android light to Light and Android dark to Charcoal; explicit choices ignore system state. Migrate stored legacy `dark` to Charcoal and persist the three new dark values directly. Charcoal uses neutral near-black `#0D0D0F`/`#151517`/`#222226`; Warm black uses `#100E0B`/`#191612`/`#28231D`; OLED black uses `#000000`/`#101010`/`#1C1C1E`, with palette-appropriate text/outlines, retained blue/amber accents, and explicit dark error containers. All themes work on the target device; detailed contrast/system-bar edge checks remain pending.
15. [x] Add Change server with a swap icon immediately above Log out/Sign in in the profile menu. It opens an editor prefilled from `BrowserState.serverUrl`; malformed values stay editable with the existing invalid-URL message. A valid value opens a Cancel/Continue warning that changing server logs out and cancels active downloads. Cancel returns to the populated editor. Continue resets transient work, disables cached-login fallback, clears the old server/session/cookies/caches/current-reader/cover-warming state, then validates the replacement. A reachable URL is normalized, configured, and opened in Login with prefill and a Server changed message; validation failure opens Server Setup with the attempted unreachable URL/error. Target-device validation passed.
16. [x] Add a profile Achievements destination backed by the official authenticated `GET /api/v1/achievements` contract. Show an overall earned/available summary and adaptive cards split into Unlocked then Locked. Earned cards show award date; locked cards show current/threshold progress only when both values exist; server-censored secret fields remain preserved. Treat HTTP 404 as an older-server unsupported state and other failures as retryable. The functional flow works on the target server.
17. [x] Prevent visible Library Browse and Series catalog jump rails from overlaying trailing grid cards. Both grids now reserve 32 dp trailing content padding only while the shared 20 dp rail is visible, accounting for the 20 dp rail, 4 dp outer edge, and 8 dp card-to-rail separation; without a rail they retain 16 dp full-width padding. Target-device spacing is validated; additional responsive layouts remain pending.
18. [x] Make Change server a silent no-op when the submitted URL normalizes to the current URL: close the editor without a warning or state change.
19. [x] Replace missing-letter forward fallback with a full stable #/Aâ€“Z rail whenever Library or Series navigation is eligible. Ascending uses #/Aâ€“Z and descending uses Zâ€“A/#; only exact represented targets are clickable, while absent labels are disabled and unavailable.
20. [x] Raise the fresh EPUB Top margin default to 30% while keeping Bottom/Left/Right at 15%; preserve every saved per-book margin value unchanged.
21. [x] Restyle Achievements as Library-like adaptive poster tiles with server-driven symbols. The implementation works on the target device but the tiles are too large for favicon-like symbols and are superseded by the compact information-card follow-up below.
22. [x] Restyle series Previous/Next as transparent borderless 46 dp controls matching detail actions and retain the first complete series load so adjacent navigation does not reload it.
23. [x] Remove the haptic-feedback setting and every explicit app-haptic path by user direction.
24. [x] Fix indefinitely stuck non-audio progress events caused by stale/deleted file IDs. On progress POST 404, load the current book, parse its primary file, and retry once with a changed replacement file ID before patching status. If the book/current file/remapped file is still 404 or the ID is unchanged, classify the event INVALID and acknowledge it so the queue drains instead of retrying forever.

### Latest target-device feedback work order â€” 2026-07-18

Target-device validation now passes for stale progress queue draining; Download/Delete local reconciliation; offline Local-book thumbnails; Options navigation from book detail; server-wide Home and Currently Reading aggregation; prompt reader close; the current-chapter slider; series Previous/Next; Change server; jump-rail spacing; theme/system-bar contrast; background network policy; interrupted and large downloads; and online/local CBZ and CBR reading.

Pending follow-up:

1. [x] Replace the oversized Achievement poster tiles with compact adaptive information cards using a 260 dp grid minimum. Each card keeps a 22 dp server-driven icon immediately before the title, lock/unlock state at row end, and description/category/rarity plus conditional progress/date inside the card.
2. [x] Replace the horizontally scrolling book-detail action `LazyRow` with a wrapping `FlowRow`, keeping Download/Update/Cancel/Delete labels and replacing More with a directly labeled live Mark as read or Mark as unread tile.
3. [x] Restore embedded EPUB resources through `WebViewAssetLoader` at `appassets.androidplatform.net`. Generate safe nested/encoded base URLs from chapter paths, use one extracted EPUB root for the visible reader and hidden page measurer, and disable broad WebView file/content access.
4. [x] Fix remote nonlocal EPUB/PDF preparation by downloading an authenticated temporary reader copy. Remove the incorrect CBZ-signature gate that prevented EPUB Preview even after a successful relogin.
5. [x] Restore the full #/Aâ€“Z Library and Series jump rail while retaining sort/catalog hiding rules and the existing grid gutter. Missing labels use 38%-alpha `onSurfaceVariant`, disabled semantics, unavailable content descriptions, and no click action; descending order is Zâ€“A/#.
6. [x] Add a Local books shelf at the bottom of top-level Home from server-wide `homeBooks` and at the bottom of Library Recommended from selected-library books. `HomeFeed` derives a deterministic deduplicated alphabetical preview capped at 12, reuses `ShelfBookCard` actions/covers, and routes See all to global Local books or a selected-library-scoped Local books destination/title. More > Local books remains global.

### Reader controls work order - 2026-07-19

The virtual no-store document candidate in commit `4854b0b` and its later custom-renderer SVG normalization remained insufficient on the Samsung Galaxy S24. The supplied books isolated bitmap-only SVG cover pages as a recurring failure shape, while Overlord Vol. 1's ordinary chapter-3 image could render.

All samples correctly identify a JPEG manifest item with EPUB3 `properties=cover-image` (Zero Damage also carries legacy metadata), so missing OPF cover metadata is not the cause. Readium Kotlin Toolkit is now pinned at 3.0.2: 3.0.0's image navigator crashed during construction, while 3.0.2 fixes it and remains compatible with Kotlin 1.9.24. At this checkpoint EPUB and comics used dedicated Readium activities while PDF and audio remained unchanged; both were migrated in later work-order steps.

Permanent coverage includes EPUB routing/progress and three connected EPUB cases plus comic routing/preparation and two connected comic cases. The current full gate passes 229 JVM tests across 38 suites with zero failures/errors/skips, `lintDebug`, `assembleDebug`, and `assembleDebugAndroidTest`; all five focused connected reader tests pass. Samsung physical validation of the new comic path remains open.

Execute the current work in this order:

1. Install version 0.2.7 from `app/build/outputs/apk/debug/app-debug.apk` on the Samsung Galaxy S24 and validate local/online CBZ plus connected CBR/CB7 through normal Read and Preview. Confirm retained controls/footer/system behavior, exact normal locator resume/progress, Preview isolation, cached reuse after the first connected CBR/CB7 preparation, and clear offline failure for downloaded CBR/CB7. Spot-check unchanged EPUB, PDF, and audio paths. Do not overstate comic validation until this physical pass succeeds.
2. Completed in version 0.2.4: use one fixed 46 dp-high, non-wrapping, non-scrolling row. Read/Preview are labeled. Nonlocal books always keep an inline 46 dp Download/Retry/Cancel slot, disabled but present when unavailable/offline. Local books have no inline transfer; Delete and eligible Update/Cancel update live in More. Mark as read/unread uses current typography/font-scale measurement and moves to More unless the full row fits. More appears whenever anything is hidden. At extreme widths only Read/Preview compact through weights. Physical S24 validation remains pending.
3. Completed in version 0.2.5: show a compact primary-colored `Reading Â· n%` or `Read Â· n%` line directly above actions. Use finite canonical progress clamped to 0â€“100 with at most two decimals; opened 0% is valid, unread-reset 0% is absent, and unknown percentage renders status-only. Keep identity metadata free of duplicate reading status and prefer canonical BrowserState over stale detail cache so Mark as unread removes the line immediately. Physical S24 validation remains pending.
4. Completed in version 0.2.7: after navigator readiness, every EPUB/comic Read and Preview entry shows an above-all-UI tutorial. Three equal full-height thirds are Previous RGB(255,114,118), Menu RGB(0,0,0), and Next RGB(144,238,144), each alpha 0.5. The current exact 3,000 ms timeout starts on first pre-draw; every region consumes taps and dismisses immediately.
5. Completed July 20: move the shared rail to the right and approximately 75% height; make its primary unit Page; add EPUB chapter arrows plus the retained outer Chapters picker; remove duplicate cog position controls; keep chrome below status insets; remove its visible Back action, place labeled Exit/X leftmost, retain surface/scrim dismissal; and pin book-detail More to the trailing edge. The current gate passes 244 JVM tests across 41 suites, lint, debug APK assembly, and Android-test APK assembly.
6. Run target-device, accessibility, large-text, responsive, orientation, theme, timing, resume/sync, Preview-isolation, and offline checks, then resume CB7, representative PDF/audiobook, compact Achievement, series-neighbor, jump-rail, and partial-refresh edge validation.

The implemented trigger is every initial EPUB/comic reader activity entry/open, including Read and Preview. No seen-state is persisted across repeat opens, books, files, or installs.

### Reader stability and interaction follow-up - 2026-07-20

1. [x] Diagnosis complete: the former `AppCoordinator.showBrowser()` conflated browser-snapshot mutation with navigation, allowing delayed refresh or download-state callbacks to replace `ReaderLoading` or `Reader`.
2. [x] Implement the stability fix. Guarded `showBrowser()` updates `lastBrowserState` while preserving `ReaderLoading`/`Reader`; explicit reader open failure and close call `navigateToBrowser()`. Two deterministic `AppCoordinatorTest` regressions cover delayed catalog refresh and download-state updates, and the focused test run passes.
3. [ ] The full automated gate passes. Run physical-device validation, including refresh/download activity during reader open/use plus explicit failure and close navigation.
4. [x] Remove the lightweight chrome Back button and place labeled Exit/X leftmost. Center/content-scrim taps continue to dismiss chrome and return to reading.
5. [x] Change the tutorial to exactly 3,000 ms. All three full-screen regions consume taps, call dismissal, and are hidden immediately by the EPUB/comic activities.
6. [ ] The complete automated gate passes and the debug APK is built. Validate refresh/sync races and revised reader interactions on the Samsung Galaxy S24 before resuming the remaining media/responsive queue.

Target-device feedback confirms the original reader replacement race is resolved during sync/refresh/download activity, Exit works, and tutorial timeout/tap dismissal works. Broader format/responsive validation remains open.

### Reader tutorial, Series grouping, and profile-menu follow-up - 2026-07-20

1. [x] Remove the tutorial-label background and enlarge Previous/Menu/Next text to 28 sp, approximately twice its prior size, without changing region colors, timing, or tap consumption. Focused JVM and Android-test compilation pass.
2. [x] Add mutually exclusive Library and File format grouping controls below Series-detail Genres. Default to Library; allow neither; persist the selection globally across Series. Because unscoped BookCard payloads omit `libraryId`, fetch Series books per configured library and merge ownership by book ID so Library sections separate correctly. Library sections follow configured order, formats use normalized alphabetical labels, books retain series-index order, and each section label has an inline trailing divider. Focused grouping/preference tests and Android-test compilation pass.
3. [x] Reorder the profile dropdown to Achievements, Options with a cogwheel, About, divider, Change server, and Log out/Sign in. Remove About from More. Compiled Compose coverage checks the order, icon, divider, routing, and remaining More destinations.
4. [x] Deterministic state/grouping and compiled Compose coverage are in place; the full gate passes 258 JVM tests across 44 suites, lint, debug APK assembly, and Android-test APK assembly. Tutorial readability, cross-library grouping and persistence, section label/divider layout, and profile-menu ordering pass target-device validation.
5. [ ] Separately validate the Options cog icon, profile divider semantics, About routing, accessibility, and large-text layouts.

### Readium, per-library cover shape, and persistent audiobook player - 2026-07-20

1. [x] Baseline Readium on the target device: EPUB and CBZ work directly; connected CBR works through BookOrbit page extraction followed by cached-CBZ normalization. The local `sample/86 Volume 01/` fixture supplies a 489,114,453-byte M4B and companion metadata with 17 chapter ranges for the audio migration and validation pass.
2. [x] Audit Readium parser/navigator capability and encode it in `ReadiumPublicationRoute`: EPUB/KEPUB use direct EPUB, PDF uses direct PDF, supported audio uses direct audio, CBZ uses direct image publication, CBR/CB7 normalize to cached CBZ, and MOBI/AZW/AZW3/FB2 use `UNSUPPORTED_EBOOK`. Unsupported ebooks map to `MediaKind.UNKNOWN` and show `This file format is not supported.` instead of silently entering an invalid EPUB reader. Focused repository-helper and download-update tests pass; conversion is deferred outside current product scope.
3. [x] Parse exact BookOrbit `coverAspectRatio` values (`2/3` and `1/1`) with portrait fallback and propagate the owning library's aspect through Library/Book summaries, mixed search/author/series/cache/local enrichment, Browser/active-reader/detail persistence, and Room catalog v2's 1â†’2 migration with `2/3` default. Shared book/fullscreen and compact-audio covers use Crop with true 2:3 or 1:1 geometry and no artificial vertical padding. Poster/shelf cards use a portrait-height cover slot with the actual cover at `BottomCenter`, aligning cover bottoms and labels while attaching More to the image. Focused parsing, ownership, persistence, Room, and `BookCoverLayoutTest` coverage plus the 265-test/46-suite full gate pass; physical mixed-library alignment validation remains pending.
4. [x] Establish persistent audiobook playback with Readium 3.0.2's media `AudioNavigator` and ExoPlayer adapter hosted by a foreground Media3 `MediaSessionService`. The application controller samples progress every 1.5 seconds independently of screen/background ownership; Readium handles audio focus/noisy output, Preview is isolated, explicit Close publishes final normal progress and stops the service, and Android 13 notification permission is requested on first playback.
5. [x] Add only an app-global compact player above the browser bottom navigation, also visible in separate Readium EPUB/comic activities. It exposes tappable cover-to-detail, title/author, elapsed/remaining seek slider, Replay 10, play/pause, Forward 30, BookOrbit `audioMetadata` chapters, 0.75/1/1.25/1.5/2Ã— speed, and Close; audiobook detail says Play. Metadata loads before preparation and persists through active-reader/detail caches. Authenticated online Read and Preview now use remote byte-range resources and remain progress-isolated where applicable; local/downloaded playback is unchanged. `CookieManager` clearing is main-thread confined. No expanded/fullscreen player exists.
   - [x] Correct the July 21 black-screen/perpetual-Preparing regression: chapter enrichment retains the tapped book's `fileId`, format, and `localPath` and copies only chapter metadata. A versioned, format-aware reader cache now stages, verifies, and atomically promotes complete readable audio so interrupted Preview output cannot be reused. Playback binding explicitly auto-creates/fails, preparation times out after 30 seconds, and close cleanup after 5 seconds.
   - [x] Target-device feedback confirms the hardened audiobook opening/playback flow works well.
   - [x] Make the complete separate-activity EPUB/comic viewport end above the measured compact player and expand on Close. Protect Browser/detail bottom content when regular bottom navigation is absent and non-Browser overlays with Android navigation-bar insets. Focused connected coverage validates a 240 px reserve and restoration.
   - [x] Keep persisted normal-audio progress for resume without restoring the transient Reader destination at startup. Local-only and authenticated bootstrap continue to Browser while the surviving playback service/controller supplies the compact player. The focused coordinator regression failed before the fix and passes afterward.
   - [x] The full gate passes 262 JVM tests across 44 suites with zero failures/errors/skips plus lint and both APK assemblies.
   - [ ] Validate surviving-playback relaunch, authenticated Preview, and the revised layout on the target device before continuing notification/background and service/process validation.
6. [x] Replace the legacy Compose `PdfRenderer` path with pinned Readium PDF support: add `readium-adapter-pdfium:3.0.2` and required Jetifier compatibility, parse with `PdfiumDocumentFactory`/`PublicationOpener`, render with `PdfNavigatorFragment`/`PdfiumEngineProvider` in `ReadiumPdfReaderActivity`, and retain locator/progress, Preview isolation, page controls, chrome/tutorial, orientation/keep-awake/system bars, invalid-file failure, and compact-audio overlay behavior. Remove routed legacy EPUB/comic fallbacks; unsupported/non-normalizable comics now show conversion/reconnect guidance. Two JVM routing tests pass, and the generated three-page connected regression passes on Medium_Phone AVD API 17 as `Profile.PDF` with exactly three positions. Physical target-device UI validation remains pending.
7. [x] Defer MOBI/AZW/AZW3/FB2 conversion by explicit product choice; keep the formats unsupported unless future product direction changes.
8. [ ] Run the full physical/device validation matrix for supported formats, cover aspect ratios, notification/background behavior, sync/offline transitions, process recreation, accessibility, and responsive layouts.

The completed fullscreen comic-reader step passes 178 JVM tests across 28 suites with zero failures/errors/skips; `lintDebug`, `assembleDebug`, and `assembleDebugAndroidTest` pass. Compose instrumentation compiles tap-next, right/left swipe, options-open, and Continue reading dismissal regressions.

The earlier haptic-perceptibility step was later superseded: its preference, UI, provider, manual requests, and obsolete tests are now removed.

The current reader/detail, stability, audiobook, cover-aspect/alignment, Readium-PDF, tutorial-label, Series-grouping, profile-menu, orientation, fixed Library controls, and remote-media gate passes 274 JVM tests across 47 suites with zero failures/errors/skips; `testDebugUnitTest`, `lintDebug`, `assembleDebug`, and Android-test APK assembly pass. Connected/compiled coverage proves the generated three-page PDF opens as `Profile.PDF` with exactly three positions and includes authenticated Readium audio ranges, ignored-range rejection, one credential-renewal retry, legacy full-media cache pruning, plus lazy comic page publications that open without fetching pages and request only a selected page. Target-device remote-audio/comic, PDF UI, mixed-library cover alignment, audio relaunch, revised audiobook UI/Preview, and remaining media/device validation remain pending.

The completed immediate reader-close step passes 184 JVM tests across 30 suites with zero failures/errors/skips; `testDebugUnitTest`, `lintDebug`, `assembleDebug`, and `assembleDebugAndroidTest` pass. Focused coordinator coverage verifies cached-browser restoration before background work and final-progress preservation.

The completed missing-cover fallback step passes 187 JVM tests across 30 suites with zero failures/errors/skips; `testDebugUnitTest`, `lintDebug`, `assembleDebug`, and `assembleDebugAndroidTest` pass. Three helper regressions cover missing metadata, candidate order, and canonical deduplication.

The completed EPUB current-chapter slider step passes 188 JVM tests across 30 suites with zero failures/errors/skips; `testDebugUnitTest`, `lintDebug`, `assembleDebug`, and `assembleDebugAndroidTest` pass. Unit coverage verifies the clamped JavaScript command; instrumentation compiles retained selector/slider semantics and a WebView jump/rendered-text regression.

The repository HTTP integration-test step retains 188 JVM tests across 30 suites with zero failures/errors/skips; `testDebugUnitTest`, `lintDebug`, `assembleDebug`, and `assembleDebugAndroidTest` pass. Three `BookOrbitRepositoryIntegrationTest` cases compile into the androidTest APK; connected execution remains pending because adb device enumeration hung/no usable target was available.

The completed Home initial-sync optimization passes 189 JVM tests across 30 suites with zero failures/errors/skips; `testDebugUnitTest`, `lintDebug`, `assembleDebug`, and `assembleDebugAndroidTest` pass. A six-library gated coordinator regression proves the first three nonselected refreshes start, the next three wait, maximum library concurrency is three, every slice merges, and final refresh state clears.

The completed downloaded-book update step passes 196 JVM tests across 31 suites with zero failures/errors/skips; `testDebugUnitTest`, `lintDebug`, `assembleDebug`, and `assembleDebugAndroidTest` pass. Store/model unit coverage exercises version decisions, ebook compatibility, and atomic replacement; compiled Compose and real-repository MockWebServer regressions cover the Update local action plus malformed-replacement preservation/retry. The target-device Update local flow works correctly.

The corrected BookOrbit genre-contract step passes 197 JVM tests across 31 suites with zero failures and `assembleDebugAndroidTest` passes. The implementation now matches the official current BookOrbit source: book relation filters use singular `genre` (and adjacent `author`) with `includesAny` and an array value instead of the previous `genres`/`contains`/scalar shape. A compiled real-repository MockWebServer test asserts the exact genre request payload and parses the filtered result. Target-device/server genre filtering is validated.

The completed jump-rail layout step passes 198 JVM tests across 31 suites with zero failures/errors/skips; `testDebugUnitTest`, `lintDebug`, `assembleDebug`, and `assembleDebugAndroidTest` pass. Unit coverage verifies conditional 16/32 dp trailing padding, and compiled Compose regressions assert that Library and Series card right bounds stay left of the visible rail. Target-device spacing is validated; additional responsive-layout validation remains pending.

The completed Change server step passes 200 JVM tests across 31 suites with zero failures/errors/skips; `testDebugUnitTest`, `lintDebug`, `assembleDebug`, and `assembleDebugAndroidTest` pass. Two coordinator tests cover reachable replacement and unreachable prefilled recovery. A compiled Compose regression verifies menu order above Log out, current-URL prefill, the warning, Cancel returning to the editor, and Continue invoking reset/set behavior. Target-device validation passes.

The completed series Previous/Next navigation step passes 203 JVM tests across 32 suites with zero failures/errors/skips; `testDebugUnitTest`, `lintDebug`, `assembleDebug`, and `assembleDebugAndroidTest` pass. `SeriesNavigationTest` adds three JVM cases for unordered numeric ordering, stable disabled edges, and null-index ordering with unrelated-book filtering. Basic navigation is target-device validated; the transparent 46 dp visual follow-up and retained-load optimization are implemented, with physical visual/responsive/long-title/loading/offline validation pending.

The completed five-option app-theme step passes 205 JVM tests across 33 suites with zero failures/errors/skips; `testDebugUnitTest`, `lintDebug`, `assembleDebug`, and `assembleDebugAndroidTest` pass. `AppThemePaletteTest` verifies Follow-system/manual mapping and exact distinct background/surface colors. Preference unit coverage round-trips all values and migrates legacy `dark` to Charcoal; instrumentation persists Warm black, and the compiled Options UI regression selects OLED black from the automatically generated five-item list. All five themes work on the target device; detailed contrast/system-bar edge checks remain pending.

The completed Achievements step passes 208 JVM tests across 34 suites with zero failures/errors/skips; `testDebugUnitTest`, `lintDebug`, `assembleDebug`, and `assembleDebugAndroidTest` pass. The functional target-server/device flow is validated, and the oversized poster tiles are now replaced by compact 260 dp-minimum information cards with 22 dp server-driven icons.

The compact Achievement/action-row follow-up passes the full gate: 208 JVM tests across 34 suites with zero failures/errors/skips, lint, debug APK assembly, and Android-test APK assembly. Focused coverage includes `AchievementIconTest`, `BookDetailReadingStatusActionTest`, and Android-test Kotlin compilation.

The full disabled-letter jump-rail follow-up passes 209 JVM tests across 34 suites with zero failures/errors/skips, lint, debug APK assembly, and Android-test APK assembly. Unit coverage exercises `catalogJumpRailLabels`; compiled Compose assertions verify unavailable `B` entries in both Library and Series.

The Local books shelf follow-up passes 210 JVM tests across 34 suites with zero failures/errors/skips, lint, debug APK assembly, and Android-test APK assembly. Unit coverage verifies server-wide aggregation, library scoping, deduplication, alphabetical order, and the 12-item limit; compiled Compose coverage exercises the global shelf/global See all route and selected-library exclusion.

The completed EPUB preparation/resource fix passes 213 JVM tests across 35 suites with zero failures/errors/skips; `testDebugUnitTest`, `lintDebug`, `assembleDebug`, and `assembleDebugAndroidTest` pass. JVM coverage verifies the remote EPUB/PDF cache policy plus safe nested/encoded asset base URLs and root-boundary rejection. Compiled instrumentation covers the authenticated nonlocal EPUB Preview download endpoint and a real WebView resolving `../Images/cover.png` with nonzero `naturalWidth`. Instrumentation compiled but was not executed because no device was attached.

The completed combined feedback batch passes 206 JVM tests across 33 suites with zero failures/errors/skips after removing the obsolete haptic suite; `testDebugUnitTest`, `lintDebug`, `assembleDebug`, and `assembleDebugAndroidTest` pass. Coverage includes same-server no-op handling, represented jump-rail labels, fresh EPUB margin defaults with saved-value preservation, retained series loading with transparent 46 dp neighbor controls, and complete app-haptic removal. APK: `app/build/outputs/apk/debug/app-debug.apk`.

The stuck progress-queue fix passes 207 JVM tests across 33 suites with zero failures/errors/skips; `lintDebug`, `assembleDebug`, and `assembleDebugAndroidTest` pass. A parser unit regression covers current-primary-file extraction, while a compiled real-repository MockWebServer case proves stale-to-current remapping, both progress and status writes, and a final queue count of zero. APK: `app/build/outputs/apk/debug/app-debug.apk`.

Audiobook implementation and validation can use the local M4B/chapter-metadata fixture. Direct OIDC/SSO remains deferred until its provider/redirect contract is confirmed.

## Source of truth

### Options backlog

Interface

- [x] Add a lock-current-orientation toggle.
- [x] Add a haptic-feedback toggle (superseded and removed by user direction).
- [x] Add one flat app Theme list: Follow system, Light, Charcoal, Warm black, or OLED black, applied immediately with legacy `dark` migration to Charcoal.
- [x] Add default opening screen selection: Home, Library, or Local books, applied on fresh app start.
- [x] Add a Reduce motion/animations accessibility option using immediate catalog jumps.

Data

- [x] Add downloads-over-cellular policy: Always, Never, or Ask for confirmation, with browser-wide start/prompt/block behavior and metered non-Wi-Fi treated as cellular.
- [x] Add storage management showing downloaded/disposable-cache sizes and a confirmed Clear cache action that preserves downloaded books, downloaded-book metadata, settings, progress, and catalog data.
- [x] Add background metadata/cover refresh network policy: Any network, Wi-Fi only, or Disabled; current scheduled cover work uses CONNECTED/UNMETERED constraints and reconfigures immediately. A separate scheduled metadata worker is not yet present.
- [x] Add confirmation before deleting a local copy, enabled by default across native-browser delete entry points.
- [x] Validate lock-current-orientation, default opening screen, Reduce motion, downloads-over-cellular behavior, storage/cache clearing, and delete-local confirmation on a physical device.
- [x] Validate all five app themes on the target device; detailed contrast/system-bar edge cases remain open.
- [x] Remove the haptic-feedback setting, storage, provider, manual requests, and obsolete tests.
- [ ] Validate background network policy on physical devices.

Detailed checkpoint status is tracked in:

- [../CHECKLIST.md](../CHECKLIST.md)
- [ui-ux.md](./ui-ux.md)
