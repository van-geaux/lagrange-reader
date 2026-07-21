# UI/UX Workstream

Version `0.2.7` includes the July 20 reader/detail follow-ups. Reader ownership is isolated from delayed browser updates, chrome has no visible Back action and places labeled Exit/X leftmost, and the tutorial is tap-dismissible with an exact three-second timeout. The automated gate passes; target-device validation remains.

Latest detail feedback: keep compact action spacing while showing Read, Preview, Download, and Delete local labels beside clear icons; cap long book titles at five rows with expand/collapse; keep series name and index visible as separate rows; dismiss the full-screen cover viewer from any screen tap; and support multi-book selection with bulk read/unread actions. Genre chips navigate to paginated filtered Books or Series results, while tags remain informational. Authentication remains native username/password; direct OIDC/SSO is deferred.

Follow-up action feedback is implemented: Read and Preview show visible labels beside clear Play/Visibility icons, Download uses an unmistakable download symbol, and active per-file download progress/status is discoverable from book details. Multi-select headers avoid overlap and prune stale selections. Genre chips navigate to fully paginated server-filtered Books or Series results; book filters use the official singular `genre`/`includesAny`/array contract, with the adjacent `author` relation filter corrected to the same shape. Tags remain informational. Native username/password remains the only authentication flow for now; direct OIDC/SSO is deferred.

Audited implementation status: code and automated verification are complete; genre query compatibility and scope are target-server validated. Download interruption recovery and final responsive density remain open. Direct OIDC/SSO remains an open backlog item.

Latest target-device feedback: Download and Delete local now have visible labels, and a completed download updates the still-open detail immediately. BookOrbit has no verified tag filter, so Tag chips are informational and non-clickable. Red Home/Library Recommended messages now support explicit X dismissal and horizontal swipe dismissal, clearing the message immediately. Recently read now contains only completed books, excludes every ID still in Currently reading, and is capped at 12. The reader footer now shows layout-derived whole-book pages with a calculating fallback; measurement timing/stability still requires device validation. Direct OIDC/SSO is deferred; native username/password remains current.

Latest target-device validation: lock-current-orientation, default opening screen, Reduce motion, downloads-over-cellular behavior, storage/cache clearing, delete-local confirmation, whole-book progress correctness, genre filtering, all five app themes, jump-rail spacing, basic series Previous/Next, and the functional Achievements flow work as intended. The haptic setting and all explicit app haptics were removed by user direction.

Latest implementation: Delete local immediately returns the still-open detail action to Download and refreshes Local books; incomplete Local books summaries recover thumbnails and related metadata from the latest cached rich detail; and Options now dismisses retained book-detail state before opening. These browser/local flows and server-wide Home/Currently Reading aggregation are target-device validated. Comic routing supports online CBZ/CBR/CB7 plus offline ZIP/CBZ and records page progress; fullscreen interactions and online/local CBZ/CBR are target-device validated, while CB7 validation and optional offline RAR/7z extraction remain open. Direct OIDC/SSO remains deferred.

Options backlog

- Interface implementation complete: lock current orientation toggle; one flat app Theme list (Follow system, Light, Charcoal, Warm black, OLED black) applied immediately; default opening screen (Home, Library, Local books) on fresh start; Reduce motion/animations using immediate catalog jumps. Follow system uses Light or Charcoal according to Android, explicit choices remain fixed, and legacy Dark migrates to Charcoal. Orientation locking, default opening screen, Reduce motion, and all five themes are device-validated. The former haptic setting and explicit app haptics are removed.
- Data implementation complete: downloads-over-cellular policy (Always, Never, Ask for confirmation) with browser-wide start/prompt/block behavior; storage management with downloaded/disposable-cache sizes and a confirmed Clear cache action that preserves downloaded books, downloaded-book metadata, settings, progress, and catalog data; background metadata/cover refresh policy (Any network, Wi-Fi only, Disabled) governing current scheduled cover work and future metadata work; confirmation before deleting a local copy, enabled by default. Cellular behavior, cache clearing, delete confirmation, and background network policy are device-validated.

## Checkpoints

### Checkpoint 0: Functional baseline - complete

- Core setup, login, browser, download, offline, sync, and reader flows are implemented.
- Manual device testing confirms EPUB download, offline reopen, progress sync, and last-session restore.
- JVM tests pass and the Compose instrumentation target compiles.
- UI/UX discussion can start now without waiting for other media samples.

### Checkpoint 1: Product direction and design system - ready now

- Agree on the visual character, density, and accessibility goals.
- Define color, typography, spacing, shape, elevation, and motion tokens.
- Confirm the information hierarchy and navigation model for setup, login, library browsing, and reading.
- Coding can begin with theme tokens and shared app-shell components once the direction is agreed.

Implementation candidate: an editorial-observatory direction is now coded for review, using navy ink, warm paper surfaces, amber accents, serif display typography, restrained rounded geometry, and shared top-bar/status components. Setup, login, and browser surfaces use the candidate without changing flow behavior. Checkpoint 1 remains open until the direction is reviewed on device.

### Checkpoint 2: Setup, login, and app shell - ready after Checkpoint 1

- Refine server setup guidance, validation feedback, and retry states.
- Improve the login container, server identity, progress feedback, and change-server action.
- Establish consistent top bars, loading states, error surfaces, and offline/session indicators.

### Checkpoint 3: Library browser - ready after shared components exist

- Rework library selection, book-card hierarchy, download state, and primary actions.
- Add intentional empty, loading, offline, and failure presentations.
- Keep actions usable with long titles, large font scales, and narrow phone widths.

The first browser-shell candidate opened on a native Home feed with a hamburger drawer and integrated search. Device review found that interaction too web-like, so it is superseded by an approved Plex-inspired direction: standard Android status-bar space, bottom navigation for primary destinations, a top-level Libraries view with a library-change control, and a dedicated search layer opened from an icon. Home puts Currently reading first, while completed/recently read books remain available separately. Home shelves aggregate every library on the connected server; selected-library scoping belongs to Libraries Recommended/Browse, not Home.

The first Home shelf renders as Currently reading. It recognizes active percentage, page, position, label, or timestamped progress across all server libraries and excludes completed books; Recently read books remains a separate history shelf. Cross-library restoration is target-device/server validated.

Current refinement: book poster cards use roughly half of the first candidate size, search uses BookOrbit's global query endpoint, and covers load through the authenticated API client. Series shelf cards open an ordered series detail list. Book selections open a detail screen with Read/Continue, Download, and local-copy actions instead of launching content immediately. The browser shell now uses Home, Libraries, and More in a bottom bar; More expands to Series, Authors, Local books, and About, while the profile menu exposes Options and Achievements, then Change server immediately above Log out/Sign in. The Home top bar carries the launcher mark, while Library uses the selected library name as its selector and separates Recommended shelves from complete Room-cached Browse content.

Detail refinement: book details mirror the reader-relevant BookOrbit metadata and use one 46 dp-high action row. Read/Preview stay labeled; a nonlocal icon slot maps Download/Retry/Cancel and remains disabled in place when unavailable; local Delete and Update/Cancel update live in More. Mark as read/unread uses current typography/font-scale measurements and remains inline only if the complete row fits. More appears whenever anything is hidden, and only Read/Preview become weighted at extreme widths. The series eyebrow/neighbor controls, independently wrapped Genre/Tag chips, compact metadata cards, and full-screen dismissible cover viewer remain unchanged.

### Checkpoint 4: EPUB reader - Readium migration device-functional, broader validation pending

- Refine reading chrome, chapter navigation, theme controls, typography controls, and distraction-free states.
- Preserve the validated resume, local-image, offline, and progress behavior.
- Test changes against the available EPUB files before merging.

Implemented baseline: normal EPUB Read/Preview and comics use Readium with shared lightweight chrome layered over the navigators. Every activity entry first shows the Previous/Menu/Next tutorial for exactly 3,000 ms after rendering readiness, or until any consumed tutorial-region tap dismisses it; once dismissed, outer 25% taps turn pages and center toggles chrome. Existing footer, system bars, orientation, keep-awake, exact locator resume, progress, and Preview isolation remain.

### Device feedback workplan

- [x] Make Currently reading the first Home shelf and retain Recently read books as a distinct history shelf.
- [x] Replace the hamburger/drawer interaction with Plex-inspired bottom navigation and a top library selector/change action.
- [x] Replace Home's large persistent search field with a search icon and dedicated search layer.
- [x] Keep the Android status bar visible and add intentional Home top spacing.
- [x] Add reader padding controls and a larger default padding value.
- [x] Add independent Top and Bottom reader padding controls with repagination.
- [x] Replace reader padding presets with independent percentage sliders for all four edges; fresh books default Top to 30% and Bottom/Left/Right to 15%, without overwriting saved per-book values.
- [x] Replace the launch spinner with the branded adaptive-icon loading state; physical-device validation remains open.

### Latest device feedback workplan — 2026-07-13

- [x] Restore the exact in-chapter EPUB page on reopen and restart; physical-device validation remains required.
- [x] Use the adaptive poster-card grid for library books, matching Series and Authors; physical-device validation remains required.
- [x] Rename the visible app brand to Lagrange and show the subtitle `a BookOrbit reader`; physical-device branding validation remains required.
- [x] Add an About destination with placeholder content for review; its original More-menu placement was later superseded by the profile-dropdown revision.

### Continued device feedback workplan — 2026-07-13

- [x] Show the `a BookOrbit reader` subtitle only on the splash/loading presentation; omit it from the opened-app top bar and About screen.
- [x] Add a Libraries series-collapse control that reduces each series to a representative card and restores the full book grid.
- [x] Add Local books immediately before Options in More, using the shared poster-card grid for valid downloaded files.

### Library refinement workplan — 2026-07-13

- [x] Reduce book poster-card sizing to 75% of the current size, preserving the adaptive grid and readable metadata.
- [x] Add a paginated Browse tab with automatic near-end loading for library books (superseded by complete Room caching).
- [x] Keep the Lagrange logo on Home only; use the selected library name as the tappable selector in Library.
- [x] Split Library into Recommended Home-style shelves and Browse's complete book grid.

### Final browser polish workplan — 2026-07-13

- [x] Reduce Library, Series, and Authors poster-card grids enough to create an additional compact phone column where possible.
- [x] Add a downward-triangle affordance beside the tappable Library name selector.
- [x] Replace the Library refresh action with swipe-down-to-refresh over Recommended and Browse content.
- [x] Persist cover thumbnails locally and prefer the local cache for Local books.

### Follow-up browser polish — 2026-07-13

- [x] Use Expand series as the inverse action after a series has been collapsed.
- [x] Keep cover loading responsive and retry transient/decode failures instead of permanently pinning a first-letter placeholder.
- [x] Keep the More sheet's final action above the Android navigation area with explicit bottom inset spacing.
- [x] Show full Library book/series totals from the complete Browse catalog.
- [x] Make the top-left Libraries title return from the picker to the selected library page.
- [x] Replace Browse's Load more action with automatic near-end page loading (superseded by complete Room caching).
- [x] Add a right-side title-initial jump rail while preserving the left side for normal scrolling.
- [x] Show the full stable #/A–Z vocabulary on eligible Library and Series jump rails (Z–A/# descending); preserve exact represented targets and expose missing labels as disabled/unavailable without forward fallback.
- [x] Order collapsed series by series name and restore the nearest scroll anchor after collapse/expand.
- [x] Route background authenticated-load expiry through login recovery instead of silently returning empty content.
- [x] Show downloaded-book details from cached metadata without requiring a server request.
- [x] Make reader edge padding changes apply while the options surface remains open; the Close-only dismissal behavior was superseded by later visible-content dismissal feedback.
- [x] Restore Home Continue reading for tolerant active-progress payloads.
- [x] Remove the duplicate Home/library heading from Library Recommended.
- [x] Move Options into the profile menu above Log out and raise the More sheet with additional inset spacing.
- [x] Avoid showing a partial Browse series count before lazy loading completes (superseded by complete Room caching).

### Latest device feedback workplan - 2026-07-13 (session, catalog, and density follow-up)

- [x] Prioritize session persistence with refresh-cookie renewal and one authenticated-request retry before returning to Login; validate the actual endpoint on the target server.
- [x] Retry Series and Authors catalog thumbnails, resolve Series `coverBookIds` through representative book thumbnails, and cache successful catalog image bytes for fast scroll-back rendering.
- [x] Merge reader progress into Home immediately so Continue reading appears without waiting for a full browser reload or a later page.
- [x] Remove redundant in-content Home/library headings from Home, Library Browse, Series, Authors, and Local books.
- [x] Reduce shared app typography by approximately 10% and organize book card metadata as title, optional series, and series index rows.
- [x] Validate visible reader padding during slider movement on the target device; the last known-good visible-overflow strip is restored and Top/Bottom resize the WebView outside the EPUB HTML, with JVM plus Android WebView regression coverage.
- [x] Restore and validate visible EPUB chapter content before validating reader padding changes on the device.
- [x] Persist independent reader padding per book/file and flush the final progress update before returning to the browser.
- [x] Add server-aligned filter controls to Library Browse and Series, with the same title/author/series, read-status, format, and sort controls applied to Local books.
- [ ] Validate catalog thumbnails, typography/card density, and metadata wrapping on the physical device; session-expiry recovery is already validated.

### Cache-first Browse and exact navigation — 2026-07-13

- [x] Render a complete selected-library metadata cache before cold-start network checks finish.
- [x] Keep cached cards interactive while refresh reconciles all server pages, and keep the prior complete catalog if refresh is interrupted.
- [x] Remove automatic end-of-grid page loading and run Browse filters/sorts against the complete local catalog.
- [x] Use BookOrbit's absolute jump-bucket indexes for the unfiltered default listing; use complete local indexes for filtered/title/author and collapsed-series views.
- [x] Keep eligible letter rails hidden until the complete result is ready, then show full stable ordered labels with only represented targets enabled and no missing-letter forward fallback.
- [ ] Validate first-sync messaging, instant reopen, refresh continuity, exact jumps, filters, and large-library scroll performance on the target device.

### Large-library thumbnail/detail cache - 2026-07-13

- [x] Cancel a card's authenticated thumbnail request when it leaves composition instead of retaining global scroll-era jobs.
- [x] Persist versioned thumbnails and warm all missing/changed selected-library covers in low-priority unmetered WorkManager batches.
- [x] Cache rich detail data for every opened title and refresh it only after the catalog version changes.
- [x] Keep lazy Compose item rendering for bounded memory while removing network-backed lazy catalog paging.
- [ ] Validate thumbnail warm-up, far-letter offline covers, rapid-scroll visible-card priority, and repeat detail-open latency with the 5k-book library.

### New user feedback workplan - 2026-07-15

- [x] Provide both a visible overflow menu and long-press menu for removing a title from Currently reading; the action uses normal-user APIs to clear primary/current BookOrbit progress, mark the title unread, clear local reading progress, and remains unavailable in an offline snapshot.
- [x] Make On Deck represent series progression: show the next unread volume only when a prior volume is completely read, and hide it once that volume is Currently reading.
- [x] Add swipe-to-refresh to Home using the established Libraries pull indicator and refresh behavior.
- [x] Ensure Series cards display representative book thumbnails from BookOrbit's `coverBookIds` response field.
- [x] Give Series complete, deduplicated in-memory results and #/A–Z or Z–A navigation for Name sorting instead of Load more.
- [x] Include the series book count on collapsed series cards in Libraries.
- [x] Persist the selected light/dark/sepia reader background theme per reader session and across app restarts.

### Additional reader feedback workplan - 2026-07-15

- [x] Keep the reading screen open indefinitely while the user is idle unless the user explicitly leaves it or the system interrupts it.
- [x] Use a bottom sheet with distinct Continue reading and Close book actions, while visible book content still dismisses options without leaving the reader.
- [x] Show battery and signal indicators in the reader's top-right area through Android's native status bar.
- [x] Keep an always-visible EPUB bottom status area showing weighted completion, chapter page progress, and measured whole-book current/total pages with a calculating fallback.

### Device validation follow-up and new UI work order - 2026-07-15

- [x] Resolve stale Currently Reading state after server-confirmed removal by suppressing non-positive unread progress metadata during Home refresh.
- [x] Reduce perceived Home sync latency for large libraries with 100-book pages and bounded four-request catalog batches while preserving immediate interaction with cached content.
- [x] Give reader options distinct Continue reading and Close book actions.
- [x] Establish theme-safe reader-options typography contrast across Light, Sepia, and Dark.
- [x] Allow explicit HTTP server entry when required by the configured BookOrbit deployment while retaining HTTPS as the bare remote-host default.
- [x] Add Mark as read and Mark as unread to shared three-dot/long-press actions on Home shelves and individual Library, Series, Author, and Local Books posters; keep collapsed series cards as series navigation rather than single-book actions.
- [x] Keep global search results as list rows and add the same Mark as read/unread menu through both a visible three-dot action and long-press.
- [x] Replace oversized book-detail actions with compact labeled tiles in an adaptive wrapping row that never hides trailing actions off-screen.
- [x] Make the book-detail series title a navigation affordance to series details.
- [x] Redesign the book-detail genres/tags and lower metadata hierarchy.
- [x] Add a full-screen, tap-to-dismiss/back-dismiss cover viewer from book details.
- [x] Redesign the reader options window as a rounded bottom sheet with clearer hierarchy, spacing, and grouped controls.

### Latest target-device work order - 2026-07-17

- [x] Validate lock-current-orientation, default opening screen, Reduce motion, cellular download behavior, storage/cache clearing, delete-local confirmation, and whole-book progress correctness.
- [x] Remove the former haptic-feedback setting, preference storage, custom provider, manual requests, and obsolete tests by user direction.
- [x] After Delete local succeeds, update the still-open detail actions and offline availability immediately; target-device validation passed.
- [x] Show cached Local books thumbnails online and offline by recovering incomplete summaries from the latest rich-detail cache; target-device validation passed.
- [x] Ensure the main Options destination opens from book details instead of retained detail state masking it; target-device validation passed.
- [x] Restore genuinely in-progress titles from any server library to Currently reading; target-device/server validation passed.
- [x] Aggregate Home shelves across all server libraries while retaining selected-library scope in Libraries Recommended/Browse; target-device/server validation passed.
- [x] Reduce server-wide Home loading latency by refreshing nonselected libraries in ordered batches of at most three after the selected library becomes current; merge completed slices incrementally and retain cached failures with partial-cache messaging. Device/server latency and load validation remain pending.
- [x] Migrate local/readable CBZ to Readium 3.0.2 and prepare connected CBR/CB7 as reusable cached CBZ publications from authenticated BookOrbit pages; offline downloaded CBR/CB7 remains explicitly unsupported without the server.
- [x] Match the novel reader's fullscreen interaction model in comics: outer tap zones and horizontal swipes change pages, center tap opens options, exposed content or Back dismisses options first, and Back exits only when options are closed; target-device validation passed.
- [x] Replace the former three-dot overflow with a directly labeled live Mark as read/unread tile in the wrapping action row, using selected-library/server-wide Home status and remaining disabled for offline snapshots.
- [x] Make Close book dismiss the reader immediately to cached Browser state (or Loading without a cache), merge the latest visible progress into Home/Library, and continue persistence, sync, cleanup, and refresh in the background; target-device validation passed.
- [x] Restore missing foreground book-detail covers such as `your name.` by trying normalized explicit metadata before the canonical BookOrbit thumbnail endpoint; physical-device/server validation remains pending.
- [x] Add the approved current-chapter page slider beneath the unchanged Choose chapter/chip controls. Show `Page X of Y`, jump while dragging, disable for single-page chapters, and never cross a chapter boundary; target-device validation passed.
- [x] Show an online-only Update local action when the server catalog version is newer (or once for legacy versionless downloads), reusing cellular confirmation and update-specific progress/cancel/retry wording; target-device validation passed.
- [x] Correct book genre filtering to BookOrbit's official singular `genre`/`includesAny`/array contract and apply the analogous `author` relation fix; target-server validation passed.
- [x] Add the approved dedicated Previous/Next row directly below book identity/series metadata. It uses transparent borderless 46 dp controls matching detail actions, retains the first complete series load across adjacent navigation, and is target-device validated; additional responsive, long-title, loading, and offline edge states remain pending.
- [x] Replace the old app Dark option with one flat ordered list: Follow system, Light, Charcoal, Warm black, OLED black. All themes, detailed contrast, and system bars work on the target device.
- [x] Put Change server with a swap icon immediately above Log out/Sign in in the profile menu. The current-URL no-op plus reachable/unreachable replacement, confirmation, session clearing, and recovery flows are target-device validated.
- [x] Add a functional profile Achievements destination and validate its server data on the target device. The poster-grid presentation works but is superseded by the compact-card follow-up below because the favicon-like symbols do not justify large poster tiles.
- [x] Prevent visible Library and Series jump rails from covering trailing grid cards. Reserve 32 dp trailing grid padding only while the shared 20 dp rail is visible (20 dp rail + 4 dp edge + 8 dp separation); otherwise retain 16 dp full-width padding. Target-device spacing validation passed; broader responsive checks remain pending.
- [x] Make same-URL Change server submission normalize, close silently, and leave state unchanged.
- [x] Remove missing-letter forward fallback from Library and Series rails; the completed disabled-letter follow-up below supersedes the earlier represented-only presentation.
- [x] Raise the fresh EPUB Top margin default to 30% while retaining 15% Bottom/Left/Right and all saved per-book values.
- [x] Prevent the debug pending-progress count from remaining stuck on stale/deleted non-audio file IDs: remap one 404 to the current primary file, or acknowledge terminal INVALID targets.
- [x] Replace Achievement posters with compact adaptive information cards: 260 dp minimum width, 22 dp server-driven symbol immediately before the title, lock/unlock state at the title row's end, and description/category/rarity plus progress/date inside the card.
- [x] Make every book-detail action discoverable on narrow screens with a wrapping `FlowRow`; keep labels for every context action and expose Mark as read/unread directly.
- [x] Restore EPUB embedded images through `WebViewAssetLoader` with safe nested/encoded appassets base URLs, one extracted root shared by visible/hidden renderers, and broad file/content access disabled. Fix nonlocal EPUB/PDF Preview by preparing an authenticated temporary reader copy without applying comic archive detection.
- [x] Migrate both normal EPUB Read and Preview to Readium, then retain that behavior through the 3.0.2 comic-compatible upgrade. The S24 normal-Read path and restored controls have a successful functional report; exact resume/progress and the broader regression matrix remain explicit follow-up checks.
- [x] Retain the full stable #/A–Z jump rail when eligible (Z–A/# descending), rendering unavailable entries with 38%-alpha `onSurfaceVariant`, disabled semantics, content descriptions such as `B unavailable`, and no click action. Existing sort/catalog hiding and grid gutter behavior remain unchanged.
- The full gate passes 209 JVM tests across 34 suites, lint, debug APK assembly, and Android-test APK assembly; `catalogJumpRailLabels` and compiled Library/Series assertions cover unavailable labels.
- [x] Add a bottom Local books shelf to top-level Home using server-wide local titles and to Library Recommended using only titles local to the selected library. Both use deterministic deduplicated alphabetical previews capped at 12 and reuse normal shelf cards/actions/covers; See all opens global or library-scoped Local books with the appropriate title, while More > Local books remains global.
- The full gate passes 210 JVM tests across 34 suites, lint, debug APK assembly, and Android-test APK assembly; compiled coverage exercises the global shelf, global See all, and library-scope exclusion.
- [ ] Optionally add offline client-side RAR/7z extraction for downloaded CBR/CB7; current UX must clearly require a connection without calling a valid archive corrupt.

### Reader controls work order - 2026-07-19

The user's final book-detail action-row decision is implemented:

- Use exactly one row with no wrapping, horizontal scrolling, or clipped overflow.
- Read and Preview are always visible, labeled actions.
- When the book is not local, one 46 dp inline slot maps to Download, Retry download, or Cancel download and stays present but disabled when unavailable/offline. It disappears once local.
- Delete local, whenever applicable, is always in the three-dot overlay and never inline.
- Mark as read/unread is inline only when width permits; otherwise it moves into the three-dot overlay.
- Show the three-dot action whenever any applicable action is hidden. At minimum, the row must preserve Read, Preview, eligible Download, and required More at every supported width.
- Local Update local or Cancel update shares More with Delete local. Status copy directs local update cancellation/retry through More. Only Read/Preview compact through weights at extreme widths.

Book details now show the selected compact primary progress line directly above actions. Finite canonical progress is clamped to 0–100 and displayed with up to two decimals. Opened 0% is valid when activity exists; explicit unread-reset 0% omits the line. Unknown percentages retain status-only `Reading`/`Read`, and identity metadata retains only availability/format to avoid duplication.

The Suwayomi-inspired lightweight hierarchy is implemented with the user's selected Lagrange adaptation:

- A center tap toggles lightweight chrome instead of opening the complete reader-options sheet immediately.
- The status-bar-safe top bar contains leftmost labeled Exit/X and the book title, with no visible Back action. Tapping the reading surface/center scrim dismisses chrome.
- The right side contains an approximately 75%-height page rail. Its arrows move one page for EPUB and comics; EPUB adds a second previous/next arrow pair for chapter movement.
- The bottom contains a Chapter list button and a cog/settings button. The cog is the deliberate route to the existing full reader options; the lightweight surface does not duplicate those settings.
- On initial reader entry/open, show a labeled tap-zone preview as three equal-width, full-height thirds: left Previous in `rgba(255, 114, 118, 0.5)`, center Menu in `rgba(0, 0, 0, 0.5)`, and right Next in `rgba(144, 238, 144, 0.5)`. These are Suwayomi's `RIGHT_LEFT` preview colors at 50% opacity.
- Keep the preview visible for exactly 2 seconds, then dismiss it automatically. This intentionally overrides Suwayomi's five-second initial preview.

Working order:

1. [ ] Install version 0.2.7 on the Samsung Galaxy S24 and validate the action row across normal/large text, narrow/wide widths, portrait/landscape, local/nonlocal, online/offline, Download/Retry/Cancel, Update/Cancel update, Delete, and Mark read/unread states. Confirm no wrapping, scrolling, clipping, or missing required action. Keep comic validation in the remaining device queue.
2. [x] Implement and test the exact single-row policy, including inline nonlocal Download/Retry/Cancel, local Delete/Update/Cancel update in More, typography-aware Mark placement, disabled transfer-slot stability, and extreme-width compaction. Physical S24 validation remains pending.
3. [x] Implement option A with canonical formatting, zero/reset distinction, unknown status-only output, identity de-duplication, canonical BrowserState precedence, and immediate read-to-unread removal. Physical S24 validation remains pending.
4. [x] Implement lightweight chrome with leftmost labeled Exit/X and no visible Back action, a right-side page rail, separate EPUB chapter arrows, outer list/cog routes, theme-aware status-inset-safe bars, surface/scrim dismissal, landscape adaptation, and layered Android Back dismissal.
   [x] Remove duplicate chapter/page selectors from cog options and retain the outer list button as the only chapter/page picker.
   [x] Implement the exact 3,000 ms tap-zone preview on every EPUB/comic Read and Preview entry, above all UI; every region consumes taps and dismisses immediately.
   [x] Align book-detail More to the right/trailing edge whenever it is shown and space permits.
5. [ ] Validate accessibility semantics, large text, themes/contrast, narrow/wide layouts, orientation, gestures, Back behavior, tutorial timing, resume/sync, Preview isolation, and offline behavior, then continue remaining media and edge-state validation.

The implemented trigger is every initial reader activity entry/open for EPUB and comics, including both Read and Preview; it does not persist a seen-state across repeat opens, books, files, or installs.

### Pending reader stability/interaction revision - 2026-07-20

- [x] Identify why readers are sometimes replaced by Home during sync/refresh: background browser updates call the navigation-owning `showBrowser()` after reader launch, with no reader ownership/generation guard.
- [x] Split browser snapshot updates from screen navigation. Guarded snapshot updates preserve `ReaderLoading`/`Reader`, explicit reader failure/close retains deliberate navigation, and focused delayed-refresh/download regressions pass.
- [x] Remove Back from lightweight chrome and place labeled Exit/X at the left edge. Center/content tap remains the dismiss-to-reading interaction; Android Back continues to dismiss overlays before exiting.
- [x] Show the tutorial for exactly three seconds unless the user taps anywhere on the tutorial layer, in which case consume that tap and dismiss immediately.
- [ ] Validate the final layout and behavior across EPUB/comic Read and Preview, status-bar insets, large text, portrait/landscape, themes, refresh/sync activity, and repeated opens.

Target-device feedback confirms reader ownership during sync/refresh/download activity, Exit behavior, and tutorial timeout/tap dismissal.

### Reader tutorial, Series grouping, and profile-menu revision - 2026-07-20

- [x] Remove the tutorial-label background and enlarge Previous/Menu/Next text to 28 sp, approximately twice its prior size. Focused JVM and Android-test compilation pass; target-device readability validation passes.
- [x] Add globally persistent, mutually exclusive Library and File format grouping chips below Series-detail Genres. Library is the default and tapping the active chip leaves both inactive. Scoped per-library Series requests restore ownership omitted by unscoped BookCard payloads, so cross-library Library sections separate correctly. Each section label shares its row with a trailing divider while books retain series-index order. Focused JVM and Android-test compilation pass; target-device validation confirms cross-library separation, persistence, and the label/divider layout.
- [x] Order the profile dropdown as Achievements, Options with cogwheel, About, divider, Change server, and Log out/Sign in; remove About from More. Compiled Compose coverage passes; target-device feedback confirms the menu ordering. Cog icon, divider semantics, and About routing remain separately unvalidated.
- [ ] Validate accessibility, large text, the Options cog icon, profile divider semantics, and About routing.

Automated gate: 259 JVM tests across 44 suites, lint, debug APK assembly, and Android-test APK assembly pass. Tutorial readability, Series grouping/persistence, section label/divider layout, profile-menu ordering, and hardened audiobook opening/playback are target-device validated; audio relaunch, authenticated Preview, the revised audio layout, icon/divider/About, and accessibility checks remain open on device.

### Readium, library cover aspect, and persistent audiobook player - 2026-07-20

- [x] EPUB and CBZ work through Readium on the target device. Connected CBR also works after BookOrbit page extraction is packaged as cached CBZ for Readium. The local `sample/86 Volume 01/` fixture contains a 489,114,453-byte M4B and companion metadata with 17 chapter ranges.
- [ ] Use one audited Readium publication path for every supported ebook, PDF, audiobook, and comic format. Directly open supported publications; explicitly normalize unsupported source containers first, as with CBR-to-CBZ. Do not keep visually or behaviorally divergent legacy readers once parity is established.
- [ ] Respect each BookOrbit library's `coverAspectRatio`: 2/3 cards remain portrait and 1/1 cards are genuinely square with no top/bottom padding. Resolve the shape from each book's owning library everywhere, including mixed-library Home, Search, Series, Authors, Local books, and player surfaces.
- [x] Build persistent Readium audio with an application-scoped controller. It samples progress every 1.5 seconds independently of UI/background navigation; Preview is isolated, explicit Close publishes final normal progress and stops the service, Readium handles focus/noisy output, and Android 13 notification permission is requested on first playback.
- [x] Keep one compact player above browser bottom navigation and across separate Readium EPUB/comic activities until explicitly closed. Normal Read/Preview returns to Browser with playback retained. The Audiobookshelf-inspired small player shows tappable cover-to-detail, title/author, elapsed/remaining seek slider, Replay 10, play/pause, Forward 30, BookOrbit chapter picker, 0.75/1/1.25/1.5/2× speed, and Close. Audiobook detail says Play. It never expands and there is no fullscreen audiobook surface.
- [x] Load audiobook metadata before preparation and retain it across active-reader/detail caches. Authenticated online Preview downloads a temporary audio copy and remains progress-isolated; `CookieManager` clearing is main-thread confined.
- [x] Fix the actual M4B launch crash by keeping parsing on IO and moving Readium player/navigator/session/play/close work to the main dispatcher. The pushed fixture opens, plays with active media session/foreground service/audio focus, and closes under instrumentation. Failed preparation clears stale reader state and returns to the browser instead of entering a restart loop.
- [x] Fix the July 21 black-screen/perpetual-Preparing regression by retaining the exact tapped audiobook identity during chapter enrichment, invalidating old potentially partial reader-cache entries, staging and atomically promoting only complete readable copies, explicitly auto-creating/failing service binding, and bounding preparation/cleanup. Interrupted Preview and the real M4B session-delivery connected regressions pass.
- [x] Target-device feedback confirms the hardened audiobook opening/playback flow works well.
- [x] Keep active compact audio from covering separate EPUB/comic readers: their full content/chrome/options/tutorial/footer viewport ends above the measured player and expands on Close. When the main app does not show regular bottom navigation, keep Browser/detail bottom content and non-Browser overlays above Android navigation buttons. Focused connected coverage validates a 240 px reserve and restoration.
- [x] On local-only and authenticated startup, never turn persisted AUDIO resume metadata into the transient Reader/Preparing screen. Bootstrap Browser and let the surviving service/controller supply the compact player. The focused coordinator regression fails before the fix and passes afterward.
- [ ] Validate relaunch on the user device: play audio, leave or close the app without closing the player, reopen, and confirm Browser plus compact player appears without Preparing. Also validate authenticated Preview and the revised compact layout, then notification/lock-screen/headset/Bluetooth controls, offline/interruption behavior, accessibility/responsive layout, and service/process recreation.
- [ ] Validate square/portrait and mixed-library layouts plus background audio across process/service recreation, interruptions, offline/online transitions, progress sync, Preview isolation, accessibility, large text, orientation, themes, and every migrated format.

### Checkpoint 5: Other media readers - Readium comics implemented, device validation pending

- Readium 3.0.2 retains fullscreen image fitting/footer, 25% edge navigation, the outer page rail/list, leftmost labeled Exit/X with no visible Back action, surface/scrim dismissal, exact normal locator resume, Preview isolation, orientation lock, keep-awake, and dark system bars. Validate the revised flow on the target device.
- Keep downloaded CBR/CB7 available but show the connection requirement for server-side extraction; client-side RAR/7z support is optional future work.
- Use the available local M4B/chapter-metadata fixture for audiobook-specific UX implementation and validation.

### Checkpoint 6: Polish and release validation - after primary screens

- Run accessibility, large-text, rotation, dark/light theme, and small-screen checks.
- Execute Compose instrumentation tests on a connected emulator or device.
- Re-run debug, unit, instrumentation-compile, lint, and release build checks.

## Guardrails

- UI work must not weaken offline behavior, session recovery, reader resume, or progress sync.
- Prefer reusable Compose components and theme tokens over screen-local styling.
- Keep interactive controls accessible and preserve meaningful semantics in tests.
- Treat unavailable media types as deferred validation, not as completed UX work.
