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

The Android project builds locally with `assembleDebug`, can connect to a BookOrbit server, and has working paths for login, library browsing, downloads, local persistence, progress sync, PDF reading with zoom and pan controls, audio playback, EPUB reading with chapter/theme/font controls, CBZ comic reading from local downloads or authenticated cache copies, active-reader restoration after recreation or restart, and improved accessibility semantics for reader controls and status surfaces.

Focused JVM coverage now exists for repository payload parsing, nullable-field fallbacks, multiple-file selection, cover URL resolution, server URL normalization, media kind inference, normalized progress labels, sync conflict resolution, download record persistence, and progress queue persistence.

The audiobook reader now includes play and pause controls, skip back and forward actions, resume-at-position handling, and a small playback speed selector.

Manual app testing can now start from [Testing](./testing.md), and the server setup flow distinguishes malformed URLs, unreachable hosts, TLS failures, timeouts, and generic HTTP/network failures with clearer user-facing messages while preserving the attempted URL for direct retry.

The main gaps are:

- end-to-end offline verification on real content
- comic/CBZ reader support if the deployed server exposes comics
- stronger retry/backoff behavior and broader sync verification
- broader integration and end-to-end test coverage
