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

Current instrumentation coverage includes server setup validation, login recovery UI and server-change routing, populated live-browser rendering, browser loading states, cached offline browser actions, an in-memory Room reconciliation transaction, and a WebView-level EPUB regression that checks runtime padding geometry plus visible text after page translation. `assembleDebugAndroidTest` compiles this coverage with the AndroidX test runner; run the connected test task when a device or emulator is available.

## Latest device feedback — July 12, 2026

- Server setup and login passed.
- Full relaunch with an existing session passed.
- Library browsing and navigation passed.
- EPUB reading and the current pagination behavior passed.
- Download, airplane-mode reopen, and progress-sync behavior passed.
- The previous launch visual issue was a spinning loading indicator instead of the expected app-specific adaptive-icon presentation; it is now replaced in code with a branded splash/loading state and needs physical-device confirmation.
- The next device pass must validate the new first-row Currently reading shelf and the implemented Plex-inspired shell: Home/Libraries/More bottom navigation, More expansion, Home-only logo/search/profile actions, the tappable Library name selector, Recommended/Browse tabs, visible status bar/Home spacing, and search layer. It must also validate the complete #/A–Z jump rail, series ordering after collapse/expand, session-expiry recovery, and the branded launch state.
- Exact in-chapter EPUB restore, compact poster-card library browsing, Lagrange branding with the subtitle `a BookOrbit reader` on splash/loading only, the Libraries series-collapse control, Local books before Options in More, the placeholder About destination, swipe-down refresh, and persistent cover-thumbnail caching are implemented. Physical-device validation remains required for the new behavior.
- The latest reader follow-up restores the same single visible-overflow strip used before the blank-screen regression. Target-device testing confirms that EPUB content renders and that Top/Bottom WebView resizing plus Left/Right in-page updates visibly change the reader padding.
- The July 13 follow-up adds refresh-cookie retry before session-expired recovery, deterministic/retried Series catalog covers with in-memory image caching, immediate Continue reading updates, smaller shared typography, and title/series/index card metadata rows. These changes require physical-device validation.
- The progress reconciliation follow-up parses BookOrbit's numeric `readingProgress` and nested `readStatus`, keeps all reader and API values on a single 0-100 scale, and permits newer reread/backward corrections. JVM tests, debug assembly, and instrumentation-test compilation pass; target-server bidirectional validation is required.
- The catalog follow-up replaces Browse lazy loading with a complete server-scoped Room metadata cache. Cached content renders before cold-start network checks finish; refresh reconciles all pages and writes only changed/new/reordered rows or deletions. Exact default-sort rail taps use BookOrbit jump-bucket indexes. JVM tests and instrumentation-test compilation pass; target-device/server validation is required.

## Manual Test Matrix

### 1. Server And Login

1. Launch the app on an emulator or device.
2. Enter the BookOrbit server URL.
3. Verify a non-local `http://` URL is rejected and that a local development URL such as `http://10.0.2.2:3000` is still accepted when appropriate.
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
3. Confirm Library has Recommended and Browse tabs. Recommended shows Home-style shelves scoped to the selected library; Browse shows the compact adaptive poster-card grid matching Series and Authors. When series are present, tap Collapse series and confirm one representative card remains per series, then tap Expand series and confirm every book returns.
4. Open More > Local books and confirm valid downloads appear in the same poster-card grid, open locally, and can still be deleted from book details. After metadata has been cached, open the local book with the server unavailable and confirm its full details still appear.
5. In Library > Browse on the first open, confirm `Caching full library...` is temporary and the #/A–Z rail does not appear until the complete catalog is ready. Once ready, confirm the header totals match BookOrbit and scrolling to the end never triggers another page load. Tap # and several widely separated letters; each tap must land directly on the requested or next available initial without first scrolling to the old loaded boundary. Pull down from Recommended or Browse and confirm cached cards remain visible under `Updating cached catalog...` until refresh completes.
6. Fully close and reopen Lagrange. Confirm cached Home/Browse content appears before the server refresh finishes. Add, rename, change progress on, and delete test titles in BookOrbit, then refresh Lagrange and confirm each change appears while unchanged cards retain their order. Interrupt one refresh if practical and confirm the prior complete catalog remains usable.
7. Tap Filter in Browse and verify title/author/series matching, unread/in-progress/finished, format, and sort/direction controls update immediately from local metadata without a loading-more row. Confirm the letter rail remains exact for server-default, Title, and Author sorts (including descending order) and is hidden for non-letter sorts such as Date added or Read progress.
8. Return Home and confirm shelves only appear when they contain matching books.
9. Confirm Continue reading/Currently reading is the first book shelf, active progress is shown there even if the server read flag is also set, completed books are excluded from it, and Recently read books remains a separate history shelf.
10. Search for a title outside the selected library cache and confirm global BookOrbit results appear.
11. Confirm real book covers load on Home, search results, library lists, and detail screens. After a cover has loaded, open More > Local books and verify the thumbnail remains visible without requiring a server response.
12. While covers are filling in, rapidly scroll vertically and across several shelves; confirm gestures remain smooth, failed thumbnails retry, and memory use does not progressively degrade. Open More and confirm About remains above the Android navigation controls.
13. Open Series, tap Filter, and verify author/library/completion/sort/direction choices reload the server catalog. Open Authors and wait for the first screen of cards; confirm covers appear rather than remaining first-letter placeholders, then scroll away and back and confirm cached covers return quickly. Confirm a series without cover metadata still tries the server cover endpoint.
14. Open a series card and confirm it loads the complete ordered series, including series over 100 books, name/authors, read/total count, completion bar, any reported gaps, first-book synopsis, genres/tags, and book list instead of opening a reader.
15. Open a book and confirm title/subtitle, author/narrator, synopsis, genres/tags, publisher, publication date, language, pages, ISBN, rating, library, format, and available file metadata match the main BookOrbit detail page; absent fields should be omitted cleanly.
16. From book details, use Read or Continue reading to enter the reader and verify Download/Delete local still work; use Preview separately and confirm normal progress is unchanged.
17. Confirm Android Back returns from book details to series details when appropriate, then to Home or Libraries.
18. Confirm only Home shows the Lagrange logo in the browser top bar; Library shows the selected library name selector, and other browser destinations show their page title.
19. Confirm the in-content content areas do not repeat Home/library, Series, Authors, or Local books titles above their actual cards; destination bars remain understandable.
20. Confirm the reduced typography is more compact without clipping, and book cards show one title row, an optional series row, and an index row; books without a series may use up to three title rows.
21. Confirm the Android status bar remains visible and Home content has comfortable top spacing below it.
22. Refresh the browser and confirm loading, empty, offline, and error states behave sensibly.

### 3. Reading And Listening

1. Open an EPUB and confirm the chapter content renders instead of a blank/black reading surface, with no one-line-per-page layout or permanent app toolbar; system bars should remain immersive with transient swipe reveal. This is the regression check for the latest debug build.
2. Tap the left and right outer quarters and swipe left and right; confirm each action moves exactly one paginated screen and navigation crosses chapter boundaries.
3. Confirm a swipe does not also trigger a second tap-zone navigation.
4. Tap the center and confirm Back, title, chapter/page status, chapter picker, theme, and text-size controls appear as overlays; confirm additional center or outside taps leave them open until Close is tapped.
5. After content renders, confirm the reader menu exposes independent Top, Bottom, Left, and Right percentage sliders. Verify every edge starts at 15%, 100% maps to roughly one quarter of the relevant screen dimension, changing one slider does not change the others, and moving each slider visibly changes the text inset and repaginates while the menu remains open. Test top and bottom with large changes in both directions, advance to later pages, and confirm translated pages still contain visible text rather than becoming blank. Verify only the top-right Close action dismisses the options; center or other outside taps must not dismiss it. Images must remain constrained to the page.
6. Close and reopen the EPUB and confirm it returns to the exact saved page within the saved chapter; repeat after fully closing and relaunching the app.
7. Recheck offline images, progress sync, and last-session restore against the available EPUB sample.
8. Audiobook, PDF, and CBZ validation is deferred until representative sample files are available.

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
7. Read an unfinished EPUB in Lagrange, close the reader, refresh BookOrbit's web app, and confirm the same title and approximate percentage appear in BookOrbit's Currently Reading view.
8. Advance that title in BookOrbit's web reader, return to Lagrange, refresh Home, and confirm the newer server percentage replaces the prior local overlay.
9. Move backward by at least 10 percentage points in Lagrange and close the reader; confirm BookOrbit accepts the lower percentage as reread/correction progress instead of retaining an old 100% or higher marker.
10. Repeat with a title below 1% progress and confirm neither side turns that value into 50% or 100%.
