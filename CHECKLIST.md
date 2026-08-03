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
- [x] [#19](https://github.com/van-geaux/lagrange-reader/issues/19): preserve current-package reader appearance preferences through a versioned, tolerant per-library store; legacy flat profiles remain readable, and old-package migration remains intentionally out of scope.

## Verification handoff

Before asking for manual testing:

1. Build the debug APK with `assembleDebug`.
2. Report the exact timestamped handoff APK path.
3. Use the applicable procedure in [`docs/testing.md`](docs/testing.md).
4. Distinguish compiled Android instrumentation from instrumentation executed on a connected device.
5. Preserve unrelated worktree changes and review the complete diff.
