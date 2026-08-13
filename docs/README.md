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
- [Security Policy](../SECURITY.md) — responsible-disclosure guidance for security reports.

## Local operator documents

The session handover (`docs/handover.md`) and historical operator archives are intentionally kept local and ignored by Git. They are not part of the public documentation set and are referenced elsewhere only by plain-text path, not as clickable links. The superseded native-app expansion plan remains public as historical product context.

## Current status

Lagrange 1.4.2 is the latest published release. Active development documentation may describe implemented, user-validated work that still awaits pull-request integration; `CHECKLIST.md` and `docs/roadmap.md` distinguish that work from behavior already present in `main`. The application supports authenticated BookOrbit browsing, offline downloads, EPUB/PDF/comic reading, audiobook playback, progress synchronization, server-missing catalog state, pull-to-refresh, reader font selection, the implemented interim server sign-in WebView, and the current Phase 1–2 portion of issue #55's global annotations work. Issue #55 is the highest-priority active feature; local reader search, highlighting, and durable annotation mutation synchronization remain pending.

Use `CHECKLIST.md` for active completion and validation items and `docs/roadmap.md` for the next user-directed work. Session-specific worktree state belongs in the local-only `docs/handover.md`; do not use historical archives as current status without re-verification.
