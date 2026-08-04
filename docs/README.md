# Documentation

This folder contains focused public engineering documentation for Lagrange Reader. Active documents describe current behavior, contracts, procedures, and decisions; local operator handovers and historical implementation logs are intentionally excluded from the public documentation set.

## Current documents

- [Architecture](./architecture.md) — current components, data flow, storage, synchronization, and guardrails.
- [Local Setup](./setup.md) — machine prerequisites and local build setup.
- [Privacy Notes](./privacy.md) — local storage, network behavior, and current privacy gaps.
- [Release Policy](./release.md) — versioning, signing, naming, and publishing rules.
- [BookOrbit API Contract](./bookorbit-api.md) — server endpoints and payload contracts used by the client.
- [Testing](./testing.md) — current automated gate and manual validation procedures.
- [UI/UX Workstream](./ui-ux.md) — current interaction contracts, design rules, and unresolved UX decisions.
- [Roadmap](./roadmap.md) — current priorities and deferred work.
- [OIDC / SSO Authentication](./oidc-authentication.md) — current interim flow and native AppAuth blocker.

## Local operator documents

The session handover and historical operator archives are intentionally kept local and ignored by Git. They are not part of the public documentation set. The superseded native-app expansion plan remains public as historical product context.

## Current status

Lagrange 1.4.1 is the latest published release; 1.4.2 is the approved release candidate. The current branch is `main`, aligned with `origin/main`. The application supports authenticated BookOrbit browsing, offline downloads, EPUB/PDF/comic reading, audiobook playback, progress synchronization, server-missing catalog state, pull-to-refresh, reader font selection, server reading sessions, indexed book navigation, and the implemented interim server sign-in WebView.

Use `CHECKLIST.md` for active completion and validation items and `docs/roadmap.md` for the next user-directed work. Session-specific worktree state belongs in the local-only `docs/handover.md`; do not use historical archives as current status without re-verification.
