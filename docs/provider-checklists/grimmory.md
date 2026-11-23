# Grimmory provider checklist

## Validation record

- Provider/server type: `Grimmory` native `/api/v1`
- Server base URL: `Configured test server, URL omitted`
- App build/version: `Full Gradle verification passed on 2026-11-22; user reports the audiobook-cover fix works after the latest APK handoff, but exact tested build/device details were not separately supplied`
- APK path/hash: `Lagrange-debug-202611222217.apk; SHA-256 c59b0a50ca1d5a5412c032b001d43d8b21f701c9c47ae9ee549966c31e2b7f22; 42,590,993 bytes; built in the issue worktree at /projects/bookorbit-android/.hermes/worktrees/issue-422-grimmory-provider/app/build/outputs/apk/debug/Lagrange-debug-202611222217.apk and hash-verified at /projects/bookorbit-android/app/build/outputs/apk/debug/Lagrange-debug-202611222217.apk`
- Maintainer/requester: `Van Geaux`
- Device/emulator: `User reports the audiobook-cover fix works; device model/OS not supplied`
- Date: `2026-11-22`
- Account/library fixture: `Configured private test profile; account and catalog values omitted`
- Result key: `[x] Pass  [!] Fail  [-] Blocked / unavailable  [ ] Not tested`
- Evidence: Automated verification covers `GrimmoryApiContractTest`, `GrimmoryProgressModuleTest`, `GrimmoryReaderModuleTest`, `AudiobookTimelineTest`, `ReadiumAudioPlaybackTest`, and the relevant `AppCoordinator` tests. Chapter tests verify native single-file chapter projection and folder-track fallback; player helper tests verify progress is unavailable without valid bounds and the no-bounds seek position stays at zero. Comic coverage verifies native CBX routes, strict contiguous integer page-number parsing, page-list/image request headers and authentication through MockWebServer, one-based image URL mapping, online comic state/restore, generic `CBX` preparation without a local archive suffix, and local-only unavailable behavior. Existing regression coverage verifies On deck mapping for missing/`UNSET` status and preserves explicit `PAUSED`. Home regressions cover multiple same-format native results and successful-empty continuation responses falling back to paged catalog data; the API contract test verifies comma-delimited status/file-type values on the final HTTP request. These are automated/source checks. A sanitized read-only live probe found the native continuation routes empty and the generic filtered catalog populated; the pre-fix ADB screen showed one Continue Reading card. The user reports the audiobook-cover fix works; exact device/build and individually tested cover/fallback cases were not specified. Continue Reading post-fix device validation remains pending.`
- Known auth limitation: a live refresh attempt returned HTTP 401; successful refresh/rotation remains unverified.
- Full gate (clean issue branch, 2026-11-23): Passed with `:app:compileDebugKotlin`, `:app:compileDebugUnitTestKotlin`, `:app:compileDebugAndroidTestKotlin`, `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:assembleDebug`, and `:app:assembleDebugAndroidTest`, all with `--rerun-tasks`. Aggregated JUnit XML totals: 1,561 tests in 146 files, 0 failures, 0 errors, 0 skipped. Lint and both APK assembly tasks succeeded. Fresh debug APK: `/projects/bookorbit-android/.hermes/worktrees/issue-422-grimmory-provider-clean/app/build/outputs/apk/debug/Lagrange-debug-202611231116.apk`, 42,612,673 bytes, SHA-256 `e27347dab7db77ed8f4172a8d44aef0aa567f63d3cf3823e9b2d66852469e0a1`; matching copy: `/projects/bookorbit-android/app/build/outputs/apk/debug/Lagrange-debug-202611231116.apk`. This is build/test evidence only; the clean-branch APK was not installed for live Home-shelf validation.`
- Device validation: ADB captured the pre-fix phone screen with one Continue Reading card. The user reports the audiobook-cover fix works, but device/OS, exact tested build, and separate 404-fallback scope were not supplied; the full cover checklist remains pending. Continue Reading post-fix validation and authenticated Grimmory status/progress round-trips remain pending.`

Use these result markers for each manual item:

- `[x] Pass`
- `[!] Fail`
- `[-] Blocked / unavailable by provider contract or current adapter`
- `[ ] Not tested`

Do not mark an item passed from source inspection, a MockWebServer test, or compilation alone. Record the exact app build/device and observed result when testing manually.

## 1. Setup, authentication, and identity isolation

- [ ] Grimmory appears as a distinct server type and persists as provider `grimmory`.
- [ ] Valid primary-account username/password login succeeds through `POST /api/v1/auth/login`.
- [ ] Login verifies `GET /api/v1/users/me` and requires the stable provider `id` before enabling account-scoped downloads.
- [ ] Invalid credentials and an unavailable/malformed identity response fail without exposing credentials or reusing another provider's session.
- [ ] A second Grimmory account on the same server receives a distinct account-scoped download identity.
- [ ] Switching away and back restores only the selected profile's Grimmory session and account scope.
- [ ] Logout deactivates the account scope without deleting the owning profile's downloaded media.
- [ ] Token expiry and refresh-token rotation work; investigate the previously observed live HTTP 401 before marking this passed.
- [ ] Credentials and token values do not appear in the profile list, URL, logs, screenshots, or ordinary profile storage.

## 2. Libraries, catalog, Home, detail, and covers

- [ ] Library discovery and selection show only the authenticated account's accessible Grimmory libraries.
- [ ] Library Browse, search, paging, refresh, empty, offline, and error states use Grimmory native routes and do not fall through to BookOrbit.
- [ ] Home's Currently Reading shelf combines Grimmory's native `GET /api/v1/app/books/continue-reading?limit=20` and `GET /api/v1/app/books/continue-listening?limit=20` results, retains multiple active books with the same format, and orders the merged entries by `lastReadTime`; if either native route fails or their combined result is empty, it falls back to the paginated app-book catalog using comma-delimited `status=READING,RE_READING` and merges by `(libraryId, id)`. Recently read uses `status=READ`. On deck treats a missing `readStatus` or `UNSET` as `UNREAD` for untouched books and chooses the next unread book after the last completed book (not the active book); Recently added uses the expected order.
- [ ] Home See all paging and selected-library filtering match the preview; a non-auth failure in one shelf preserves successful sibling shelves, while authentication errors enter session recovery.
- [ ] Home shows Recently added series from `GET /api/v1/app/series` in Grimmory's `recentlyAdded` descending order, covers resolve from `coverBooks[].bookId`, `See all` paging uses provider page metadata, and selecting a card loads its series books in `seriesNumber` order. Grimmory orders a series by the newest `addedOn` among its books, unlike BookOrbit's earliest-book series-introduction order. Want to read, Recently released books, and Recently updated series remain absent; release-date coverage/window and a general series-update timestamp are not established.
- [ ] Book cards and Book Detail show the primary and alternate supported formats, exact file choices/identities, metadata, and cover. Catalog/Home/search labels use bounded, cached native detail lookups and fall back to the primary label when detail metadata is unavailable.
- [ ] A missing cover is treated as missing; authentication and server errors remain visible rather than being hidden as a BookOrbit fallback.
- [ ] For a multi-format book (for example, EPUB + M4B), verify compact and full audiobook players use Grimmory's dedicated audiobook cover when available and fall back to the generic book cover on 404; opening the EPUB continues to use the generic cover.
- [ ] Switching provider/profile never reuses Grimmory catalog, Home, detail, or cover data from another account.

## 3. Reading, downloads, and local files

- [ ] Supported online EPUB/PDF content and online CBX page streaming use the authenticated native Grimmory routes; audiobooks use `/api/v1/audiobooks/{bookId}/stream`, including `bookType` for an alternate format, and do not inherit the primary EPUB content route. The Grimmory provider opener reaches the existing audio player; verify playback and authentication on a Grimmory server/device.
- [ ] For a single-file audiobook, verify the server's `chapters[]` appear in the chapter list and can be selected/seeking works; for folder-based audio without `chapters[]`, verify `tracks[]` titles and `cumulativeStartMs` provide the fallback chapters. Confirm chapter progress tracks playback in portrait and landscape, and missing/invalid bounds do not show a fabricated percentage or enabled seek control.
- [ ] Online comic streaming uses Grimmory's native `GET /api/v1/cbx/{bookId}/pages` and `GET /api/v1/media/book/{bookId}/cbx/pages/{pageNumber}` endpoints; verify the first and subsequent images, one-based page mapping/progress, and that streaming does not create a durable download. A comic that is not downloaded must still report unavailable when opened in local-only mode.
- [ ] Primary download uses `GET /api/v1/books/{bookId}/download`; selecting a non-primary file downloads exactly `GET /api/v1/books/{bookId}/files/{fileId}/download`, with Bearer authentication, streamed response bytes, and shared profile-scoped download lifecycle.
- [ ] Each completed transfer is verified before atomic promotion; cancellation/failure preserves an existing completed file.
- [ ] Internal and Android portable-storage targets, capacity handling, retry/cancel, account switching, and local deletion use the selected file identity (`fileId` when present); primary and attached-file routes receive their documented identities.
- [ ] Downloaded supported media reopens offline through Local Books and the shared reader.
- [ ] Selecting an alternate audiobook (for example, M4B alongside EPUB) retains its exact `fileId`, M4B media kind/stream selector, audiobook info selector, and file-specific download route; the selected downloaded file reopens from its own local record.
- [ ] Online and local reader restoration uses Grimmory identity/content state and never requests a persisted BookOrbit URL.

## 4. Native progress synchronization (manual validation pending)

- [ ] Online EPUB progress loads from and writes to `GET`/`PUT /api/v1/app/books/{bookId}/progress`, preserving the CFI and resource `href`.
- [ ] Online PDF and CBX progress restores Grimmory's one-based page number as the reader's zero-based page index and writes the reverse conversion.
- [ ] Online audiobook progress loads `/api/v1/audiobooks/{bookId}/info`, streams the single-file or zero-based-track route, and keeps app/core's absolute timeline milliseconds, zero-based `trackIndex`, and track-local milliseconds separate. On Grimmory's wire, folder-based `positionMs`/`fileProgress.positionData` is track-local, `trackIndex`/`fileProgress.positionHref` identifies the track, and `percentage` is overall progress; `trackPositionMs` is only an optional supplemental local field. This item remains unverified on a server round-trip.
- [ ] Closing the full player at a known nonzero position and reopening the same selected audiobook restores the saved track and offset online; confirm the final close progress write completes before the progress read.
- [-] Offline device-local audiobook checkpoint persistence and offline progress replay are not implemented in this provider integration; a cached catalog position is not a durable resume checkpoint.
- [ ] Reader close and progress-write failure leave the reader safe and do not report a successful server update when the write fails.

## 5. Native reading-status mutation (manual validation pending)

- [ ] Verify authenticated `PUT /api/v1/app/books/{bookId}/status` for `UNREAD`, `READING`, `REREADING` (`RE_READING`), `READ`, and `ABANDONED` against Grimmory.
- [ ] Confirm the Book Detail `Mark as...` action is present for Grimmory with only those choices, while statuses without an exact shared counterpart remain unavailable.

## 6. Explicitly unavailable in the current adapter

Confirm each action is hidden or reports an explicit unavailable state, never a successful no-op:

- `[-]` General series browsing and filters beyond Recently added, collections, Smart Scopes, and authors.
- `[-]` User ratings.
- `[-]` Server reading sessions/attempts, annotations, and EPUB bookmarks.
- `[-]` Achievements, statistics, and provider offline/background cache management.

## 7. Automated/source checks (not manual validation)

- Upstream Grimmory source: native login/refresh controllers, `/api/v1/users/me`, and the stable numeric user `id` DTO.
- Sanitized live probe: login and current-user identity returned HTTP 200; native download previously returned HTTP 200 with `application/octet-stream`.
- MockWebServer coverage: native auth, identity route/Bearer header, catalog contracts, download transport, capability gates, reader URL mapping, and Grimmory native API request headers. `GrimmoryNativeApiClientTest` exercises the actual page-list request/production parser with JSON Accept and the authenticated page-image request with `image/*` Accept.
- MockWebServer status-write coverage verifies the native single-book PUT route, payload, Bearer header, one-time 401 refresh, explicit failure behavior, and rejection of unsupported shared statuses without a request; parser/progress tests cover safe `RE_READING`, missing/`UNSET` status mapping to `UNREAD`, and explicit `PAUSED` remaining unmapped.
- Focused multi-format/audio coverage: `GrimmoryApiContractTest` verifies mixed EPUB/M4B detail, per-file media kind/format, selected M4B info/stream routing and exact file IDs; `GrimmoryDownloadModuleTest` verifies the file-specific download route; `GrimmoryReaderModuleTest` verifies the selected M4B stream remains the reader source; `AppCoordinatorTest` verifies Grimmory's opener preserves the selected M4B state while BookOrbit remains enabled and Komga/Kavita remain explicitly unavailable. `GrimmoryFormatMetadataEnricherTest` covers bounded concurrency, profile/server cache isolation, cache hits, failure fallback, and non-caching failures; `GrimmoryHomeShelfModuleTest` verifies enriched format fields reach shelf summaries. These tests are automated only; live alternate-file/server and device behavior remain pending.
- Grimmory Home regression coverage verifies that two active EPUBs survive in the same shelf, audiobook continuation is merged in `lastReadTime` order, native continuation request limits are explicit, and generic status arrays are serialized according to the catalog contract; it also verifies native `recentlyAdded` paging, series-card author/count/cover projection, provider series-number ordering, shelf-failure isolation, and existing On Deck behavior. These tests do not establish live-server or device rendering.
- Full automated verification: See the validation record above for the 2026-11-22 gate, JUnit totals, lint outcome, and debug APK paths/hashes. This is source/build evidence only, not a live-server round-trip or device validation.
- AppCoordinator regression tests confirm initial-browser, remaining-library, and selected-library refreshes invoke the provider `BookCatalogModule` without calling the compatibility repository refresh when the module resolves.
- Production `AppGraph` wires Grimmory's provider modules. The coordinator retains nullable legacy fallbacks for incomplete/alternate compositions, and cached catalog/Home/browser-state projections still use shared local compatibility stores; full fail-closed and provider-local cache isolation remain open audit items.
