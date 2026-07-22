# Handover

Last updated: 2026-07-22

## Current outcome

BookOrbit Android/Lagrange 1.1.0 is published at `origin/main` commit `1556ea5` and tagged `v1.1.0`. Including this handover-only commit, the local `main` branch is five commits ahead; the latest implementation HEAD is `4621160`.

Two items from the current feedback work order are implemented locally:

- Android system audiobook controls now expose Replay 10 and Forward 30 instead of chapter-skip actions. Chapter navigation remains available inside the compact app player.
- Book Detail now shows an editable whole 1-5-star authenticated personal rating below the cover. Tapping the selected star clears it. Writes use BookOrbit's bulk user-rating endpoint, then re-fetch authoritative detail; offline cache fallback and optimistic rollback are implemented.

The remaining feedback work is queued and documented. No project terminal, Gradle process, server, watcher, or emulator is running.

## Repository and publishing state

- Repository: `C:\Users\vangeaux\Desktop\.git_projects\bookorbit-android`
- Branch: `main`
- Remote: `origin` via SSH
- Published release: `1556ea5 release: package Lagrange 1.1.0`, tagged `v1.1.0`
- Latest implementation HEAD before this handover-only commit: `4621160 feat: add server-backed user ratings`
- Including this handover refresh, the local branch is five commits ahead of `origin/main`.
- Push only when explicitly requested.

Implementation and work-order commits since `origin/main`, excluding this handover-only commit, newest first:

- `4621160 feat: add server-backed user ratings`
- `eea3bb4 fix: expose audiobook seek controls`
- `bc4fedf docs: define card sizing and audiobook history`
- `fcdcf52 docs: queue new user feedback work order`

## Newly completed work

### Android system audiobook controls

The foreground Media3 session now advertises 10-second backward and 30-second forward seek actions to Android's notification, lock-screen, headset, and Bluetooth surfaces. It no longer substitutes chapter previous/next there. The in-app compact player retains BookOrbit chapter navigation.

The implementation gate passed 295 JVM tests across 50 suites, lint, and both APK assemblies. Physical notification, lock-screen, headset, and Bluetooth validation remains pending.

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

1. Change the reading-status action to `Mark as...` and expose every BookOrbit status: Unread, Want to read, Reading, Rereading, On hold, Abandoned, Read, and Skimmed. Persist the selected value to the server and local caches.
2. Replace the About destination's placeholder text with the real app description, BookOrbit relationship/disclaimer, version/build information, acknowledgements, and relevant links.
3. Add audiobook-only Session history below the Book Detail actions. The accepted design uses an app-private Room table keyed by canonical server origin plus BookOrbit book/file ID. Record explicit play/pause wall-clock time and exact playback timepoint with duplicate-callback protection, bounded recent per-book retention, and a clear-history action; tapping a timepoint seeks there. Records are never sent to BookOrbit, survive app updates, clear when the configured server changes, and disappear with Android app-data removal on uninstall.
4. Add one global Library card-size option: Small (the current size), Medium, and Large. It must apply across libraries and content types rather than being stored per library or type.
5. Physically validate the implemented Android system seek controls and Book Detail personal rating before marking those device checks complete.

Before asking the user to test another build, assemble the debug APK and report the exact path above. Use `docs/testing.md` for the applicable procedure.

## Current architecture guardrails

- Keep audiobook playback foreground-service-owned and compact-only; do not add a fullscreen audiobook player without a user-approved product/GUI decision.
- Connected standalone audio uses BookOrbit's authenticated serve URL directly through Media3. Do not route it back through Readium publication retrieval.
- Explicit downloaded/local audio remains on the proven Readium path unless a future migration is approved.
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

Those earlier confirmations remain valid. They do not cover the newly implemented Android system seek-action presentation or Book Detail personal-rating interaction.

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

## Environment notes

- JDK 17 and the Android SDK are installed and working.
- No Android physical-device validation was performed for the two newly completed feedback items in this session.
- No project or Gradle process started during this work remains running.
- Git SSH authentication is configured through `origin`.

## Handover maintenance rule

- Update this file only when the user explicitly requests it.
- Stop session-started project terminals, servers, watchers, emulators, and Gradle daemons before updating it.
