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
3. Rename the visible app brand to `Lagrange` and add the subtitle `a BookOrbit reader` to the logo/launch presentation. BookOrbit remains the connected server/product name.
4. Add an About destination after Options in the More menu, using placeholder app/version/server information until the About content is reviewed.

## Source of truth

Detailed checkpoint status is tracked in:

- [../CHECKLIST.md](../CHECKLIST.md)
- [ui-ux.md](./ui-ux.md)
