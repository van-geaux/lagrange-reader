# Documentation

This folder contains the working engineering documentation for `Lagrange Reader`.

## Documents

- [Architecture](./architecture.md)
- [Local Setup](./setup.md)
- [BookOrbit API Contract](./bookorbit-api.md)
- [Testing](./testing.md)
- [Roadmap](./roadmap.md)
- [Handover](./handover.md)

## Current status

The Android project builds locally with `assembleDebug`, can connect to a BookOrbit server, and has working paths for login, explicit authenticated-session bootstrap through `/api/v1/auth/me`, sign-out/session reset, library browsing, downloads, local persistence, progress sync, PDF reading with zoom and pan controls, audio playback, EPUB reading with chapter/theme/font controls, CBZ comic reading from local downloads or authenticated cache copies, active-reader restoration after recreation or restart, improved accessibility semantics for reader controls and status surfaces, normalized stored progress percentages across media types, and earlier user-facing failures when a reader cannot be prepared from local or cached content.

Focused JVM coverage now exists for repository payload parsing, nullable-field fallbacks, multiple-file selection, cover URL resolution, server URL normalization, media kind inference, normalized progress labels, sync conflict resolution, download record persistence, and progress queue persistence.

The audiobook reader now includes play and pause controls, skip back and forward actions, resume-at-position handling, and a small playback speed selector.

Manual app testing can now start from [Testing](./testing.md), and the server setup flow distinguishes malformed URLs, unreachable hosts, TLS failures, redirect responses, timeouts, and generic HTTP/network failures with clearer user-facing messages while preserving the attempted URL for direct retry.

The local release build also passes with `.\gradlew.bat assembleRelease`.

The main gaps are:

- end-to-end offline verification on real content
- broader session-persistence and expiry-recovery verification on real servers
- broader integration and end-to-end test coverage
