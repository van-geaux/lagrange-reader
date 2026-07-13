# Handover

Last updated: 2026-07-13

## Current outcome

The latest implementation pass addresses the two current user-reported problems:

1. Reading progress shown inside Lagrange was not reliably reaching BookOrbit, leaving BookOrbit's Currently Reading widget blank.
2. A roughly 5,000-book library loaded covers slowly, especially after rapid scrolling and when opening book details.

The complete catalog/jump-rail work, progress reconciliation, in-flight queue fix, and large-library thumbnail/detail caching are implemented and committed locally. Full automated verification passes. The remaining work is physical-device and live-server validation with the user's real library.

## Repository and publishing state

- GitHub: `https://github.com/van-geaux/lagrange-reader`
- Local path: `C:\Users\vangeaux\Desktop\.git_projects\bookorbit-android`
- Branch: `main`
- Remote: `origin` via SSH
- The user-owned untracked root `AGENTS.md` is intentionally untouched and must not be included in project commits.
- Recent implementation commits:
  - `39e409e fix: reconcile reading progress with server`
  - `6e636fd feat: cache complete library catalogs locally`
  - `6d19283 fix: preserve in-flight reading progress`
  - `974eccb perf: warm large library caches`

## Latest implementation state

### Bidirectional reading progress

- BookOrbit card payloads now parse the current scalar `readingProgress` value, nested `readStatus`, and tolerated legacy page/time progress containers.
- EPUB, PDF, comic, audiobook, queue, marker, resume, and API paths use one canonical 0-100 percentage scale.
- A newer lower percentage is allowed through as reread/correction progress instead of being rejected as stale.
- Browser bootstrap replays pending progress before catalog refresh. Closing a reader records the newest event, flushes it, and attempts foreground sync before clearing active-reader state.
- Foreground and WorkManager repository instances share queue and last-synced file locks.
- Sync acknowledges only the exact event IDs it processed. A newer update written while an older network request is in flight is preserved instead of being erased by an old queue snapshot.
- Rapid reader callbacks replace a short-delayed unique worker so the latest compacted event retains a trailing replay.
- Unknown percentages inherit the known book percentage when possible and are otherwise rejected; they are no longer submitted as a valid `0%` update.
- BookOrbit's file-progress and audio-progress handlers automatically update reading status after successful nonzero progress. A separate reading-session submission is not required for the Currently Reading widget.
- Temporary local browser overlays are removed after successful replay and fresh catalog reconciliation so later BookOrbit-side progress can become authoritative in Lagrange.

### Complete catalog and exact jump rail

- The active selected-library catalog is stored in server/library-scoped Room tables instead of the former first-page JSON snapshot.
- Cached metadata renders before cold-start session and network checks complete.
- BookOrbit has no reliable catalog revision/delta contract, so refresh still walks every metadata page to detect additions, deletions, reordering, metadata edits, and remote progress changes.
- Reconciliation retries once if page totals shift, then atomically writes only changed/new/reordered rows and deletions. An interrupted refresh keeps the previous complete generation usable.
- Browse no longer performs network-backed near-end lazy paging. Local filters and supported sorts operate over the complete cached catalog.
- Default-sort rail taps use BookOrbit's absolute jump-bucket indexes when valid. Complete local indexes cover filtered, title, author, descending, collapsed-series, and older-server cases.
- The rail is hidden until the first complete catalog exists and for sorts without meaningful letter buckets.
- Compose item rendering remains lazy intentionally; composing roughly 5,000 cards simultaneously would waste memory. The removed behavior is network-backed lazy catalog loading, not bounded UI virtualization.

### Large-library covers and book details

- Card cover loads are owned by Compose. When a card scrolls out of composition, cancellation reaches the underlying OkHttp call instead of leaving a process-global stale request running.
- Short striped locks deduplicate simultaneous requests without serializing unrelated covers.
- Cover decoding remains off the Compose main thread, uses downsampling and `RGB_565`, and stores decoded bitmaps in a bounded 32 MB memory cache.
- Persisted thumbnail keys include the book catalog update version, so unchanged thumbnails are reused and changed books fetch a new image.
- `CoverCacheStore` uses per-file locks shared across repository instances, allowing visible foreground reads and unrelated background writes to proceed concurrently.
- After a successful complete catalog refresh, `CoverCacheWarmWorker` starts one selected-library chain on unmetered connectivity. It waits five seconds, scans past existing files, and downloads at most 50 missing/changed thumbnails per durable batch.
- Switching the selected library replaces the previous warm chain so multiple libraries do not compete with visible cards.
- Rich `BookDetailInfo` is cached for every opened title, not only downloads, and reused until the catalog `updatedAt` version changes.
- Rich details are deliberately not prefetched for every title: doing so for the target library would add roughly 5,000 detail API requests. The summary screen renders immediately, and the rich supplement is fetched once per changed/opened title.

### Reader and UI status

- The Plex-inspired Home/Libraries/More shell, Home search/profile actions, library selector, Recommended/Browse tabs, compact poster grids, Series, Authors, Local books, Options, About, and details are implemented.
- The complete selected-library cache drives Home and Browse; global search and Series/Authors catalogs remain server-backed.
- EPUB exact local chapter/page resume and independent Top, Bottom, Left, and Right padding persistence are implemented.
- The earlier blank EPUB/clipped-wrapper candidate was superseded. The current visible-overflow page strip renders the real EPUB, and target-device testing confirmed all four padding controls visibly change the reading surface.
- Preview remains isolated from normal active-reader and progress state.
- Audiobook, PDF, and CBZ paths remain implemented, but broader format-specific device validation is deferred until representative samples are available.

## Verification completed

The final combined command passed:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

Results:

- 123 JVM tests across 21 suites
- 0 failures, 0 errors, 0 skipped
- Android lint passed
- Debug APK assembly passed
- Android instrumentation-test APK compilation passed
- `git diff --check` passed; only the repository's expected LF-to-CRLF warnings were reported

Debug APK:

`app/build/outputs/apk/debug/app-debug.apk`

No Android device was attached during the final progress/cache pass, so the latest behavior is not yet live-validated.

## Highest-priority next validation

### 1. Android to BookOrbit progress

1. Install the latest debug APK.
2. Open several unfinished EPUBs and move each to a visibly different percentage.
3. Turn several pages rapidly in at least one title, then immediately close the reader.
4. Confirm the debug pending-progress count drains.
5. Refresh BookOrbit's web app and confirm each title appears in Currently Reading at approximately the final Android percentage.
6. Repeat after reading offline and reconnecting.

### 2. BookOrbit to Android progress

1. Advance one of those titles in BookOrbit's web reader.
2. Refresh Lagrange.
3. Confirm the newer server percentage replaces the temporary local overlay and reader resume follows it when there is no newer queued Android event.
4. Test a backward correction/reread and a value below 1% to verify neither direction scales or suppresses it incorrectly.

### 3. Five-thousand-book library

1. On the first complete sync, confirm cached cards remain usable while reconciliation runs and the letter rail appears only after completion.
2. Tap `#` and several distant letters; each tap should land directly on the requested or next available initial without loading intermediate server pages.
3. Rapidly scroll through distant rows, stop, and confirm visible covers fill promptly instead of waiting behind off-screen requests.
4. Leave the app on unmetered Wi-Fi after refresh so thumbnail warming can advance. Then enable airplane mode and jump to distant letters; warmed thumbnails should still render.
5. Open a book detail, return, and reopen it; the rich detail should be immediate on the second open.
6. Change that book's metadata in BookOrbit, refresh Lagrange, and confirm the next detail open replaces the old cached version.

## Architecture guardrails

- Do not restore network-backed Browse lazy paging; exact jumps rely on the complete local catalog.
- Do not replace `LazyVerticalGrid` with eager rendering of every book; UI virtualization is still required for memory safety.
- Do not acknowledge progress by rewriting an old queue snapshot. Use exact event-ID acknowledgement so concurrent reader writes survive.
- Keep all progress percentages on the canonical 0-100 scale.
- Do not submit an unknown percentage as zero.
- Keep thumbnails server-, book-, URL-, and catalog-version-scoped.
- Keep full thumbnail warming unmetered and single-chain unless the user explicitly chooses another bandwidth policy.
- Do not eagerly fetch every rich-detail endpoint without an explicit user decision; the current on-open versioned cache is intentional.
- Preserve offline reader behavior, Preview isolation, session recovery, local resume, and server-scoped persistence when changing browser or sync behavior.

## Important files for the next session

- `README.md`
- `CHECKLIST.md`
- `docs/roadmap.md`
- `docs/testing.md`
- `docs/architecture.md`
- `docs/ui-ux.md`
- `app/src/main/java/com/bookorbit/android/AppCoordinator.kt`
- `app/src/main/java/com/bookorbit/android/BookOrbitRepository.kt`
- `app/src/main/java/com/bookorbit/android/ProgressQueueStore.kt`
- `app/src/main/java/com/bookorbit/android/LastSyncedProgressStore.kt`
- `app/src/main/java/com/bookorbit/android/CoverCacheStore.kt`
- `app/src/main/java/com/bookorbit/android/CoverCacheWarmWorker.kt`
- `app/src/main/java/com/bookorbit/android/BookDetailCacheStore.kt`
- `app/src/main/java/com/bookorbit/android/BookOrbitHomeScreen.kt`
- `app/src/test/java/com/bookorbit/android/ProgressQueueStoreTest.kt`
- `app/src/test/java/com/bookorbit/android/ProgressPercentNormalizationTest.kt`
- `app/src/test/java/com/bookorbit/android/BookDetailCacheStoreTest.kt`
- `app/src/test/java/com/bookorbit/android/CoverCacheStoreTest.kt`
- `app/src/test/java/com/bookorbit/android/BookOrbitRepositoryHelpersTest.kt`

## Known remaining limitations

- Live bidirectional progress reconciliation still requires target-server validation after the in-flight queue fix.
- Full thumbnail warming and visible-card prioritization require target-device validation with the real 5,000-book library.
- BookOrbit metadata refresh must still request all catalog pages because the server does not expose a reliable revision/delta contract.
- Thumbnail versions that become obsolete remain on disk; an eviction/cleanup policy can be added later if real storage measurements justify it.
- Long-lived access-token refresh behavior still needs a physical-device/server-expiry pass.
- Series and Authors catalogs remain server-backed and do not yet use the selected-library Room catalog.
- Exact in-chapter resume is implemented for EPUB; equivalent format-specific validation remains incomplete for audiobook, PDF, and CBZ.
- CBR remains unsupported.

## Environment notes

- JDK 17 and the Android SDK are installed and working.
- `local.properties` points to the Android SDK.
- Gradle requires access to the user cache under `C:\Users\vangeaux\.gradle`.
- The Gradle daemon started during this session was stopped before this handover was updated.
- GitHub CLI is installed, but its stored API token was stale during this session. Direct Git SSH authentication is configured separately through the `origin` remote.

## Handover maintenance rule

- Update this file only when the user explicitly requests it.
- Stop project servers, watchers, tasks, and session-started Gradle daemons before updating it.
