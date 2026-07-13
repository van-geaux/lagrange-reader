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

## 3. Authentication Flow

- [x] Open server login page inside app
- [x] Share session cookies between WebView and API client
- [x] Verify server-side login contract against the live BookOrbit server
- [ ] Verify login completion detection is robust
- [x] Confirm session persistence after app restart
- [x] Keep explicit Sign in on the login screen instead of bouncing to cached Home
- [ ] Confirm logout/session expiry recovery behavior on a physical device
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
- [x] Add proper in-reader loading/error states
- [x] Add resume-from-last-position when streaming
- [x] Ensure opening a non-downloaded book always chooses streaming path

## 7. Download For Offline Use

- [x] Download file from API to app-local storage
- [x] Persist download record locally
- [x] Expose downloaded status in UI
- [ ] Verify large-download behavior on real content
- [x] Add progress indicator for active downloads
- [x] Add retry/cancel behavior for failed downloads
- [x] Validate file naming and extension handling for all supported formats
- [x] Add storage-space failure handling
- [x] Add download integrity checks
- [x] Add delete-local-copy action
- [x] Prune stale download records when local files are missing
- [ ] Add redownload/update behavior if server file changes

## 8. Offline Reading / Listening

- [x] Reopen local audio/PDF files
- [x] Prefer local file when available
- [x] Keep queued progress locally while offline
- [ ] Test full airplane-mode behavior with downloaded books
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
- [ ] Validate Android-to-BookOrbit and BookOrbit-to-Android progress reconciliation on the target server
- [ ] Add session event support if `sessions` endpoint is useful

## 10. Offline Sync Queue

- [x] Persist pending progress queue to disk
- [x] Add worker-triggered sync attempt
- [x] Replay queued progress when online
- [x] Use newest-progress-wins policy in stored events
- [x] Verify queue replay against live BookOrbit server for the tested EPUB flow
- [x] Prevent duplicate progress submissions without discarding valid reread/backward updates
- [x] Collapse multiple pending updates for the same book/file
- [x] Add backoff and retry policy for server errors
- [x] Distinguish auth failures from transient network failures
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
- [x] Restore and validate the last device-known-good visible-overflow EPUB page strip after the clipped-wrapper regression
- [x] Validate visible Top/Bottom EPUB padding and repagination on the target device; vertical padding resizes the WebView outside the known-good HTML renderer
- [x] Persist independent EPUB padding values across reader close/reopen
- [x] Flush the final reader progress before browser refresh and sync pending progress before first Home load
- [x] Keep reader options open until the explicit Close action is tapped
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
- [ ] Add integration tests for login bootstrap
- [ ] Add integration tests for library/book loading
- [ ] Add integration tests for offline queue replay
- [x] Add an Android WebView instrumentation regression for EPUB padding geometry and translated-page visibility
- [x] Add at least one end-to-end manual test matrix

## 14. Release Readiness

- [ ] Remove remaining scaffold shortcuts and placeholders
- [x] Audit cleartext/TLS policy
- [ ] Confirm no secrets or internal URLs are committed
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
- [x] Replace reader padding presets with independent Top/Bottom/Left/Right percentage controls; default each edge to 15%
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
- [x] Rename the collapsed-series action to Expand series
- [x] Keep collapsed series ordered by series name and restore the scroll anchor when toggling collapse
- [x] Improve thumbnail loading performance and retry failed covers
- [x] Add safe bottom spacing to the More menu
- [x] Show full Library book/series totals from the complete cached catalog
- [x] Return from Library selection by tapping the top-left Libraries title
- [x] Cache full downloaded-book metadata for server-free local detail screens
- [x] Show # and every A–Z label on the Library jump rail, grouping non-alphabetic titles under #
- [x] Persist complete per-server/per-library book metadata in Room and render it before cold-start network checks finish
- [x] Reconcile every server page atomically, writing only changed/new/reordered rows and deleting titles no longer returned by BookOrbit
- [x] Remove Browse's near-end lazy loading and apply Browse filters/sorts to the complete local catalog
- [x] Use BookOrbit jump-bucket absolute indexes for default Browse navigation, with a complete-cache local fallback
- [x] Disable the jump rail until the first full catalog is ready and for sort modes that do not support meaningful letter buckets
- [ ] Validate cache-first reopen, catalog additions/deletions/progress changes, and exact #/A–Z jumps on the target device/server
- [x] Route background authenticated request failures through the login recovery flow
- [x] Retry authenticated requests through the refresh-cookie session flow before showing session-expired login
- [x] Retry and cache Series/Authors catalog thumbnails, including the series cover fallback endpoint
- [x] Surface newly recorded progress immediately in Home Continue reading, including books outside the first loaded page
- [x] Remove duplicate in-content Home/library headings from Home, Library Browse, Series, Authors, and Local books content
- [x] Reduce app typography tokens by approximately 10% for phone density
- [x] Render book cards with title, optional series, and series-index metadata rows
- [x] Validate reader edge padding while sliders move on the target device; Top/Bottom resize the WebView externally while Left/Right remain in the known-good page strip
- [ ] Checkpoint 1: agree on product direction and design-system tokens
- [ ] Checkpoint 2: refine server setup, login, and shared app shell
- [ ] Checkpoint 3: validate and refine Home shelves, search, drawer, library selection, and book cards
- [ ] Checkpoint 3a: validate and refine native book/series detail hierarchy, density, metadata, and actions
- [ ] Checkpoint 4: refine the EPUB reader with available sample content
- [x] Implement the Checkpoint 4 fullscreen paginated EPUB reader candidate with Komga-style tap zones
- [ ] Checkpoint 5: refine audiobook, PDF, and CBZ readers when samples are available
- [ ] Checkpoint 6: complete accessibility, responsive-layout, theme, and device validation

Detailed gates and guardrails are in [docs/ui-ux.md](./docs/ui-ux.md).

## Immediate Next Stack

UI/UX discussion and design-system work can start now:

- The functional and JVM baseline is ready.
- EPUB is the validated representative reader path.
- Audiobook, PDF, and CBZ-specific work is deferred until sample files are available.
- The immediate implementation pass is the user-feedback workplan below: Home reading priority first, then the Plex-inspired shell, Home search/status-bar spacing, reader padding, and launch visual.
- Use [docs/ui-ux.md](./docs/ui-ux.md) for UI/UX checkpoints and [docs/testing.md](./docs/testing.md) for validation.

- [x] Validate live BookOrbit authentication and library APIs with the server
- [x] Replace generic ebook fallback with a real EPUB reader
- [x] Test EPUB download/offline/sync end to end on real content
- [x] Add queue compaction and stronger sync error handling
