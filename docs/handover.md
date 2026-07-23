# Handover

Last updated: 2026-07-23

## Current outcome

BookOrbit Android/Lagrange 1.1.0 is published at `origin/main` commit `1556ea5` and tagged `v1.1.0`. Before this planning/handover commit, local `main` is eight commits ahead; including it, the branch will be nine commits ahead. The latest implementation HEAD remains `e452d24`.

The Book Detail rating and audiobook preparation repairs remain implemented locally. Android system audiobook controls are reopened as the current highest-priority defect after target-device feedback confirmed that the pull-down player still shows only Play/Pause:

- The notification-provider repair in `e452d24` affects Android 12 and below, but cannot define Android 13+ SystemUI controls. The API 33+ player is populated from the media session's platform `PlaybackState`.
- Media3 1.4.1 converts custom-layout buttons into platform custom actions only when they use custom `SessionCommand`s. Lagrange currently uses `Player.COMMAND_SEEK_BACK/FORWARD`; those buttons are omitted, and filtering previous/next leaves only Play/Pause.
- The highest-priority work order keeps the compatible Readium 3.0.2/Media3 1.4.1 dependency line and bridges Back 10 / Forward 30 through custom session commands, then verifies the real platform session and physical notification/lock-screen behavior.
- Book Detail now shows an editable whole 1-5-star authenticated personal rating below the cover. Tapping the selected star clears it. Writes use BookOrbit's bulk user-rating endpoint, then re-fetch authoritative detail; offline cache fallback and optimistic rollback are implemented.
- The perpetual `Preparing audiobook` regression for both local and streamed books was traced to an explicit Media3 1.9.0/Readium 3.0.2 incompatibility. Media3 is restored to 1.4.1, and playback preparation now has bounded waits plus failure-safe cleanup.

The remaining feedback work is queued and documented. No project terminal, Gradle process, ADB server, watcher, or emulator is running.

## Repository and publishing state

- Repository: `C:\Users\vangeaux\Desktop\.git_projects\bookorbit-android`
- Branch: `main`
- Remote: `origin` via SSH
- Published release: `1556ea5 release: package Lagrange 1.1.0`, tagged `v1.1.0`
- Latest implementation HEAD before this planning/handover commit: `e452d24 fix: expose audiobook notification seeking`
- Before this refresh, the local branch is eight commits ahead of `origin/main`; including this planning/handover commit it will be nine commits ahead.
- Push only when explicitly requested.

Implementation and work-order commits since `origin/main`, excluding this planning/handover commit, newest first:

- `b4a9097 docs: update audiobook handover`
- `e452d24 fix: expose audiobook notification seeking`
- `62521e9 fix: restore audiobook preparation`
- `179ccad docs: refresh current handover`
- `4621160 feat: add server-backed user ratings`
- `eea3bb4 fix: expose audiobook seek controls`
- `bc4fedf docs: define card sizing and audiobook history`
- `fcdcf52 docs: queue new user feedback work order`

## Completed work and reopened priority defect

### Audiobook preparation regression repair

Both local and streamed audiobooks became stuck indefinitely on `Preparing audiobook` after explicit Media3 dependencies were raised to 1.9.0 while Readium remained at 3.0.2. Commit `62521e9` restores the explicit Media3 dependencies to compatible 1.4.1 versions while preserving the notification-seek design.

Preparation now has a 10-second service-bind timeout and a 30-second engine-ready timeout. Cancellation and failure clear the preparing state non-cancellably, close partially created publications/engines/sessions, recheck Media3 ready/error state after listener registration, and log restore failures. Its full gate passed 299 JVM tests across 50 suites, lint with zero errors, and both APK assemblies.

### Android system audiobook notification controls — reopened highest-priority defect

Commit `e452d24` supplies Back 10 / Play-Pause / Forward 30 through a `DefaultMediaNotificationProvider`, filters previous/next from external player commands, and keeps chapter navigation inside the app. Direct Media3 streaming and local Readium playback both have the correct 10/30-second increments. Target-device feedback nevertheless confirms that the Android pull-down player still exposes only Play/Pause.

The problem is now identified. The app targets SDK 35, so Android 13+ builds its media controls from the platform `PlaybackState` rather than the notification provider. In bundled Media3 1.4.1, `PlayerWrapper.createPlaybackStateCompat()` adds custom-layout entries only when `button.sessionCommand` is non-null. Lagrange's timed-seek buttons instead use `Player.COMMAND_SEEK_BACK/FORWARD`, so neither becomes a platform custom action. Because previous/next are deliberately removed, the backward and forward system slots are empty.

The earlier 300-JVM-test/50-suite gate, lint, and both APK assemblies verified the attempted repair but did not prove this outcome. `AudiobookMediaNotificationInstrumentedTest` constructs synthetic helper/provider inputs; it does not query a real media session's platform token or inspect `PlaybackState.customActions`. That missing OS-facing assertion allowed the defect to pass automated coverage.

The implementation work order is:

1. Retain Readium 3.0.2 and Media3 1.4.1. Define stable Back-10 and Forward-30 custom `SessionCommand`s and session-command `CommandButton`s; authorize them in `onConnect`; handle them in `onCustomCommand` through the active player's bounded `seekBack()`/`seekForward()`; keep standard seek commands and previous/next filtering; and reuse the buttons in the provider for API 26-32.
2. Add focused unit coverage and real-session instrumentation. On API 33+, query the actual platform token, assert ordered Back-10/Forward-30 `PlaybackState` custom actions and icons, invoke them, and verify exact bounded -10/+30 position changes. Retain compact/provider coverage for API 26-32 and exercise local Readium plus streamed Media3 command sets.
3. Compile affected source sets, run focused tests and the complete unit/lint/APK gate, and build a fresh debug APK. Do not close the item until a physical API 33+ device shows and operates all three pull-down and lock-screen controls for local and streamed audiobooks, including boundaries, paused/background/task-relaunch states, and headset/Bluetooth where available; spot-check API 26-32.

A notification-provider-only change cannot repair API 33+. A Media3 upgrade is a broader coordinated Readium migration because Media3 1.9.0 already broke preparation with Readium 3.0.2. Reusing previous/next for timed seeking is not acceptable because it exposes incorrect transport semantics to external controllers.

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

1. Implement the API 33+ custom-session-command bridge and OS-facing regression above, run the complete gate, build the debug APK, and physically validate local/streamed pull-down and lock-screen Back 10 / Play-Pause / Forward 30 before marking the system-control item complete.
2. Revalidate the repaired local and streamed audiobook opening path during the same physical pass, including exact -10/+30 boundaries and headset/Bluetooth behavior where available.
3. Physically validate the implemented Book Detail personal rating before marking its device checks complete.
4. Change the reading-status action to `Mark as...` and expose every BookOrbit status: Unread, Want to read, Reading, Rereading, On hold, Abandoned, Read, and Skimmed. Persist the selected value to the server and local caches.
5. Replace the About destination's placeholder text with the real app description, BookOrbit relationship/disclaimer, version/build information, acknowledgements, and relevant links.
6. Add audiobook-only Session history below the Book Detail actions. The accepted design uses an app-private Room table keyed by canonical server origin plus BookOrbit book/file ID. Record explicit play/pause wall-clock time and exact playback timepoint with duplicate-callback protection, bounded recent per-book retention, and a clear-history action; tapping a timepoint seeks there. Records are never sent to BookOrbit, survive app updates, clear when the configured server changes, and disappear with Android app-data removal on uninstall.
7. Add one global Library card-size option: Small (the current size), Medium, and Large. It must apply across libraries and content types rather than being stored per library or type.

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

Those earlier confirmations remain valid. They do not cover the repaired post-regression audiobook preparation path or the Book Detail personal-rating interaction, and the latest feedback explicitly confirms that the Android system timed-seek controls remain broken on API 33+.

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
- Latest target-device feedback confirms the notification-button repair remains incomplete: the pull-down player still shows only Play/Pause. No completed physical-device validation exists yet for the preparation repair, fixed notification controls, or personal-rating interaction.
- No project, Gradle, ADB, watcher, or emulator process started during this work remains running.
- Git SSH authentication is configured through `origin`.

## Handover maintenance rule

- Update this file only when the user explicitly requests it.
- Stop session-started project terminals, servers, watchers, emulators, and Gradle daemons before updating it.
