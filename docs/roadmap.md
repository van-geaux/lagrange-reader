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

- Add tests for progress throttling and queue compaction behavior
- Add backoff and retry policy
- Add debug-visible queue inspection/logging
- Verify queue replay against the live BookOrbit server
- Confirm behavior when server selection changes with pending progress

### 4. UX hardening

- Better loading and error states
- Download progress and retry UI
- Session-expiry handling
- Cleaner unsupported-format handling

### 5. Testing and release readiness

- Unit tests for repository parsing and progress DTO generation
- Integration tests for login bootstrap and library loading
- Offline sync verification matrix
- Physical device validation

## Source of truth

Detailed checkpoint status is tracked in:

- [../CHECKLIST.md](../CHECKLIST.md)
