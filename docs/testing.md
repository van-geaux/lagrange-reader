# Testing

This document marks the current point where manual app testing can start.

## Start Here

You can begin manual testing once these conditions are true:

- `.\gradlew.bat assembleDebug` passes
- `.\gradlew.bat testDebugUnitTest` passes
- `.\gradlew.bat assembleDebugAndroidTest` passes if you plan to run instrumentation tests
- a reachable BookOrbit server URL is available
- at least one account can sign in and access a library with real content

Current Compose instrumentation coverage includes server setup validation, login recovery UI and server-change routing, populated live-browser rendering, browser loading states, and cached offline browser actions. `assembleDebugAndroidTest` compiles this coverage; run the connected test task when a device or emulator is available.

## Latest device feedback — July 12, 2026

- Server setup and login passed.
- Full relaunch with an existing session passed.
- Library browsing and navigation passed.
- EPUB reading and the current pagination behavior passed.
- Download, airplane-mode reopen, and progress-sync behavior passed.
- The previous launch visual issue was a spinning loading indicator instead of the expected app-specific adaptive-icon presentation; it is now replaced in code with a branded splash/loading state and needs physical-device confirmation.
- The next device pass must validate the new first-row Currently reading shelf and the implemented Plex-inspired shell: Home/Libraries/More bottom navigation, More expansion, Home-only logo/search/profile actions, the tappable Library name selector, Recommended/Browse tabs, visible status bar/Home spacing, and search layer. It must also validate four independent reader percentage sliders for Top, Bottom, Left, and Right, the 15% defaults, the 100%-equals-25%-viewport mapping, repagination after changing each control, the complete #/A–Z jump rail, series ordering after collapse/expand, session-expiry recovery, and the branded launch state.
- Exact in-chapter EPUB restore, compact poster-card library browsing, Lagrange branding with the subtitle `a BookOrbit reader` on splash/loading only, the Libraries series-collapse control, Local books before Options in More, the placeholder About destination, swipe-down refresh, and persistent cover-thumbnail caching are implemented. Physical-device validation remains required for the new behavior.
- The latest follow-up also persists the login access token for authenticated requests, restores Continue reading from tolerant progress payloads, removes the duplicate Library Recommended heading, moves Options into the profile menu, keeps reader options open until Close, applies reader padding changes while sliding, and adds more More-sheet spacing. Physical-device validation remains required.
- The July 13 follow-up adds refresh-cookie retry before session-expired recovery, deterministic/retried Series catalog covers with in-memory image caching, immediate Continue reading updates, smaller shared typography, and title/series/index card metadata rows. These changes require physical-device validation.

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
5. In Library > Browse, confirm the header shows the full library book/series totals rather than only the loaded page. Scroll near the end of the loaded grid and confirm the next page loads automatically without replacing existing books; repeat until the server total is reached. Confirm the right-side rail always shows # followed by A–Z, including letters not yet loaded; tap # for non-alphabetic titles and tap letters to move to the nearest available content. Pull down from the top of Recommended and Browse and confirm the library refreshes without a refresh arrow in the top bar.
6. Return Home and confirm shelves only appear when they contain matching books.
7. Confirm Continue reading/Currently reading is the first book shelf, active progress is shown there even if the server read flag is also set, completed books are excluded from it, and Recently read books remains a separate history shelf.
8. Search for a title outside the initially loaded library page and confirm global BookOrbit results appear.
9. Confirm real book covers load on Home, search results, library lists, and detail screens. After a cover has loaded, open More > Local books and verify the thumbnail remains visible without requiring a server response.
10. While covers are filling in, rapidly scroll vertically and across several shelves; confirm gestures remain smooth, failed thumbnails retry, and memory use does not progressively degrade. Open More and confirm About remains above the Android navigation controls.
11. Open Series and Authors and wait for the first screen of cards; confirm covers appear rather than remaining first-letter placeholders, then scroll away and back and confirm cached covers return quickly. Confirm a series without cover metadata still tries the server cover endpoint.
12. Open a series card and confirm it loads the complete ordered series, including series over 100 books, name/authors, read/total count, completion bar, any reported gaps, first-book synopsis, genres/tags, and book list instead of opening a reader.
13. Open a book and confirm title/subtitle, author/narrator, synopsis, genres/tags, publisher, publication date, language, pages, ISBN, rating, library, format, and available file metadata match the main BookOrbit detail page; absent fields should be omitted cleanly.
14. From book details, use Read or Continue reading to enter the reader and verify Download/Delete local still work; use Preview separately and confirm normal progress is unchanged.
15. Confirm Android Back returns from book details to series details when appropriate, then to Home or Libraries.
16. Confirm only Home shows the Lagrange logo in the browser top bar; Library shows the selected library name selector, and other browser destinations show their page title.
17. Confirm the in-content content areas do not repeat Home/library, Series, Authors, or Local books titles above their actual cards; destination bars remain understandable.
18. Confirm the reduced typography is more compact without clipping, and book cards show one title row, an optional series row, and an index row; books without a series may use up to three title rows.
19. Confirm the Android status bar remains visible and Home content has comfortable top spacing below it.
20. Refresh the browser and confirm loading, empty, offline, and error states behave sensibly.

### 3. Reading And Listening

1. Open an EPUB and confirm the text and images fill the viewport with no one-line-per-page layout or permanent app toolbar; the standard Android status bar may remain visible.
2. Tap the left and right outer quarters and swipe left and right; confirm each action moves exactly one paginated screen and navigation crosses chapter boundaries.
3. Confirm a swipe does not also trigger a second tap-zone navigation.
4. Tap the center and confirm Back, title, chapter/page status, chapter picker, theme, and text-size controls appear as overlays; tap the center again to hide them.
5. Confirm the reader menu exposes independent Top, Bottom, Left, and Right percentage sliders. Verify every edge starts at 15%, 100% maps to roughly one quarter of the relevant screen dimension, changing one slider does not change the others, and moving each slider visibly changes the text inset and repaginates while the menu remains open. Test top and bottom with large changes in both directions. Verify only the top-right Close action dismisses the options; center or other outside taps must not dismiss it. Images must remain constrained to the page.
6. Close and reopen the EPUB and confirm it returns to the exact saved page within the saved chapter; repeat after fully closing and relaunching the app.
7. Recheck offline images, progress sync, and last-session restore against the available EPUB sample.
8. Audiobook, PDF, and CBZ validation is deferred until representative sample files are available.

### 4. Downloads And Offline Reopen

1. EPUB download and local reopen are the currently validated paths.
2. Confirm the browser shows the downloaded state correctly.
3. Defer audiobook, PDF, and CBZ download testing until representative sample files are available.

### 5. Offline Recovery

1. Put the device or emulator offline.
2. Relaunch the app and confirm cached browser state is shown.
3. Reopen downloaded content and verify local playback or reading still works.
4. Confirm non-downloaded titles show offline limitations cleanly.

### 6. Progress Sync

1. Read or listen long enough to create progress updates.
2. Go offline and continue generating progress.
3. Reconnect the device.
4. Confirm queued progress leaves the debug queue and reaches the server.
