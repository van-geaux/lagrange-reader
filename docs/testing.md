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

## Manual Test Matrix

### 1. Server And Login

1. Launch the app on an emulator or device.
2. Enter the BookOrbit server URL.
3. Verify a non-local `http://` URL is rejected and that a local development URL such as `http://10.0.2.2:3000` is still accepted when appropriate.
4. Verify the login page opens in the in-app WebView.
5. Complete sign in and confirm the app reaches the library browser.

### 1a. Session Recovery

1. While signed in, fully close and relaunch the app.
2. Confirm an already-valid session returns to the reader or browser without forcing a fresh login.
3. Sign out from the browser screen and confirm the app returns to login cleanly.
4. Latest manual device result on July 10, 2026: explicit sign-out returned to login with the `Change server` action visible, which matches the intended fix for the cached-browser fallback bug.
5. Latest manual device result on July 10, 2026: after signing back in and fully relaunching the app, the existing session returned to the app without forcing a fresh login.
6. If possible on the target server, expire the session server-side and confirm the app routes back through login and resumes the intended action after re-authentication.

### 1b. Physical Device Install

1. Install the latest debug APK on a physical Android device.
2. Confirm the launcher icon is the app-specific adaptive icon instead of the Android default placeholder.
3. Launch the app from the device home screen and verify first-run setup still works.

### 2. Library Browsing

1. Confirm the post-login screen opens on Home with the menu button and search field visible.
2. Open the drawer and confirm Home, Libraries, each available library, and Log out are present.
3. Select a library child and confirm its book list loads.
4. Return Home and confirm shelves only appear when they contain matching books.
5. Search for a title outside the initially loaded library page and confirm global BookOrbit results appear.
6. Confirm real book covers load on Home, search results, library lists, and detail screens.
7. Open a series card and confirm it shows the ordered books in that series instead of opening a reader.
8. Open a book and confirm its details appear; use Read or Continue reading to enter the reader.
9. Confirm Android Back returns from book details to series details when appropriate, then to Home or Libraries.
10. Confirm the status bar is hidden and can be revealed transiently with a system-edge swipe.
11. Refresh the browser and confirm loading, empty, offline, and error states behave sensibly.

### 3. Reading And Listening

1. EPUB chapter navigation, theme and font controls, local reopen, and session restore are the currently validated reading paths.
2. Latest manual device result on July 10, 2026: closing and reopening the tested EPUB, and fully closing and relaunching the app, both restored the last reading session correctly.
3. Audiobook, PDF, and CBZ validation is deferred until representative sample files are available.

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
