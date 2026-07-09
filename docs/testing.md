# Testing

This document marks the current point where manual app testing can start.

## Start Here

You can begin manual testing once these conditions are true:

- `.\gradlew.bat assembleDebug` passes
- `.\gradlew.bat testDebugUnitTest` passes
- a reachable BookOrbit server URL is available
- at least one account can sign in and access a library with real content

## Manual Test Matrix

### 1. Server And Login

1. Launch the app on an emulator or device.
2. Enter the BookOrbit server URL.
3. Verify the login page opens in the in-app WebView.
4. Complete sign in and confirm the app reaches the library browser.

### 2. Library Browsing

1. Refresh the library browser.
2. Switch between at least two libraries if available.
3. Confirm loading, empty, and error states behave sensibly.

### 3. Reading And Listening

1. Open one audiobook and verify play, pause, skip, speed, and resume behavior.
2. Open one PDF and verify paging plus zoom and pan behavior.
3. Open one EPUB and verify chapter navigation plus theme and font controls.
4. Open one CBZ if available and verify page navigation.

### 4. Downloads And Offline Reopen

1. Download one audiobook, one PDF, and one EPUB if available.
2. Reopen each downloaded item from local storage.
3. Confirm the browser shows the downloaded state correctly.

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
