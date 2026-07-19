# UI/UX Workstream

The functional prototype is stable enough for UI/UX work to continue. EPUB embedded-image rendering remains the highest-priority physical verification: the prior target-device build opened chapter text but did not render embedded images, while CBZ/CBR images worked. The code now mounts the entire dedicated extracted EPUB root rather than only the OPF package directory, and automated coverage passes, but the user must verify the new APK before the regression is considered fixed. The manga unsupported-format regression remains fixed in code: bare CBZ/CBR/CB7 formats open the comic reader, with authenticated server pages for online reading and on-device ZIP extraction for offline CBZ/mislabeled ZIP archives. Downloaded CBR/CB7 remains valid but requires a connection for server-side extraction in this build. Validation of CB7 and remaining downloaded-comic formats remains open; offline CBR/CB7 extraction is optional future work. Audiobook validation remains deferred without a representative sample.

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

Detail refinement candidate: book details mirror the reader-relevant content of BookOrbit's main detail page, including identity, synopsis, genres/tags, publication data, identifiers, rating, library, format, and file metadata. Primary actions use compact labeled tiles in an adaptive wrapping row, keeping every action discoverable without horizontal scrolling. The final tile directly exposes the live Mark as read/unread action instead of a three-dot menu. The series eyebrow is a visible navigation affordance into the existing Series detail page, and a dedicated stable Previous/Next row directly below identity/series metadata moves among series books without leaving the detail screen. Genre and Tag chips wrap independently, and the lower hierarchy groups Publication, Identifiers, and Library/file values into compact cards. Tapping the cover opens a dark full-screen viewer, and tapping the displayed cover or pressing Android Back dismisses it. Compose regression coverage exercises the action area, metadata content, cover-viewer tap dismissal, and Series navigation. Series details continue to load the complete server series, authors, read completion, possible gaps, first-book synopsis, and ordered books.

### Checkpoint 4: EPUB reader - ready with current sample content

- Refine reading chrome, chapter navigation, theme controls, typography controls, and distraction-free states.
- Preserve the validated resume, local-image, offline, and progress behavior.
- Test changes against the available EPUB files before merging.

Implementation candidate: EPUB follows Komga's paginated interaction pattern. Reading uses transient controls; left/right outer-quarter taps change pages and the center currently opens one rounded bottom sheet containing the current location, chapters, theme, text size, and independent Top/Bottom/Left/Right percentage sliders. Continue reading dismisses the sheet without leaving the book, while Close book immediately restores cached Browser content (or Loading when no cache exists) and lets persistence, sync, cleanup, and refresh finish in the background. Exposed-book taps or Android Back dismiss the sheet; Android Back exits the reader only when the sheet is closed. The sheet matches Light, Sepia, and Dark reader palettes with verified normal-text contrast. Android's native top status bar remains visible for battery and network indicators with reader-theme-aware contrast, while the bottom navigation bar stays immersive. Fresh books default Top to 30% and Bottom/Left/Right to 15%; saved per-book values remain unchanged. A value of 100% represents one quarter of the relevant viewport dimension. Top/Bottom resize the Android `WebView` outside the EPUB HTML, while Left/Right update the known-good page strip in place. The extraction-root implementation and compiled safe-appassets coverage are complete; physical verification of embedded images is required before the planned lightweight reader chrome or tutorial.

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
- [x] Add an About destination after Options in the More menu with placeholder content for review.

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
- [x] Fix the bare-format unsupported regression and route online CBZ/CBR/CB7 plus offline ZIP/CBZ through the comic reader; general comic reading is target-device validated, while online CBZ/CBR/CB7 and offline downloaded comic formats still need broader validation.
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
- The extraction-root EPUB fix passes the full combined gate: 213 JVM tests across 35 suites with zero failures/errors/skips, lint, debug APK assembly, and Android-test APK assembly. Package parsing and `EpubBook.rootDir` retain the full extraction root, and the WebView regression resolves `../../Images/cover.png` from `OEBPS/Text/chapter.xhtml`, crossing the package-directory boundary while remaining inside the archive. Canonical extraction-root, package, and chapter checks reject any package/chapter path that escapes the archive root. Physical target-device confirmation remains required; compiled coverage alone does not close the regression.
- [x] Retain the full stable #/A–Z jump rail when eligible (Z–A/# descending), rendering unavailable entries with 38%-alpha `onSurfaceVariant`, disabled semantics, content descriptions such as `B unavailable`, and no click action. Existing sort/catalog hiding and grid gutter behavior remain unchanged.
- The full gate passes 209 JVM tests across 34 suites, lint, debug APK assembly, and Android-test APK assembly; `catalogJumpRailLabels` and compiled Library/Series assertions cover unavailable labels.
- [x] Add a bottom Local books shelf to top-level Home using server-wide local titles and to Library Recommended using only titles local to the selected library. Both use deterministic deduplicated alphabetical previews capped at 12 and reuse normal shelf cards/actions/covers; See all opens global or library-scoped Local books with the appropriate title, while More > Local books remains global.
- The full gate passes 210 JVM tests across 34 suites, lint, debug APK assembly, and Android-test APK assembly; compiled coverage exercises the global shelf, global See all, and library-scope exclusion.
- [ ] Optionally add offline client-side RAR/7z extraction for downloaded CBR/CB7; current UX must clearly require a connection without calling a valid archive corrupt.

### Reader controls work order - 2026-07-19

Before the reader-chrome refinement, implement the user's final book-detail action-row decision:

- Use exactly one row with no wrapping, horizontal scrolling, or clipped overflow.
- Read and Preview are always visible, labeled actions.
- When the book is not local, eligible Download is always visible inline as an icon-only action; it is absent when the book is local and is never moved into overflow while eligible.
- Delete local, whenever applicable, is always in the three-dot overlay and never inline.
- Mark as read/unread is inline only when width permits; otherwise it moves into the three-dot overlay.
- Show the three-dot action whenever any applicable action is hidden. At minimum, the row must preserve Read, Preview, eligible Download, and required More at every supported width.
- Preserve active download/update/cancel behavior, but decide and cover the exact state-to-inline/overflow mapping during implementation rather than inventing it here.

Book details must also show canonical 0-100 progress for currently-reading and read/completed states when a real server or local value is known. Unknown progress remains absent, not synthesized. The user must confirm the exact visual placement before this GUI change is implemented.

The subsequent reader refinement adopts Suwayomi's lightweight control hierarchy while retaining the user's explicit Lagrange adaptation:

- A center tap toggles lightweight chrome instead of opening the complete reader-options sheet immediately.
- The top bar contains separate Back and Close buttons plus the book title.
- The left side contains a vertical chapter jump/progress control with previous- and next-chapter actions.
- The bottom contains a Chapter list button and a cog/settings button. The cog is the deliberate route to the existing full reader options; the lightweight surface does not duplicate those settings.
- On initial reader entry/open, show a labeled tap-zone preview as three equal-width, full-height thirds: left Previous in `rgba(255, 114, 118, 0.5)`, center Menu in `rgba(0, 0, 0, 0.5)`, and right Next in `rgba(144, 238, 144, 0.5)`. These are Suwayomi's `RIGHT_LEFT` preview colors at 50% opacity.
- Keep the preview visible for exactly 1 second, then dismiss it automatically. This intentionally overrides Suwayomi's five-second initial preview.

Working order:

1. [ ] Physically verify the implemented extraction-root fix with opened/local and nonlocal Preview EPUB samples, including nested and package-boundary-crossing parent-relative resources; confirm CBZ/CBR images remain unaffected.
2. [ ] Implement and test the exact single-row book-detail action policy above, including responsive behavior and explicit Download/Update/Cancel state mapping.
3. [ ] After the user confirms placement, implement and test the conditional canonical progress percentage without fabricating unknown values.
4. [ ] Implement the lightweight reader chrome, including the separate Back/Close Lagrange header and cog route to full options, then add the exact one-second tap-zone preview so Menu teaches that final center-tap behavior.
5. [ ] Validate accessibility semantics, large text, themes/contrast, narrow/wide layouts, orientation, gestures, Back behavior, tutorial timing, resume/sync, Preview isolation, and offline behavior, then continue remaining media and edge-state validation.

The current wording deliberately limits the tutorial trigger to initial reader entry/open. Persistence scope across books, files, app installs, repeat opens, Preview, and comic readers must be confirmed before implementation rather than inferred.

### Checkpoint 5: Other media readers - partially implemented

- Fullscreen image fitting/footer, tap and swipe navigation, options actions/slider, and Back dismissal/exit ordering are target-device validated. Finish validating authenticated online CBZ/CBR/CB7 page loading/progress and offline downloaded comic formats with the available manga samples.
- Keep downloaded CBR/CB7 available but show the connection requirement for server-side extraction; client-side RAR/7z support is optional future work.
- Defer audiobook-specific UX until a representative sample is available.

### Checkpoint 6: Polish and release validation - after primary screens

- Run accessibility, large-text, rotation, dark/light theme, and small-screen checks.
- Execute Compose instrumentation tests on a connected emulator or device.
- Re-run debug, unit, instrumentation-compile, lint, and release build checks.

## Guardrails

- UI work must not weaken offline behavior, session recovery, reader resume, or progress sync.
- Prefer reusable Compose components and theme tokens over screen-local styling.
- Keep interactive controls accessible and preserve meaningful semantics in tests.
- Treat unavailable media types as deferred validation, not as completed UX work.
