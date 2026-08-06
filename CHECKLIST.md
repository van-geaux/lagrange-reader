# Lagrange Reader Checklist

This is the active completion and validation checklist. Historical completed work orders and dated verification logs are preserved in [`docs/checklist-archive.md`](docs/checklist-archive.md).

## Current release

- [x] Lagrange 1.4.1 released and integrated into `main`.
- [x] Release notes, signed asset, release workflow, merge state, and current branch state verified.
- [x] Current debug/unit/Android-test compilation and release-related verification recorded in the handover and testing documents.

## Current implementation status

- [x] Pull-to-refresh implemented for Book Detail, Series, Authors, Local books, Statistics, and Achievements.
- [x] Continuous EPUB active-resource seek rail implemented and user-confirmed on-device.
- [x] EPUB reader font selection implemented, including Publisher default, built-in accessibility choices, and one imported custom-font slot.
- [x] Staged startup behavior implemented; cached content remains usable without a blocking global spinner.
- [x] Server-missing catalog state implemented with distinct visibility, overlay, and action behavior.
- [x] Download lifecycle state handling implemented with separate attempts and completed local copies.
- [x] Interim server-hosted sign-in WebView implemented; native AppAuth remains deferred by the upstream redirect contract.

## Active validation

- [ ] Validate the complete interrupted-download and failed-update lifecycle on a connected physical device or emulator when an ADB target is available.
- [ ] Reconcile any remaining device-validation items against [`docs/testing.md`](docs/testing.md) before requesting another manual APK test.
- [ ] Validate native OIDC only after deployed BookOrbit mobile-redirect support is available; do not treat the interim WebView as native AppAuth.

## Active product follow-up

- [ ] Select the next user-directed roadmap or issue item before implementation.
- [ ] Keep MOBI, AZW, AZW3, and FB2 explicitly unsupported unless the user approves a conversion/support plan.
- [ ] Treat broader offline RAR/7z extraction and optional device matrices as separately scoped follow-up.
- [x] [#24](https://github.com/van-geaux/lagrange-reader/issues/24): keep EPUB reading-direction changes limited to switching the left-to-right/right-to-left tap region; preserve text alignment, punctuation placement, and all other typography/layout settings. Automated coverage and user-confirmed on-device validation are complete.
- [x] [#31](https://github.com/van-geaux/lagrange-reader/issues/31): wrap reader-option choices onto additional rows when the available width is insufficient, so users do not need to swipe horizontally to see the complete set of options. Automated verification passed and the user confirmed the rebuilt debug APK works on-device.
- [x] [#23](https://github.com/van-geaux/lagrange-reader/issues/23): make the reading-options window adjustable for live preview, with a default height of approximately two-thirds of the screen so the reader remains visible while settings are changed; include the already requested persisted EPUB line-spacing option. Automated coverage and user-confirmed on-device validation are complete.
- [x] [#26](https://github.com/van-geaux/lagrange-reader/issues/26) (related to [#23](https://github.com/van-geaux/lagrange-reader/issues/23)): add a persisted EPUB option for default word spacing without changing reading direction, alignment, or unrelated typography settings. Implemented with a 0.0–1.0 rem live Readium user-CSS control; automated verification is complete and device validation remains in the testing procedure.
- [x] [#27](https://github.com/van-geaux/lagrange-reader/issues/27): research and implement configurable tap-zone layouts plus None/Horizontal/Vertical/Both inversion for EPUB, PDF, paginated comics, and continuous comic mode; use equal horizontal thirds for the default Previous/Menu/Next layout and equal vertical thirds for the Vertical thirds layout, keep reading direction separate from typography, and verify mode-specific adapters. Changing tap-zone layout or inversion from reader options re-shows the tutorial over the reading surface while keeping the options sheet above it. Automated verification is complete; device validation remains in the testing procedure.
- [x] [#37](https://github.com/van-geaux/lagrange-reader/issues/37): expose compatible same-book files through a separated Book Detail Available file bottom-sheet picker, preserve selected file IDs and metadata through detail hydration/cache, keep unknown attachments such as JSON out of reader/media selection, and preserve the configured square audiobook cover ratio. Automated verification and user-confirmed manual validation are complete.
- [x] [#40](https://github.com/van-geaux/lagrange-reader/issues/40): keep Home/Libraries/More visible across every browser-owned screen, including Search and catalog/detail/genre views, while leaving dedicated reader/player surfaces unchanged. Focused instrumentation coverage and main/Android-test compilation passed; device execution remains pending because no ADB target is connected.
- [x] [#19](https://github.com/van-geaux/lagrange-reader/issues/19): preserve current-package reader appearance preferences through a versioned, tolerant per-library store; legacy flat profiles remain readable, and old-package migration remains intentionally out of scope.

## Verification handoff

Before asking for manual testing:

1. Build the debug APK with `assembleDebug`.
2. Report the exact timestamped handoff APK path.
3. Use the applicable procedure in [`docs/testing.md`](docs/testing.md).
4. Distinguish compiled Android instrumentation from instrumentation executed on a connected device.
5. Preserve unrelated worktree changes and review the complete diff.
- [x] [#41](https://github.com/van-geaux/lagrange-reader/issues/41): clear persisted active-reader state when reader preparation/opening fails, return to the browser with an error message, and prevent failed readers from being restored after restart. Automated verification passed; the user confirmed the fix works on-device.
