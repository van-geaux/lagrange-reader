# Handover

Last updated: 2026-07-23

## Current outcome

BookOrbit Android/Lagrange 1.1.0 is published at `origin/main` commit `1556ea5` and tagged `v1.1.0`. Local `main` is 13 commits ahead of `origin/main`. The current implementation HEAD, excluding this handover update, is `b6bee4c`.

The Book Detail rating and audiobook preparation repairs remain implemented. The Android system audiobook control defect is fixed: the user confirms that Back 10 / Play-Pause / Forward 30 now work in the audiobook player. The release-channel migration is implemented in the repository; future tagged releases publish APKs through GitHub Releases instead of storing binaries in Git. The next feedback work is queued and documented. No project terminal, Gradle process, ADB server, watcher, emulator, or project daemon is running.

## Repository and publishing state

- Repository: `C:\Users\vangeaux\Desktop\.git_projects\bookorbit-android`
- Branch: `main`
- Remote: `origin` via SSH
- Published release: `1556ea5 release: package Lagrange 1.1.0`, tagged `v1.1.0`
- Current implementation HEAD, excluding this handover update: `b6bee4c docs: queue GitHub release update notifications`
- Local `main` is 13 commits ahead of `origin/main`.
- Release migration: `fa3481e build: publish release APKs through GitHub Releases`
- The tracked `app/build/release-artifacts/Lagrange-1.1.0.apk` and the custom `packageReleaseApk` task were removed. The local release output is `app/build/outputs/apk/release/app-release.apk`.
- `.github/workflows/android-release.yml` builds signed `Lagrange-<tag-version>.apk` assets for `v*` tag pushes and creates the GitHub Release using `RELEASE_KEYSTORE_BASE64`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, and `RELEASE_KEY_PASSWORD` repository secrets.
- The historical `v1.1.0` APK still needs manual upload to the `v1.1.0` GitHub Release; the workflow has not run remotely and no release asset was published by this session.
- Push only when explicitly requested.

Implementation and work-order commits since `origin/main`, excluding this planning/handover commit, newest first:

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

- Five editable stars appear directly below the Book Detail cover.
- Tapping 1-5 sets the rating; tapping the selected star clears it.
- The UI updates optimistically, disables itself offline and while a write is active, reconciles to the authoritative response, and rolls back on failure.
- Writes use `POST /api/v1/books/bulk-set-rating` with an integer or JSON null, followed by `GET /api/v1/books/{id}`.
- A returned value that differs from the request is treated as rejection, including metadata-lock behavior.
- Online detail loading is network-first; a version-matching detail cache remains the offline fallback.

The latest full gate passed 299 JVM tests across 50 suites with zero failures, errors, or skips. Lint reports zero errors, and both debug and Android-test APKs assemble. Physical layout, accessibility, online/offline, persistence, rejection, and clearing validation remains pending.

Debug APK:

`app/build/outputs/apk/debug/app-debug.apk`

Android-test APK:

`app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`

## Highest-priority next work

1. Investigate whether the app can detect a newer GitHub release tag. If supported, on app open/reopen show an overlay with the release notes and Acknowledge (open the GitHub release link) and Ignore actions.
2. Fix the Book Detail opening path so the Android bottom system/navigation bar remains visible.
3. Name every debug APK `Lagrange-debug-yyyymmddhhmm.apk` using the actual build datetime.
4. If needed, finish any remaining physical audiobook matrix checks not covered by the user's confirmation; the reported API 33+ system-control defect is closed.
5. Physically validate the repaired local and streamed audiobook opening path, including exact -10/+30 boundaries and headset/Bluetooth behavior where available.
6. Physically validate the implemented Book Detail personal rating before marking its device checks complete.
7. Change the reading-status action to Mark as... and expose every BookOrbit status: Unread, Want to read, Reading, Rereading, On hold, Abandoned, Read, and Skimmed. Persist the selected value to the server and local caches.
8. Replace the About destination's placeholder text with the real app description, BookOrbit relationship/disclaimer, version/build information, acknowledgements, and relevant links.
9. Add audiobook-only Session history below the Book Detail actions. The accepted design uses an app-private Room table keyed by canonical server origin plus BookOrbit book/file ID. Record explicit play/pause wall-clock time and exact playback timepoint with duplicate-callback protection, bounded recent per-book retention, and a clear-history action; tapping a timepoint seeks there. Records are never sent to BookOrbit, survive app updates, clear when the configured server changes, and disappear with Android app-data removal on uninstall.
10. Add one global Library card-size option: Small (the current size), Medium, and Large. It must apply across libraries and content types rather than being stored per library or type.

The tracked release-artifact cleanup is complete. The historical `v1.1.0` APK still needs manual upload to the GitHub Release, and the release-workflow secrets must be configured before a future tag can publish automatically.

Before asking the user to test another build, assemble the debug APK and report the exact path: `app/build/outputs/apk/debug/app-debug.apk`. Use docs/testing.md for the applicable procedure.

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

The following user-owned changes remain unrelated and must not be staged or committed:

- Modified `CHECKLIST.md`
- Modified `README.md`
- Modified `app/src/main/res/drawable/ic_launcher_foreground.xml`
- Modified `app/src/main/res/drawable/ic_launcher_monochrome.xml`
- Modified `docs/README.md`
- Modified `docs/roadmap.md`
- Untracked `sample/`

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
- `app/src/main/java/com/bookorbit/android/BookOrbitApp.kt`
- `app/src/main/java/com/bookorbit/android/BookOrbitHomeScreen.kt`
- `app/src/main/java/com/bookorbit/android/BookOrbitRepository.kt`
- `app/src/main/java/com/bookorbit/android/ReadiumAudioPlayback.kt`
- `app/src/main/java/com/bookorbit/android/ReadiumAudioPlayerOverlay.kt`
- `app/src/androidTest/java/com/bookorbit/android/AudiobookMediaNotificationInstrumentedTest.kt`
- `app/src/androidTest/java/com/bookorbit/android/ReadiumAudioOpenInstrumentedTest.kt`

## Environment notes

- JDK 17, the Android SDK, the Gradle wrapper, and the project build are installed and working.
- Release migration verification passed `git diff --check`, `:app:compileDebugKotlin`, and `:app:assembleRelease`; the fresh local release APK is at `app/build/outputs/apk/release/app-release.apk` (61,713,842 bytes, generated 2026-07-23 12:14:25).
- The Gradle task-list check still intermittently hits the known `%USERPROFILE%\.gradle\wrapper\dists\gradle-8.7-bin\...\gradle-8.7-bin.zip.lck` permission issue. This did not block release assembly.
- The historical release APK is absent from the working tree and active release-artifact/package-task references are absent. It remains in Git history until any separate history rewrite is approved.
- No project, Gradle, ADB, watcher, emulator, or project daemon process started during this work remains running.
- Git SSH authentication is configured through origin.

## Handover maintenance rule

- Update this file only when the user explicitly requests it.
- Stop session-started project terminals, servers, watchers, emulators, and Gradle daemons before updating it.
