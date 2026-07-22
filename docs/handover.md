# Handover

Last updated: 2026-07-23

## Current outcome

BookOrbit Android/Lagrange 1.1.0 is published at `origin/main` commit `1556ea5` and tagged `v1.1.0`. Before this handover-only commit, local `main` is seven commits ahead; the latest implementation HEAD is `e452d24`.

Two feedback items remain implemented locally, and the audiobook playback regression reported during their validation has been repaired:

- Android system audiobook controls are now implemented through the Media3 notification provider as compact Back 10, Play/Pause, and Forward 30 actions instead of chapter-skip actions. Chapter navigation remains available inside the compact app player. This notification-specific repair is built and automated-test verified but still needs physical-device confirmation.
- Book Detail now shows an editable whole 1-5-star authenticated personal rating below the cover. Tapping the selected star clears it. Writes use BookOrbit's bulk user-rating endpoint, then re-fetch authoritative detail; offline cache fallback and optimistic rollback are implemented.
- The perpetual `Preparing audiobook` regression for both local and streamed books was traced to an explicit Media3 1.9.0/Readium 3.0.2 incompatibility. Media3 is restored to 1.4.1, and playback preparation now has bounded waits plus failure-safe cleanup.

The remaining feedback work is queued and documented. No project terminal, Gradle process, ADB server, watcher, or emulator is running.

## Repository and publishing state

- Repository: `C:\Users\vangeaux\Desktop\.git_projects\bookorbit-android`
- Branch: `main`
- Remote: `origin` via SSH
- Published release: `1556ea5 release: package Lagrange 1.1.0`, tagged `v1.1.0`
- Latest implementation HEAD before this handover-only commit: `e452d24 fix: expose audiobook notification seeking`
- Before this handover refresh, the local branch is seven commits ahead of `origin/main`; including the handover commit it will be eight commits ahead.
- Push only when explicitly requested.

Implementation and work-order commits since `origin/main`, excluding this handover-only commit, newest first:

- `e452d24 fix: expose audiobook notification seeking`
- `62521e9 fix: restore audiobook preparation`
- `179ccad docs: refresh current handover`
- `4621160 feat: add server-backed user ratings`
- `eea3bb4 fix: expose audiobook seek controls`
- `bc4fedf docs: define card sizing and audiobook history`
- `fcdcf52 docs: queue new user feedback work order`

## Newly completed work

### Audiobook preparation regression repair

Both local and streamed audiobooks became stuck indefinitely on `Preparing audiobook` after explicit Media3 dependencies were raised to 1.9.0 while Readium remained at 3.0.2. Commit `62521e9` restores the explicit Media3 dependencies to compatible 1.4.1 versions while preserving the notification-seek design.

Preparation now has a 10-second service-bind timeout and a 30-second engine-ready timeout. Cancellation and failure clear the preparing state non-cancellably, close partially created publications/engines/sessions, recheck Media3 ready/error state after listener registration, and log restore failures. Its full gate passed 299 JVM tests across 50 suites, lint with zero errors, and both APK assemblies.

### Android system audiobook notification controls

Commit `e452d24` supplies the Android media notification buttons through a `DefaultMediaNotificationProvider` override instead of relying on Media3 1.4.1's ignored player-command custom layout. Compact slots 0/1/2 are Back 10, Play/Pause, and Forward 30. Previous/next commands are removed from external surfaces, while available external commands are derived from the actual player. In-app chapter navigation remains unchanged.

Direct Media3 streaming retains 10/30-second seek increments. Local Readium playback is now explicitly configured to the same values instead of its former 15-second rewind default.

The latest full gate passed 300 JVM tests across 50 suites with zero failures, errors, or skips; lint reported zero errors and 39 warnings; and `assembleDebug` plus `assembleDebugAndroidTest` passed. The notification instrumentation test compiles but was not executed because no device was connected.

Physical validation remains pending. Test one downloaded/local audiobook and one non-downloaded streamed audiobook. Each must leave Preparing, and the pull-down notification and lock screen must show Back 10 / Play-Pause / Forward 30. Verify actual elapsed position changes by exactly -10/+30 seconds, then check headset/Bluetooth behavior where available.

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

1. Physically validate the repaired audiobook opening path and Android notification controls before marking them complete. Cover local and streamed books, pull-down and lock-screen controls, exact -10/+30 elapsed changes, and headset/Bluetooth where available.
2. Physically validate the implemented Book Detail personal rating before marking its device checks complete.
3. Change the reading-status action to `Mark as...` and expose every BookOrbit status: Unread, Want to read, Reading, Rereading, On hold, Abandoned, Read, and Skimmed. Persist the selected value to the server and local caches.
4. Replace the About destination's placeholder text with the real app description, BookOrbit relationship/disclaimer, version/build information, acknowledgements, and relevant links.
5. Add audiobook-only Session history below the Book Detail actions. The accepted design uses an app-private Room table keyed by canonical server origin plus BookOrbit book/file ID. Record explicit play/pause wall-clock time and exact playback timepoint with duplicate-callback protection, bounded recent per-book retention, and a clear-history action; tapping a timepoint seeks there. Records are never sent to BookOrbit, survive app updates, clear when the configured server changes, and disappear with Android app-data removal on uninstall.
6. Add one global Library card-size option: Small (the current size), Medium, and Large. It must apply across libraries and content types rather than being stored per library or type.

Before asking the user to test another build, assemble the debug APK and report the exact path above. Use `docs/testing.md` for the applicable procedure.

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

Those earlier confirmations remain valid. They do not cover the repaired post-regression audiobook preparation path, the new Android system notification controls, or the Book Detail personal-rating interaction.

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

- `repository_explorer`: Ramanujan diagnosed the preparation regression; Schrodinger inspected Media3 1.4.1 behavior and established why custom layout alone could not change the pull-down notification.
- `mechanical_editor`: Laplace attempted a bounded test edit, but the Windows restricted-token split-writable-root sandbox blocked both patch attempts; the main agent completed and reviewed the test work.
- `verification_runner`: Halley verified the preparation repair; Helmholtz verified the notification repair, including the latest full gate.
- `document_editor`: Einstein and Socrates attempted the earlier documentation synchronizations but hit the same patch restriction; the main agent applied and reviewed those changes. Dalton reviewed this handover update, verified the ahead count, and stopped after two blocked patch attempts; the main agent then applied and reviewed the handover diff.

The recurring delegated-write failure is environmental rather than a role-policy failure: the Windows unelevated restricted-token sandbox refuses split writable roots. Future sessions should keep the two-failure stop behavior and let the main agent apply the reviewed patch when this exact error recurs.

## Protected working-tree changes

The following user-owned changes remain unrelated and must not be staged or committed:

- Modified `README.md`
- Modified `app/src/main/res/drawable/ic_launcher_foreground.xml`
- Modified `app/src/main/res/drawable/ic_launcher_monochrome.xml`
- Modified `docs/README.md`
- Untracked `sample/`

## Important files for the next session

- `CHECKLIST.md`
- `docs/roadmap.md`
- `docs/testing.md`
- `docs/architecture.md`
- `docs/ui-ux.md`
- `docs/bookorbit-api.md`
- `app/src/main/java/com/bookorbit/android/AppCoordinator.kt`
- `app/src/main/java/com/bookorbit/android/BookOrbitApp.kt`
- `app/src/main/java/com/bookorbit/android/BookOrbitHomeScreen.kt`
- `app/src/main/java/com/bookorbit/android/BookOrbitRepository.kt`
- `app/src/main/java/com/bookorbit/android/ReadiumAudioPlayback.kt`
- `app/src/main/java/com/bookorbit/android/ReadiumAudioPlayerOverlay.kt`
- `app/src/androidTest/java/com/bookorbit/android/AudiobookMediaNotificationInstrumentedTest.kt`
- `app/src/androidTest/java/com/bookorbit/android/ReadiumAudioOpenInstrumentedTest.kt`

## Environment notes

- JDK 17 and the Android SDK are installed and working.
- No Android physical-device validation was performed for the preparation repair, notification-button repair, or personal-rating interaction.
- No project, Gradle, ADB, watcher, or emulator process started during this work remains running.
- Git SSH authentication is configured through `origin`.

## Handover maintenance rule

- Update this file only when the user explicitly requests it.
- Stop session-started project terminals, servers, watchers, emulators, and Gradle daemons before updating it.
