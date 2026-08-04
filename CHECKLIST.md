# Lagrange Reader Checklist

This is the active completion and validation checklist. Historical completed work orders and dated verification logs are preserved in [`docs/checklist-archive.md`](docs/checklist-archive.md).

## Current release

- [ ] Lagrange 1.4.2 release candidate prepared; release publication and integration remain pending.
- [ ] Release notes, signed asset, release workflow, merge state, and current branch state will be verified after publication.
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

- [x] Validate the complete interrupted-download and failed-update lifecycle on a connected physical device or emulator; the user confirmed the lifecycle works correctly.
- [x] Reconcile the remaining device-validation items against [`docs/testing.md`](docs/testing.md); the user confirmed the validated reader, media, navigation, and session behavior works correctly.
- [ ] Validate native OIDC only after deployed BookOrbit mobile-redirect support is available; do not treat the interim WebView as native AppAuth.

## Active product follow-up

- [ ] Select the next user-directed roadmap or issue item before implementation; new implementation work is deferred for now.
- [ ] Keep MOBI, AZW, AZW3, and FB2 explicitly unsupported unless the user approves a conversion/support plan.
- [ ] Treat broader offline RAR/7z extraction and optional device matrices as separately scoped follow-up.
- [x] [#24](https://github.com/van-geaux/lagrange-reader/issues/24): keep EPUB reading-direction changes limited to switching the left-to-right/right-to-left tap region; preserve text alignment, punctuation placement, and all other typography/layout settings. Automated coverage and user-confirmed on-device validation are complete.
- [ ] [#25](https://github.com/van-geaux/lagrange-reader/issues/25): research whether Android e-ink devices can be supported by lowering the minimum Android version, using Boox Lumi devices with Android 10 as a research example; research is complete, but physical validation is deferred pending a response from a user who has the device. No minSdk change is currently recommended.
- [x] [#31](https://github.com/van-geaux/lagrange-reader/issues/31): wrap reader-option choices onto additional rows when the available width is insufficient, so users do not need to swipe horizontally to see the complete set of options. Automated verification passed and the user confirmed the rebuilt debug APK works on-device.
- [x] [#23](https://github.com/van-geaux/lagrange-reader/issues/23): make the reading-options window adjustable for live preview, with a default height of approximately two-thirds of the screen so the reader remains visible while settings are changed; include the already requested persisted EPUB line-spacing option. Automated coverage and user-confirmed on-device validation are complete.
- [x] [#26](https://github.com/van-geaux/lagrange-reader/issues/26) (related to [#23](https://github.com/van-geaux/lagrange-reader/issues/23)): add a persisted EPUB option for default word spacing without changing reading direction, alignment, or unrelated typography settings. Implemented with a 0.0–1.0 rem live Readium user-CSS control; automated and user-confirmed device validation are complete.
- [x] [#27](https://github.com/van-geaux/lagrange-reader/issues/27): research and implement configurable tap-zone layouts plus None/Horizontal/Vertical/Both inversion for EPUB, PDF, paginated comics, and continuous comic mode; use equal horizontal thirds for the default Previous/Menu/Next layout and equal vertical thirds for the Vertical thirds layout, keep reading direction separate from typography, and verify mode-specific adapters. Changing tap-zone layout or inversion from reader options re-shows the tutorial over the reading surface while keeping the options sheet above it. Automated and user-confirmed device validation are complete.
- [x] [#19](https://github.com/van-geaux/lagrange-reader/issues/19): preserve current-package reader appearance preferences through a versioned, tolerant per-library store; legacy flat profiles remain readable, and old-package migration remains intentionally out of scope.
- [x] [#20](https://github.com/van-geaux/lagrange-reader/issues/20): keep Previous/Next book navigation in the current library and format family first; fall back to the same family by user library order, then use the defined fallback format-family order within the first library containing the indexed target. JVM coverage includes second/third library regressions, audiobook-family variants, duplicate indexes, and unavailable boundaries. User confirmed the rebuilt debug APK works on-device.
- [x] [#21](https://github.com/van-geaux/lagrange-reader/issues/21): research and implement server-registered reading sessions/attempts across EPUB/KEPUB, PDF, CBZ/CBR/CB7, and audiobooks, while preserving local exact-position audiobook history as separate seek authority. Automated and user-confirmed device/server validation are complete.

## Verification handoff

Before asking for manual testing:

1. Build the debug APK with `assembleDebug`.
2. Report the exact timestamped handoff APK path.
3. Use the applicable procedure in [`docs/testing.md`](docs/testing.md).
4. Distinguish compiled Android instrumentation from instrumentation executed on a connected device.
5. Preserve unrelated worktree changes and review the complete diff.
