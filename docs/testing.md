# Testing

This document marks the current point where manual app testing can start.

Reader regression result on July 13, 2026: the restored visible-overflow page strip renders the real EPUB on the target device, and all four independent padding controls visibly update the reading surface. Top/Bottom use Android layout padding around the `WebView`, outside the EPUB HTML; Left/Right update the page strip in place.

## Start Here

You can begin manual testing once these conditions are true:

- `.\gradlew.bat assembleDebug` passes
- `.\gradlew.bat testDebugUnitTest` passes
- `.\gradlew.bat assembleDebugAndroidTest` passes if you plan to run instrumentation tests
- a reachable BookOrbit server URL is available
- at least one account can sign in and access a library with real content

Current instrumentation coverage includes server setup validation, login recovery UI and server-change routing, populated live-browser rendering, the Home pull-to-refresh gesture, the Currently Reading reset menu, browser loading states, cached offline browser actions, in-memory Room reconciliation and reading-state reset transactions, the EPUB progress-footer content/accessibility contract, and a WebView-level EPUB regression that checks runtime padding geometry plus visible text after page translation. `assembleDebugAndroidTest` compiles this coverage with the AndroidX test runner; run the connected test task when a device or emulator is available.

## Latest device feedback — July 12, 2026

- Server setup and login passed.
- Full relaunch with an existing session passed.
- Library browsing and navigation passed.
- EPUB reading and the current pagination behavior passed.
- Download, airplane-mode reopen, and progress-sync behavior passed.
- The previous launch visual issue was a spinning loading indicator instead of the expected app-specific adaptive-icon presentation; it is now replaced in code with a branded splash/loading state and needs physical-device confirmation.
- The next device pass must validate the new first-row Currently reading shelf and the implemented Plex-inspired shell: Home/Libraries/More bottom navigation, More expansion, Home-only logo/search/profile actions, the tappable Library name selector, Recommended/Browse tabs, visible status bar/Home spacing, and search layer. It must also validate the complete #/A–Z jump rail, series ordering after collapse/expand, session-expiry recovery, and the branded launch state.
- Exact in-chapter EPUB restore, compact poster-card library browsing, Lagrange branding with the subtitle `a BookOrbit reader` on splash/loading only, the Libraries series-collapse control, Local books before Options in More, the placeholder About destination, Home/Library swipe-down refresh, and persistent cover-thumbnail caching are implemented. Physical-device validation remains required for the new behavior.
- The latest reader follow-up restores the same single visible-overflow strip used before the blank-screen regression. Target-device testing confirms that EPUB content renders and that Top/Bottom WebView resizing plus Left/Right in-page updates visibly change the reader padding.
- The July 13 follow-up adds refresh-cookie retry before session-expired recovery, retried Series catalog covers with in-memory image caching, immediate Continue reading updates, smaller shared typography, and title/series/index card metadata rows. The Series thumbnail follow-up corrects its URL contract to use BookOrbit's `coverBookIds` and `/books/{bookId}/thumbnail` rather than a nonexistent Series cover route. These changes require physical-device validation.
- The progress reconciliation follow-up parses BookOrbit's numeric `readingProgress` and nested `readStatus`, keeps all reader and API values on a single 0-100 scale, permits newer reread/backward corrections, and explicitly writes `reading`/`read` status as part of the queued progress operation. Target-server bidirectional validation is required.
- The catalog follow-up replaces Browse lazy loading with a complete server-scoped Room metadata cache. Cached content renders before cold-start network checks finish; refresh reconciles all pages and writes only changed/new/reordered rows or deletions. Exact default-sort rail taps use BookOrbit jump-bucket indexes. JVM tests and instrumentation-test compilation pass; target-device/server validation is required.
- The large-library follow-up cancels off-screen cover calls, warms versioned selected-library thumbnails in unmetered WorkManager batches, and caches rich details per catalog version. The full pass completes 127 JVM tests across 21 suites with zero failures, Android lint, debug APK assembly, and instrumentation-test compilation; target-device validation with the 5k-book library is required.
- The July 15 Currently Reading/On Deck pass and normal-user reset follow-up complete 138 JVM tests across 21 suites with zero failures, Android lint, debug APK assembly, and instrumentation-test compilation. The follow-up replaces the metadata-admin reset endpoint with BookOrbit's normal-user primary-file/audio progress and unread-status APIs; target-device validation must confirm this fallback with an account that lacks metadata-edit permission, plus the overflow/long-press interaction, restart behavior, and revised On Deck sequence.
- The July 15 Home refresh and Series thumbnail pass completes 139 JVM tests across 21 suites with zero failures, Android lint, debug APK assembly, and instrumentation-test compilation. Target-device validation must confirm the Home pull gesture and representative Series thumbnails against the live server.
- The July 15 complete Series navigation pass completes 142 JVM tests across 21 suites with zero failures, Android lint, debug APK assembly, and instrumentation-test compilation. Target-device/server validation must confirm multi-page loading, the absence of Load more, and exact ascending/descending rail navigation on the full Series catalog.
- The July 15 collapsed Libraries series-count pass completes 143 JVM tests across 21 suites with zero failures, Android lint, debug APK assembly, and instrumentation-test compilation. It adds JVM grammar/grouping coverage and a compiled Compose assertion for the visible count. Target-device validation must confirm the count remains readable on narrow cards and matches the books represented by the active Browse filter.
- The July 15 EPUB theme-persistence pass completes 144 JVM tests across 22 suites with zero failures, Android lint, debug APK assembly, and instrumentation-test compilation. It adds round-trip/fallback coverage plus a compiled Android SharedPreferences recreation test. Target-device validation must confirm the selected app-wide theme survives reader close, another EPUB, and a full app relaunch.
- The July 15 reader keep-awake pass completes 145 JVM tests across 23 suites with zero failures, Android lint, debug APK assembly, and instrumentation-test compilation. It adds visual-format selection coverage and a compiled Compose lifecycle test that clears the keep-awake flag on exit. Target-device validation must confirm an idle visual reader outlives the configured display timeout while explicit system locking still works.
- The July 15 reader-options pass retains 145 JVM tests across 23 suites with zero failures, Android lint, debug APK assembly, and instrumentation-test compilation. Its compiled Compose interaction test verifies there is no visible Back action, exactly one Close action, and both Close and exposed-content dismissal paths. Target-device validation must confirm Android Back dismisses options before exiting the reader and exposed-content taps do not turn a page.
- The July 15 native reader-status pass completes 146 JVM tests across 24 suites with zero failures, Android lint, debug APK assembly, and instrumentation-test compilation. Policy coverage verifies a visible top status bar, immersive bottom navigation, and theme-appropriate icon contrast. Target-device validation must confirm Android's battery and network indicators remain legible across Light, Sepia, and Dark.
- The July 15 EPUB progress-footer pass completes 148 JVM tests across 25 suites with zero failures, Android lint, debug APK assembly, and instrumentation-test compilation. Unit coverage verifies completion and normalized 1-1000 location calculations, while a compiled Compose test verifies the visible and accessible footer text. Target-device validation must confirm the footer stays readable, theme-matched, and unobstructed on representative screen sizes while page and chapter changes update every field.
- The July 15 unread-refresh follow-up completes 149 JVM tests across 25 suites with zero failures, Android lint, debug APK assembly, and instrumentation-test compilation. Its regression fixture includes zero progress plus stale page, position, book, progress, and unread-status timestamps and verifies that none can reconstruct Currently Reading activity. Target-device confirmation remains required against the live server response.
- The July 15 large-catalog refresh pass completes 150 JVM tests across 25 suites with zero failures, Android lint, debug APK assembly, and instrumentation-test compilation. Its coroutine regression verifies 100-book page ordering, a four-request concurrency ceiling, and complete 1,000-book merging. Target-device timing and server-load confirmation remain required with the roughly 5,000-book library.
- The July 15 HTTP-server pass retains 150 JVM tests across 25 suites with zero failures, Android lint, debug APK assembly, and instrumentation-test compilation. URL coverage verifies explicit remote HTTP, HTTPS and bare-host defaults, unsupported-scheme rejection, and a compiled Compose setup flow that submits an explicit HTTP URL. Target-device confirmation remains required against the intended HTTP BookOrbit deployment.
- The July 15 book-status action pass completes 153 JVM tests across 25 suites with zero failures, Android lint, debug APK assembly, and instrumentation-test compilation. Coordinator and payload coverage verifies position-preserving Mark as read and progress-resetting Mark as unread; Room coverage verifies immediate cached read status, and compiled Compose coverage exercises overflow plus long-press menus. Target-device/server confirmation is deferred with the rest of the manual pass.

## Latest device feedback — July 15, 2026

### Validation completed

- Explicit HTTP BookOrbit connections work correctly.
- Mark as read and Mark as unread work correctly.
- Android/BookOrbit progress synchronization works correctly.
- Large-library browsing and the #/A–Z jump rail are fast and accurate.
- Airplane-mode browsing works. Undownloaded books correctly cannot open full details without a connection.
- Session-expiry recovery works as expected.
- Series navigation and Series thumbnails work correctly.

### Remaining UI work

- Global book-search results do not currently expose the book context menu through either long-press or the three-dot action.
- Search results intentionally remain list rows rather than poster cards; preserve this presentation while adding the missing actions.

- Launch, navigation, Home refresh, On Deck, battery indicators, and EPUB footer progress passed on the target device.
- Home remains interactive while its initial sync/loading indicator is visible, but the tested build kept the indicator for a noticeable time on roughly 5,000 books. The follow-up now doubles catalog pages to 100 items and loads large page ranges in bounded four-request batches; target-device timing confirmation remains required.
- Currently Reading removal succeeds in the app and BookOrbit's server view, but the tested build repopulated removed titles after Home pull-to-refresh because BookOrbit's explicit unread record carried a fresh status timestamp. The follow-up parser fix now suppresses non-positive unread activity metadata and requires target-device confirmation.
- The remaining reader sync/progress reconciliation test is deferred for now at the user's request.

## Manual Test Matrix

### 1. Server And Login

1. Launch the app on an emulator or device.
2. Enter the BookOrbit server URL.
3. Verify explicit `https://` and `http://` BookOrbit URLs are accepted, while unsupported schemes such as `ftp://` are rejected. Confirm a bare remote hostname defaults to HTTPS and a bare local development target such as `10.0.2.2:3000` defaults to HTTP. Use HTTP only on a trusted network because credentials, tokens, metadata, progress, and content are not encrypted.
4. Verify the native username/password form appears with password visibility control and Change server.
5. Submit valid credentials and confirm the app reaches the library browser.

### 1a. Session Recovery

1. While signed in, fully close and relaunch the app.
2. Confirm an already-valid session returns to the reader or browser without forcing a fresh login.
3. Sign out from the browser screen and confirm the app returns to login cleanly.
4. Latest manual device result on July 10, 2026: explicit sign-out returned to login with the `Change server` action visible, which matches the intended fix for the cached-browser fallback bug.
5. Latest manual device result on July 10, 2026: after signing back in and fully relaunching the app, the existing session returned to the app without forcing a fresh login.
6. If cached Home appears because the server is unavailable, open the drawer and tap `Sign in`; confirm Login remains visible until authentication succeeds.
7. If the reachable server rejects the saved session, confirm cold start opens Login instead of presenting cached Home as if it were merely offline.
8. If possible on the target server, expire the session server-side and confirm the app routes back through login and resumes the intended action after re-authentication.
9. While signed in, trigger search, cover loading, or a library/detail load after expiring the session and confirm the app shows the same session-expired login recovery instead of silently displaying an empty result.
10. If the server exposes refresh-cookie renewal, leave the app idle past the short access-token lifetime or expire only the access token, then browse, load a cover, open a detail screen, and open a downloaded reader file. Confirm the first request renews the session and completes without showing login. Repeat several times over at least 15 minutes; record the server response if login still appears.
11. If possible, trigger two or more authenticated loads together after expiry and confirm only one refresh is needed and none of the loads routes to Login unnecessarily.

### 1b. Physical Device Install

1. Install the latest debug APK on a physical Android device.
2. Confirm the launcher icon is the app-specific adaptive icon instead of the Android default placeholder.
3. Launch the app from the device home screen and confirm the branded adaptive-icon splash/loading state appears without the old spinning loading screen; verify first-run setup still works.
4. Confirm the launcher/app label is Lagrange and the splash/loading presentation shows the subtitle `a BookOrbit reader`; after the app opens, confirm the top bar and About screen do not show that subtitle.

### 2. Library Browsing

1. Confirm the post-login screen opens on Home with the visible Android status bar, intentional top spacing, Lagrange logo, bottom navigation, and search icon visible.
2. Confirm bottom navigation exposes Home, Libraries, and More without a hamburger drawer; open More and verify Series, Authors, Local books, and About appear with comfortable space above the Android navigation area. Open the profile menu and verify Options appears immediately above Log out. Open Libraries and verify the first/selected library opens directly, the logo is replaced by the tappable library name with a downward-triangle affordance, and tapping it opens library selection. While the picker is open, tap the top-left Libraries title and confirm it returns to the selected library page.
3. Confirm Library has Recommended and Browse tabs. Recommended shows Home-style shelves scoped to the selected library; Browse shows the compact adaptive poster-card grid matching Series and Authors. When series are present, tap Collapse series and confirm one representative card remains per series with an accurate `1 book` or `N books` label, then tap Expand series and confirm every book returns.
4. Open More > Local books and confirm valid downloads appear in the same poster-card grid, open locally, and can still be deleted from book details. After metadata has been cached, open the local book with the server unavailable and confirm its full details still appear.
5. In Library > Browse on the first open, confirm `Caching full library...` is temporary and the #/A–Z rail does not appear until the complete catalog is ready. On the roughly 5,000-book library, time one refresh and confirm the indicator clears materially faster than the previous serialized 50-book-page build. Once ready, confirm the header totals match BookOrbit and scrolling to the end never triggers another page load. Tap # and several widely separated letters; each tap must land directly on the requested or next available initial without first scrolling to the old loaded boundary. Pull down from Recommended or Browse and confirm cached cards remain visible under `Updating cached catalog...` until refresh completes.
6. Fully close and reopen Lagrange. Confirm cached Home/Browse content appears before the server refresh finishes. Add, rename, change progress on, and delete test titles in BookOrbit, then refresh Lagrange and confirm each change appears while unchanged cards retain their order. Interrupt one refresh if practical and confirm the prior complete catalog remains usable.
7. Tap Filter in Browse and verify title/author/series matching, unread/in-progress/finished, format, and sort/direction controls update immediately from local metadata without a loading-more row. Confirm the letter rail remains exact for server-default, Title, and Author sorts (including descending order) and is hidden for non-letter sorts such as Date added or Read progress.
8. Return Home and confirm shelves only appear when they contain matching books. From the top of Home, pull down and confirm the refresh indicator appears, existing cached shelves remain usable, and server-side changes are reflected when refresh completes.
9. Confirm Continue reading/Currently reading is the first book shelf, active progress is shown there even if the server read flag is also set, completed books are excluded from it, and Recently read books remains a separate history shelf.
10. Search for a title outside the selected library cache and confirm global BookOrbit results appear.
11. Confirm real book covers load on Home, search results, library lists, and detail screens. Leave the app on unmetered Wi-Fi after a full catalog refresh so the thumbnail worker can advance, then enable airplane mode and jump to several distant letters; already warmed thumbnails should render without a server response.
12. While covers are filling in, rapidly scroll vertically and across several shelves, then stop on a distant row. Confirm off-screen requests stop competing with the visible row, its covers fill promptly, gestures remain smooth, failed thumbnails retry, and memory use does not progressively degrade. Open More and confirm About remains above the Android navigation controls.
13. Open Series and confirm there is no Load more action after loading completes. With Name/Ascending, tap # and several A–Z rail entries and confirm each lands directly on the requested or next available initial across the complete result; repeat with Name/Descending and confirm the rail runs Z–A then #. Select Book count, Last added, or Read progress and confirm the letter rail is hidden because the list is not alphabetic. Tap Filter and verify author/library/completion/sort/direction choices rebuild the complete server result. Confirm Series cards backed by `coverBookIds` show a representative book thumbnail instead of a first-letter placeholder; a series with no covered books should retain the placeholder without requesting a nonexistent Series route. Open Authors and wait for the first screen of cards; confirm covers appear, then scroll away and back and confirm cached catalog images return quickly.
14. Open a series card and confirm it loads the complete ordered series, including series over 100 books, name/authors, read/total count, completion bar, any reported gaps, first-book synopsis, genres/tags, and book list instead of opening a reader.
15. Open a book and confirm title/subtitle, author/narrator, synopsis, genres/tags, publisher, publication date, language, pages, ISBN, rating, library, format, and available file metadata match the main BookOrbit detail page; absent fields should be omitted cleanly. Return and reopen it to confirm the rich detail is immediate. Change that title's metadata in BookOrbit, refresh Lagrange, and confirm the next open replaces the old cached detail.
16. From book details, use Read or Continue reading to enter the reader and verify Download/Delete local still work; use Preview separately and confirm normal progress is unchanged.
17. Confirm Android Back returns from book details to series details when appropriate, then to Home or Libraries.
18. Confirm only Home shows the Lagrange logo in the browser top bar; Library shows the selected library name selector, and other browser destinations show their page title.
19. Confirm the in-content content areas do not repeat Home/library, Series, Authors, or Local books titles above their actual cards; destination bars remain understandable.
20. Confirm the reduced typography is more compact without clipping, and book cards show one title row, an optional series row, and an index row; books without a series may use up to three title rows.
21. Confirm the Android status bar remains visible and Home content has comfortable top spacing below it.
22. Refresh the browser and confirm loading, empty, offline, and error states behave sensibly.

### 3. Reading And Listening

1. Open an EPUB and confirm the chapter content renders instead of a blank/black reading surface, with no one-line-per-page layout or permanent app toolbar. Confirm Android's native top status bar remains visible with battery and current network indicators while the bottom navigation bar stays immersive. Switch among Light, Sepia, and Dark and confirm the status background matches the reader and its icons remain legible. This is the regression check for the latest debug build.
2. Confirm the theme-matched bottom footer remains visible and does not cover book content. It must show rounded completion, chapter number/count, current chapter page/count, and a whole-book location out of 1000. Turn pages and cross a chapter boundary; verify every field updates and the final page reaches 100% and `Book 1000/1000`. Treat Book as a normalized location, not a physical page count.
3. Tap the left and right outer quarters and swipe left and right; confirm each action moves exactly one paginated screen and navigation crosses chapter boundaries.
4. Confirm a swipe does not also trigger a second tap-zone navigation.
5. Tap the center and confirm title, chapter/page status, chapter picker, theme, text-size controls, and exactly one visible Close action appear as overlays; no separate Back action should appear. Confirm the progress footer remains visible and the options are not hidden behind it. Tap the exposed book area and confirm the options close without turning the page. Reopen the options, press Android Back, and confirm it closes the options; press Android Back again and confirm it exits the reader.
6. After content renders, confirm the reader menu exposes Light, Sepia, and Dark themes plus independent Top, Bottom, Left, and Right percentage sliders. Verify every edge starts at 15%, 100% maps to roughly one quarter of the relevant screen dimension, changing one slider does not change the others, and moving each slider visibly changes the text inset and repaginates while the menu remains open. Test top and bottom with large changes in both directions, advance to later pages, and confirm translated pages still contain visible text rather than becoming blank. Verify the Close action dismisses the options. Images must remain constrained to the page.
7. Select Dark, close and reopen the EPUB, and confirm Dark remains selected while the reader returns to the exact saved page within the saved chapter. Open another EPUB and confirm it also uses Dark. Repeat after fully closing and relaunching the app, then restore the preferred theme.
8. Set the device display timeout to its shortest practical value, leave an EPUB untouched for longer than that timeout, and confirm the display and reader remain open. Leave the reader and confirm normal display timeout behavior returns. A manual power-button lock or other system interruption must still work.
9. Recheck offline images, progress sync, and last-session restore against the available EPUB sample.
10. Audiobook, PDF, and CBZ validation is deferred until representative sample files are available.

### 4. Downloads And Offline Reopen

1. EPUB download and local reopen are the currently validated paths.
2. Confirm the browser shows the downloaded state correctly.
3. Defer audiobook, PDF, and CBZ download testing until representative sample files are available.

### 5. Offline Recovery

1. Put the device or emulator offline.
2. Relaunch the app and confirm the complete cached browser state is shown before network recovery finishes; Browse must not fall back to only its former first page.
3. Reopen downloaded content and verify local playback or reading still works.
4. Confirm non-downloaded titles show offline limitations cleanly.

### 6. Progress Sync

1. Read or listen long enough to create progress updates.
2. Go offline and continue generating progress.
3. Reconnect the device.
4. Close the reader immediately after a page change and confirm the final progress still leaves the debug queue and reaches the server.
5. Fully relaunch the app and confirm the first Home render already shows the current-reading shelf without opening and closing the book first.
6. Change Top and Bottom padding to visibly different values, close and reopen the same EPUB, and confirm both values persist independently and the text inset remains changed.
7. Read an unfinished EPUB in Lagrange, close the reader, and confirm the debug pending count drains only after sync. Refresh BookOrbit's web app and confirm the same title, `Reading` status, and approximate percentage appear in BookOrbit's Currently Reading view.
8. Advance that title in BookOrbit's web reader, return to Lagrange, refresh Home, and confirm the newer server percentage replaces the prior local overlay.
9. Move backward by at least 10 percentage points in Lagrange and close the reader; confirm BookOrbit accepts the lower percentage as reread/correction progress instead of retaining an old 100% or higher marker.
10. Repeat with a title below 1% progress and confirm neither side turns that value into 50% or 100%.
11. Turn several pages rapidly while a sync is likely active, immediately close the reader, and confirm the last page—not an earlier in-flight page—reaches BookOrbit and the title remains in Currently Reading.
12. Finish a title at 99.5% or above and confirm BookOrbit changes its status to `Read` and removes it from Currently Reading.
13. On a Currently Reading card, open the visible overflow menu and confirm `Remove from Currently reading` is available; repeat by long-pressing the card.
14. Using a normal account without metadata-edit permission, remove the title and confirm it disappears from Currently Reading in both Lagrange and BookOrbit and its primary/current progress resets. Pull down to refresh Home and confirm the removed title stays absent, then reopen it and confirm it starts from the beginning. Relaunch Lagrange once more to confirm stale queued or exact EPUB resume state does not restore the old position. For an audiobook, confirm BookOrbit shows zero progress. Historical reading sessions and progress on additional non-primary files are outside this fallback.
15. On Home and an individual Library Browse book, open the three-dot menu and repeat with a long-press. Confirm both paths show `Mark as read` and `Mark as unread`; collapsed series representatives should continue opening the series without single-book status actions.
16. Mark an in-progress book read and confirm it leaves Currently Reading, appears read in Lagrange and BookOrbit, and retains its prior reading position. Then mark it unread and confirm both sides report unread, progress resets, and pull-to-refresh does not restore the prior read/progress state.
17. For a series with volumes 1 and 2 complete, confirm On Deck shows volume 3. Begin volume 3 and confirm On Deck shows no later volume while volume 3 remains in Currently Reading.
