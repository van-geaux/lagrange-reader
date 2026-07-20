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
- [x] Add generic fallback WebView/file path for unsupported formats
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
- [x] Route every EPUB launch through `ReadiumEpubReaderActivity` and every locally readable comic through `ReadiumComicReaderActivity` on Readium 3.0.2; leave PDF and audio unchanged
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
- [x] Add an About destination after Options in the More menu
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
- [x] Restrict Recently read to completed books and exclude titles still in progress
- [x] Restore Currently reading for genuinely in-progress books when any server library contains active progress
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
- [x] Make On Deck show only the next unread book in a series after a completely read book, excluding books already present in Currently reading
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

Implement and validate in this dependency order. Version 0.2.7 includes the July 20 reader/detail feedback follow-up. The current gate passes 244 JVM tests across 41 suites plus lint and both APK assemblies. No device is attached, so the revised chrome, tutorial, stability race, and detail alignment remain physically unvalidated.

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
- [ ] Checkpoint 1: agree on product direction and design-system tokens
- [ ] Checkpoint 2: refine server setup, login, and shared app shell
- [ ] Checkpoint 3: validate and refine Home shelves, search, drawer, library selection, and book cards
- [ ] Checkpoint 3a: validate and refine native book/series detail hierarchy, density, metadata, and actions
- [ ] Checkpoint 4: refine the EPUB reader with available sample content
- [x] Implement the Checkpoint 4 fullscreen paginated EPUB reader candidate with Komga-style tap zones
- [ ] Checkpoint 5: fullscreen comic interactions plus online/local CBZ and CBR are device-validated; validate CB7, consider optional offline CBR/CB7 extraction, and refine audiobook/PDF readers when representative samples are available
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
- EPUB and comics now use Readium 3.0.2. Five focused connected tests pass; Samsung Galaxy S24 comic validation is the highest-priority next task.
- Local/readable CBZ opens directly. Connected CBR/CB7 uses authenticated server pages to prepare a cached CBZ; downloaded CBR/CB7 remains unsupported offline because no local RAR/7z extraction is bundled. PDF and audio are unchanged.
- The reader tutorial work order is implemented. Next: target-device, accessibility, responsive, theme, exact timing, resume, Preview-isolation, offline, edge-state, and remaining media validation.
- Use [docs/ui-ux.md](./docs/ui-ux.md) for UI/UX checkpoints and [docs/testing.md](./docs/testing.md) for validation.

- [x] Validate live BookOrbit authentication and library APIs with the server
- [x] Replace generic ebook fallback with a real EPUB reader
- [x] Test EPUB download/offline/sync end to end on real content
- [x] Add queue compaction and stronger sync error handling
