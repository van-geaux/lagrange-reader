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
- Completed in code: read remote comics through authenticated `/api/v1/cbz/files/{fileId}/pages` and `/pages/{pageIndex}`, with page progress; retain local ZIP extraction for offline CBZ and mislabeled ZIP archives
- Treat RAR4/RAR5/7z signatures as valid downloaded comics so they are not deleted as corrupt; downloaded/local CBR/CB7 still requires server page extraction in this build
- Completed and target-device validated: give comics the established novel-reader interaction model with a black fullscreen fitted-image surface, always-visible page footer, outer tap zones and horizontal swipes for page changes, center-tap options, exposed-content/Back dismissal, and Back-to-exit only after options close
- Device-validate online CBZ/CBR/CB7 and offline ZIP/CBZ behavior; client-side offline RAR/7z extraction remains an optional future enhancement
- Obtain representative audiobook and PDF files; audiobook testing remains deferred without a sample

### 5. Release readiness

- Complete accessibility, large-text, narrow-screen, rotation, and theme checks
- Run unit, lint, debug, instrumentation-compile, and release build gates
- Completed: audit the tracked tree and history for sensitive filenames, high-confidence key signatures, hardcoded credential assignments, and unexplained production/internal URLs
- Create the first tagged release

## User feedback workplan — 2026-07-12

The latest debug APK passed login, session relaunch, browsing, EPUB reading, download/offline reopen, and progress-sync testing on the target device. The remaining device feedback is ordered as follows:

1. Completed: put currently reading books in the first Home shelf, with completed/recently read titles remaining available as a separate shelf.
2. Completed: replace the hamburger/drawer browser shell with a Plex-inspired native bottom-navigation layout. Libraries open at the top-level library view, with a library-change control at the top of that screen; More expands to Series, Authors, and Options.
3. Completed: add breathing room below the system status bar on Home, replace the persistent large search field with a top search icon, and open search in a dedicated layer. The standard Android status bar remains visible.
4. Completed: add Compact, Comfortable, and Wide reader padding options, using Comfortable as the more generous default and repaginating when the setting changes.
5. Implemented: keep the branded splash state through bootstrap and replace the post-splash spinner with the branded loading mark; validate the result on a physical device.

Each item must preserve session recovery, offline behavior, progress sync, Preview isolation, and reader resume.

## Latest device feedback workplan — 2026-07-13

1. Implemented: restore the exact EPUB page within the current chapter on close/reopen and full app restart, while preserving the existing server progress percentage and chapter-boundary behavior; physical-device validation remains required.
2. Implemented: change the selected-library book browser from list rows to the adaptive poster-card grid used by Series and Authors, preserving metadata, offline-disabled cards, and detail actions; physical-device validation remains required.
3. Implemented: rename the visible app brand to `Lagrange` and add the subtitle `a BookOrbit reader` to the logo/launch presentation. BookOrbit remains the connected server/product name; physical-device branding validation remains required.
4. Implemented: add an About destination after Options in the More menu, using placeholder app/version/server information until the About content is reviewed.

## Latest device feedback workplan — 2026-07-13 (continued)

1. Implemented: show the `a BookOrbit reader` subtitle only on the splash/loading presentation; the opened app top bar and About screen now show the Lagrange name without the subtitle. Physical-device presentation validation remains required.
2. Implemented: add a Libraries content control that collapses books belonging to the same series into one representative series card, with a Show all action to restore the full book grid; physical-device validation remains required.
3. Implemented: add Local books to More immediately before Options, loading valid downloaded titles from local storage and presenting them with the same poster-card grid.

## Latest device feedback workplan — 2026-07-13 (library refinement)

1. Implemented: reduce the current book poster-card minimum width to 75% of its current value, which is half of the original candidate size; physical-device density validation remains required.
2. Implemented: add paginated library loading with automatic near-end page fetching in the Browse tab using the library response total/page metadata.
3. Implemented: show the Lagrange logo only on Home; Library now opens the first/selected library directly, and its library name replaces the logo as a tappable library selector.
4. Implemented: split the selected Library view into Recommended, reusing the Home-style shelves for that library, and Browse, containing the complete paginated book grid.

## Latest device feedback workplan — 2026-07-13 (final browser polish)

1. Implemented: make the Library, Series, and Authors poster-card grids visibly smaller on narrow phones by lowering their adaptive minimum width; physical-device density validation remains required.
2. Implemented: add a downward-triangle affordance beside the selected Library name so its tap-to-select behavior is discoverable.
3. Implemented: remove the Library refresh arrow and add swipe-down-to-refresh to the Library content tabs.
4. Implemented: persist fetched book cover thumbnails locally and read that cache before attempting the server, including for Local books.

## Latest device feedback workplan — 2026-07-13 (follow-up polish)

1. Implemented: rename the collapsed-series action from Show all to Expand series while preserving the full-grid behavior.
2. Implemented: improve cover-thumbnail decoding and retry behavior so slow or failed cover loads do not permanently remain as first-letter placeholders.
3. Implemented: add extra bottom spacing to the More sheet so About remains comfortably above the Android navigation controls.
4. Implemented: show the full library book total in the paginated Browse header, with optional server-provided series totals instead of only the currently loaded page count.
5. Implemented: make the top-left Libraries title tappable while the picker is open so it returns to the selected library page.
6. Implemented: cache full BookDetailInfo metadata for downloaded titles and prefer that cache when opening Local books.

## Latest device feedback workplan — 2026-07-13 (lazy library navigation)

1. Implemented: replace Browse's Load more button with automatic page loading as the user approaches the end of the current grid.
2. Implemented: add a right-side title-initial jump rail for fast movement through loaded library content.
3. Superseded by the complete Room catalog workplan below; Browse no longer lazy-loads as the grid scrolls.

## Latest device feedback workplan â€” 2026-07-13 (reader spacing)

1. Implemented: keep the existing overall Compact/Comfortable/Wide reader padding presets and add independent Top and Bottom controls using the same levels.
2. Implemented: keep Comfortable as the default for all three controls and repaginate the current EPUB chapter whenever any padding value changes.

## Latest device feedback workplan â€” 2026-07-13 (percentage padding and recovery)

1. Implemented: replace named reader padding presets with four independent percentage sliders for Top, Bottom, Left, and Right. Each slider ranges from 0–100%, where 100% maps to one quarter of the relevant viewport dimension; every edge defaults to 15%.
2. Implemented: make edge changes independent and apply the new values when the slider is released, then repaginate the current EPUB chapter.
3. Implemented: keep the jump rail visible for # and every A–Z label even before those titles are loaded; group every non-alphabetic title under # and place it first.
4. Implemented: alphabetize collapsed library representatives by their series name and restore the nearest book/series scroll anchor when switching between collapsed and expanded views.
5. Implemented: route authentication failures from background search, cover, catalog, detail, and paginated library loads through the login recovery screen instead of silently returning empty content. Physical-device expiry testing remains required.

## Latest device feedback workplan — 2026-07-13 (reader, Home, and session follow-up)

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
5. Implemented: request `/api/v1/libraries/{id}/books/jump-buckets`, persist valid default-sort bucket indexes, and scroll directly to the server's absolute index. Missing letters fall forward to the next bucket; complete-cache local indexes cover filters, author/title sorts, collapsed series, and older servers.
6. Implemented: hide the rail while an initial full catalog is incomplete and for non-letter sort modes, preventing a rail tap from degenerating into end-of-list lazy loading.
7. Added JVM coverage for pagination termination, duplicate handling, jump parsing/index mapping, and cache-first coordinator failure recovery. Added a compiled Android Room transaction test for changed rows and deletions.
8. Physical-device validation remains required for first sync, instant reopen, pull refresh, additions/deletions/progress changes, offline browsing, and #/A–Z jumps across a large target-server library.

### Latest device feedback workplan - 2026-07-13 (large-library thumbnails and details)

1. Implemented: replace process-global cover jobs with Compose-owned cancellable OkHttp calls so rapidly scrolled-off cards stop consuming server and client request capacity.
2. Implemented: version memory/disk thumbnail keys from catalog metadata and use per-file cache locks, allowing visible foreground reads to proceed independently of background writes.
3. Implemented: after complete catalog reconciliation, use WorkManager on unmetered connectivity to scan the selected library and download up to 50 missing/changed thumbnails per durable chained batch.
4. Implemented: persist rich `BookDetailInfo` for every opened title, not only downloaded books, and invalidate it when the title's catalog update version changes.
5. Deliberate boundary: keep the complete catalog and thumbnails locally, but do not prefetch one rich-detail endpoint per title; a 5k-book library would otherwise generate roughly 5k extra API calls. The summary detail screen renders immediately and the rich supplement is fetched once per changed/opened title.
6. Verification passes: 127 JVM tests across 21 suites, Android lint, debug APK assembly, and Android instrumentation-test compilation. Physical-device validation against the 5k-book library remains required.

### New user feedback workplan - 2026-07-15

- [x] Add visible overflow and long-press actions to Currently reading cards. Removal uses BookOrbit's normal-user file/audio progress and status APIs to clear primary/current progress, mark the title unread, and clear matching local queued, synced, resume, and cached progress so stale work cannot restore it. Reading-session history and additional-file progress remain an admin-reset limitation.
- [x] Redefine On Deck to show only the next unread book in a series after a completely read book; omit it when that next book is already in Currently reading. For example, after volumes 1 and 2 are read, show volume 3 until volume 3 becomes Currently reading.
- [x] Add pull-down refresh to Home using the same refresh indicator and cache-preserving catalog reconciliation as Libraries.
- [x] Fix missing thumbnails on Series cards by mapping BookOrbit's `coverBookIds` to representative book thumbnail URLs.
- [x] Fetch every filtered Series page into a deduplicated in-memory catalog, remove Load more, and add #/A–Z or Z–A direct navigation for Name sorting.
- [x] Show the number of books in collapsed Libraries series cards, matching the Series screen.
- [x] Persist the reader background choice (light, dark, or sepia) across close/reopen and app sessions.

### Additional reader feedback workplan - 2026-07-15

- [x] Prevent the reading screen from closing unexpectedly after an idle period.
- [x] Simplify the reader options overlay to one Close action; tapping the visible book content should also close the options overlay.
- [x] Show battery and signal indicators in the reader's top-right area using Android's native status bar while keeping the bottom navigation bar immersive.
- [x] Show book completion percentage, chapter page progress, and a measured whole-book page location in an always-visible EPUB footer.

### Device validation follow-up and new work order — 2026-07-15

- [x] Validate explicit HTTP server setup and authenticated browsing/reading.
- [x] Validate Mark as read/unread from supported book-card menus.
- [x] Validate Android/BookOrbit progress synchronization in both directions.
- [x] Validate large-library browsing, fast #/A–Z jumps, and airplane-mode behavior; undownloaded books correctly require a connection for full details.
- [x] Validate session-expiry recovery, Series navigation, and Series thumbnails.
- [x] Add the book context menu to global search results for both long-press and three-dot actions.
- [x] Keep global search results as list rows rather than converting them to poster cards.
- [x] Fix stale Currently Reading state after refresh: explicit `unread` records with no positive progress now discard stale page/position and status timestamps, so a server-confirmed removal cannot be reconstructed as reading activity.
- [x] Reduce Home sync/loading time for large libraries by requesting BookOrbit's 100-book page limit and loading large known page ranges in ordered batches with at most four concurrent requests. Existing cached content remains usable, and unstable totals/duplicates still trigger the full consistency retry.
- [x] Redesign reader options as a bottom sheet with separate actions for Continue reading (close options and remain in the book) and Close book (exit the reader).
- [x] Improve reader-options typography with reliable text/background contrast in Light, Sepia, and Dark modes.
- [x] Allow explicitly entered HTTP BookOrbit server URLs and Android cleartext traffic; bare remote hostnames still default to HTTPS, while bare local development hosts retain HTTP shorthand.
- [x] Add Mark as read and Mark as unread to the book-card three-dot and long-press menus. Read status is written directly while preserving position; unread uses the normal-user reset flow so completed progress cannot immediately restore Read.
- [x] Redesign book-detail actions as a horizontally swipeable compact tile area, using selective icons without hiding labels.
- [x] Make the tapped series title in book details navigate to that Series detail page.
- [x] Wrap Genre and Tag chips separately and group lower metadata into compact Publication, Identifiers, and Library/file cards.
- [x] Open the book-detail cover in a full-screen image viewer, dismissible by tapping the cover again or pressing Android Back.
- [x] Redesign the reader options window as a rounded bottom sheet with clearer hierarchy, spacing, and grouped controls.

The book-detail follow-up includes a Compose instrumentation regression for the action area, Genre/Tag content, cover-viewer tap dismissal, and Series navigation. `testDebugUnitTest`, `lintDebug`, `assembleDebug`, and `assembleDebugAndroidTest` pass; physical-device layout, horizontal swiping, and Android Back dismissal remain to be validated.

### Latest device feedback work order — 2026-07-17

- [x] Dismiss the full-screen cover viewer when any part of the screen is tapped; retain Android Back dismissal.
- [x] Replace oversized book-detail action cards with compact controls: Read/Preview labels beside clear icons and an icon-only Download action.
- [x] Clamp long book-detail titles to five rows with an expand/collapse affordance.
- [x] Support selecting multiple books in library grids and applying bulk Mark as read/unread actions.
- [x] Make genre selections navigate to filtered Books or Series results through the catalog filter model; keep tags informational.
- [x] Keep a book's series name and series index visible as distinct metadata rows.
- [ ] Validate genre-filter query support and result scope against the target BookOrbit server.
- [ ] Add and validate direct OIDC/SSO authentication after the provider and redirect contract are confirmed; native username/password remains current.
- [x] Complete the revised detail density, title expansion, multi-selection, genre navigation, and series-index implementation in code/tests.
- [ ] Run device validation for the revised detail density, multi-selection, genre-filter result scope, and interrupted-download recovery.
- [ ] Keep audiobook validation deferred until a representative sample is available; device-validate the implemented online CBZ/CBR/CB7 and offline ZIP/CBZ paths.
- [x] Revise detail actions so Read and Preview show text beside clear icons, while Download uses an unmistakable download symbol.
- [x] Expose active per-file download progress, percentage/linear or indeterminate state, cancel guidance, and retry failure status from book details.

### Latest detail-action feedback — 2026-07-17

The full-screen cover viewer, long-title expansion, series-index presentation, revised action labels/icons, and download status presentation are implemented. Multi-select header overlap and stale-selection pruning are fixed. Genre chips now navigate to fully paginated server-filtered Books or Series results; tags remain informational. The remaining work is physical-device/server validation, especially genre query compatibility and interrupted downloads; direct OIDC/SSO remains deferred.

### Target-device follow-up work order — 2026-07-17

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

The haptic perceptibility fix is implemented but not yet device-validated. While enabled, `MainActivity` supplies a custom provider that maps Compose requests to Android `HapticFeedbackConstants.CONFIRM` on API 30+ and `VIRTUAL_KEY` on API 26-29 through `View.performHapticFeedback`, so system haptic settings remain authoritative. Switching from off to on confirms immediately through the newly enabled provider; switching off receives the pre-change feedback and suppresses later requests. Existing coverage remains limited to Options switch/selection rows and supported Foundation long presses. Whether navigation, ordinary book taps, and page turns should also request haptics remains an open design decision.

Implement next, in order:

1. [x] Reconcile the still-open book-detail state immediately after Delete local succeeds so the action returns to Download, Local books refreshes, and catalog/snapshot offline state updates without backing out and reopening. Physical-device validation remains pending.
2. [x] Restore cached thumbnails and related metadata in Local books, including while offline, by filling incomplete catalog/snapshot summaries from the latest cached rich detail. Physical-device validation remains pending.
3. [x] Fix navigation to the main Options screen from book details by dismissing retained detail state before selecting Options. Physical-device validation remains pending.
4. [x] Restore Currently reading for genuinely in-progress books by deriving top-level Home from the server-wide collection rather than only the selected library. Physical-device validation remains pending.
5. [x] Aggregate Home shelves across every library on the connected server. Restore cached slices from every Room catalog and refresh the selected library first so its screen becomes usable/current before nonselected work. Refresh remaining libraries in deterministic ordered batches with at most three concurrent library reconciliations, merging successful slices after each batch; normal failures retain cached slices and contribute names to the partial-cache message, while cancellation/auth failures propagate immediately. Keep Libraries Recommended/Browse selected-library scoped and clear `isRefreshing` after the final batch. Physical-device/server latency and load validation remain pending.
6. [x] Fix the comic unsupported-format regression by recognizing bare CBZ/CBR/CB7 formats. Use authenticated server page extraction for all three online and local ZIP extraction for offline CBZ/mislabeled ZIP; keep client-side offline RAR/7z extraction optional. Physical-device validation remains pending.
7. [x] Replace the comic reader's visible buttons with a fullscreen fitted-image interaction model: left/right tap zones and horizontal swipes turn pages, center tap opens a dark rounded options sheet with title/page, Continue reading, Close book, and page slider, exposed-content tap or Android Back closes options first, and Android Back exits only when options are closed. Target-device validation passed.
8. [x] Add a final compact three-dot overflow at the end of the book-detail action row. It exposes exactly one live context-sensitive action: Mark as read for unfinished books or Mark as unread for read/completed books, resolving status from selected-library or server-wide Home state and remaining disabled for offline snapshots. Physical-device validation remains pending.
9. [x] Make Close book synchronously restore cached Browser state before coroutine/network/storage work, immediately merge captured progress into selected-library `books` and server-wide `homeBooks`, and show refreshing/loading state. Background work flushes the captured progress, attempts pending sync, clears non-preview active-reader state, and refreshes the browser; Preview still neither persists nor clears. If no cached browser exists, leave the reader for Loading while the browser loads. Physical-device validation remains pending.
10. [x] Fix foreground book-detail cover gaps such as `your name.` with ordered distinct candidates: normalize explicit `/cover` metadata to `/thumbnail` first, then try canonical `{server}/api/v1/books/{bookId}/thumbnail`. Missing metadata uses canonical directly; empty/failed explicit candidates fall through; cancellation still propagates; successful bytes retain the existing `updatedAt` memory/disk cache identity. The low-priority library-wide warmer remains unchanged, avoiding a request-per-missing-cover catalog scan. Physical-device/server validation remains pending.
11. [x] Add the approved current-chapter page slider beneath the unchanged Choose chapter button and chapter-chip selector in Reading position. It shows `Page X of Y`, uses the primary WebView's current chapter page count rather than whole-book measurement, disables for single-page chapters, jumps immediately while dragging, and cannot cross chapter boundaries. `BookOrbitReaderLayout.goToPage` clamps to `0..pageCount-1`, keeps page rendering, and publishes normal `pageChanged` progress. Physical-device validation remains pending.
12. [x] Add version-aware downloaded-book updates. Persist `sourceUpdatedAtMillis`; show online-only Update local when catalog `updatedAt` is newer, and give legacy versionless downloads one baseline-establishing update. Reuse cellular policy/confirmation and progress/cancel/retry UI with update wording. Download into a sibling `.part`, validate non-empty and EPUB/PDF/comic structure while preserving nonempty MOBI/AZW3 compatibility, then atomically replace the old file. Cancellation, failure, or malformed content deletes only staging and preserves the working local copy. Physical-device/server validation remains pending.

The completed fullscreen comic-reader step passes 178 JVM tests across 28 suites with zero failures/errors/skips; `lintDebug`, `assembleDebug`, and `assembleDebugAndroidTest` pass. Compose instrumentation compiles tap-next, right/left swipe, options-open, and Continue reading dismissal regressions.

The completed haptic-perceptibility step passes 181 JVM tests across 29 suites with zero failures/errors/skips; `lintDebug`, `assembleDebug`, and `assembleDebugAndroidTest` pass. `HapticFeedbackTest` covers API mapping and the enable transition.

The completed book-detail reading-status overflow step passes 183 JVM tests across 30 suites with zero failures/errors/skips; `testDebugUnitTest`, `lintDebug`, `assembleDebug`, and `assembleDebugAndroidTest` pass. Helper unit coverage and a compiled instrumentation flow cover the context-sensitive action.

The completed immediate reader-close step passes 184 JVM tests across 30 suites with zero failures/errors/skips; `testDebugUnitTest`, `lintDebug`, `assembleDebug`, and `assembleDebugAndroidTest` pass. Focused coordinator coverage verifies cached-browser restoration before background work and final-progress preservation.

The completed missing-cover fallback step passes 187 JVM tests across 30 suites with zero failures/errors/skips; `testDebugUnitTest`, `lintDebug`, `assembleDebug`, and `assembleDebugAndroidTest` pass. Three helper regressions cover missing metadata, candidate order, and canonical deduplication.

The completed EPUB current-chapter slider step passes 188 JVM tests across 30 suites with zero failures/errors/skips; `testDebugUnitTest`, `lintDebug`, `assembleDebug`, and `assembleDebugAndroidTest` pass. Unit coverage verifies the clamped JavaScript command; instrumentation compiles retained selector/slider semantics and a WebView jump/rendered-text regression.

The repository HTTP integration-test step retains 188 JVM tests across 30 suites with zero failures/errors/skips; `testDebugUnitTest`, `lintDebug`, `assembleDebug`, and `assembleDebugAndroidTest` pass. Three `BookOrbitRepositoryIntegrationTest` cases compile into the androidTest APK; connected execution remains pending because adb device enumeration hung/no usable target was available.

The completed Home initial-sync optimization passes 189 JVM tests across 30 suites with zero failures/errors/skips; `testDebugUnitTest`, `lintDebug`, `assembleDebug`, and `assembleDebugAndroidTest` pass. A six-library gated coordinator regression proves the first three nonselected refreshes start, the next three wait, maximum library concurrency is three, every slice merges, and final refresh state clears.

The completed downloaded-book update step passes 196 JVM tests across 31 suites with zero failures/errors/skips; `testDebugUnitTest`, `lintDebug`, `assembleDebug`, and `assembleDebugAndroidTest` pass. Store/model unit coverage exercises version decisions, ebook compatibility, and atomic replacement; compiled Compose and real-repository MockWebServer regressions cover the Update local action plus malformed-replacement preservation/retry. Connected execution and cancellation remain in the manual device matrix.

Audiobook validation remains deferred without a representative sample. Direct OIDC/SSO remains deferred until its provider/redirect contract is confirmed.

## Source of truth

### Options backlog

Interface

- [x] Add a lock-current-orientation toggle.
- [x] Add a haptic-feedback toggle that suppresses supported haptics.
- [x] Add app theme selection: Follow system, Light, or Dark, applied immediately.
- [x] Add default opening screen selection: Home, Library, or Local books, applied on fresh app start.
- [x] Add a Reduce motion/animations accessibility option using immediate catalog jumps.

Data

- [x] Add downloads-over-cellular policy: Always, Never, or Ask for confirmation, with browser-wide start/prompt/block behavior and metered non-Wi-Fi treated as cellular.
- [x] Add storage management showing downloaded/disposable-cache sizes and a confirmed Clear cache action that preserves downloaded books, downloaded-book metadata, settings, progress, and catalog data.
- [x] Add background metadata/cover refresh network policy: Any network, Wi-Fi only, or Disabled; current scheduled cover work uses CONNECTED/UNMETERED constraints and reconfigures immediately. A separate scheduled metadata worker is not yet present.
- [x] Add confirmation before deleting a local copy, enabled by default across native-browser delete entry points.
- [x] Validate lock-current-orientation, default opening screen, Reduce motion, downloads-over-cellular behavior, storage/cache clearing, and delete-local confirmation on a physical device.
- [ ] Validate the haptic perceptibility fix, app theme, and background network policy on a physical device.
- [ ] Decide whether to extend haptic requests beyond Options rows and supported Foundation long presses.

Detailed checkpoint status is tracked in:

- [../CHECKLIST.md](../CHECKLIST.md)
- [ui-ux.md](./ui-ux.md)
