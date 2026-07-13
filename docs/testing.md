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
- The next device pass must validate the new first-row Currently reading shelf and the implemented Plex-inspired shell: Home/Libraries/More bottom navigation, More expansion, top logo/search/profile actions, library picker/change control, visible status bar/Home spacing, and search layer. It must also validate Compact/Comfortable/Wide reader padding, the Comfortable default, repagination after changing it, and the branded launch state.
- New implementation targets are Lagrange branding with the subtitle `a BookOrbit reader` and a placeholder About destination after Options in More. Exact in-chapter EPUB restore and poster-card library browsing are implemented and now need physical-device validation.

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

### 1b. Physical Device Install

1. Install the latest debug APK on a physical Android device.
2. Confirm the launcher icon is the app-specific adaptive icon instead of the Android default placeholder.
3. Launch the app from the device home screen and confirm the branded adaptive-icon splash/loading state appears without the old spinning loading screen; verify first-run setup still works.

### 2. Library Browsing

1. Confirm the post-login screen opens on Home with the visible Android status bar, intentional top spacing, bottom navigation, and search icon visible.
2. Confirm bottom navigation exposes Home, Libraries, and More without a hamburger drawer; open More and verify Series, Authors, Options, and About expand from it. Open Libraries and verify the top-level library view and top library-change control.
3. Select a library child and confirm its books load as adaptive poster cards matching the Series and Authors grids, with metadata and download actions still available.
4. Return Home and confirm shelves only appear when they contain matching books.
5. Confirm Currently reading is the first book shelf, active progress is shown there, completed books are excluded from it, and Recently read books remains a separate history shelf.
6. Search for a title outside the initially loaded library page and confirm global BookOrbit results appear.
7. Confirm real book covers load on Home, search results, library lists, and detail screens.
8. While covers are filling in, rapidly scroll vertically and across several shelves; confirm gestures remain smooth and memory use does not progressively degrade.
9. Open a series card and confirm it loads the complete ordered series, including series over 100 books, name/authors, read/total count, completion bar, any reported gaps, first-book synopsis, genres/tags, and book list instead of opening a reader.
10. Open a book and confirm title/subtitle, author/narrator, synopsis, genres/tags, publisher, publication date, language, pages, ISBN, rating, library, format, and available file metadata match the main BookOrbit detail page; absent fields should be omitted cleanly.
11. From book details, use Read or Continue reading to enter the reader and verify Download/Delete local still work; use Preview separately and confirm normal progress is unchanged.
12. Confirm Android Back returns from book details to series details when appropriate, then to Home or Libraries.
13. Confirm the top logo, search icon/search layer, and profile menu are present; live profile action shows Log out and cached offline profile action shows Sign in.
14. Confirm the Android status bar remains visible and Home content has comfortable top spacing below it.
15. Refresh the browser and confirm loading, empty, offline, and error states behave sensibly.

### 3. Reading And Listening

1. Open an EPUB and confirm the text and images fill the viewport with no one-line-per-page layout or permanent app toolbar; the standard Android status bar may remain visible.
2. Tap the left and right outer quarters and swipe left and right; confirm each action moves exactly one paginated screen and navigation crosses chapter boundaries.
3. Confirm a swipe does not also trigger a second tap-zone navigation.
4. Tap the center and confirm Back, title, chapter/page status, chapter picker, theme, and text-size controls appear as overlays; tap the center again to hide them.
5. Confirm changing theme, text size, or reader padding repaginates without enabling vertical scrolling, images remain constrained to the page, and the default text inset is comfortably away from the edges.
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
