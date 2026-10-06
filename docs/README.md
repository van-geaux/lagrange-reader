# Documentation

This folder contains focused public engineering documentation for Lagrange Reader. Active documents describe current behavior, contracts, procedures, and decisions; local operator handovers and historical implementation logs are intentionally excluded from the public documentation set.

## Current documents

- [Architecture](./architecture.md) — current components, data flow, storage, synchronization, and guardrails.
- [Provider Module Separation](./modules.md) — provider-neutral feature contracts, composition rules, and migration boundaries.
- [Provider documentation](./providers/README.md) — provider-specific API, module, authentication, integration, and capability notes.
- [EPUB Text-to-Speech Module](./epub-tts-module.md) — TTS boundaries, lifecycle isolation, persistence, and implementation pieces.
- [Local Setup](./setup.md) — machine prerequisites and local build setup.
- [Privacy Notes](./privacy.md) — local storage, network behavior, and current privacy gaps.
- [Release Policy](./release.md) — versioning, signing, naming, and publishing rules.
- Provider-specific API and integration contracts are indexed in [Provider documentation](./providers/README.md).
- [Testing](./testing.md) — current automated gate and manual validation procedures.
- [Provider Module/Function Matrix](./provider-checklists/module-function-matrix.md) — compare supported, unsupported, partial, and unverified functions across target servers.
- [Provider Manual Validation Checklist](./provider-checklists/provider-checklist-template.md) — copyable maintainer/user checklist for validating each target server/provider.
- [UI/UX Workstream](./ui-ux.md) — current interaction contracts, design rules, and unresolved UX decisions.
- [Roadmap](./roadmap.md) — current priorities and deferred work.
- BookOrbit OIDC / SSO authentication is documented in [Provider documentation](./providers/bookorbit/oidc-authentication.md).
- [Security Policy](../SECURITY.md) — responsible-disclosure guidance for security reports.

## Local operator documents

The session handover and historical operator archives (`docs/checklist-archive.md`, `docs/roadmap-archive.md`, and `docs/testing-archive.md`) are intentionally kept local and ignored by Git. They preserve historical evidence for maintainers but are not part of the public documentation set. The superseded native-app expansion plan remains public as historical product context.

## Current status

Lagrange 1.5.4 is the latest published release. The application supports authenticated BookOrbit browsing, selected-library offline caching, series and library bulk downloads, EPUB/PDF/comic reading, EPUB annotations, Preview mode, enhanced audiobook player with session history, background-safe book downloads, fullscreen cover viewer, continuous connected split-MP3 audiobook playback, progress synchronization, account isolation on logout, server-missing catalog state, pull-to-refresh, reader font selection, server reading sessions, indexed book navigation, Smart Scopes, grouped download notifications, and the implemented interim server sign-in WebView.

Use `CHECKLIST.md` for active completion and validation items and `docs/roadmap.md` for the next user-directed work. Session-specific worktree state belongs in the local-only `docs/handover.md`; do not use historical archives as current status without re-verification.
