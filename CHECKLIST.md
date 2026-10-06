# Lagrange Reader Checklist

This is the active completion and validation checklist. Historical work orders and dated verification logs are preserved in [`docs/checklist-archive.md`](docs/checklist-archive.md).

## Current release

- [x] Lagrange 1.5.4 is published as [`v1.5.4`](https://github.com/van-geaux/lagrange-reader/releases/tag/v1.5.4), with combined and ABI-specific APK assets.
- [x] `app/build.gradle.kts` records `versionName 1.5.4` and `versionCode 25`.
- [x] Release validation is recorded in [`docs/release-notes/v1.5.4.md`](docs/release-notes/v1.5.4.md); Android-test sources compiled but no connected device was available during release preparation.

## Current implementation status

- [x] BookOrbit browsing, cache-first offline fallback, downloads, progress/status synchronization, EPUB/PDF/comic reading, audiobook playback, server reading sessions, statistics, achievements, and EPUB image/TTS tools are implemented.
- [x] Provider module separation is implemented for BookOrbit and Komga; provider-specific behavior remains behind provider-owned modules and focused contracts.
- [x] Komga catalog filtering, format sorting, scoped Recommended results, local-reader fallback, download lifecycle projection, and supported format coverage are documented in [`docs/provider-checklists/komga.md`](docs/provider-checklists/komga.md).
- [x] EPUB 3 read-along narration, BookOrbit OIDC WebView sign-in, navigation-bar controls, audiobook speed/progress controls, and grouped physical-file handling are included in 1.5.4.
- [x] CBR/CB7 offline opening uses client-side RAR4/RAR5/7z extraction into a cached CBZ; MOBI, AZW, AZW3, and FB2 remain unsupported.

## Active validation

- [ ] Validate native AppAuth/Custom Tabs only after deployed BookOrbit mobile-redirect support and provider registration are available. Do not describe the existing WebView flow as native AppAuth.
- [ ] Validate issue [#118](https://github.com/van-geaux/lagrange-reader/issues/118) PDF internal navigation, HTTP/HTTPS and `mailto:` external-handler opening, and unsupported-scheme fallback on a connected device or emulator. Automated verification is complete; device execution remains unverified.
- [ ] Re-run the applicable provider checklist when a provider-specific implementation or server contract changes. Keep automated evidence separate from user/device confirmation.

## Active product follow-up

- [ ] Select the next user-directed roadmap or issue item before implementation; no new implementation scope is approved by this checklist.
- [ ] Keep MOBI, AZW, AZW3, and FB2 unsupported unless a conversion/support plan is explicitly approved.
- [ ] Treat broader offline RAR/7z coverage, additional device matrices, and Android e-ink validation for [#25](https://github.com/van-geaux/lagrange-reader/issues/25) as separately scoped follow-up.
- [ ] Keep provider-exclusive UI unavailable when the selected provider does not supply the required capability.

## Verification handoff

Before asking for manual testing:

1. Build the debug APK with `assembleDebug`.
2. Report the exact timestamped handoff APK path and filename.
3. Use the applicable procedure in [`docs/testing.md`](docs/testing.md).
4. Distinguish compiled Android instrumentation from instrumentation executed on a connected device.
5. Preserve unrelated worktree changes and review the complete diff.
