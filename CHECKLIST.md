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
- [ ] Add proper package/version naming policy
- [ ] Add app icon and production branding assets
- [ ] Add release signing strategy
- [ ] Add CI workflow for debug build
- [ ] Add baseline static quality checks

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
- [ ] Confirm session persistence after app restart
- [ ] Confirm logout/session expiry recovery behavior
- [ ] Validate OIDC login flow on a real OIDC-enabled BookOrbit server
- [x] Add explicit authenticated-user bootstrap check after login
- [x] Add clean logout/reset session behavior

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
- [ ] Add session event support if `sessions` endpoint is useful

## 10. Offline Sync Queue

- [x] Persist pending progress queue to disk
- [x] Add worker-triggered sync attempt
- [x] Replay queued progress when online
- [x] Use newest-progress-wins policy in stored events
- [ ] Verify queue replay against live BookOrbit server
- [x] Prevent duplicate or stale progress submissions
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
- [x] Add better PDF zoom/pan behavior
- [x] Preserve reader/player state across rotation/process death
- [x] Improve accessibility for controls and reader screens

## 12. Error Handling And Recovery

- [x] Basic fallback back to login on session/API failure
- [x] Add structured user-facing error messages
- [ ] Add recoverable network error flows
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
- [ ] Add integration tests for login bootstrap
- [ ] Add integration tests for library/book loading
- [ ] Add integration tests for offline queue replay
- [ ] Add instrumentation tests for reader/player flows
- [x] Add at least one end-to-end manual test matrix

## 14. Release Readiness

- [ ] Remove remaining scaffold shortcuts and placeholders
- [ ] Audit cleartext/TLS policy
- [ ] Confirm no secrets or internal URLs are committed
- [ ] Add privacy notes if user data is stored locally
- [x] Verify release build compiles
- [ ] Verify app install on physical device
- [ ] Create first tagged release
- [x] Document setup, build, and test instructions in `README`

## Immediate Next Stack

Manual app testing can start here:

- Build + JVM baseline is ready.
- Use [docs/testing.md](./docs/testing.md) as the current manual test entry point.

- [x] Validate live BookOrbit authentication and library APIs with the server
- [x] Replace generic ebook fallback with a real EPUB reader
- [ ] Test download/offline/sync end to end on real content
- [x] Add queue compaction and stronger sync error handling
