# Testing

This document contains current verification gates and reusable manual procedures. Historical test results, old APK paths, and dated validation logs are preserved in [`docs/testing-archive.md`](testing-archive.md).

## Automated verification

For a normal implementation step, run the narrowest relevant checks first, then the complete gate when the approved scope requires it:

```text
./gradlew compileDebugKotlin compileDebugUnitTestKotlin compileDebugAndroidTestKotlin
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

For release work, also run the approved release assembly and release workflow checks. Record test counts, lint severity counts, artifact paths, freshness, `git diff --check`, and ADB/device status.

Compiled Android instrumentation is not executed instrumentation. If `adb devices -l` does not enumerate a usable target, report connected tests as unexecuted.

## Debug APK handoff

When asking the user to perform manual testing:

1. Run `assembleDebug` from the final worktree.
2. Use the generated timestamped artifact:
   `app/build/outputs/apk/debug/Lagrange-debug-yyyymmddhhmm.apk`
3. Report the exact path and filename.
4. Use the standard `app-debug.apk` only as the Gradle source artifact.

## Current validation priorities

### Download lifecycle

On a connected device or emulator, verify from Home, Library, Search, Series, Authors, Genre, and Local books:

- remote idle books show `Download local`;
- active transfers show `Cancel`;
- failed transfers show `Retry` and `Clear`;
- downloaded books show `Delete local`;
- Local books shows active/failed Downloads rows only when needed;
- `Clear` and `Clear all` remove failed state without cancelling active transfers;
- force-closing during a download restores a failed row with `Retry` and `Clear`;
- a failed first download stays out of Local books;
- a failed update preserves the previous local copy and exposes `Update local` plus `Delete local`.

This is the primary outstanding device-validation item.

### Refresh lifecycle

On Book Detail, Series, Authors, Local books, Statistics, and Achievements:

- explicit pull-to-refresh shows the indicator through completion;
- current content remains usable while loading;
- duplicate gestures do not create duplicate requests;
- failed refreshes preserve the last successful content where applicable;
- Local books refresh does not interrupt active downloads or destructive actions;
- automatic/background synchronization remains silent.

### Reader and media regression checks

When the affected scope requires it, verify:

- normal and Preview launch isolation;
- EPUB resume and continuous-mode active-resource seeking;
- EPUB reading-direction changes preserve text alignment, punctuation, and other typography/layout settings while only reversing left/right edge tap navigation; verify both directions in paginated and continuous modes. User-confirmed on the rebuilt debug APK;
- PDF/comic page navigation and progress;
- audiobook compact-player restoration, seeking, chapters, and speed;
- reader settings persistence per library; issue #19 current-package APK-upgrade persistence was user-confirmed working on the supplied debug build;
- EPUB reader options open at approximately two-thirds of the reader surface, the top handle exposes a minimum 48 dp touch target and expands/contracts the sheet without losing the live reader preview, and the EPUB line-spacing slider updates the preview and persists after reopening the options. User-confirmed on the rebuilt debug APK;
- changing line spacing or the options-sheet height preserves the other reader settings, including theme, font, direction, margins, and layout;
- same-application-ID APK upgrade preserves EPUB theme/font/size/margins and the applicable PDF/comic direction, layout, and page-gap settings for more than one library;
- the reader preference store accepts legacy flat profiles and preserves valid profiles when another stored profile is malformed; migration from the retired `com.bookorbit.android` package is intentionally out of scope;
- server-missing versus locally deleted state;
- large text, accessibility, orientation, offline, and narrow-width behavior.

### Authentication and OIDC

Verify password login, `/api/v1/auth/me` bootstrap, sign-out/session reset, session-expiry recovery, and pending-destination recovery. The interim server-hosted sign-in WebView is distinct from native AppAuth. Native AppAuth requires deployed BookOrbit mobile-redirect support and separate provider/device validation.

## Verification reporting

Every final report should state:

- commands executed and results;
- focused and full test counts;
- lint error/warning/information counts;
- generated APK paths and whether they were fresh;
- `git diff --check` result;
- ADB/device and instrumentation status;
- remaining manual validation;
- preserved unrelated worktree changes.
