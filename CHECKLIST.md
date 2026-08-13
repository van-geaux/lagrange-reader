# Lagrange Reader Checklist

This is the active completion and validation checklist. Historical completed work orders and dated verification logs are preserved locally in `docs/checklist-archive.md` (ignored by Git, not part of the public documentation set).

## Current release

- [x] Lagrange 1.4.2 released and integrated into `main`.
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

- [x] Validate the complete interrupted-download and failed-update lifecycle on a connected physical device or emulator; the user confirmed the lifecycle works correctly.
- [x] Reconcile the remaining device-validation items against [`docs/testing.md`](docs/testing.md); the user confirmed the validated reader, media, navigation, and session behavior works correctly.
- [ ] Validate native OIDC only after deployed BookOrbit mobile-redirect support is available; do not treat the interim WebView as native AppAuth.

## Active product follow-up

- [ ] **Highest priority — [#55](https://github.com/van-geaux/lagrange-reader/issues/55): Synced highlighting and search.** Phase 1 global annotation hub and Phase 2 annotation-to-reader navigation are implemented. EPUB text selection via Readium ActionMode, highlight/note creation against `POST /api/v1/books/{bookId}/annotations`, highlight/underline decorations, note edit/delete, durable offline mutation replay, and local-to-server annotation ID mapping are implemented. Annotation taps open the saved EPUB location in isolated Preview and render only the tapped annotation's saved color/style without changing normal reading state. Remaining acceptance work is PDF/comic annotation creation and publication full-text search. The latest full JVM suite passed 496 tests with 0 failures, errors, or skips; main and Android-test Kotlin compilation, lint, and debug APK assembly passed. No connected device was available for the latest automated gate; the user subsequently confirmed the annotation Preview highlight works.
- [ ] Select the next user-directed roadmap or issue item after issue #55; other new implementation work is deferred while #55 is the active priority.
- [ ] Keep MOBI, AZW, AZW3, and FB2 explicitly unsupported unless the user approves a conversion/support plan.
- [ ] Treat broader offline RAR/7z extraction and optional device matrices as separately scoped follow-up.
- [x] [#24](https://github.com/van-geaux/lagrange-reader/issues/24): keep EPUB reading-direction changes limited to switching the left-to-right/right-to-left tap region; preserve text alignment, punctuation placement, and all other typography/layout settings. Automated coverage and user-confirmed on-device validation are complete.
- [ ] [#25](https://github.com/van-geaux/lagrange-reader/issues/25): research whether Android e-ink devices can be supported by lowering the minimum Android version, using Boox Lumi devices with Android 10 as a research example; research is complete, but physical validation is deferred pending a response from a user who has the device. No minSdk change is currently recommended. This item is separate from [#36](https://github.com/van-geaux/lagrange-reader/issues/36).
- [x] [#31](https://github.com/van-geaux/lagrange-reader/issues/31): wrap reader-option choices onto additional rows when the available width is insufficient, so users do not need to swipe horizontally to see the complete set of options. Automated verification passed and the user confirmed the rebuilt debug APK works on-device.
- [x] [#23](https://github.com/van-geaux/lagrange-reader/issues/23): make the reading-options window adjustable for live preview, with a default height of approximately two-thirds of the screen so the reader remains visible while settings are changed; include the already requested persisted EPUB line-spacing option. Automated coverage and user-confirmed on-device validation are complete.
- [x] [#26](https://github.com/van-geaux/lagrange-reader/issues/26) (related to [#23](https://github.com/van-geaux/lagrange-reader/issues/23)): add a persisted EPUB option for default word spacing without changing reading direction, alignment, or unrelated typography settings. Implemented with a 0.0–1.0 rem live Readium user-CSS control; automated and user-confirmed device validation are complete.
- [x] [#27](https://github.com/van-geaux/lagrange-reader/issues/27): research and implement configurable tap-zone layouts plus None/Horizontal/Vertical/Both inversion for EPUB, PDF, paginated comics, and continuous comic mode; use equal horizontal thirds for the default Previous/Menu/Next layout and equal vertical thirds for the Vertical thirds layout, keep reading direction separate from typography, and verify mode-specific adapters. Changing tap-zone layout or inversion from reader options re-shows the tutorial over the reading surface while keeping the options sheet above it. Automated and user-confirmed device validation are complete.
- [x] [#37](https://github.com/van-geaux/lagrange-reader/issues/37): expose compatible same-book files through a Book Detail Available file dropdown, preserve the selected file ID through detail hydration, and keep unknown attachments such as JSON out of reader/media selection. Automated verification is complete and the user confirmed the fix works on-device. Delivered through PR #39 (open, mergeable, CI passed, not yet merged into `main`).
- [x] [#40](https://github.com/van-geaux/lagrange-reader/issues/40): keep Home/Libraries/More visible across every browser-owned screen, including Search and catalog/detail/genre views, while leaving dedicated reader/player surfaces unchanged. Automated verification passed and the user confirmed the fix works correctly. Commit `ea36ff2` is not in `origin/main` and has no associated pull request yet; see local `docs/handover.md` for the delivery gap.
- [x] [#41](https://github.com/van-geaux/lagrange-reader/issues/41): clear persisted active-reader state when reader preparation/opening fails, return to the browser with an error message, and prevent failed readers from being restored after restart. Automated verification passed and the user confirmed the fix works on-device. Commit `0c31df3` is not in `origin/main` and has no associated pull request yet.
- [x] [#36](https://github.com/van-geaux/lagrange-reader/issues/36): "Cannot skip to next Mp3 Chapter" for multi-file MP3 audiobooks (separate per-chapter files such as "01 of 20.mp3", "02 of 20.mp3"). Connected split-MP3 playback now builds an authenticated Media3 playlist with one item per ordered file, auto-advances between files, and maps chapter/slider/seek actions and progress reporting onto the complete audiobook timeline; single M4B and downloaded/local audio remain unchanged. Automated verification is complete and the user confirmed the fix works with the supplied `sample/issue-36-gone-girl/` files; see `docs/testing.md` for the reusable regression procedure. Delivered through PR #46 (open, mergeable, CI passed, not yet merged; depends on PR #39/issue #37).
- [x] [#42](https://github.com/van-geaux/lagrange-reader/issues/42): "Cached series Previous/Next navigation fails offline"; when cached series books are available offline or in airplane mode, opening the target book directly works, but tapping Previous/Next from an adjacent cached book (for example index 2 to 3 or 3 to 2) now opens the cached target detail. Implemented and the user confirmed the fix works on-device. Delivered through PR #45 (open, mergeable, CI passed, not yet merged).
- [x] [#43](https://github.com/van-geaux/lagrange-reader/issues/43): user-selected offline library caching now provides independent detail/thumbnail categories, manual progress/cancellation/clearing, a 256 MB thumbnail cap, and optional daily unmetered automatic refresh. JVM tests, lint, Android-test compilation, and debug assemblies pass; the user confirmed the feature works on-device. Delivered through PR #44 (open, mergeable, CI passed, not yet merged).
- [x] [#38](https://github.com/van-geaux/lagrange-reader/issues/38): whole-series and selected-library download use file-level selection and collection-level warnings, series files dispatch in ascending index order, active operations expose aggregate progress, completed collections hide download actions, and series/library screens provide confirmed mass deletion of local copies. Library actions sit below the Browse filter row; Shelves remain deferred. Automated verification is complete; physical-device validation remains pending.
- [x] [#19](https://github.com/van-geaux/lagrange-reader/issues/19): preserve current-package reader appearance preferences through a versioned, tolerant per-library store; legacy flat profiles remain readable, and old-package migration remains intentionally out of scope.
- [x] [#20](https://github.com/van-geaux/lagrange-reader/issues/20): keep Previous/Next book navigation in the current library and format family first; fall back to the same family by user library order, then use the defined fallback format-family order within the first library containing the indexed target. JVM coverage includes second/third library regressions, audiobook-family variants, duplicate indexes, and unavailable boundaries. User confirmed the rebuilt debug APK works on-device.
- [x] [#21](https://github.com/van-geaux/lagrange-reader/issues/21): research and implement server-registered reading sessions/attempts across EPUB/KEPUB, PDF, CBZ/CBR/CB7, and audiobooks, while preserving local exact-position audiobook history as separate seek authority. Automated and user-confirmed device/server validation are complete.
- [x] [#50](https://github.com/van-geaux/lagrange-reader/issues/50): add one Check for updates button in the About screen; it shows checking/result states and shows the existing update card again when an update is available. Automated verification is complete; physical-device validation remains pending.

## Verification handoff

Before asking for manual testing:

1. Build the debug APK with `assembleDebug`.
2. Report the exact timestamped handoff APK path.
3. Use the applicable procedure in [`docs/testing.md`](docs/testing.md).
4. Distinguish compiled Android instrumentation from instrumentation executed on a connected device.
5. Preserve unrelated worktree changes and review the complete diff.
