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

- Add integration coverage for login bootstrap and library/book loading
- Add integration coverage around offline queue replay
- Execute Compose instrumentation tests on a connected emulator or device
- Validate server-forced session expiry and return-to-intended-action recovery on a real deployment

### 4. Deferred media-specific validation

- Obtain representative audiobook, PDF, and CBZ files
- Validate offline reopen, restart resume, streaming/range behavior, and progress sync for each format
- Adjust format-specific controls only after their behavior can be exercised on device

### 5. Release readiness

- Complete accessibility, large-text, narrow-screen, rotation, and theme checks
- Run unit, lint, debug, instrumentation-compile, and release build gates
- Confirm no secrets or internal URLs are committed
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
- [x] Show book completion percentage, chapter page progress, and a normalized 1-1000 whole-book location in an always-visible EPUB footer.

### Device validation follow-up and new work order — 2026-07-15

- [x] Validate explicit HTTP server setup and authenticated browsing/reading.
- [x] Validate Mark as read/unread from supported book-card menus.
- [x] Validate Android/BookOrbit progress synchronization in both directions.
- [x] Validate large-library browsing, fast #/A–Z jumps, and airplane-mode behavior; undownloaded books correctly require a connection for full details.
- [x] Validate session-expiry recovery, Series navigation, and Series thumbnails.
- [ ] Add the book context menu to global search results for both long-press and three-dot actions.
- [x] Keep global search results as list rows rather than converting them to poster cards.
- [x] Fix stale Currently Reading state after refresh: explicit `unread` records with no positive progress now discard stale page/position and status timestamps, so a server-confirmed removal cannot be reconstructed as reading activity.
- [x] Reduce Home sync/loading time for large libraries by requesting BookOrbit's 100-book page limit and loading large known page ranges in ordered batches with at most four concurrent requests. Existing cached content remains usable, and unstable totals/duplicates still trigger the full consistency retry.
- [ ] Redesign reader options with separate actions for Continue reading (close options and remain in the book) and Close book (exit the reader).
- [ ] Improve reader-options typography with reliable text/background contrast in Light, Sepia, and Dark modes.
- [x] Allow explicitly entered HTTP BookOrbit server URLs and Android cleartext traffic; bare remote hostnames still default to HTTPS, while bare local development hosts retain HTTP shorthand.
- [x] Add Mark as read and Mark as unread to the book-card three-dot and long-press menus. Read status is written directly while preserving position; unread uses the normal-user reset flow so completed progress cannot immediately restore Read.
- [ ] Redesign book-detail actions as a swipeable/compact action area, using symbols where they improve discoverability without hiding labels.
- [ ] Make the tapped series title in book details navigate to that series detail page.
- [ ] Redesign book-detail genres/tags and the lower metadata section for a more compact, elegant hierarchy.
- [ ] Open the book-detail cover in a full-screen image viewer, dismissible by tapping the image again or pressing Android Back.
- [ ] Redesign the reader options window for clearer hierarchy, spacing, and controls.

## Source of truth

Detailed checkpoint status is tracked in:

- [../CHECKLIST.md](../CHECKLIST.md)
- [ui-ux.md](./ui-ux.md)
