# Handover

Last updated: 2026-07-21

## Current outcome

The current reader, audiobook, cover-layout, series-grouping, and profile-menu work order is implemented. Every supported publication route now uses Readium 3.0.2:

- EPUB and KEPUB open directly with Readium.
- PDF uses the Readium PDF navigator with the pinned Pdfium adapter.
- Audiobooks use the Readium audio navigator through a persistent compact player and media service.
- CBZ opens directly; connected CBR/CB7 is normalized to cached CBZ before opening.
- MOBI, AZW, AZW3, and FB2 remain explicitly unsupported by user choice. They route to `UNSUPPORTED_EBOOK`/`UNKNOWN`, not to a legacy reader or an invalid EPUB attempt.

Legacy routed EPUB, PDF, and comic reader fallbacks have been removed. Preview remains isolated from normal progress and active-reader state, and normal reading restores exact Readium locators.

The latest cover change honors each library's `2/3` portrait or `1/1` square cover setting throughout mixed-library data. Square covers retain their true dimensions and sit at the bottom of the portrait-height card slot so labels align across rows.

## Repository and publishing state

- Repository: `C:\Users\vangeaux\Desktop\.git_projects\bookorbit-android`
- Branch: `main`
- Remote: `origin` via SSH
- Current implementation HEAD: `5a3f82c`
- Before the handover commit, local `main` is 15 commits ahead of `origin/main`.
- The user requested that the handover commit and all preceding local commits be pushed to `origin/main`.

Latest 15 commits, newest first:

- `5a3f82c refine supported formats and cover alignment`
- `8751fa3 test: verify Readium PDF opening on Android`
- `46ab10f feat: migrate PDF reading to Readium`
- `e3cb58f feat: respect library cover aspect ratios`
- `545c16a fix: audit Readium publication routes`
- `91a0d51 fix: reopen browser for active audiobook`
- `d9fa4f6 fix: keep readers above compact player`
- `36654d1 fix: restore audiobook preparation`
- `c4fafe2 feat: refine compact audiobook playback`
- `e6dafcb fix: keep Readium audio on main thread`
- `b2089d0 feat: add persistent compact audiobook player`
- `ea99caa feat: add Readium audiobook service foundation`
- `f48178c docs: scope compact Readium audio player`
- `3ea11ca docs: queue Readium and audiobook work`
- `dda4ea6 docs: record reader and menu validation`

## Implemented reader and UI state

### Reader chrome and tutorial

- The location rail is on the right and occupies about 75% of the reader height.
- EPUB page controls advance one page at a time; a separate control pair changes chapters.
- The options bar sits below the system status bar. Center-tapping toggles it, so its redundant Back action was removed and Exit is on the left with a label.
- Chapter selection and the page rail live in the outer reader UI rather than being duplicated inside the cog menu.
- Tutorial timing is three seconds and any screen tap dismisses it immediately. Tutorial labels have transparent backgrounds and enlarged text.
- The sync/refresh/download lifecycle no longer ejects an active reader to Home.

### Series and profile UI

- Series can be grouped by owning library or file format, with only one mode active at a time and both modes allowed to be inactive. Library grouping is the default and the selected mode persists globally across series.
- Group headers separate books using a label followed by a line extending toward the row edge.
- The main profile menu orders Achievements above Options, uses a cog for Options, places About beneath Options, and separates those items from Change server and Log out.
- Book-detail overflow menus stay near the right edge of their row.

### Compact audiobook playback

- The app deliberately has no fullscreen audiobook player. The persistent compact player overlays app content above bottom navigation and remains available while browsing or opening details.
- Reader content and controls reserve space above the compact player instead of rendering underneath it. Detail-screen placement also stays above Android navigation.
- The player provides chapter selection, seek with elapsed and remaining time, playback speed, rotating-arrow skip controls, and cover-to-book-detail navigation.
- Audiobook details label the primary action Play. Authenticated Preview uses the same Readium preparation route.
- `ReadiumAudioPlaybackService` owns the active navigator and Media3 session so playback can continue in the background and expose Android media controls.
- On process/app relaunch, `AppCoordinator` restores Browser first and lets the surviving service repopulate the compact player. It must not restore AUDIO as a fullscreen reader, which previously caused the perpetual preparation screen.

### Covers

- Library `coverAspectRatio` is parsed and propagated to books by owning library, including mixed-library and series results.
- The value is persisted through Room catalog rows, Browser snapshots, active-reader state, and detail cache; portrait is the compatibility fallback.
- Poster/shelf cards, cover-viewer artwork, and compact-player artwork render at their true `2/3` or `1/1` aspect ratio. Square poster covers are bottom-aligned within portrait-height slots so adjacent labels line up.

## User validation already completed

The user has confirmed on the target device/server:

- EPUB, CBZ, connected CBR, and audiobook opening/playback work.
- Sync, refresh, and downloads no longer kick the user out of the reading screen.
- Reader Exit and the revised tutorial overlay work.
- Series grouping and its labels work for both library and file-format separation.
- The revised profile-menu ordering works.
- The compact audiobook player works after preparation, placement, controls, Preview, and reader/detail overlap fixes.

The latest square-cover bottom alignment and Readium PDF interaction matrix still need target-device confirmation.

## Verification completed

The final combined gate passed:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

Results:

- 265 JVM tests across 46 suites
- 0 failures, 0 errors, 0 skipped
- Android lint passed
- Debug APK assembly passed
- Android instrumentation-test APK assembly passed

A generated three-page PDF was also opened by the connected `Medium_Phone` AVD (API 17). The test confirmed `Publication.Profile.PDF` and exactly three Readium positions.

Debug APK:

`app/build/outputs/apk/debug/app-debug.apk`

## Highest-priority next validation

1. Validate a real BookOrbit PDF in normal and Preview modes: first open, page navigation, options/chrome, resume locator, reopen, and exit.
2. Check mixed portrait/square shelves and series on narrow and wide layouts. Confirm square covers sit at the bottom and every title/metadata label aligns.
3. Recheck audiobook relaunch with an active session: the app must open Browser with the compact player, never a blank preparing screen.
4. Validate authenticated audiobook Preview and the revised compact controls across narrow/wide layouts.
5. Validate notification and lock-screen controls, wired-headset/Bluetooth controls, background playback, process recreation, accessibility announcements/touch targets, and responsive layouts.

Before handing target-device testing to the user, rebuild the debug APK and report the exact path above.

## Architecture guardrails

- All supported publication formats must remain on Readium routes; do not restore legacy reader fallbacks.
- Keep MOBI/AZW/AZW3/FB2 explicitly unsupported unless the user revisits the conversion decision.
- Keep Preview isolated from normal reading progress and active-reader persistence.
- Persist exact Readium locators for normal reading rather than approximating position from percentages.
- Keep audiobook playback service-owned and compact-only. Do not add a fullscreen audio player.
- During bootstrap, skip restoring an AUDIO active reader; open Browser and allow the playback service to restore the compact player.
- Keep reader content above the compact player and keep the compact player above app/system navigation.
- Preserve per-library cover ownership and aspect-ratio persistence. Do not add top/bottom padding inside square artwork; use the bottom-aligned outer slot for card-label alignment.
- Connected CBR/CB7 may normalize to cached CBZ. Offline direct CBR/CB7 remains unavailable without an approved local RAR/7z implementation or an existing cached CBZ.
- Pdfium currently requires Jetifier because of its transitive Android support-library dependency.

## Important files for the next session

- `README.md`
- `CHECKLIST.md`
- `docs/roadmap.md`
- `docs/testing.md`
- `docs/architecture.md`
- `docs/ui-ux.md`
- `app/src/main/java/com/bookorbit/android/AppCoordinator.kt`
- `app/src/main/java/com/bookorbit/android/BookOrbitApp.kt`
- `app/src/main/java/com/bookorbit/android/BookOrbitHomeScreen.kt`
- `app/src/main/java/com/bookorbit/android/BookOrbitRepository.kt`
- `app/src/main/java/com/bookorbit/android/ReadiumPublicationRoute.kt`
- `app/src/main/java/com/bookorbit/android/ReadiumPdfReaderLauncher.kt`
- `app/src/main/java/com/bookorbit/android/ReadiumPdfReaderActivity.kt`
- `app/src/main/java/com/bookorbit/android/ReadiumEpubReaderActivity.kt`
- `app/src/main/java/com/bookorbit/android/ReadiumComicReaderActivity.kt`
- `app/src/main/java/com/bookorbit/android/ReadiumAudioPlayback.kt`
- `app/src/main/java/com/bookorbit/android/ReadiumAudioPlayerOverlay.kt`
- `app/src/androidTest/java/com/bookorbit/android/ReadiumPdfOpenInstrumentedTest.kt`
- `app/src/test/java/com/bookorbit/android/ReadiumPdfReaderRoutingTest.kt`
- `app/src/test/java/com/bookorbit/android/BookCoverLayoutTest.kt`

## Protected working-tree changes

The following pre-existing user-owned changes are unrelated to this handover and must not be staged or committed:

- Modified `app/src/main/res/drawable/ic_launcher_foreground.xml`
- Modified `app/src/main/res/drawable/ic_launcher_monochrome.xml`
- Modified `docs/README.md`
- Untracked `.agents/`
- Untracked `AGENTS.md`
- Untracked `artwork/`
- Untracked `sample/`

## Environment notes

- JDK 17 and the Android SDK are installed and working.
- The Readium PDF route pins `readium-adapter-pdfium:3.0.2`; `android.enableJetifier=true` is required for packaging compatibility.
- The emulator and the Gradle daemon started for the latest work were stopped before this handover update.
- Git SSH authentication is configured through `origin`.

## Handover maintenance rule

- Update this file only when the user explicitly requests it.
- Stop session-started project terminals, servers, watchers, emulators, and Gradle daemons before updating it.
