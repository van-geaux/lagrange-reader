# Roadmap

This roadmap summarizes the next practical engineering sequence for the project.

## Completed foundation

- Android project scaffold exists
- Live BookOrbit login contract validated
- Live library and book payloads validated
- Local build is working on this machine
- Download and sync scaffolding are implemented

## Next execution order

### 1. Sync queue hardening

- Collapse multiple pending progress updates for the same book or file
- Classify auth failures separately from retryable network failures
- Reduce unnecessary queued writes

### 2. Real reader support

- Replace the generic EPUB fallback with a real EPUB reader
- Add comic support if required by the deployed BookOrbit library
- Preserve reader position and state more reliably

### 3. End-to-end offline verification

- Download real books from the live server
- Read while offline
- Reconnect and verify progress sync
- Verify failure and retry behavior

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
