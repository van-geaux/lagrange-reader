# Testing

This document contains current verification gates and reusable procedures. Historical results, old APK paths, and dated validation logs are preserved in [`docs/testing-archive.md`](testing-archive.md).

## Evidence rules

- Report source/compile checks, JVM tests, lint, APK assembly, and connected-device execution as separate evidence.
- Compiled Android instrumentation is not executed instrumentation. If `adb devices -l` has no usable target, report device tests as unexecuted.
- Record exact test counts, lint findings, APK paths, freshness, device status, and remaining manual validation from the command output. Do not infer them from older reports.
- Use a timestamped debug APK for manual handoff: `app/build/outputs/apk/debug/Lagrange-debug-yyyymmddhhmm.apk`. The standard `app-debug.apk` is the Gradle source artifact.

## Automated verification

Run the narrowest relevant checks first, then the complete gate for the approved scope:

```text
./gradlew compileDebugKotlin compileDebugUnitTestKotlin compileDebugAndroidTestKotlin
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

For release work, also verify the approved release assembly/workflow and the release asset naming policy. For PDFium packaging, verify the built APK is `arm64-v8a`-only, run `zipalign -P 16 -c`, and inspect packaged native libraries for `p_align=0x4000`. These checks do not replace connected UI tests.

## Debug APK handoff

1. Run `assembleDebug` from the final worktree.
2. Report the exact generated timestamped path and filename.
3. Copy the same verified handoff APK to the main checkout when an issue worktree is used, then compare SHA-256 hashes before reporting both paths.
4. Use the applicable manual procedure below and distinguish user confirmation from assistant-side execution.

## Current validation priorities

### Provider switching and capability routing

On a connected device, switch between a configured Komga server and a configured BookOrbit server without restarting the app. Verify each provider rebinds its supported modules, audiobook playback opens only where supported, and unsupported providers show an explicit unavailable state instead of stale callbacks or empty success. Repeat after sign-out/server change and after process recreation.

Use [`docs/provider-checklists/komga.md`](provider-checklists/komga.md) for Komga catalog, format, progress, download, and offline-reader validation. Use [`docs/provider-checklists/module-function-matrix.md`](provider-checklists/module-function-matrix.md) to confirm provider-exclusive actions remain unavailable when the selected provider lacks the capability.

### Settings and reader options

Verify Settings contains Appearance, General, Library, Downloads & Offline, and Network & Sync without duplicate About or Account & Server entries. Change a library reader preference and confirm it remains scoped to that library after reopening.

Open reader options for EPUB, PDF, and comics. EPUB exposes typography, margins, and layout controls; PDF exposes PDF layout and page-gap controls; comics expose CBR/CBZ layout and page-gap controls. Confirm changes apply live and persist after closing/reopening the reader.

Verify `Immersive reading: hide navigation bar` persists and applies to EPUB, PDF, and comics. When disabled, content, progress, and reader menus remain above the visible navigation-bar inset. Audiobook behavior is unchanged.

### Download and offline lifecycle

On Home, Library, Search, Series, Authors, Genre, and Local books, verify:

- remote idle books show `Download local`;
- active transfers show `Cancel` and progress;
- failed transfers show `Retry` and `Clear`;
- downloaded books expose `Delete local`;
- Local books shows active/failed Downloads rows only when needed;
- `Clear` and `Clear all` remove failed state without cancelling active transfers;
- force-closing during a transfer restores a failed row with `Retry` and `Clear`;
- a failed first download stays out of Local books;
- a failed update preserves the previous local copy and exposes `Update local` plus `Delete local`.

Download EPUB, PDF, CBZ, CBR, and CB7 samples. Reopen valid local files with the network disabled. Connected CBR/CB7 should use server page extraction; downloaded CBR/CB7 should use the client-side RAR4/RAR5/7z extractor. Unsupported ebook formats must show the explicit unsupported-format state. Confirm no partial file is promoted after cancellation or failure.

### Reader and media regression checks

When the affected scope requires it, verify normal versus Preview launch isolation, exact normal resume, Preview starting without persisted progress, progress on close, offline local fallback, orientation lock, keep-awake, themes, accessibility, and large-text behavior.

For EPUB, verify chapter/page navigation, continuous active-resource seeking, text size, margins, reading direction, custom/accessibility fonts, image handling, and optional read-along narration. For PDF and comics, verify page navigation, progress, links where applicable, layout modes, page gaps, and reader chrome. For audiobook playback, verify compact/full players, chapters, seeking, speed, sleep timer, session history, notification controls, backgrounding, screen lock, audio focus, and process/task recreation.

### EPUB Image Library

For a downloaded EPUB, verify the EPUB-only Image Library action, document-order image listing, duplicate suppression, SVG exclusion, minimum-size filtering, thumbnail navigation, bounded zoom/pan, outside-tap and Back dismissal, and original-image export. For an online EPUB, consent must start the normal durable selected-file download; the gallery opens only after download success. Offline missing content must report unavailable without a consent prompt.

### Refresh, cache, and lifecycle

On Book Detail, Series, Authors, Local books, Statistics, and Achievements:

- explicit pull-to-refresh keeps its indicator visible through completion;
- content remains usable while loading;
- duplicate gestures do not create duplicate requests;
- failed refresh preserves the last successful content where applicable;
- Local books refresh does not interrupt active downloads or destructive actions;
- automatic/background synchronization remains silent.

For cached catalogs, confirm complete cached content appears before network refresh finishes, additions/deletions/progress changes reconcile after refresh, and an interrupted refresh retains the last complete usable snapshot. For orientation/process recreation, confirm one reader navigator remains active, the exact locator/page is restored, Preview remains isolated, and explicit Close is distinguished from recreation.

### Authentication and account isolation

Verify password login, `/api/v1/auth/me` bootstrap, sign-out/session reset, session-expiry recovery, and pending-destination recovery. For BookOrbit OIDC, verify provider discovery, PKCE/state/nonce validation, callback exchange, session verification, and recovery in the server-hosted WebView. This is not native AppAuth; native redirect validation remains pending.

After logout or account switch, confirm account-owned progress, sessions, annotations, catalogs, detail state, and queued progress are not reused by the next account. Confirm completed downloaded media remains available as device-shared Local books. Repeat with compact/full audiobook playback and a preparing session.

## Verification reporting

Every final verification report should state:

- commands executed and actual results;
- focused/full test counts and lint findings;
- generated APK paths, freshness, size, and SHA-256 when required;
- `git diff --check` and CRLF-aware whitespace results;
- ADB/device and instrumentation status;
- remaining manual validation;
- preserved unrelated worktree changes.
