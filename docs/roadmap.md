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

### 1. End-to-end offline verification

- Download real books from the live server
- Read while offline
- Reconnect and verify queued progress sync
- Verify that local EPUB/PDF/audio reopen without accidental API dependency
- Verify failure and retry behavior against real disconnect/reconnect cycles

### 2. Reader quality hardening

- Preserve EPUB in-chapter position instead of chapter-only progress
- Add comic support if required by the deployed BookOrbit library
- Improve PDF zoom and pan behavior
- Improve audio controls as needed within the read/listen-only scope

### 3. Sync queue hardening

- Add more integration coverage around queue compaction behavior
- Add backoff and retry policy
- Add debug-visible queue inspection/logging
- Verify queue replay against the live BookOrbit server
- Confirm end-to-end server-switch behavior on a real device, including queued progress and server-scoped local downloads

### 4. UX hardening

- Session-expiry handling
- Cleaner unsupported-format handling

### 5. Testing and release readiness

- Unit tests for repository parsing and progress DTO generation
- Integration tests for login bootstrap and library loading
- Broaden coordinator and repository JVM coverage around session persistence and expiry recovery before moving to device-only test gaps
- Offline sync verification matrix
- Physical device validation

## Source of truth

Detailed checkpoint status is tracked in:

- [../CHECKLIST.md](../CHECKLIST.md)
