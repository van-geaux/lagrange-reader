# Handover

Last updated: 2026-07-24

## Current outcome

Lagrange 1.2.0 is published at the GitHub Release for tag `v1.2.0`. The current Git HEAD is `92ba1b9`, and `main` is synchronized with `origin/main`.

The Book Detail rating, complete reading-status menu, reading-status/progress placement repairs, audiobook Session history, Book Detail action-row redesign, About content, current-reading resume repair, global Library card-size setting, and supported reader/media validation are implemented and user-confirmed as working. The Android audiobook controls work in the app player; optional API 33+ pull-down/lock-screen platform validation remains deferred. Release APK publication is automated through GitHub Releases, with the signed `Lagrange-1.2.0.apk` asset published. No project terminal, Gradle process, ADB server, watcher, emulator, or project daemon is running.

## Repository and publishing state

- Repository: `/projects/bookorbit-android`
- Branch: `main`
- Remote: `origin` via SSH
- Published release: `Lagrange 1.2.0`, tagged `v1.2.0`, with `Lagrange-1.2.0.apk` attached
- Current Git HEAD: `92ba1b9 docs: clarify current roadmap and release state`; `main` is synchronized with `origin/main`.
- Release migration: `fa3481e build: publish release APKs through GitHub Releases`
- The tracked `app/build/release-artifacts/Lagrange-1.1.0.apk` and the custom `packageReleaseApk` task were removed. The local release output is `app/build/outputs/apk/release/app-release.apk`.
- `.github/workflows/android-release.yml` builds signed `Lagrange-<tag-version>.apk` assets for `v*` tag pushes and creates the GitHub Release using `RELEASE_KEYSTORE_BASE64`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, and `RELEASE_KEY_PASSWORD` repository secrets.
- The historical `v1.1.0` release already contains `Lagrange-1.1.0.apk`.
- The release workflow publishes the matching `docs/release-notes/v<version>.md` file as the full GitHub Release body.
- Push only when explicitly requested.
- Development-machine migration completed to `vangeaux@192.168.1.5:/projects/bookorbit-android`. The project source, Git history, current worktree, untracked `sample/` media, `keystore.properties`, and `release-key.jks` were copied. `.gradle/`, all `build/` directories, `.idea/`, and Windows-specific `local.properties` were excluded. The Debian machine still needs its own JDK/Android SDK setup and Debian-specific `local.properties`.

Implementation and work-order commits since `origin/main`, excluding this planning/handover commit, newest first:

- `9d80341 fix: keep more sheet over book details`
- `644f0df fix: dismiss Book Detail when app navigation changes destination`
- `acae5a7 fix: keep app bottom navigation visible on Book Detail`
- `aa251a6 docs: mark GitHub release update flow complete`
- `fb14ca1 build: add timestamped debug APK handoff`
- `e78f881 docs: add Book Detail backlog items`
- `0c51ed8 feat: add complete book reading status menu`
- `87d20dd feat: notify users about newer GitHub releases`
- `b6bee4c docs: queue GitHub release update notifications`
- `fa3481e build: publish release APKs through GitHub Releases`
- `434a142 docs: record audiobook fix and pc agent audit`
- `77695b1 fix: expose audiobook platform seek controls`
- `b4a9097 docs: update audiobook handover`
- `e452d24 fix: expose audiobook notification seeking`
- `62521e9 fix: restore audiobook preparation`
- `179ccad docs: refresh current handover`
- `4621160 feat: add server-backed user ratings`
- `eea3bb4 fix: expose audiobook seek controls`
- `bc4fedf docs: define card sizing and audiobook history`
- `fcdcf52 docs: queue new user feedback work order`

## Completed work and resolved priority defect

### Audiobook preparation regression repair

Both local and streamed audiobooks became stuck indefinitely on `Preparing audiobook` after explicit Media3 dependencies were raised to 1.9.0 while Readium remained at 3.0.2. Commit `62521e9` restores the explicit Media3 dependencies to compatible 1.4.1 versions while preserving the notification-seek design.

Preparation now has a 10-second service-bind timeout and a 30-second engine-ready timeout. Cancellation and failure clear the preparing state non-cancellably, close partially created publications/engines/sessions, recheck Media3 ready/error state after listener registration, and log restore failures. Its full gate passed 299 JVM tests across 50 suites, lint with zero errors, and both APK assemblies.

### Android system audiobook notification controls - fixed and device-confirmed

Commit 77695b1 fixes the API 33+ platform-session gap behind the audiobook player-button defect:

- Back 10 and Forward 30 now use stable custom SessionCommands and session-command CommandButtons.
- The media-session callback authorizes those commands, exposes the custom layout, and dispatches them through the active player's bounded seek operations.
- The existing standard seek commands and previous/next filtering remain intact, so external controllers receive timed seeking without incorrect chapter semantics.
- The same buttons continue through the notification provider for API 26-32.
- Focused JVM and instrumentation coverage verifies command ordering, authorization, provider compact-view indices, and the custom action strings.

The final automated gate passed 300 JVM tests across 50 suites with zero failures, errors, or skips; lint reports zero errors and 39 warnings; and both debug APKs assemble:

app/build/outputs/apk/debug/app-debug.apk

app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk

The root cause was Media3 1.4.1's platform bridge: API 33+ derives SystemUI controls from platform PlaybackState, and custom-layout buttons become platform custom actions only when they carry custom session commands. The earlier implementation used player seek commands on those buttons, so Android exposed only Play/Pause after previous/next were filtered.

The user has now confirmed that the audiobook player buttons work, closing the reported API 33+ only-Play/Pause defect. A broader physical matrix - streamed versus local, lock screen, boundaries, task relaunch, paused/background state, and headset/Bluetooth - is optional follow-up where not already checked, not an implementation blocker.

### Book Detail personal rating

`BookDetailInfo.userRating` represents BookOrbit's authenticated per-user rating only: a whole value from 1 through 5 or null. It is distinct from decimal or aggregate metadata.

- The Book Detail order is cover, reading-status/percentage line, then five editable stars.
- Tapping 1-5 sets the rating; tapping the selected star clears it.
- The UI updates optimistically, disables itself offline and while a write is active, reconciles to the authoritative response, and rolls back on failure.
- Writes use `POST /api/v1/books/bulk-set-rating` with an integer or JSON null, followed by `GET /api/v1/books/{id}`.
- A returned value that differs from the request is treated as rejection, including metadata-lock behavior.
- Online detail loading is network-first; a version-matching detail cache remains the offline fallback.

The user has confirmed that the personal-rating behavior works. The latest full gate passed 303 JVM tests across 50 suites with zero failures, errors, or skips. Lint reports zero errors with 39 warnings and 14 informational findings, and both debug and Android-test APKs assemble. Broader physical accessibility, online/offline, persistence, rejection, and clearing checks remain optional follow-up.

Debug APK:

`app/build/outputs/apk/debug/Lagrange-debug-202607231939.apk`

Android-test APK:

`app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`

### Timestamped debug APK handoff - 2026-07-23

`assembleDebug` passed and finalized `copyDebugApkWithTimestamp`. The standard Gradle `app-debug.apk` and the timestamped handoff file are the same binary; only one debug build is performed.

The exact artifact produced for the next manual test handoff is:

`app/build/outputs/apk/debug/Lagrange-debug-202607231939.apk`

### Book Detail app navigation and More sheet - 2026-07-23

Book Detail keeps the app Home/Libraries/More bar visible. Home and Libraries navigate away and dismiss Book Detail; More opens over the current Book Detail. Series, Authors, and Local books dismiss Book Detail only after the selected destination is chosen. Regression instrumentation assertions cover the navigation state and More-sheet destination behavior.

The earlier automated final gate passed 303 JVM tests across 50 suites with zero failures, errors, or skips; compilation passed; lint reported zero errors and 39 warnings; and debug assembly passed. That checkpoint's handoff APK was `app/build/outputs/apk/debug/Lagrange-debug-202607231939.apk`; later release validation and user-confirmed physical validation supersede its pending-status note.

### Book Detail reading-status and percentage placement - 2026-07-23

The reading-status/progress line now renders in the cover column directly below the cover and directly above the personal-rating stars. It is no longer repeated in the detail actions section. Instrumentation coverage asserts cover bottom <= progress top <= rating top, and the user confirmed that the result works.

The focused source-set compile and `BookDetailReadingProgressTest` passed. The complete gate passed 303 JVM tests across 50 suites with zero failures, errors, or skips; lint reported zero errors; and the fresh handoff APK was `app/build/outputs/apk/debug/Lagrange-debug-202607232052.apk`. No Android device was connected during automated verification.

### Currently reading server-first resume repair - 2026-07-24

`AppCoordinator.openBook` now performs `syncPendingProgress()` first and then authoritative `loadBookDetail()` before `buildReaderState` for normal online opens. This prevents a stale cached Currently reading `BookSummary` after an app update/reopen from launching at the wrong position. Offline snapshots skip the server refresh and retain cached/local restoration; Preview skips the new server refresh path and remains progress-isolated. The user has confirmed that the fix works, including physical update/reopen validation.

Regression coverage in `AppCoordinatorTest` covers online stale cached browser reopen, offline fallback, and Preview isolation. The latest fresh focused verification passed all 51 `AppCoordinatorTest` tests with 0 failures, errors, or skips. The earlier full gate passed 306 JVM tests across 50 suites; lint reported 0 errors with existing warnings; compilation and both APK assemblies passed.

Artifacts:

- Debug APK: `app/build/outputs/apk/debug/Lagrange-debug-202607240803.apk`
- Android-test APK: `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`

### About content and global Library card sizing - 2026-07-24

AboutScreen is fully implemented with the real app description, independent/non-official BookOrbit relationship disclaimer, installed version/build, connected server, acknowledgements, and relevant links; the user confirmed the content works.

Library card sizing is implemented as one globally persisted Options preference across all libraries and supported card types. Small uses 88 dp grid cards and 84 dp shelf cards, Medium uses 110 dp / 105 dp, and Large uses 132 dp / 126 dp. Focused tests, compilation, lint, and APK assembly pass. The user confirmed successful physical-device validation of all three sizes, including persistence and consistent application across libraries and card types.

### Audiobook Session history and Book Detail action-row redesign - 2026-07-24

Audiobook Session history is complete and app-private: an audiobook-only Room history records session events locally. Currently-reading audiobook details show a history icon before Play; tapping it opens a scrollable overlay inside Book Detail with Play/Pause labels, event timestamps, exact positions, Clear, and close controls, while selecting an entry reopens or seeks playback at that position. The compact Book Detail action row uses two regions: tight left-aligned actions and an optional More button pinned to the right edge. When More has no content, its trailing slot disappears rather than reserving space.

Verification passed full unit tests, lint, compilation, and `assembleDebug`; focused action-row/history tests also passed. Fresh handoff APK: `app/build/outputs/apk/debug/Lagrange-debug-202607241017.apk`.

## Highest-priority next work

1. Physically/UI validate the completed release overlay and Book Detail app-navigation behavior later, as previously deferred. Validate overlay presentation/scrolling, Acknowledge link launch, Ignore/reopen/offline behavior, Home/Libraries dismissal, More staying over Book Detail, and Series/Authors/Local-books selection dismissal.
2. The tracked release-artifact cleanup is complete. The historical `v1.1.0` APK is uploaded, the `v1.2.0` signed APK is published, and the release-workflow secrets are configured.

Before asking the user to test another build, assemble the debug APK and report the exact generated timestamped path, `app/build/outputs/apk/debug/Lagrange-debug-yyyymmddhhmm.apk`. The standard `app-debug.apk` is the internal Gradle source for that same binary. Use docs/testing.md for the applicable procedure.

## Current architecture guardrails

- Keep audiobook playback foreground-service-owned and compact-only; do not add a fullscreen audiobook player without a user-approved product/GUI decision.
- Connected standalone audio uses BookOrbit's authenticated serve URL directly through Media3. Do not route it back through Readium publication retrieval.
- Explicit downloaded/local audio remains on the proven Readium path unless a future migration is approved.
- Keep explicit Media3 dependencies at the Readium 3.0.2-compatible 1.4.1 line unless a coordinated upgrade is implemented and both local and streamed audiobook preparation are reverified.
- Never implicitly download an entire large audio or comic item for ordinary connected Play/Read.
- Keep explicit downloads independent and usable offline.
- Preserve Preview isolation from normal progress and active-reader persistence.
- During bootstrap, skip restoring audio as a fullscreen Reader; open Browser and let the service restore the compact player paused.
- Preserve exact BookOrbit reading statuses independently from the legacy `isRead` flag.
- Preserve the series-derived On Deck rule; do not map On Hold into On Deck.
- Keep shelf order Currently reading -> On Deck -> Want to read.
- Keep MOBI/AZW/AZW3/FB2 explicitly unsupported unless the user revisits conversion.
- Preserve per-library reader settings, cover ownership/aspect ratios, and square-cover bottom alignment.
- Pdfium currently requires Jetifier because of its transitive Android support-library dependency.
- Treat personal user rating as a whole 1-5-or-null server field, not decimal metadata. Re-fetch after writes so metadata locks cannot appear successful.

## Prior target-device validation

The user previously confirmed the main reader, library, media, responsive, accessibility, and profile/detail groups, including orientation lock, local batch deletion, Authors jump rail, destination icons, exact reading-state shelves, series-derived On Deck, remote audiobook playback, EPUB/PDF/comic behavior, Other versions, reader tutorials, themes, and compact-player placement.

Those earlier confirmations remain valid. The user has now confirmed that the repaired Android audiobook system controls work, closing the reported API 33+ only-Play/Pause defect. Physical validation of the repaired preparation path and the Book Detail personal-rating interaction remains separate unless explicitly confirmed.

## Local agent workflow configuration

`AGENTS.md` and the four `.agents/*.toml` role definitions were improved during this session. They now define:

- generic-runtime role mapping and inherited-setting reporting;
- coherent-step rather than micro-edit verification;
- disjoint editor ownership and two-failure stop behavior;
- source-set compile preflights and artifact freshness reporting;
- narrow documentation routing;
- structured repository exploration with fact/inference separation.

These files are intentionally local-only and are excluded through `.git/info/exclude`:

- `/AGENTS.md`
- `/.agents/`

Strict UTF-8 decoding, TOML parsing, role-policy checks, and Git-ignore checks passed. Do not stage, commit, or suggest publishing these local files.

Subagent activity for the recent audiobook work:

- repository_explorer: Boyle traced the relevant playback/session source and tests for the current fix. Earlier repository explorers Ramanujan diagnosed the preparation regression and Schrodinger inspected Media3 1.4.1 behavior.
- mechanical_editor: Laplace attempted a bounded test edit during the earlier repair, but the Windows restricted-token split-writable-root sandbox blocked both patch attempts; the main agent completed and reviewed the test work.
- verification_runner: Lorentz ran the final current verification gate. Earlier verifiers Halley and Helmholtz covered the preparation and notification repairs.
- document_editor: Bacon synchronized the current checklist/roadmap status. Hooke began this explicitly requested handover update but stopped after a patch-stream transport failure; the main agent reviewed the partial diff and completed the document.

The runtime exposed generic multi_agent_v1 delegation identities rather than per-role model/reasoning overrides. The configured role instructions were passed in each delegated prompt; no configured override was claimed where the runtime did not expose one.

## Agent and PC/runtime problem audit

| Problem encountered | What happened | Fixable? |
| --- | --- | --- |
| Windows restricted-token split-writable-root sandbox | apply_patch was refused because the sandbox could not enforce the workspace writable-root set directly. This affected delegated editors and the main agent. | Yes. Reconcile the sandbox writable-root configuration or run the patch operation in an approved workspace context. The reviewed-patch workaround was to generate a normalized UTF-8 Git patch in the workspace and apply it with approved Git execution. |
| C:\tmp permission mismatch | Temporary inspection-directory creation was denied even though the environment advertised C:\tmp as writable. | Yes. Reconcile the sandbox root and Windows ACLs. Workspace-local temporary files were a safe workaround and were removed afterward. |
| apply_patch transport and encoding | UTF-8 patch input through stdin was rejected; multiline PowerShell argument transport produced missing-end-marker/tokenization errors, and some attempts lacked a final newline. | Yes. Fix the wrapper's UTF-8 argument and newline handling. Generated Git diffs with normalized LF endings avoided the transport path. |
| Git metadata lock permission | Git initially could not create .git/index.lock with Permission denied, so staging was blocked until approved Git execution was used. | Yes. Grant the Git process metadata-write permission or use the approved Git execution context. No repository data was lost. |
| No enumerated ADB target | The final automated run could compile instrumentation but could not execute it on a physical API 33+ device or emulator. | Yes. Connect/configure an API 33+ device or emulator and ensure ADB enumeration. The user's later confirmation closes the reported button defect, while broader matrix checks can still be run if desired. |
| Android framework stubs in local JVM tests | Local JVM stubs returned null for Bundle.EMPTY; framework helpers such as TextUtils.equals and SparseBooleanArray.append were not mocked. | Yes. Use JVM-safe construction and assertions, or move framework-dependent checks to instrumentation/Robolectric. The current tests use Bundle() and compare stable action strings. |
| PowerShell/patch orchestration mistakes | No-match rg exit codes, malformed Select-String, accidental literal escape text, manual hunk-count errors, and missing-newline patches caused avoidable command failures. | Yes. These are command-construction issues, not project or PC defects. Use safer command handling and generated diffs. |
| Delegation capability-report mismatch | Generic delegation was available and used, but some child reports incorrectly said no callable delegation capability was exposed. | Yes. Improve runtime capability propagation/reporting. It did not block repository work. |

JDK 17, the Android SDK, the Gradle wrapper, and the project build remain functional; the final compile/test/lint/APK gate passed. No project terminal, Gradle daemon, ADB server, watcher, emulator, or other session-started process remains running.

## Protected working-tree changes

The following local user-owned fixture remains intentionally untracked and must not be staged or committed:

- Untracked `sample/`

All requested source, test, release, and documentation changes are committed and pushed.

## Important files for the next session

- `CHECKLIST.md`
- `docs/roadmap.md`
- `docs/testing.md`
- `docs/architecture.md`
- `docs/ui-ux.md`
- `docs/bookorbit-api.md`
- `docs/release.md`
- `.github/workflows/android-release.yml`
- `app/build.gradle.kts`
- `app/src/main/java/com/bookorbit/android/AppCoordinator.kt`
- `app/src/test/java/com/bookorbit/android/AppCoordinatorTest.kt`
- `app/src/main/java/com/bookorbit/android/BookOrbitApp.kt`
- `app/src/main/java/com/bookorbit/android/BookOrbitHomeScreen.kt`
- `app/src/main/java/com/bookorbit/android/BookOrbitRepository.kt`
- `app/src/main/java/com/bookorbit/android/ReadiumAudioPlayback.kt`
- `app/src/main/java/com/bookorbit/android/ReadiumAudioPlayerOverlay.kt`
- `app/src/androidTest/java/com/bookorbit/android/AudiobookMediaNotificationInstrumentedTest.kt`
- `app/src/androidTest/java/com/bookorbit/android/ReadiumAudioOpenInstrumentedTest.kt`

## Environment notes

- JDK 17, the Android SDK, the Gradle wrapper, and the project build are installed and working.
- Release migration verification passed `git diff --check`, `:app:compileDebugKotlin`, `:app:assembleRelease`, and the signed 1.2.0 GitHub Actions release build; the local release APK is at `app/build/outputs/apk/release/app-release.apk` (61,746,610 bytes).
- The Gradle task-list check still intermittently hits the known `%USERPROFILE%\.gradle\wrapper\dists\gradle-8.7-bin\...\gradle-8.7-bin.zip.lck` permission issue. This did not block release assembly.
- The historical release APK is absent from the working tree and active release-artifact/package-task references are absent. It remains in Git history until any separate history rewrite is approved.
- No project, Gradle, ADB, watcher, emulator, or project daemon process started during this work remains running.
- Git SSH authentication is configured through origin.

## Development-machine migration - 2026-07-23

The current project was transferred to Debian host `vangeaux@192.168.1.5:/projects/bookorbit-android`.

- The destination was created as `vangeaux:vangeaux` and verified writable.
- The copied tree includes source, `.git`, current uncommitted worktree changes, untracked `sample/` media, `keystore.properties`, and `release-key.jks`.
- The transfer excluded `.gradle/`, all `build/` directories, `.idea/`, and Windows-specific `local.properties`.
- Historical migration verification matched the transferred tree, the `BookOrbitHomeScreen.kt` SHA-256, and a sample media file size; the temporary archive was removed on both machines.
- Next setup on Debian: install JDK 17 and the Android SDK, configure SDK paths in a new Debian-specific `local.properties`, then run the Gradle wrapper checks.

## Handover maintenance rule

- Update this file only when the user explicitly requests it.
- Stop session-started project terminals, servers, watchers, emulators, and Gradle daemons before updating it.
