# Documentation

This folder contains the working engineering documentation for `Lagrange Reader`.

## Documents

- [Architecture](./architecture.md)
- [Local Setup](./setup.md)
- [Privacy Notes](./privacy.md)
- [Release Policy](./release.md)
- [BookOrbit API Contract](./bookorbit-api.md)
- [Testing](./testing.md)
- [Roadmap](./roadmap.md)
- [Handover](./handover.md)

## Current status

The Android project builds locally with `assembleDebug`, can connect to a BookOrbit server, and has working paths for login, explicit authenticated-session bootstrap through `/api/v1/auth/me`, sign-out/session reset, authentication-expired return-to-intended-screen recovery, library browsing, downloads, server-scoped local persistence, server-scoped authenticated reader cache copies, offline-local-only reader reopen from cached browser snapshots, progress sync, PDF reading with zoom and pan controls, audio playback, EPUB reading with chapter/theme/font controls, CBZ comic reading from local downloads or authenticated cache copies, active-reader restoration after recreation or restart, corrupted local reader file pruning, improved accessibility semantics for reader controls and status surfaces, normalized stored progress percentages across media types, and earlier user-facing failures when a reader cannot be prepared from local or cached content.

Focused JVM coverage now exists for repository payload parsing, nullable-field fallbacks, multiple-file selection, cover URL resolution, server URL normalization, media kind inference, normalized progress labels, sync conflict resolution, download record persistence, progress queue persistence, progress throttling policy behavior, and coordinator bootstrap/login/browser recovery flows.

Initial live-browser load failures without a cached snapshot now fall back to an empty browser state with a user-facing retryable error instead of leaving the app stranded on a loading path.

The audiobook reader now includes play and pause controls, skip back and forward actions, resume-at-position handling, and a small playback speed selector.

Manual app testing can now start from [Testing](./testing.md), and the server setup flow distinguishes malformed URLs, unreachable hosts, TLS failures, redirect responses, timeouts, and generic HTTP/network failures with clearer user-facing messages while preserving the attempted URL for direct retry.

The local release build also passes with `.\gradlew.bat assembleRelease`.

The repository now includes a basic GitHub Actions workflow for debug CI that runs the JVM unit suite, Android lint, and debug APK build on `main` pushes and pull requests.

Network policy is now explicit: cleartext HTTP is blocked by default and only allowed for local development hosts such as `localhost`, `127.0.0.1`, `10.0.2.2`, and `10.0.3.2`.

The main gaps are:

- end-to-end offline verification on real content
- broader session-persistence and expiry-recovery verification on real servers
- broader integration and end-to-end test coverage
