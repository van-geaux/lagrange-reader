# Handover

Last updated: 2026-07-18

## Current outcome

The active user work order is implemented through the EPUB preparation/resource follow-up. The latest app includes the revised book-detail actions, server-wide Home aggregation, Local books shelves, complete comic interactions, five app themes, server switching, Achievements, stable jump rails, series-neighbor navigation, stale progress recovery, and the latest EPUB fixes.

The newest EPUB step fixes two separate regressions:

1. Nonlocal EPUB/PDF reading and Preview now download an authenticated temporary reader copy. The previous resolver accidentally applied a CBZ-only archive check to EPUB/PDF, so a nonlocal EPUB could never be prepared even after relogin.
2. Extracted EPUB chapter resources now load through `WebViewAssetLoader` at `appassets.androidplatform.net`. Nested and parent-relative paths receive safe encoded base URLs, the visible reader and hidden page measurer share the same extracted root, and broad WebView file/content access is disabled. Read-only inspection of the live `your name.` sample confirmed matching `OEBPS` chapter references and image entries.

Automated verification passes. The next useful work is physical validation of nonlocal EPUB Preview after relogin and embedded images, followed by the remaining format and responsive edge checks.

## Repository and publishing state

- GitHub: `https://github.com/van-geaux/lagrange-reader`
- Local path: `C:\Users\vangeaux\Desktop\.git_projects\bookorbit-android`
- Branch: `main`
- Remote: `origin` via SSH
- Before this handover commit/push, local `main` is 45 commits ahead of `origin/main`.
- The user requested this handover update and publication. Push is pending until the handover commit is created.
- The user-owned untracked root `AGENTS.md` is intentionally untouched and must not be committed.
- The untracked `.agents/` workspace directory is also intentionally untouched.

Latest implementation commits:

- `596da23 fix: prepare and render epub resources`
- `5f5c850 feat: add local books home shelves`
- `87e240a feat: retain disabled jump rail letters`
- `f5045cf feat: compact detail and achievement actions`
- `7c929ae feat: redesign achievement tiles`
- `d88a1ff fix: recover stale progress file ids`
- `fc94aa5 fix: refine device feedback follow-ups`
- `d7b866a feat: add achievements catalogue`
- `894e511 feat: add selectable dark palettes`
- `3b19388 feat: navigate adjacent series books`
- `053227b feat: add confirmed server switching`
- `e4683c5 fix: keep catalog cards clear of jump rails`
- `7c999c2 fix: align genre filters with server contract`
- `e424d54 feat: add safe local download updates`
- `5a298ca perf: parallelize home library refresh`
- `3b1c32a test: add repository http integration coverage`
- `dd3255a feat: add chapter page slider`
- `dc2df21 fix: make reader close immediate`
- `631a94d feat: add fullscreen comic reader controls`
- `abd806e feat: aggregate home across libraries`
- `4f1c6a6 feat: add data preferences`
- `d457855 feat: add interface preferences`

Consult `git log` plus `CHECKLIST.md` and `docs/roadmap.md` for the complete intervening commit history and work-order record.

## Latest implementation state

### Book details and catalog actions

- Book-detail actions use a wrapping `FlowRow`, so narrow screens no longer hide trailing controls behind an unexplained horizontal swipe.
- Read, Preview, Download/Update/Cancel/Delete local, and the direct live Mark as read/unread action remain labeled.
- The selected detail reconciles immediately after download, update, and Delete local without requiring the user to leave and reopen it.
- Long titles expand from a five-line limit, series name/index remain visible, and series books expose transparent Previous/Next controls backed by one retained complete-series load.
- Genre chips open the official paginated BookOrbit relation filter using singular `genre`, `includesAny`, and an array value. Tags remain informational.
- Multi-book selection supports bulk Mark as read/unread.
- The cover viewer dismisses from any full-screen tap or Android Back. Missing foreground covers fall through to the canonical thumbnail endpoint.

### Home, local state, and large libraries

- Top-level Home aggregates all libraries on the server. Selected-library screens remain scoped to their library.
- Currently Reading and completed-only Recently read derive from the correct server-wide collections.
- Top-level Home has a global Local books shelf; Library Recommended has a selected-library Local books shelf. Each uses a deterministic deduplicated alphabetical preview of up to 12 and a correctly scoped See all route.
- Download/delete/update changes reconcile Room, snapshots, details, Local books, and Home immediately. Local books retain cached thumbnails offline.
- Initial multi-library refresh makes the selected library current first, then refreshes nonselected libraries in deterministic batches of at most three concurrent libraries.
- Library/Series grids reserve a rail gutter. Eligible rails retain the stable `#/A-Z` vocabulary (`Z-A/#` descending); unavailable entries are greyed, disabled, announced as unavailable, and cannot forward to another letter.
- The stale pending-progress queue fix remaps one deleted/stale file-id 404 to the book's current primary file, or acknowledges a terminal invalid target so the debug queue can drain.

### Readers and downloads

- EPUB uses a fullscreen paginated reader with tap/swipe navigation, center-tap options, exact chapter/page resume, chapter selection, a current-chapter page slider, measured whole-book progress, independent margins, themes, and prompt close behavior.
- EPUB resources use a scoped appassets origin instead of broad file URL access. Visible rendering and hidden page measurement resolve the same fonts/images/assets.
- Nonlocal EPUB/PDF opens through an authenticated temporary reader cache; explicit offline downloads remain separate durable files.
- Comic CBZ/CBR works online through authenticated server page extraction. Local ZIP/CBZ works offline. The comic reader matches the novel interaction model: fullscreen fitted pages, tap/swipe page turns, center options, page slider, and options-first Back behavior.
- Download/Update local shows determinate or indeterminate progress, cancel/retry state, cellular policy handling, staged validation, and atomic replacement. Interrupted and large downloads are target-device validated.
- Closing a reader restores cached Browser state immediately and completes persistence/sync/cleanup/refresh in the background. Preview remains isolated from normal progress and active-reader state.

### Options, profile, and appearance

- Interface options include lock orientation, default opening screen, Reduce motion, and a five-item theme list: Follow system, Light, Charcoal, Warm black, and OLED black.
- The former haptic-feedback setting and explicit app-haptic paths were removed by user direction.
- Data options include cellular download behavior (Always/Never/Ask), storage usage, cache clearing, delete-local confirmation, and background network policy.
- Change server appears above Log out. An unchanged normalized URL is a silent no-op; an actual replacement warns, logs out, cancels transient work, validates the new URL, and prefills the next login/setup flow.
- Achievements uses the authenticated server endpoint and compact adaptive information cards with a small server-provided icon, title-row state, metadata, and conditional progress/date.
- Native username/password remains the only authentication flow. Direct OIDC/SSO is deferred until the provider/redirect contract is confirmed.

## Target-device validation already completed

The user has validated the following on the target device/server:

- Read/Preview/Download/Delete local labels, action wrapping, direct read-status action, long titles, and series-index presentation
- Download progress, update/delete reconciliation, interrupted/large downloads, and cellular/background policies
- Multi-selection and genre filtering
- Local thumbnails and offline availability changes
- Server-wide Home/Currently Reading aggregation
- Prompt reader close, EPUB chapter-page slider, correct whole-book progress, and reader margin controls
- Online/local CBZ and CBR plus fullscreen comic interactions
- Series Previous/Next behavior, Change server, all five themes, system bars, jump-rail spacing, and stale queue recovery
- Functional Achievements flow; the newest compact density still needs physical validation

The newest EPUB resource/Preview fix still needs physical confirmation.

## Verification completed

The final combined command passed before the handover update:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

Results:

- 213 JVM tests across 35 suites
- 0 failures, 0 errors, 0 skipped
- Android lint passed
- Debug APK assembly passed
- Android instrumentation-test APK compilation passed
- Focused EPUB cache/asset tests passed
- `git diff --check` passed; only expected LF-to-CRLF warnings were reported

Compiled instrumentation coverage now includes:

- A real `BookOrbitRepository`/MockWebServer case proving nonlocal EPUB preparation requests `/api/v1/books/files/{fileId}/download` and returns a validated local reader file.
- A real WebView case proving a chapter in `Text/` resolves `../Images/cover.png` through the appassets loader with nonzero `naturalWidth`.

No Android device or emulator was attached, so these newest instrumentation cases compiled but were not executed.

Debug APK:

`app/build/outputs/apk/debug/app-debug.apk`

## Highest-priority next validation

### 1. EPUB Preview and embedded resources

1. Install the latest debug APK and sign in normally.
2. Preview a nonlocal EPUB, including after a forced relogin, and confirm it opens instead of showing the prepare/download/reconnect error.
3. Open `your name.` and representative EPUBs with nested/parent-relative resources. Confirm inline illustrations and covers render, remain page-constrained, and do not destabilize page counts.
4. Confirm the same books reopen offline after an explicit durable download.
5. If a device/emulator becomes attached to this workspace, execute the compiled repository and WebView instrumentation tests.

### 2. Remaining format coverage

- Obtain a representative audiobook before audiobook-specific validation/refinement.
- Validate CB7 online and downloaded behavior. Offline client-side RAR/7z extraction remains an optional enhancement, not current scope.
- Continue representative PDF validation when a suitable sample is available.

### 3. Remaining responsive and failure edges

- Check compact Achievement cards for secret/censored entries, unsupported-server response, retry, conditional metadata, and narrow/wide layouts.
- Check series Previous/Next across long titles, responsive widths, loading transitions, and offline snapshots.
- Check disabled jump rails/gutters across additional widths and orientations.
- Exercise partial failure of a nonselected library during server-wide Home refresh and confirm cached slices remain usable with clear messaging.

### 4. Deferred authentication

- Direct OIDC/SSO remains deferred. Do not restore the embedded server login button without first confirming BookOrbit's provider discovery, redirect URI, token handoff, and mobile callback contract.

## Architecture guardrails

- Do not restore network-backed Browse lazy paging; exact jumps rely on the complete local catalog.
- Keep `LazyVerticalGrid`/lazy lists for UI virtualization; do not compose thousands of cards eagerly.
- Keep Home server-wide while Library Recommended/Browse and library Local books remain selected-library scoped.
- Preserve exact event-ID progress acknowledgement, stale-file remapping, 0-100 percentage scale, and the paired progress plus `reading`/`read` status operation.
- Preserve Preview isolation from normal progress and active-reader state.
- Keep nonlocal EPUB/PDF temporary reader copies separate from durable offline downloads.
- Keep EPUB assets confined to the extracted root/appassets handler. Do not re-enable broad WebView file/content access.
- Keep visible and hidden EPUB WebViews on the same extracted root so image/font timing and whole-book measurement agree.
- Keep comic server-page routing for online CBZ/CBR/CB7 and local extraction only for supported ZIP content unless an approved RAR/7z implementation is added.
- Keep thumbnail keys server/book/URL/catalog-version scoped and background warming bounded by the selected network policy.
- Do not eagerly fetch every rich-detail endpoint for a large catalog.
- Keep explicit HTTP opt-in; bare remote hosts default to HTTPS.
- Do not infer or implement direct OIDC/SSO without an approved mobile redirect/token design.

## Important files for the next session

- `README.md`
- `CHECKLIST.md`
- `docs/roadmap.md`
- `docs/testing.md`
- `docs/architecture.md`
- `docs/ui-ux.md`
- `app/src/main/java/com/bookorbit/android/AppCoordinator.kt`
- `app/src/main/java/com/bookorbit/android/BookOrbitRepository.kt`
- `app/src/main/java/com/bookorbit/android/EpubSupport.kt`
- `app/src/main/java/com/bookorbit/android/BookOrbitApp.kt`
- `app/src/main/java/com/bookorbit/android/BookOrbitHomeScreen.kt`
- `app/src/main/java/com/bookorbit/android/AchievementScreen.kt`
- `app/src/main/java/com/bookorbit/android/ProgressQueueStore.kt`
- `app/src/main/java/com/bookorbit/android/DownloadStore.kt`
- `app/src/main/java/com/bookorbit/android/LibraryCatalogStore.kt`
- `app/src/test/java/com/bookorbit/android/EpubAssetUrlTest.kt`
- `app/src/test/java/com/bookorbit/android/BookOrbitRepositoryHelpersTest.kt`
- `app/src/androidTest/java/com/bookorbit/android/BookOrbitRepositoryIntegrationTest.kt`
- `app/src/androidTest/java/com/bookorbit/android/EpubWebViewInstrumentedTest.kt`

## Known remaining limitations

- The newest nonlocal EPUB Preview and embedded-image fixes need target-device validation.
- No connected device/emulator was available for the latest instrumentation execution.
- Audiobook validation is deferred without a representative sample.
- CB7 validation remains open; offline RAR/7z extraction is optional future work.
- Direct OIDC/SSO is deferred pending a confirmed mobile authentication contract.
- BookOrbit still lacks a reliable complete-catalog revision/delta contract, so metadata refresh must request all pages.
- Some responsive, partial-failure, and unusual Achievement/series edge states remain validation work rather than known implementation failures.

## Environment notes

- JDK 17 and the Android SDK are installed and working.
- `local.properties` points to the Android SDK.
- Gradle requires access to the user cache under `C:\Users\vangeaux\.gradle`.
- The Gradle daemon used for this session was stopped before this handover was updated.
- Git SSH authentication is configured through `origin`.

## Handover maintenance rule

- Update this file only when the user explicitly requests it.
- Stop project servers, watchers, tasks, and session-started Gradle daemons before updating it.
