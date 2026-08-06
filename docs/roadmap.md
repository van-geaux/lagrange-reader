# Roadmap

This document contains the current project direction only. Historical work orders and dated implementation logs are preserved in [`docs/roadmap-archive.md`](roadmap-archive.md).

## Current status

- Release: Lagrange 1.4.1 is published. See [`docs/release-notes/v1.4.1.md`](release-notes/v1.4.1.md).
- Branch: `main`, aligned with `origin/main`.
- Recent completed work: behavior-neutral cleanup, release integration, pull-to-refresh, continuous EPUB seeking, reader fonts including one imported custom font, staged startup, server-missing catalog state, and download lifecycle implementation.
- Current implementation state: issue #23 reader-options resizing and persisted EPUB line spacing are implemented and user-confirmed on-device; complete device validation for the unrelated download/session follow-ups before release handoff.

## Active validation and follow-up

1. Validate the complete interrupted-download and failed-update lifecycle on a connected device or emulator when an ADB target is available. This is the remaining validation called out by the active checklist and testing procedure.
2. Reconcile any remaining physical-device checks against the current test matrix before asking for another APK.
3. Keep native AppAuth/Custom Tabs deferred until BookOrbit provides deployed mobile-redirect allow-list support and the identity-provider callback is registered. The interim server-hosted sign-in WebView is implemented.
4. Consider additional format support only through a new user-approved work item; MOBI, AZW, AZW3, and FB2 remain unsupported.
5. Treat broader offline RAR/7z extraction and other optional validation as user-directed follow-up, not an implementation blocker for the current release.
6. [x] [#19](https://github.com/van-geaux/lagrange-reader/issues/19): preserve current-package reader appearance preferences through a versioned, tolerant per-library store; legacy flat profiles remain readable, and old-package migration remains intentionally out of scope.
7. [x] [#24](https://github.com/van-geaux/lagrange-reader/issues/24): keep EPUB reading-direction changes limited to switching the left-to-right/right-to-left tap region; preserve text alignment, punctuation placement, and all other typography/layout settings. Automated coverage and user-confirmed on-device validation are complete.
8. [x] [#31](https://github.com/van-geaux/lagrange-reader/issues/31): wrap reader-option choices onto additional rows when the available width is insufficient, so users do not need to swipe horizontally to see the complete set of options. Automated verification passed and the user confirmed the rebuilt debug APK works on-device.
11. [x] [#23](https://github.com/van-geaux/lagrange-reader/issues/23): make the reading-options window adjustable for live preview, with a default height of approximately two-thirds of the screen so the reader remains visible while settings are changed; include the already requested persisted EPUB line-spacing option. Automated coverage and user-confirmed on-device validation are complete.
12. [x] [#26](https://github.com/van-geaux/lagrange-reader/issues/26) (related to [#23](https://github.com/van-geaux/lagrange-reader/issues/23)): add a persisted EPUB option for default word spacing without changing reading direction, alignment, or unrelated typography settings. Implemented with a 0.0–1.0 rem live Readium user-CSS control; automated verification is complete and device validation remains before release handoff.
13. [x] [#27](https://github.com/van-geaux/lagrange-reader/issues/27): research and implement configurable tap-zone layouts plus None/Horizontal/Vertical/Both inversion for EPUB, PDF, paginated comics, and continuous comic mode; use equal horizontal thirds for the default Previous/Menu/Next layout and equal vertical thirds for the Vertical thirds layout, keep reading direction separate from typography, and verify mode-specific adapters. Changing tap-zone layout or inversion from reader options re-shows the tutorial over the reading surface while keeping the options sheet above it. Automated verification is complete; device validation remains in the testing procedure.
15. [x] [#37](https://github.com/van-geaux/lagrange-reader/issues/37): expose compatible same-book files through a separated Book Detail Available file bottom-sheet picker, preserve selected file IDs and metadata through detail hydration/cache, keep unknown attachments such as JSON out of reader/media selection, and preserve the configured square audiobook cover ratio. Automated verification and user-confirmed manual validation are complete.

## Decision and documentation rules

- Use [`CHECKLIST.md`](../CHECKLIST.md) for active completion and validation status.
- Use [`docs/testing.md`](testing.md) for reusable automated and manual procedures.
- Use [`docs/architecture.md`](architecture.md), [`docs/bookorbit-api.md`](bookorbit-api.md), and [`docs/ui-ux.md`](ui-ux.md) for current contracts and guardrails.
- Record historical implementation detail in [`docs/roadmap-archive.md`](roadmap-archive.md), not in this active roadmap.
- Before changing roadmap priorities, confirm the product decision and update only the current sections.
