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

On a connected device, switch between configured BookOrbit, Komga, and Kavita servers without restarting the app. Verify each provider rebinds its supported modules, audiobook playback opens only where supported, and unsupported providers show an explicit unavailable state instead of stale callbacks or empty success. Repeat after sign-out/server change and after process recreation.

Use [`docs/provider-checklists/komga.md`](provider-checklists/komga.md) and [`docs/provider-checklists/kavita.md`](provider-checklists/kavita.md) for provider-specific catalog, format, progress, download, and offline-reader validation. Use [`docs/provider-checklists/module-function-matrix.md`](provider-checklists/module-function-matrix.md) to confirm provider-exclusive actions remain unavailable when the selected provider lacks the capability.

### Settings and reader options

Verify Settings contains Appearance, General, Library, Downloads & Offline, and Network & Sync without duplicate About or Account & Server entries. Change a library reader preference and confirm it remains scoped to that library after reopening.

Open reader options for EPUB, PDF, and comics. EPUB exposes typography, margins, and layout controls; PDF exposes PDF typography, layout, `Remove gap between pages`, and color-inversion controls; comics omit the Typography section and expose only CBR/CBZ layout and page-gap controls. Confirm changes apply live and persist after closing/reopening the reader. For paginated PDFs, confirm the gap option defaults off, is enabled only for paginated layout, and changes only the native PDFium page spacing. Confirm Readium/PDFium retains ownership of page rendering, zoom, swipe gestures, page snapping, animation, and landscape behavior. Confirm no custom facing-page pairing or custom swipe-axis behavior appears. Confirm continuous PDFs retain their page-gap control and native vertical layout. For a PDF with a white page, enable `Invert PDF colors` and verify every page pixel inverts uniformly, including text-only pages and pages containing images, while reader controls and overlays remain readable; disable it and verify the original colors return. The feature intentionally does not distinguish text from images, because content-aware inference would require a separate out-of-scope page-analysis method.

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
- after a completed Kavita download, refresh while online and confirm the book remains downloaded; disable network access and open it from Home, Browse, and Local books using the retained local copy.
- for Kavita, mark one book read and then unread; confirm only that book changes, its progress resets, and the action succeeds without the series-scope error.
- for Kavita, open a previously visited Book Detail with network access disabled; confirm the cached metadata shell opens, the selected file identity remains correct, and a completed local copy remains available.
- for Kavita, switch between two profiles that use the same server or server origin; confirm the active profile's JWT/cookie is the only session used, and that restoring one profile cannot authenticate requests as the other.
- for Kavita background cache warming, verify each continuation advances one catalog page and does not restart a complete-library scan; confirm a server/profile change stops the old worker without writing into the new profile's cache.

For book thumbnails on Home, Library, Search, Series, Authors, Genre, and Local books, verify that an untouched unread book shows a small yellow folded-corner marker at the top-right of its cover. Books with any reading progress, books marked read, and missing-server books must not show the marker. Confirm the marker does not change thumbnail dimensions, obscure the missing-book overlay, or become a separate click target, and that accessibility semantics identify the book as unread.

Download EPUB, PDF, CBZ, CBR, and CB7 samples. Reopen valid local files with the network disabled. Connected CBR/CB7 should use server page extraction; downloaded CBR/CB7 should use the client-side RAR4/RAR5/7z extractor. Unsupported ebook formats must show the explicit unsupported-format state. Confirm no partial file is promoted after cancellation or failure.

### Reader and media regression checks

When the affected scope requires it, verify normal versus Preview launch isolation, exact normal resume, Preview starting without persisted progress, progress on close, offline local fallback, orientation lock, keep-awake, themes, accessibility, and large-text behavior.

- For EPUB, verify chapter/page navigation, continuous active-resource seeking, text size, margins, reading direction, custom/accessibility fonts, image handling, and optional read-along narration. When Android TTS is available, long-press a word and a sentence, choose `Listen from here`, and confirm playback starts at the selected word/sentence rather than the chapter beginning; on EPUB 3 media-overlay books, confirm `Play narration` remains a separate action. Open TTS settings during playback, confirm the Voice selector lists only voices compatible with the EPUB language, selecting a voice changes subsequent speech, the choice persists after reopening the reader, and `Default Android voice` clears the override. Force a selected voice failure and confirm playback stops, the failed override is cleared, the player returns with `Default Android voice` selected and paused, the reading locator is preserved, and no automatic speech starts until Play is pressed. Confirm network-dependent voices are identified and the existing missing-voice-data action opens Android voice installation. Verify read-along speed changes apply immediately through the five adjustment controls and that `Apply` is disabled. Verify TTS speed and pitch apply immediately, custom punctuation pauses are hidden by default and appear only after enabling the switch, and punctuation/lock-screen changes require `Apply`. Verify `Prefer server progress when reopening` is off by default, persists per library, keeps the exact local locator when off, and when enabled follows clearly newer server progress without moving backward or treating this device's own progress write as another-device progress. Repeat once in Preview and confirm the listen actions are unavailable. For PDF and comics, verify page navigation, progress, links where applicable, layout modes, page gaps, and reader chrome. For audiobook playback, verify compact/full players, chapters, seeking, speed, sleep timer, session history, notification controls, backgrounding, screen lock, audio focus, and process/task recreation.

### Foldable EPUB spreads

Automated coverage: `ReaderSpreadPolicyTest`, `EpubSpreadPreferencesTest` (JVM); `EpubSpreadWebViewInstrumentedTest`, `EpubSpreadActivityInstrumentedTest` (connected). Spread assertions need a large horizontal window, such as the Android Studio Pixel Fold AVD unfolded (2208×1840, 420 dpi). On phone-sized devices they are skipped through a JUnit assumption. Run connected tests on a non-English host with `JAVA_TOOL_OPTIONS="-Duser.language=en -Duser.country=US"`: several existing parser tests assert `.`-formatted percentages.

On an unfolded foldable or a large landscape tablet:

- In landscape, paginated EPUB shows two columns. A page turn advances both columns.
- Rotate to portrait, fold to the cover screen, open split-screen, switch to continuous layout, or turn off `Two-page spread when unfolded`: each shows one column.
- Margins: each page has the same margin on its outer and inner side, and the gap between the pages is twice that. Raising or lowering the left/right margin widens or narrows all four page edges at once, without reopening the book. Uneven left/right settings use their mean. At the largest settings each page still keeps half its width for text.
- A fixed-layout EPUB shows two facing pages, and the left/right margin setting still applies around the spread.
- Line spacing, word spacing, font size and font family change both pages identically and reflow text across both columns.
- Confirm the following still work: resume position after fold/unfold or rotation, TTS sentence highlighting and auto page-turn across columns, highlights spanning the column boundary, and tap zones.
- Comics and PDF stay single-page.

### OLED EPUB theme

Automated coverage: `EpubOledThemeTest` (JVM); `EpubOledWebViewInstrumentedTest` (connected).

- Choosing `OLED black` shows pure white text and links on pure black.
- The following are all black, with white primary content and hairline edges: the status-bar strip, reader chrome, options sheet, chapter list, TTS/read-along panels, and system dialogs.
- Text selection is white with black text, and illustrations keep their original colours.
- Switching into or out of OLED reopens the reader at the same position.
- PDF and comics are unaffected.

### EPUB text alignment and hyphenation

Automated coverage: `EpubTypesettingPreferencesTest` (JVM); `EpubTypesettingActivityInstrumentedTest`, `ReadiumEpubOpenInstrumentedTest`, `EpubReaderOptionsOverlayInstrumentedTest` (connected).

- Left with Auto hyphenation (the default) is ragged and unhyphenated, as before.
- Justified hyphenates long words and gives flush lines.
- Hyphenation Off removes hyphens; Left with On hyphenates ragged text.
- Changes apply without reopening the book, from the reader options and from the library reader settings.
- A book whose metadata declares no language is hyphenated in the device language.
- Book language (in-book options → This book): Auto shows the source (book or device). Choosing another language switches read-aloud voices immediately, even while playing, and changes hyphenation; it is remembered for that book on that server profile only: the same book on another server or account keeps its own choice. Auto restores the book's language. Automated: `EpubBookLanguageInstrumentedTest` (voice list per language with the system speech engine, page language, persistence), `EpubBookLanguageTest` (JVM).

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

For cached catalogs, confirm complete cached content appears before network refresh finishes, additions/deletions/progress changes reconcile after refresh, and an interrupted refresh retains the last complete usable snapshot. For Kavita cache restart coverage, while online open Home, enter Browse and Series, open several books, force-close the app, disable network access, and reopen it. Confirm the cached Home shelves, visited book cards, and visited series/catalog pages remain available, while uncached pages use an explicit unavailable or incomplete state rather than an empty successful result. Repeat after switching profiles or servers and confirm their snapshots do not mix. For orientation/process recreation, confirm one reader navigator remains active, the exact locator/page is restored, Preview remains isolated, and explicit Close is distinguished from recreation.

### Authentication and account isolation

Verify password login, `/api/v1/auth/me` bootstrap where applicable, sign-out/session reset, session-expiry recovery, and pending-destination recovery. For BookOrbit and Kavita OIDC, verify provider discovery, callback/state protections, callback exchange, session verification, and recovery in the server-hosted WebView. This is not native AppAuth; native redirect validation remains pending.

With profiles for the supported providers configured, sign in to each provider and switch between BookOrbit, Komga, and Kavita in both directions. Confirm each switch opens the saved provider without showing login when its saved session is still valid, including after waiting at least five minutes. Confirm an actually expired or rejected session still requires login, and that the target provider's libraries and selected library are not replaced by the previous provider's state. Issue #268 was user-confirmed for the BookOrbit/Komga path on Samsung SM-S931B / Android 16 over ADB with `Lagrange-debug-202610092219.apk`; any Kavita device validation must be recorded separately.

After logout or account switch, confirm account-owned progress, sessions, annotations, catalogs, detail state, and queued progress are not reused by the next account. Confirm completed downloaded media remains available to its owning profile's Local books. Repeat with compact/full audiobook playback and a preparing session.

For two named profiles using the same provider and normalized server URL, confirm saving the second profile does not replace the first. Switch between the profiles and verify each account's credentials/session, browser state, downloads, and Local books remain isolated. Confirm a local book downloaded under one profile is absent from the other profile's Local books until it is downloaded there independently.

## Verification reporting

Every final verification report should state:

- commands executed and actual results;
- focused/full test counts and lint findings;
- generated APK paths, freshness, size, and SHA-256 when required;
- `git diff --check` and CRLF-aware whitespace results;
- ADB/device and instrumentation status;
- remaining manual validation;
- preserved unrelated worktree changes.
