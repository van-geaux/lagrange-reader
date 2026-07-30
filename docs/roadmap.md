# Roadmap

This document contains the current project direction only. Historical work orders and dated implementation logs are preserved in [`docs/roadmap-archive.md`](roadmap-archive.md).

## Current status

- Release: Lagrange 1.4.1 is published. See [`docs/release-notes/v1.4.1.md`](release-notes/v1.4.1.md).
- Branch: `main`, aligned with `origin/main`.
- Recent completed work: behavior-neutral cleanup, release integration, pull-to-refresh, continuous EPUB seeking, reader fonts including one imported custom font, staged startup, server-missing catalog state, and download lifecycle implementation.
- Current implementation state: no new implementation task has been selected. Choose the next item with the user before editing.

## Active validation and follow-up

1. Validate the complete interrupted-download and failed-update lifecycle on a connected device or emulator when an ADB target is available. This is the remaining validation called out by the active checklist and testing procedure.
2. Reconcile any remaining physical-device checks against the current test matrix before asking for another APK.
3. Keep native AppAuth/Custom Tabs deferred until BookOrbit provides deployed mobile-redirect allow-list support and the identity-provider callback is registered. The interim server-hosted sign-in WebView is implemented.
4. Consider additional format support only through a new user-approved work item; MOBI, AZW, AZW3, and FB2 remain unsupported.
5. Treat broader offline RAR/7z extraction and other optional validation as user-directed follow-up, not an implementation blocker for the current release.

## Decision and documentation rules

- Use [`CHECKLIST.md`](../CHECKLIST.md) for active completion and validation status.
- Use [`docs/testing.md`](testing.md) for reusable automated and manual procedures.
- Use [`docs/architecture.md`](architecture.md), [`docs/bookorbit-api.md`](bookorbit-api.md), and [`docs/ui-ux.md`](ui-ux.md) for current contracts and guardrails.
- Record historical implementation detail in [`docs/roadmap-archive.md`](roadmap-archive.md), not in this active roadmap.
- Before changing roadmap priorities, confirm the product decision and update only the current sections.
