# Lagrange Reader Checklist

Use this as the working checklist for `Lagrange Reader`. Items already completed are checked.

## 0. Repo And Build Baseline

- [x] Create GitHub repository `lagrange-reader`
- [x] Initialize local git repository on `main`
- [x] Configure GitHub remote
- [x] Push initial project scaffold
- [x] Remove sensitive internal note before publishing
- [x] Make repository public
- [x] Restore SSH remote and verify SSH auth
- [x] Add `README.md`
- [x] Add Android `.gitignore`
- [x] Add Gradle wrapper
- [x] Install JDK 17 on this machine
- [x] Install Android SDK command-line tools
- [x] Install required Android SDK packages
- [x] Configure local SDK path
- [x] Make `assembleDebug` pass on this machine

## 1. Project Foundation

- [x] Create Android app module
- [x] Set Kotlin + Compose app structure
- [x] Add application manifest, theme, and resources
- [x] Add basic app coordinator/navigation state
- [x] Add repository/data layer scaffold
- [x] Add proper package/version naming policy
- [x] Add app icon and production branding assets
- [x] Add release signing strategy
- [x] Add CI workflow for debug build
- [x] Add baseline static quality checks

## 2. Server Connection Flow

- [x] First-launch screen for server URL entry
- [x] Persist selected server locally
- [x] Support changing server from login flow
- [x] Validate server reachability before entering login
- [x] Handle malformed URL, unreachable host, TLS failure, and redirect edge cases
- [x] Add clear server-connection error UI states
- [x] Add retry and recovery behavior for server failures
- [x] Allow HTTP server URLs when explicitly configured by the user

## 3. Authentication Flow

- [x] Open server login page inside app
- [x] Share session cookies between WebView and API client
- [x] Verify server-side login contract against the live BookOrbit server
- [x] Verify native login completion through authenticated `/api/v1/auth/me` before coordinator resume
- [x] Confirm session persistence after app restart
- [x] Keep explicit Sign in on the login screen instead of bouncing to cached Home
- [x] Confirm explicit sign-out and session-expiry recovery behavior on a physical device
- [ ] Validate OIDC login flow on a real OIDC-enabled BookOrbit server
- [x] Add explicit authenticated-user bootstrap check after login
- [x] Add clean logout/reset session behavior
- [x] Persist the login access token and attach it to authenticated API/media requests

## 4. Library Browsing

- [x] Load libraries from API
- [x] Select a library
- [x] Load books for selected library
- [x] Display book list in app
- [x] Confirm real API payload parsing against live BookOrbit responses
- [x] Harden parsing for nullable/missing fields
- [x] Add loading, empty, and error states for libraries
- [x] Add loading, empty, and error states for books
- [x] Add pull-to-refresh or equivalent refresh UX
- [x] Add server-aligned filters to Library Browse and Series, with matching local filters for Local books
- [x] Cache last successful library/book snapshot locally

## 5. Book Metadata Mapping

- [x] Create `book -> fileId` aware model structure
- [x] Infer media type from format hints
- [x] Include bare CBZ/CBR/CB7 tokens in media inference, catalog filters, and download extension selection
- [x] Track local download path per file
- [x] Validate actual BookOrbit schema for ebooks
- [x] Validate actual BookOrbit schema for audiobooks
- [x] Confirm multiple-file book handling strategy
- [x] Confirm cover image handling strategy
- [x] Normalize progress labels from real server responses

## 6. Streaming Read / Listen

- [x] Build stream URL from `fileId`
- [x] Open audio stream with ExoPlayer
- [x] Open PDF locally with a basic renderer
- [x] Add generic fallback WebView/file path for unsupported formats (later superseded and removed from routing in favor of Readium or explicit unsupported/conversion guidance)
- [ ] Verify streaming endpoint behavior with authenticated session
- [ ] Confirm byte-range support and resume behavior for audio
- [x] Replace generic ebook fallback with real EPUB reader
- [x] Add comic/CBZ reader support if BookOrbit exposes comic files
- [x] Fix the manga unsupported-format regression by recognizing bare CBZ/CBR/CB7 format tokens and routing them to the comic reader
- [x] Support authenticated server-page reading for CBZ/CBR/CB7 and offline extraction for local ZIP comics, including mislabeled ZIP archives
- [x] Give the comic reader the novel reader's fullscreen interaction model: left/right tap zones and horizontal swipes change pages, center tap opens reader options, exposed-content tap or Back dismisses options first, and Back exits only when options are closed; target-device validation passed
- [x] Validate the general comic reading flow on the target device; comic books work correctly in current testing
- [ ] Optionally add client-side offline RAR/7z extraction for downloaded CBR/CB7; current handling requires a server connection without deleting valid archives as corrupt
- [x] Validate online and local/downloaded CBZ/CBR reading, page navigation, and progress on the target device/server; RAR-backed local CBR continues to use server-side extraction when required
- [ ] Validate online and downloaded CB7 reading on a physical device/server
- [x] Add proper in-reader loading/error states
- [x] Add resume-from-last-position when streaming
- [x] Ensure nonlocal content uses the appropriate authenticated stream/page route, while EPUB/PDF prepares a temporary reader copy; comic-specific archive detection must not gate ebook/PDF preparation
- [x] Route every EPUB launch through `ReadiumEpubReaderActivity` and every locally readable comic through `ReadiumComicReaderActivity` on Readium 3.0.2; PDF and audio were unchanged at this checkpoint and migrated in later work-order steps
- [x] Build and reuse a cached CBZ from authenticated BookOrbit pages for connected CBR/CB7 before opening Readium; keep offline downloaded CBR/CB7 explicitly server-required

## 7. Download For Offline Use

- [x] Download file from API to app-local storage
- [x] Persist download record locally
- [x] Expose downloaded status in UI
- [x] Verify large-download behavior on real content
- [x] Add progress indicator for active downloads
- [x] Add retry/cancel behavior for failed downloads
- [x] Validate file naming and extension handling for all supported formats
- [x] Add storage-space failure handling
- [x] Add download integrity checks
- [x] Add delete-local-copy action
- [x] Reconcile an open book-detail screen immediately after Delete local succeeds so offline availability and actions update without leaving the screen
- [x] Prune stale download records when local files are missing
- [x] Add version-aware Update local with staged validation and atomic replacement when server metadata changes; target-device Update local flow validated

## 8. Offline Reading / Listening

- [x] Reopen local audio/PDF files
- [x] Prefer local file when available
- [x] Keep queued progress locally while offline
- [x] Test full airplane-mode behavior with downloaded books
- [x] Confirm app startup behavior when offline
- [x] Confirm library list fallback when offline
- [x] Define and implement offline UX for books not downloaded
- [x] Ensure reader screens degrade gracefully without network
- [x] Verify no accidental API dependency during local playback/reading

## 9. Progress Tracking

- [x] Create local progress queue model
- [x] Capture audiobook position updates
- [x] Capture PDF page progress updates
- [x] Store timestamped progress records
- [x] Validate ebook progress payload shape against real `SaveProgressDto`
- [x] Validate audiobook progress payload shape against real `UpsertAudioProgressDto`
- [x] Add throttling/debouncing so progress is not queued too aggressively
- [x] Persist and restore last known progress on reopen
- [x] Normalize progress semantics across media types
- [x] Parse BookOrbit's scalar `readingProgress` card field and nested `readStatus`
- [x] Keep progress on one canonical 0-100 scale through readers, persistence, and API payloads
- [x] Allow newer reread/backward events to repair stale or inflated server progress
- [x] Preserve reader updates enqueued while foreground or WorkManager sync is in flight
- [x] Reject unknown progress payloads instead of resetting BookOrbit to zero percent
- [x] Explicitly sync BookOrbit `reading`/`read` status with each accepted progress event
- [x] Validate Android-to-BookOrbit and BookOrbit-to-Android progress reconciliation on the target server
- [x] Prevent removed Currently Reading titles from returning from stale unread-status timestamps after Home refresh
- [x] Reduce large-library refresh latency with 100-book pages and bounded concurrent page requests
- [ ] Add session event support if `sessions` endpoint is useful

## 10. Offline Sync Queue

- [x] Persist pending progress queue to disk
- [x] Add worker-triggered sync attempt
- [x] Replay queued progress when online
- [x] Use newest-progress-wins policy in stored events
- [x] Verify queue replay against live BookOrbit server for the tested EPUB flow
- [x] Prevent duplicate progress submissions without discarding valid reread/backward updates
- [x] Share queue/marker file locks across repository instances and acknowledge posted event IDs only
- [x] Debounce rapid reader callbacks into a trailing replacement worker
- [x] Collapse multiple pending updates for the same book/file
- [x] Add backoff and retry policy for server errors
- [x] Distinguish auth failures from transient network failures
- [x] Recover stale non-audio queued file IDs after progress 404 by resolving the book's current primary file, retrying once, and acknowledging terminal INVALID targets so they cannot retry forever
- [x] Validate on the target device that stale-file recovery clears the stuck pending-sync queue
- [x] Add queue inspection/logging for debug builds
- [x] Confirm behavior when server changes while queue still exists

## 11. Reader / Player Quality

- [x] Basic audio playback UI
- [x] Basic PDF page navigation
- [x] Add proper playback controls for audiobooks
- [x] Add skip forward/back actions
- [x] Add playback speed controls if allowed within read/listen scope
- [x] Add chapter support if API/file format exposes it
- [x] Add EPUB pagination/theme/font handling
- [x] Preserve the Lagrange EPUB behavior over Readium: 25% edge navigation, center-tap reader controls, chapter/page jumps, stored themes, 90-150% text, four stored margins, progress footer, reader system bars, orientation lock, and keep-awake behavior
- [x] Persist exact Readium locators for normal Read, restore legacy chapter/page/percentage state when no locator exists, and keep Preview progress/location-free
- [x] Restore and validate the last device-known-good visible-overflow EPUB page strip after the clipped-wrapper regression
- [x] Validate visible Top/Bottom EPUB padding and repagination on the target device; vertical padding resizes the WebView outside the known-good HTML renderer
- [x] Persist independent EPUB padding values across reader close/reopen
- [x] Flush the final reader progress before browser refresh and sync pending progress before first Home load
- [x] Keep reader options open until the explicit Close action is tapped (superseded by later visible-content dismissal feedback)
- [x] Prevent the reading screen from closing unexpectedly after an idle period
- [x] Simplify the reader options overlay to one Close action; tapping the visible book content should also close the options overlay
- [x] Show battery and signal indicators in the reader's top-right area
- [x] Show book completion percentage, chapter page progress, and measured whole-book page location at the bottom of the reader
- [x] Give reader options distinct Continue reading and Close book actions
- [x] Improve reader-options text/background contrast across all reader themes
- [x] Redesign the reader options window as a bottom sheet with clearer hierarchy and controls
- [x] Add Mark as read and Mark as unread to book overflow and long-press actions; read preserves position, while unread resets progress
- [x] Reduce Home initial sync/loading latency by refreshing nonselected libraries in deterministic batches of at most three after the selected library becomes current; physical-device/server validation remains pending
- [x] Add better PDF zoom/pan behavior
- [x] Preserve reader/player state across rotation/process death
- [x] Improve accessibility for controls and reader screens

## 12. Error Handling And Recovery

- [x] Basic fallback back to login on session/API failure
- [x] Add structured user-facing error messages
- [x] Add recoverable network error flows
- [x] Add authentication-expired flow with return to intended screen
- [x] Add corrupted local file detection and recovery
- [x] Add unsupported-format messaging
- [x] Add crash-safe handling for malformed server responses

## 13. Testing

- [x] Local debug build passes
- [x] Add unit tests for URL normalization
- [x] Add unit tests for payload parsing
- [x] Add unit tests for media kind inference
- [x] Add unit tests for download store
- [x] Add unit tests for progress queue store
- [x] Add unit tests for sync conflict resolution
- [x] Add unit tests for coordinator bootstrap and login recovery
- [x] Add repository HTTP integration tests for login bootstrap
- [x] Add repository HTTP integration tests for library/book loading
- [x] Add repository HTTP integration tests for offline queue replay
- [ ] Execute the repository HTTP integration tests on a usable connected device/emulator; current androidTest APK compiles but adb enumeration did not provide a runnable target
- [x] Add an Android WebView instrumentation regression for EPUB padding geometry and translated-page visibility
- [x] Add Readium routing/progress fallback coverage and three connected EPUB tests for bitmap-only SVG cover plus locator persistence, settings mapping, and injected center-tap controls
- [x] Add Readium comic routing/preparation coverage and two connected tests for CBZ opening/navigation plus injected center-tap controls
- [x] Add `BookDetailActionRowTest` coverage for nonlocal idle/retry/cancel, local update/delete/cancel overflow, and wide/narrow/extreme/large-text layout decisions
- [x] Add six `BookDetailReadingProgressTest` cases for opened/reset zero, partial formatting, completion, manual read with retained progress, and unknown status-only labels
- [x] Add three `ReaderChromePositionStateTest` cases and two compiled `ReaderLightweightChromeInstrumentedTest` cases; update five connected Readium tests to prove center tap opens chrome without full options
- [x] Add three reader tutorial contract JVM tests, a compiled geometry/labels Compose case, and connected assertions for tutorial launch/dismissal before center-tap chrome
- [x] Add Compose instrumentation regression coverage for book-detail actions, wrapped metadata, the full-screen cover viewer, and Series navigation
- [x] Add at least one end-to-end manual test matrix

## 14. Release Readiness

- [ ] Remove remaining scaffold shortcuts and placeholders
- [x] Audit cleartext/TLS policy
- [x] Confirm no secrets or internal URLs are committed
- [x] Add privacy notes if user data is stored locally
- [x] Verify release build compiles
- [x] Verify app install on physical device
- [ ] Create first tagged release
- [x] Document setup, build, and test instructions in `README`

## 15. UI/UX Workstream

- [x] Reach a stable functional baseline for UI/UX work
- [x] Mark UI/UX discussion as ready to begin
- [x] Implement the first design-system candidate for review
- [x] Implement native Home, search, and drawer navigation candidate
- [x] Add metadata-aware Keep Reading, On Deck, recent book, and recent series shelves
- [x] Add server-backed global search and authenticated cover loading
- [x] Move cover decoding off the UI thread and bound decoded-cover memory
- [x] Add native series and book details before reader launch
- [x] Populate book details from BookOrbit metadata (synopsis, creator, publication, identifiers, genres, files)
- [x] Populate series details from the complete server series page with reading completion and gap context
- [x] Page complete series requests within BookOrbit's 100-item server limit
- [x] Add series synopsis and genre/tag context from the lead book
- [x] Hide the Android status bar for the first immersive app-window candidate (superseded by device feedback)
- [x] Put Currently reading books in the first Home shelf
- [x] Replace the drawer-based browser shell with a Plex-inspired bottom navigation layout
- [x] Add a top-level library selector to the Libraries destination
- [x] Replace the persistent Home search field with a search icon and search layer
- [x] Restore the standard Android status bar and add intentional top breathing room on Home
- [x] Keep Continue reading visible for tolerant in-progress server progress payloads
- [x] Remove the duplicate Home/library heading from Library Recommended
- [x] Move Options from More into the profile menu above Log out
- [x] Add extra top/bottom spacing to the More sheet
- [x] Avoid showing a partial derived series count while Browse is still paginating (superseded by complete local catalogs)
- [x] Add reader padding controls with a more generous default text inset
- [x] Add independent Top and Bottom reader padding controls with repagination
- [x] Replace reader padding presets with independent Top/Bottom/Left/Right percentage controls; fresh books default Top to 30% and Bottom/Left/Right to 15%, while saved per-book values remain unchanged
- [x] Replace the launch/startup spinner with the branded adaptive-icon loading state
- [ ] Validate the branded launch state on a physical device
- [x] Restore the exact in-chapter EPUB page on reopen and restart
- [x] Render library books with the same adaptive poster cards used by Series and Authors
- [x] Rename visible app branding to Lagrange with the subtitle "a BookOrbit reader"
- [x] Add an About destination; its original More-menu placement was later superseded by the profile-dropdown revision
- [x] Show the Lagrange subtitle only on the splash/loading presentation
- [x] Add a Libraries control to collapse series into representative cards and restore all books
- [x] Add Local books before Options in the More menu
- [x] Reduce book poster-card sizing to 75% of the current device-reviewed size
- [x] Add lazy library Browse-tab pagination as the user scrolls (superseded by the Room-backed complete catalog)
- [x] Show the Lagrange logo only on Home and make the selected library name the Library selector
- [x] Split Library into Recommended and Browse tabs
- [x] Make Library, Series, and Authors poster cards visibly smaller on narrow phones
- [x] Add a downward-triangle affordance to the selected Library name
- [x] Replace the Library refresh arrow with swipe-down-to-refresh
- [x] Cache cover thumbnails locally for Local books
- [x] Recover Local books cached thumbnails and metadata from the latest rich-detail cache, including while offline
- [x] Rename the collapsed-series action to Expand series
- [x] Keep collapsed series ordered by series name and restore the scroll anchor when toggling collapse
- [x] Improve thumbnail loading performance and retry failed covers
- [x] Cancel off-screen cover requests so visible cards and details are not blocked by stale scroll work
- [x] Warm every missing/changed selected-library thumbnail into the local cache in durable unmetered batches
- [x] Redesign book-detail actions as a wrapping compact area with selective symbols and persistent labels so every action remains visible on narrow screens
- [x] Navigate from a tapped book-detail series title to Series details
- [x] Redesign book-detail genres/tags and lower metadata hierarchy
- [x] Add a full-screen book-detail cover viewer with any-screen-tap and Back dismissal
- [x] Reduce book-detail action cards to compact controls with Read/Preview labels, clear icons, and icon-only Download
- [x] Limit long book-detail titles to five rows with an expand/collapse action
- [x] Keep series name and series index visible as separate book-detail metadata rows
- [x] Add multi-book selection and bulk Mark as read/unread actions to library grids
- [x] Navigate genre selections to filtered Books or Series results, using BookOrbit's current singular `genre`/`includesAny`/array book-filter contract and the analogous `author` relation contract; keep tags informational
- [x] Validate genre-filter navigation against the target BookOrbit server's supported query contract and result scope
- [ ] Add direct OIDC/SSO authentication after the provider/redirect contract is confirmed; native username/password remains current
- [x] Validate the revised detail-action density, title expansion, multi-selection, and series-index implementation in code/tests
- [ ] Validate the remaining revised detail density on a device; genre-filter result scope is validated
- [x] Keep Read and Preview action labels visible beside clear icons while retaining compact spacing
- [x] Use an unmistakable download icon and expose active per-file download progress/status from book details
- [x] Validate interrupted download recovery, retry behavior, and large downloads on the target device
- [x] Add visible labels to Download and Delete local book-detail actions
- [x] Replace the former three-dot book-detail action with a directly labeled live Mark as read or Mark as unread tile in the wrapping action row; retain existing status logic and disable it for offline snapshots
- [x] Make Close book immediately restore cached browser state (or Loading when unavailable), merge captured progress into selected/server-wide books, and complete persistence, sync, cleanup, and refresh in the background; prompt dismissal is target-device validated
- [x] Recover missing foreground book-detail covers, including `your name.`, through ordered explicit/canonical thumbnail candidates while preserving cancellation and cache identity; physical-device/server validation remains pending
- [x] Add the approved current-chapter page slider beneath the retained Choose chapter/chip controls, with immediate clamped jumps and single-page disablement; target-device validation passed
- [x] Refresh the open book-detail state immediately when a download completes
- [x] Remove the Tag tap affordance because BookOrbit has no verified supported tag filter; keep tags informational
- [x] Keep native username/password as the current authentication flow and defer direct OIDC/SSO until its server contract is confirmed
- [x] Make red Home/Library Recommended messages dismissible with horizontal swipe and an explicit close button
- [x] Replace the normalized EPUB `Book x/1000` footer with an actual layout-derived whole-book page total, weighted completion, and calculating fallback
- [x] Restrict Recently read to completed books and exclude titles still in progress (historical heuristic; superseded by the exact Read/Skimmed state mapping below)
- [x] Restore cross-library Currently reading aggregation for active titles (historical membership heuristic; superseded by the exact Reading/Rereading state mapping below)
- [x] Aggregate Home shelves across every library on the connected server; keep selected-library scoping for Libraries/Recommended/Browse
- [x] Validate server-wide Home aggregation and cross-library Currently reading on the target device/server
- [ ] Validate incremental multi-library refresh latency and partial-cache messaging under a failing nonselected library
- [x] Fix main Options navigation from book details so retained detail state cannot mask the Options destination
- [x] Validate immediate Download/Delete local reconciliation, Local books thumbnails, and Options-from-detail navigation on the target device
- [x] Add safe bottom spacing to the More menu
- [x] Show full Library book/series totals from the complete cached catalog
- [x] Return from Library selection by tapping the top-left Libraries title
- [x] Cache full downloaded-book metadata for server-free local detail screens
- [x] Cache versioned rich details for every opened book and invalidate them when catalog metadata changes
- [x] Render the full stable #/A–Z (or Z–A/# descending) vocabulary whenever Library or Series rails are eligible; keep only exact represented targets clickable and expose missing labels as disabled/unavailable without forward fallback
- [x] Persist complete per-server/per-library book metadata in Room and render it before cold-start network checks finish
- [x] Reconcile every server page atomically, writing only changed/new/reordered rows and deleting titles no longer returned by BookOrbit
- [x] Remove Browse's near-end lazy loading and apply Browse filters/sorts to the complete local catalog
- [x] Use BookOrbit jump-bucket absolute indexes for default Browse navigation, with a complete-cache local fallback
- [x] Disable the jump rail until the first full catalog is ready and for sort modes that do not support meaningful letter buckets
- [ ] Validate cache-first reopen, catalog additions/deletions/progress changes, and exact #/A–Z jumps on the target device/server
- [ ] Validate bounded catalog-refresh latency, full thumbnail warming, rapid-scroll cancellation, and instant repeat detail opens with the 5k-book library
- [x] Route background authenticated request failures through the login recovery flow
- [x] Retry authenticated requests through the refresh-cookie session flow before showing session-expired login
- [x] Retry and cache Series/Authors catalog thumbnails, resolving Series `coverBookIds` through representative book thumbnails
- [x] Surface newly recorded progress immediately in Home Continue reading, including books outside the first loaded page
- [x] Add visible overflow and long-press Currently reading actions that use normal-user APIs to clear primary/current progress, mark the title unread, and clear matching local progress
- [x] Add the book context menu to global search results for both long-press and three-dot actions
- [x] Keep global search results as list rows rather than poster cards
- [x] Implement the former series-progression On Deck heuristic (superseded by the exact On Hold state mapping below)
- [x] Add pull-down refresh to the Home screen
- [x] Restore visible thumbnails for series cards in the Series screen using BookOrbit's representative `coverBookIds`
- [x] Add a #/A–Z jump rail to Name-sorted Series results and replace Load more with complete, deduplicated in-memory navigation
- [x] Show the number of books in each collapsed Libraries series card, matching the Series screen
- [x] Remove duplicate in-content Home/library headings from Home, Library Browse, Series, Authors, and Local books content
- [x] Reduce app typography tokens by approximately 10% for phone density
- [x] Render book cards with title, optional series, and series-index metadata rows
- [x] Validate reader edge padding while sliders move on the target device; Top/Bottom resize the WebView externally while Left/Right remain in the known-good page strip
- [x] Persist the selected reader background theme (light, dark, or sepia) across reader close/reopen and app sessions
- [x] Add a stable Previous/Next series-book row directly below book identity/series metadata, using complete online series data or cached offline neighbors ordered by numeric series index
- [x] Validate basic series Previous/Next navigation on the target device
- [x] Restyle series Previous/Next as transparent borderless 46 dp controls matching detail actions and retain the first complete series load across adjacent navigation
- [x] Validate the transparent series Previous/Next controls on the target device
- [ ] Validate remaining series Previous/Next edge cases across responsive widths, long titles, loading transitions, and offline snapshots
- [x] Replace the single app Dark choice with one flat five-option Theme list: Follow system, Light, Charcoal, Warm black, and OLED black; migrate legacy Dark to Charcoal
- [x] Add Change server immediately above Log out/Sign in in the profile menu, with current-URL prefill, inline URL validation, Cancel/Continue warning, active-download cancellation, old-session/cache clearing, and login/setup recovery for the replacement server
- [x] Validate Change server editor, confirmation, active-work cancellation, and reachable/unreachable session transitions on the target device; submitting the current normalized URL closes silently without state changes
- [x] Add a profile Achievements destination backed by authenticated `GET /api/v1/achievements`, with earned/available summary, adaptive Unlocked/Locked cards, optional locked progress, award dates, secret-field preservation, older-server unsupported state, and retry errors
- [x] Validate the basic Achievements flow on the target device/server
- [x] Replace the superseded Achievement poster layout with compact adaptive information cards using a 260 dp grid minimum, a 22 dp server-driven semantic icon immediately before the title, lock/unlock state at row end, and description/category/rarity plus conditional progress/date inside the card
- [ ] Validate secret/censored entries, unsupported-server behavior, retry, conditional progress/date metadata, and responsive layout after the compact information-card redesign
- [x] Prevent visible Library and Series jump rails from covering trailing grid cards by reserving 32 dp trailing grid padding only while the shared 20 dp rail is visible; otherwise retain 16 dp full-width padding
- [x] Validate the jump-rail grid spacing on the target device
- [ ] Validate the conditional jump-rail gutter across additional widths, orientations, and responsive grid sizes

### Latest target-device follow-up — 2026-07-18

- [x] Replace the oversized Achievement poster tiles with compact adaptive information cards: 260 dp grid minimum, 22 dp server-driven icon immediately before the title, lock/unlock state at row end, and description/category/rarity plus conditional progress/date inside one card
- [x] Replace the horizontally scrolling `book-detail-actions` row with a wrapping `FlowRow` so Read, Preview, Download/Update/Cancel/Delete local, and the directly labeled live Mark as read/unread tile remain visible on narrow screens
- [x] Supersede the wrapping row with the final fixed-height single-row policy, including inline nonlocal transfer states, local More actions, typography-aware status placement, and extreme-width compaction
- [x] Restore embedded EPUB resources through `WebViewAssetLoader` at `appassets.androidplatform.net`, using safe nested/encoded chapter base URLs and the same extracted root for visible rendering and hidden measurement while disabling broad file/content access
- [x] Fix remote nonlocal EPUB/PDF Preview preparation by downloading an authenticated temporary reader copy instead of incorrectly gating preparation on CBZ detection
- [x] Restore the full #/A–Z Library and Series jump rail: retain sort/catalog hiding rules and the existing grid gutter, but render unrepresented letters with 38%-alpha `onSurfaceVariant`, disabled semantics, unavailable content descriptions, and no click action
- [x] Add a Local books shelf at the bottom of top-level Home from server-wide `homeBooks` and at the bottom of Library Recommended from selected-library books; use deterministic deduplicated alphabetical previews up to 12, reuse normal shelf cards/actions/covers, and route See all to global or selected-library-scoped Local books

### Reader controls work order - 2026-07-19

Implement and validate in this dependency order. Version 0.2.7 includes the July 20 reader/detail feedback follow-up. The current gate passes 265 JVM tests across 46 suites plus lint and both APK assemblies. The reported reader-ownership race, Exit, and tutorial timing/dismissal are target-device validated; broader format/responsive checks remain.

1. [ ] Install version 0.2.7 from `app/build/outputs/apk/debug/app-debug.apk` on the Samsung Galaxy S24 and validate local/online CBZ plus connected CBR/CB7 through normal Read and Preview. Confirm the retained dark controls, right page rail/footer, leftmost labeled Exit/X with no visible Back action, surface/scrim dismissal, exact normal locator resume/progress, Preview page-1 isolation, orientation lock, keep-awake, and dark system bars. Confirm downloaded CBR/CB7 clearly remains unavailable offline and succeeds after reconnecting. Spot-check EPUB, PDF, and audio. Do not claim the comic migration device-validated until this pass succeeds.
2. [x] Replace the wrapping actions with one fixed-height, non-wrapping, non-scrolling row. Preserve labeled Read/Preview; map the nonlocal inline transfer slot to Download/Retry/Cancel; keep local Delete and Update/Cancel update in More; measure Mark as read/unread against current typography/font scale; show More whenever anything is hidden; compact only weighted Read/Preview at extreme widths. Samsung Galaxy S24 manual validation remains pending.
3. [x] Implement the user-selected compact progress line directly above actions, covering canonical known/zero/completed values, unknown status-only labels, explicit unread-reset omission, identity de-duplication, and immediate stale-cache-safe removal after Mark as unread. Physical S24 validation remains pending.
4. [x] Implement shared theme-aware lightweight chrome for EPUB and comics with a status-bar-safe top bar, a right-side page rail occupying about 75% of screen height, bottom list/cog, and options→chrome→exit Android Back order. The visible Back action is removed and labeled Exit/X is leftmost; reading-surface/center-scrim taps dismiss chrome.
   [x] EPUB primary slider/arrows move one page; a separate arrow pair moves chapters, and the outer Chapters button remains the chapter picker. Comics remain page-only.
   [x] Remove duplicate chapter selection and page sliders from cog options while retaining position status and appearance/margin settings.
   [x] Add the Previous/Menu/Next tutorial on every Read/Preview entry with first-render timing. The current contract is exactly 3,000 ms, with all three full-screen regions consuming taps and dismissing immediately.
   [x] Pin the book-detail three-dot More action to the row's right/trailing edge whenever it is shown and layout space permits.
5. [ ] Add automated and target-device coverage for the new detail/reader behavior, including accessibility, large text, orientation, narrow/wide layouts, reader themes, exact tutorial timing, resume/sync, Preview isolation, and offline behavior; then continue CB7 and representative PDF/audiobook coverage, compact Achievement edges, series Previous/Next edges, jump-rail responsiveness, and partial multi-library refresh failures.

The tap-zone geometry and colors intentionally match Suwayomi's `RIGHT_LEFT` preview, with the user-selected 3,000 ms duration and tap-any-region dismissal. The implemented trigger is every initial EPUB/comic reader activity entry/open, including Read and Preview, with no seen-state persisted across repeat opens, books, files, or installs.

### Reader/detail feedback follow-up - 2026-07-20

1. [x] Move the shared location rail from left to right and size the control to approximately 75% of reader height. Physical-device validation remains pending.
2. [x] Make EPUB rail/primary arrows page-based in one-page increments and add separate previous/next chapter arrows while retaining the outer Chapters selector.
3. [x] Keep the reader chrome top bar below the phone status bar.
4. [x] Remove the duplicate chapter selector and page sliders from cog options; outer controls are the only position selectors.
5. [x] The earlier labeled Back-left/Exit-right arrangement was implemented, then superseded by the interaction revision: no visible Back action and labeled Exit/X leftmost.
6. [x] The earlier two-second tutorial was implemented, then superseded by the exact 3,000 ms tap-dismissible tutorial.
7. [x] Keep the book-detail three-dot More action at the row's right/trailing edge whenever it appears.

### Reader stability and interaction follow-up - 2026-07-20

Execute in this order:

1. [x] Diagnose books being replaced by Home during sync/refresh. Source: the former `AppCoordinator.showBrowser()` always updated both `lastBrowserState` and `_screen`, so active catalog refreshes and other background browser-state callbacks could replace `ReaderLoading` or `Reader`.
2. [x] Separate background browser-snapshot updates from explicit navigation. Guarded `showBrowser()` updates `lastBrowserState` without navigating while the screen is `ReaderLoading` or `Reader`; explicit open failure and reader close use `navigateToBrowser()`. Two `AppCoordinatorTest` regressions prove delayed catalog refresh and download-state updates cannot replace the reader, and the focused `AppCoordinatorTest` run passes.
3. [ ] The full automated gate passes. Physically validate reader screen ownership during refresh, download progress/state changes, open failure, and Close/Exit.
4. [x] Remove Back from the center-tap lightweight reader chrome. Move labeled Exit/X leftmost; tapping the reading surface/center scrim remains the route back to unobstructed reading.
5. [x] Extend the tutorial to exactly 3,000 ms from first pre-draw. All three full-screen regions consume taps, call dismissal, and do not forward the tap to reader navigation or chrome; EPUB and comic activities hide the tutorial immediately.
6. [ ] The full gate passes 244 JVM tests across 41 suites, lint, debug APK assembly, and Android-test APK assembly. Physical-device validation remains for refresh/sync races, repeated reader opens, EPUB/comic Read and Preview, tutorial timeout/tap dismissal, Android Back behavior, and revised Exit placement.

Target-device feedback confirms the reported sync/refresh/download reader replacement no longer occurs, Exit works, and the tutorial timeout/tap dismissal works.

### Reader tutorial, Series grouping, and profile-menu follow-up - 2026-07-20

Execute in this order:

1. [x] Remove the background behind the Previous/Menu/Next tutorial labels and enlarge the label text to 28 sp, approximately twice its prior size, while preserving the colored tap regions, exact 3,000 ms timeout, and consumed tap dismissal. Focused JVM and Android-test compilation pass.
2. [x] Add mutually exclusive Series-detail grouping controls below Genres for grouping books by Library or File format. Library is the default, tapping the active choice can leave both inactive, and the selected state persists globally across every Series. Scoped per-library Series requests restore ownership omitted by unscoped BookCard payloads, so Library grouping separates correctly. Section labels follow configured library or normalized alphabetical format order, retain series-index book order, and share their row with a trailing divider. Focused JVM and Android-test compilation pass.
3. [x] Reorder the profile dropdown to Achievements, Options, About, divider, Change server, and Log out/Sign in. Give Options a cogwheel icon instead of the three-dot icon and remove About from the More sheet. Compiled Compose coverage asserts ordering, icon semantics, divider placement, About routing, and the reduced More contents.
4. [x] Focused coverage and the full gate pass: 258 JVM tests across 44 suites, lint, debug APK assembly, and Android-test APK assembly. Target-device validation confirms tutorial readability, cross-library Series grouping and persistence, the inline section label/divider layout, and profile-menu ordering.
5. [ ] Separately validate the Options cog icon, profile divider semantics, About routing, accessibility, and large-text layouts; these were not covered by the latest device feedback.

### Readium, library cover shape, and persistent audiobook work order - 2026-07-20

Execute in this dependency order:

1. [x] Record the current target-device baseline: EPUB and CBZ work directly through Readium, and connected CBR works after BookOrbit page extraction is normalized into a cached CBZ for Readium. The local `sample/86 Volume 01/` fixture contains a 489,114,453-byte M4B plus companion metadata with 17 chapter ranges.
2. [x] Build the explicit `ReadiumPublicationRoute` capability matrix: EPUB/KEPUB → direct EPUB, PDF → direct PDF, supported audio → direct audio, CBZ → direct image publication, CBR/CB7 → normalize to cached CBZ, and MOBI/AZW/AZW3/FB2 → `UNSUPPORTED_EBOOK`. Unsupported ebooks remain `MediaKind.UNKNOWN` and show `This file format is not supported.` rather than being falsely treated as valid EPUB. Focused routing/update coverage passes; conversion is deferred outside current product scope.
3. [x] Implement the BookOrbit cover contract: `CoverAspectRatio` parses exact `2/3` and `1/1` with portrait fallback; `LibrarySummary` and `BookSummary` carry it; owning-library enrichment covers mixed search/author/series/cache/local results; BrowserSnapshotStore, ActiveReaderStore, and BookDetailCacheStore persist it; and Room catalog v2 migrates 1→2 with a non-null `2/3` default.
4. [x] Render shared book/fullscreen covers and the compact audiobook cover with true 2:3 or 1:1 Crop geometry and no artificial square-cover top/bottom padding. Give `BookPosterCard` and `ShelfBookCard` a portrait-height `BookCardCoverSlot`, align the true-aspect cover `BottomCenter`, align cover bottoms/labels across shapes, and keep More attached to the actual cover. Parsing/fallback, mixed enrichment, persistence, Room-column, and `BookCoverLayoutTest` coverage pass. The full gate passes 265 JVM tests across 46 suites with zero failures/errors/skips plus lint and both APK assemblies; physical mixed-library square/portrait alignment validation remains pending.
5. [x] Establish the Readium audiobook playback foundation: Readium 3.0.2 media `AudioNavigator` with its ExoPlayer adapter, a Media3 foreground `MediaSessionService`, media-playback/notification permissions, an application-scoped controller, local M4B and other recognized audio opening, and an explicit task-removal survival policy. Focused format tests and compilation pass; device validation remains pending.
   - [x] Replace screen-owned playback with controller-owned lifecycle/progress sampling every 1.5 seconds. Readium's adapter handles audio focus/noisy output; Android 13 notification permission is requested on first playback. Preview progress is isolated, and explicit Close publishes final normal-Read progress before stopping the service.
6. [x] Add one app-global compact audiobook player above the browser bottom navigation, with no expandable or fullscreen player. Normal Read/Preview returns to the browser while playback persists; the overlay also appears in separate Readium EPUB/comic activities. It provides tappable cover-to-detail, title/author, elapsed/remaining seek slider, Replay 10, play/pause, Forward 30, BookOrbit `audioMetadata` chapter picker, 0.75/1/1.25/1.5/2× speed, and explicit Close. Audiobook detail uses Play as its primary action.
   - [x] Load metadata before audio preparation and persist it through active-reader/detail caches. The former authenticated temporary Preview copy was superseded by direct Media3 streaming while Preview remains progress-isolated. Confine `CookieManager` clearing to the main thread after the connected test exposed the boundary.
   - [x] Reproduce the real M4B crash and keep parsing on IO while marshaling Readium navigator/ExoPlayer creation, MediaSession opening, play, and close to `Dispatchers.Main.immediate`. `ReadiumAudioOpenInstrumentedTest` proves the externally pushed 489,114,453-byte fixture opens, plays with active session/foreground service/audio focus, and closes.
   - [x] Prevent failed preparation restart loops: save active audio only after playback succeeds; log and convert preparation exceptions to a safe failure; clear stale active-reader state; and return to the browser. Coordinator regressions cover the recovery path.
   - [x] Fix the July 21 perpetual-preparation regression by preserving the exact tapped catalog/download book identity during chapter enrichment and copying only `audioChapters`; never replace its `fileId`, format, or `localPath` with the separately parsed detail-book identity.
   - [x] Version the audio reader cache, use format-aware suffixes, stage and validate each complete/nonempty/readable download before atomic promotion, and prevent interrupted Preview from reusing partial output. Bind playback with `BIND_AUTO_CREATE` and explicit failure; bound Readium preparation to 30 seconds and close cleanup to 5 seconds.
   - [x] Target-device feedback confirms the July 21 audiobook opening/playback fix works well. Authenticated Preview remains a separate pending device check.
   - [x] Constrain separate Readium EPUB/comic activities above the measured compact-player height so content, chrome, options, tutorial, and footer never render behind it; restore the full viewport immediately after Close. Add Android navigation-bar padding below main-app Browser/detail bottom content when regular bottom navigation is hidden, and below non-Browser overlays.
   - [x] Prevent persisted normal-audio progress from reopening the transient Reader/Preparing destination during either local-only or authenticated bootstrap. Continue Browser startup and let the surviving playback service/controller provide the compact player.
   - [x] The focused `AppCoordinatorTest` case `bootstrap skips persisted audiobook reader and reopens browser for compact playback` fails without the guard and passes with it. The full gate now passes 262 JVM tests across 44 suites with zero failures/errors/skips, `lintDebug`, debug APK assembly, and Android-test APK assembly.
   - [ ] On the target device, play audio, leave or close the app without closing the player, and reopen it. Confirm Browser appears with the compact player and the Preparing screen never appears. Also validate the revised layout and authenticated Preview, then notification/lock-screen/headset/Bluetooth behavior and service/process recreation.
7. [x] Migrate PDF to Readium 3.0.2 with `readium-adapter-pdfium`, `PdfiumDocumentFactory`/`PublicationOpener`, and `PdfNavigatorFragment`/`PdfiumEngineProvider`; enable Jetifier for the adapter's legacy Pdfium transitive dependency. Route Read/Preview through `ReadiumPdfReaderActivity`, preserve exact normal locator/progress and Preview page-1 isolation, retain shared page chrome/tutorial/orientation/keep-awake/system bars/audio overlay, fail invalid PDFs explicitly, and remove the legacy Compose `PdfRenderer` path. Remove routed legacy EPUB/comic fallbacks: EPUB always uses Readium, and comic sources that cannot open directly or normalize to CBZ show conversion/reconnect guidance. Two JVM routing tests pass, and the connected generated three-page test passes on Medium_Phone AVD API 17 with `Profile.PDF` and exactly three positions. The current full gate passes 265 JVM tests across 46 suites plus lint and both APK assemblies; physical target-device PDF UI validation remains pending.
8. [x] Defer MOBI/AZW/AZW3/FB2 conversion by explicit product choice. Keep them `UNSUPPORTED_EBOOK`/`MediaKind.UNKNOWN` with the visible unsupported-format message; treat conversion only as optional future scope if product direction changes.
9. [x] Run the physical target-device matrix covering PDF controls/resume/Preview and every supported format, portrait/square libraries, mixed-library screens, local/streamed audio, Back and cross-app backgrounding, notification/lock-screen/headset controls, compact-player persistence on every destination, interruptions/audio focus, offline transitions, process/service recreation, accessibility, large text, themes, and explicit player close. Splash/loading is accepted: the app opens directly to content quickly and is no longer a blocker.

### Reader and library feedback work order - 2026-07-21

Execute in this order after confirming any required UI choices:

1. [x] Persist the portrait or landscape orientation active when Lock orientation is enabled and restore that explicit orientation in the main app plus EPUB, PDF, and comic activities. Legacy enabled preferences default to portrait instead of inheriting another app's landscape orientation. The full gate passes 267 JVM tests across 46 suites with zero failures/errors/skips, lint, debug APK assembly, and Android-test APK assembly.
   - [x] Target-device validation confirms portrait and landscape orientation locks persist across app switches, cold starts, and every reader.
2. [x] Move the Library Browse statistics and filter/collapse or multi-selection action row outside the lazy grid so it remains fixed while the catalog scrolls or handles downward swipe/pull-to-refresh gestures. Remove the former header-item offset from collapse-anchor restoration and jump-rail targets. Compiled Compose coverage scrolls to the final card and verifies unchanged toolbar bounds plus visible statistics and Filter controls. The full gate passes 267 JVM tests across 46 suites with zero failures/errors/skips, lint, debug APK assembly, and Android-test APK assembly.
3. [x] Add a multi-select `Delete local` action to the Local books screen. The fixed selection toolbar exposes the action only there, the existing confirmation preference governs the batch dialog, Cancel retains selection, and confirmation clears selection before one coordinator batch. Successful files reconcile into both browser collections and Local books in one revision even when another deletion fails; the failure summary reports the partial result. Focused JVM coverage forces a partial failure, compiled Compose coverage exercises selection/Cancel/confirmation/list removal, and the full gate passes 275 JVM tests across 47 suites with zero failures/errors/skips, lint, debug APK assembly, and Android-test APK assembly.
   - [x] Target-device validation confirms multi-select Delete local, Cancel/confirmation, partial-result handling, and immediate deletion when confirmation is disabled.
4. [x] Add the shared #/A-Z jump rail to Authors. Because the server exposes paged name-sorted authors but no jump buckets, the screen now loads every distinct page, sorts deterministically by name/id, removes Load more, and maps only represented initials to exact grid indexes. The shared rail retains unavailable-label disabled semantics, Reduce motion behavior, the one-header index offset, and the 32 dp responsive end gutter. Focused pagination/target tests and compiled Compose accessibility/gutter coverage pass; the full gate passes 278 JVM tests across 48 suites with zero failures/errors/skips, lint, debug APK assembly, and Android-test APK assembly.
   - [x] Target-device validation confirms complete Authors pagination, exact represented-letter jumps, disabled unavailable letters, and rail-safe card gutters across widths/orientations.
5. [x] Give Libraries, Series, Authors, and Local books distinct destination icons using the user-selected filled semantic direction: bookshelf (`LocalLibrary`), bookmarked collection (`CollectionsBookmark`), group (`Groups`), and offline download (`DownloadForOffline`). Each icon has a destination-specific accessibility description, and compiled Compose coverage asserts all four mappings. The full gate passes 278 JVM tests across 48 suites with zero failures/errors/skips, lint, debug APK assembly, and Android-test APK assembly.
   - [x] Target-device validation confirms the four filled destination icons are distinct, legible, aligned, and understandable across themes and display/font scales.
6. [x] Preserve BookOrbit's exact read-state values in Home and Library Recommended. Reading/Rereading alone feed Currently reading, Want to Read has its own shelf, and Read/Skimmed feed Recently read. On Deck is derived from series progression: a series with at least one Read volume contributes its first non-Read volume unless that candidate is Reading/Rereading; standalone and unstarted series are excluded. Persist exact state through Room migration 2->3, snapshots, rich-detail cache, and active-reader recovery, with focused parser/shelf/cache/coordinator coverage.
   - [x] Target-device feedback confirms the restored series-progression On Deck works correctly.
   - [x] Target-device/server validation confirms Currently reading, On Deck, Want to read, and Recently read on server-wide Home and selected-library Recommended, including offline/cold-cache reopen and On Deck progression.

### Book Detail Other versions follow-up - 2026-07-22

1. [x] Below Synopsis, show a compact horizontal `Other versions` row for distinct same-series books with the exact same non-null index, using complete retained cross-library series data. Format and library labels distinguish cards; selecting a card stays in Book Detail.
2. [x] Collapse duplicate-index variants out of Previous/Next so navigation moves between distinct series positions.
3. [x] Focused JVM/Compose coverage and the full 280-test gate pass; target-device validation confirms the row, labels, horizontal scrolling, in-detail selection, navigation, accessibility, and responsive/large-text behavior.

### Remote media streaming work order - 2026-07-21

Keep whole-file authenticated preparation for EPUB and PDF, but replace full-file preparation for large audio/video containers and comics:

1. [x] Select a BookOrbit-compatible hybrid architecture: keep the foreground service/progress model, use direct authenticated Media3 range playback for connected audio, retain Readium for explicit local audio, and use lazy Readium page resources for connected comics.
2. [x] Stream M4B/M4A/MP3/Opus/OGG/FLAC through a direct authenticated Media3 item pointed at BookOrbit's `/books/files/{fileId}/serve` route. This matches the WebUI's native HTML5/Howler delivery model and bypasses Readium's failing standalone remote-asset retrieval. Retain chapter navigation, seeking, speed, resume, Preview isolation, background playback, notification controls, local playback, and explicit offline downloads. Current same-origin Bearer/cookie headers are resolved for each data-source open, with one 401/403 recovery retry.
3. [x] Open connected CBZ/CBR/CB7 lazily as authenticated Readium image publications backed by BookOrbit's page endpoints. Preparation reads only the page count and first page for media detection; navigation fetches the selected server-rendered page instead of downloading every page or building a complete cached CBZ. Local/downloaded CBZ remains file-backed.
4. [x] Keep explicit Local books downloads independent from disposable media resources: connected audio and comics create no persistent full-media cache, and the first remote media open prunes obsolete full-audio/CBZ/readium-comics caches while retaining EPUB/PDF caches and downloads. Direct Media3 audio preserves Range requests, resolves current authentication per open, retries one 401/403 after credential renewal, and surfaces preparation errors without creating partial files.
5. [x] Complete target-device/server validation with representative large CBZ/CBR and M4B/MP4 files. Deterministic coverage includes direct Media3 header/range preservation and one authentication renewal, legacy-cache pruning, selected-page-only comics, plus compilation of the shared service/player regressions. The full gate passes 280 JVM tests across 48 suites with zero failures/errors/skips plus lint and both APK assemblies; connected execution remains pending because no device is attached.
   - [x] Target-device feedback confirms ordinary remote audiobook streaming now opens and plays correctly through direct Media3.
   - [x] Target-device validation confirms seeking, chapters, Preview isolation, background/relaunch behavior, access-token renewal during playback, and explicit-download offline reopening.

- [ ] Checkpoint 1: agree on product direction and design-system tokens
- [ ] Checkpoint 2: refine server setup, login, and shared app shell
- [ ] Checkpoint 3: validate and refine Home shelves, search, drawer, library selection, and book cards
- [ ] Checkpoint 3a: validate and refine native book/series detail hierarchy, density, metadata, and actions
- [ ] Checkpoint 4: refine the EPUB reader with available sample content
- [x] Implement the Checkpoint 4 fullscreen paginated EPUB reader candidate with Komga-style tap zones
- [ ] Checkpoint 5: fullscreen comic interactions plus online/local CBZ and CBR are device-validated; validate CB7, consider optional offline CBR/CB7 extraction, refine PDF, and use the available local M4B/chapter-metadata fixture for audiobook work
- [ ] Checkpoint 6: complete accessibility, responsive-layout, theme, and device validation

## Options backlog

### Interface

- [x] Add lock current orientation toggle
- [x] Add haptic feedback toggle and explicit app haptics (superseded and removed by user direction)
- [x] Remove the haptic setting, stored preference, custom provider, manual requests, and obsolete tests
- [x] Add the combined app theme selection: Follow system, Light, Charcoal, Warm black, or OLED black, applied immediately with legacy Dark migration to Charcoal
- [x] Add default opening screen selection: Home, Library, or Local books
- [x] Add Reduce motion/animations accessibility option for immediate catalog jumps

### Data

- [x] Add downloads-over-cellular policy: Always, Never, or Ask for confirmation, with browser-wide start/prompt/block behavior
- [x] Add storage management showing downloaded/cache sizes and a Clear cache action that preserves downloaded books, downloaded-book metadata, settings, progress, and catalog data
- [x] Add background metadata/cover refresh network policy: Any network, Wi-Fi only, or Disabled; current cover work uses CONNECTED/UNMETERED constraints and reconfigures immediately
- [x] Add confirmation before deleting a local copy, enabled by default across native-browser delete entry points
- [x] Validate lock-current-orientation, default opening screen, Reduce motion, cellular download behavior, storage/cache clearing, and delete-local confirmation on a physical device
- [x] Validate all five app themes on the target device
- [x] Validate detailed theme contrast/system-bar behavior and background network policy on the target device

Detailed gates and guardrails are in [docs/ui-ux.md](./docs/ui-ux.md).

## Immediate Next Stack

UI/UX discussion and design-system work can start now:

- The functional and JVM baseline is ready.
- EPUB, PDF, audio, and supported comics now use Readium 3.0.2. The generated three-page PDF opening test passes on Medium_Phone AVD API 17; Samsung Galaxy S24 PDF/comic validation remains open.
- Local/readable CBZ opens directly. Connected CBR/CB7 uses authenticated server pages to prepare a cached CBZ; downloaded CBR/CB7 remains unsupported offline because no local RAR/7z extraction is bundled. Unsupported comic sources show conversion/reconnect guidance rather than entering a legacy fallback reader.
- The reader tutorial work order is implemented. Next: target-device, accessibility, responsive, theme, exact timing, resume, Preview-isolation, offline, edge-state, and remaining media validation.
- Use [docs/ui-ux.md](./docs/ui-ux.md) for UI/UX checkpoints and [docs/testing.md](./docs/testing.md) for validation.

- [x] Validate live BookOrbit authentication and library APIs with the server
- [x] Replace generic ebook fallback with a real EPUB reader
- [x] Test EPUB download/offline/sync end to end on real content
- [x] Add queue compaction and stronger sync error handling

### Book Detail other versions follow-up - 2026-07-22

- [x] Show exact same-series, same-index BookOrbit records in an `Other versions` row immediately below Synopsis. The row reuses compact cover cards, labels format/library, and selecting a card replaces the current detail without leaving the detail destination. Duplicate-index records are excluded from Previous/Next volume navigation.
- [x] Add JVM coverage for exact matching, distinctness, missing series/index behavior, and duplicate-index navigation collapse; add compiled Compose coverage for placement, labels, selection, and retained series loading. The full gate passes 282 JVM tests across 48 suites with zero failures/errors/skips, lint, debug APK assembly, and Android-test APK assembly. Physical target-device validation is complete in the July 22 device pass.


### Device validation pass - 2026-07-22

- [x] Validate Book Detail Other versions, remote audio/comics, PDF, reader stability, Library/catalog/shelves, visual/UI/responsive/accessibility, and connected instrumentation coverage on device.
- [x] Accept fast direct-to-content startup; a visible splash is not required.

### Library-specific reader configuration - 2026-07-22

1. [x] Add Reading configuration under main Options with a library-name dropdown. Persist one profile per library: reading direction (LTR/RTL), EPUB theme, text size, and margins.
2. [x] Save in-reader EPUB preference changes back to the owning library profile. Carry the owning library ID through EPUB, PDF, and comic launches; apply logical edge taps/tutorial labels and direction-aware comic/PDF pager layout from that profile. Refresh main preferences after returning from a reader.
3. [x] Focused coverage and the full gate pass 286 JVM tests across 49 suites with 0 failures/errors/skips, lint, `assembleDebug`, and `assembleDebugAndroidTest`. APK: `app/build/outputs/apk/debug/app-debug.apk`.
   - [x] Physical-device validation confirms library switching, LTR/RTL edge gestures/tutorial/pager behavior, persistence after relaunch, and large-text/theme/margin combinations.

### Per-library PDF and Comics layouts - 2026-07-22

1. [x] Extend each library reader profile with independent PDF and Comics layout modes (`Paginated`/`Continuous`) plus a 0-48 dp continuous page gap. Defaults are PDF Continuous/16 dp and Comics Paginated/16 dp.
2. [x] Expose separate PDF and Comics controls under Options > Reading. Disable each gap slider while its format is paginated.
3. [x] Map PDF layout to Readium Pdfium `Axis.HORIZONTAL`/`Axis.VERTICAL` and native `pageSpacing`. Keep paginated comics on Readium `ImageNavigator`; route every supported local/remote comic through a custom continuous lazy vertical surface when Continuous is selected.
4. [x] Bound continuous comics with visible/nearby page loading, viewport downsampling, a 64 MB source-page cap, a 16-million-pixel decoded-bitmap bound, retry state, resume/progress, LTR/RTL logical edge taps, menu tap, page-slider navigation, and Reduce motion behavior.
5. [x] The full gate passes 291 JVM tests across 50 suites with 0 failures/errors/skips, `lintDebug`, `assembleDebug`, and `assembleDebugAndroidTest`. APK: `app/build/outputs/apk/debug/app-debug.apk`.
   - [ ] Validate PDF/comic mode switching, page gaps, local/remote continuous loading and retry, memory behavior, resume/progress, LTR/RTL gestures, slider navigation, and Reduce motion on a physical device.

### EPUB continuous-scroll layout - 2026-07-22

1. [x] Persist per-library `epubLayoutMode` (`Paginated`/`Continuous`) in `LibraryReaderPreferences`, defaulting to Paginated for backward compatibility. Add the EPUB layout selector under Options > Reading without a gap slider; PDF and Comics controls remain unchanged.
2. [x] Map the selected EPUB mode into Readium `EpubPreferences` (`scroll=false` for Paginated, `scroll=true` for Continuous).
3. [x] Focused coverage and the full gate pass 291 JVM tests across 50 suites with 0 failures/errors/skips, `lintDebug`, `assembleDebug`, and `assembleDebugAndroidTest`. APK: `app/build/outputs/apk/debug/app-debug.apk`.
   - [ ] Validate EPUB mode switching, long-document scrolling, resume/progress, RTL/LTR, large text/theme/margins, accessibility, and rotation on a physical device.

### Reader profile persistence and in-reader configuration - 2026-07-22

1. [x] Fix EPUB partial saves so in-reader changes persist the complete owning-library profile instead of resetting other format layouts or page gaps.
2. [x] Expose the current book library's complete Reading configuration in EPUB, PDF, and CBR/CBZ option sheets without a library selector. Apply and persist changes live in EPUB/PDF; rebuild CBR/CBZ layout while retaining the current page.
3. [x] Rename the visible `Comics layout` label to `CBR/CBZ layout`.
4. [x] Allow continuous remote CBZ Preview to read chunked or unknown-`Content-Length` pages through a bounded response capped at 64 MB.
5. [x] Regression coverage is added/compiled, and the full gate passes 291 JVM tests across 50 suites with 0 failures/errors/skips, `lintDebug`, `assembleDebug`, and `assembleDebugAndroidTest`. APK: `app/build/outputs/apk/debug/app-debug.apk`.
   - [ ] On a physical device, validate chunked/unknown-length CBZ Preview pages; confirm profiles do not reset across book opens; verify each reader options sheet is scoped to the current book's library; confirm EPUB/PDF live changes and CBR/CBZ retained-page rebuilds; and verify the `CBR/CBZ layout` label.

### Bounded continuous CBR/CBZ bitmap cache - 2026-07-22

1. [x] Scope decoded continuous-scroll bitmaps to the currently open book, key entries by page and viewport width, and retain recent pages in an adaptive LRU budget of roughly one quarter of app heap, clamped to 48-192 MB while scrolling back.
2. [x] Clear the decoded bitmap cache on reader close. Preserve the existing source-page/read protections: 64 MB response/read bound and 16M decoded-pixel bound.
3. [x] Compile the continuous-reader coverage alongside the existing source/read-bound regression coverage; the full gate passes.
   - [ ] On a physical device, scroll far down and back up through long CBR/CBZ documents and confirm recent pages reuse smoothly, memory remains stable, and closing/reopening starts with a cleared book-scoped cache.

Continuous comic tutorial refinement: Continuous mode uses vertical Swipe up/Menu/Swipe down regions; paginated mode retains LR/RL Previous/Menu/Next. The cache prefetches two previous and two next pages around the visible range. Physical validation must cover long-book scroll-down/up smoothness, adaptive cache memory stability, prefetch/reuse, close cleanup, and tutorial layout/labels.