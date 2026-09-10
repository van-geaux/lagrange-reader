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

### Android 16 KB native packaging

For the PDFium packaging gate, verify the APK is `arm64-v8a`-only and run both checks against the built artifact: `zipalign -P 16 -c` for ZIP alignment and an ELF inspection confirming each packaged native library has `p_align=0x4000`. The current PDFium dependency is JitPack `com.github.marain87:PdfiumAndroid:1.9.8`. These ZIP/ELF checks verify Android 16 KB compatibility; they do not execute or replace connected UI/instrumentation tests.

## Comic page-image gestures (issue #74)

On a connected device or emulator, test local CBZ and extracted CBR files in both paginated and continuous modes. Tap loaded pages and verify the reader's existing pagination, menu, and continuous-scrolling controls remain usable. Long-press a loaded page and verify the shared fullscreen image viewer opens on that exact page. In an EPUB document containing one visible image and no meaningful text or interactive controls, verify pinch zooms from 1× to 4×, one-finger drag pans while zoomed, and pinching back to 1× restores ordinary reader ownership. At 1×, tap and swipe the image page and verify normal Readium pagination/menu behavior continues without opening the viewer. Repeat on a mixed text-and-image EPUB page and verify pinch, taps, text selection, and page navigation remain owned by Readium. Long-press either EPUB image type and verify the viewer opens without also turning the page. In the viewer, verify pinch and double-tap zoom, bounded panning, single taps inside the image do nothing, and a tap outside the visible image closes the viewer without accidental dismissal during a pinch. Long-press inside the viewer and verify `Download page` appears just below the finger; dismissing it must not download, while selecting it saves the image with the book title and one-based page number. Repeat with network disabled after the book or page has been downloaded/cached. Loading, failed, or missing images must not expose a usable download action or crash the reader.

### EPUB Image Library (issue #143)

On a connected device or emulator, open an EPUB Book Detail and verify the EPUB-only `Image Library` action is labeled and absent for non-EPUB files. With a downloaded EPUB, open the gallery and verify images are ordered by first spine/in-document appearance, duplicate references appear once, unreferenced raster entries follow in normalized archive-path order, SVG is excluded, and both width and height must meet the Options minimum-size setting. Change the persisted minimum from its 250 px default in 25 px steps, leave and reopen Options, and verify the selected value is retained.

Open an online EPUB that is not downloaded. Verify the first action shows no download prompt and does not make a network request; choose No and confirm nothing starts, then choose Yes and confirm the selected EPUB begins the normal durable local-book download with its existing progress, cancellation, failure, retry, integrity, and notification behavior. Verify the gallery opens automatically only after the download succeeds and that the selected file appears in Local books. Repeat offline and verify the missing EPUB reports unavailable without a consent prompt. Reopen the resulting Local book with the network disabled and verify the gallery still opens. In the gallery, verify thumbnail selection, physical left/right swipe navigation at 1×, bounded pinch zoom/pan, outside-tap dismissal only when untransformed, and original-image export. Single-tap the image to show and hide the transient overlay top bar; verify its title marquee scrolls, its right-side X dismisses the gallery, image and thumbnail geometry remains stable without recalculation or shifting, and Android Back dismisses the gallery equivalently. Reading-direction and inversion settings must not reverse gallery left/right navigation.

Focused JVM and build verification:

```text
./gradlew --no-daemon --console=plain :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug
```

Compiled Android instrumentation is not executed device validation; report the APK path and keep the device/emulator procedure above pending until run on a connected target.

## Debug APK handoff

When asking the user to perform manual testing:

1. Run `assembleDebug` from the final worktree.
2. Use the generated timestamped artifact:
   `app/build/outputs/apk/debug/Lagrange-debug-yyyymmddhhmm.apk`
3. Report the exact path and filename.
4. Use the standard `app-debug.apk` only as the Gradle source artifact.

## Current validation priorities

### Home and Recommended shelf previews (issue #116)

On Home and Library → Recommended, verify Currently reading, On deck, Want to read, Recently added books, Recently added series, Recently updated series, and Recently read books show no more than eight preview cards. For Home's `Recently added series`, verify the preview and its `See all` destination load complete `/api/v1/series` records for identity/name/cover, order by the earliest local book `addedAtMillis` per series, exclude promotion of an old series when a later volume is added, and retain the eight-card preview cap while `See all` shows the complete result. Verify that request/offline fallback still renders the existing book-derived grouping when the complete Series request is unavailable. Recent book destinations retain their sorted-page/`Load more` behavior and preserve the originating Home or selected-library context when returning with Back.

For any browse surface containing a multi-format book or series, verify the card shows readable available formats beneath the cover in the order `EPUB`, `KEPUB`, `PDF`, comic formats, then `AUDIO`, with duplicates removed and unsupported formats omitted. Verify a short label remains stationary, a long label stays on one line and marquee-scrolls, Reduce motion keeps it stationary, the format text uses the theme primary color, and no format label appears when supported format data is absent. Verify every book-card surface marks completed downloaded formats that are present in the book's available-format list immediately before the matching label with the Material check-circle icon, bold text, and primary color. Specifically test a primary EPUB record with a completed M4B download, which must mark `AUDIO`; also test completed PDF and CBZ downloads, including multiple formats. Verify series-level cards show no downloaded markers, while books inside a series do. Verify accessibility announcements identify downloaded formats. Confirm Book Detail remains unchanged.

### Download lifecycle

On a connected device or emulator, verify from Home, Library, Search, Series, Authors, Genre, and Local books:

- remote idle books show `Download local`;
- active transfers show `Cancel`;
- failed transfers show `Retry` and `Clear`;
- downloaded books show `Delete local`;
- Local books shows active/failed Downloads rows only when needed;
- Local books Downloads can be expanded and collapsed; expanded rows scroll inside a body no taller than half the available screen, long book titles remain one line and marquee, each row also shows its per-file filename/part label, and active-row `Cancel` is beside its progress bar;
- `Clear` and `Clear all` remove failed state without cancelling active transfers;
- force-closing during a download restores a failed row with `Retry` and `Clear`;
- a failed first download stays out of Local books;
- a failed update preserves the previous local copy and exposes `Update local` plus `Delete local`.
- using an account without download permission, attempting a download shows a permission-specific notice, keeps the app logged in on the current screen, and does not offer immediate `Retry` for that item until the state is cleared or refreshed;
- ordinary failed downloads continue to offer `Retry` and `Clear` alongside the permission-denied case above.

The user has confirmed this lifecycle works correctly. Keep the procedure for regression testing on future changes.

### Multipart audiobook download and crash recovery (issue #156)

On a connected device or emulator, use the authenticated `Test` library and open a multipart audiobook. Verify a grouped MP3 selector row shows the group name, `17` physical files, and the aggregate known size for a group of approximately `600 MB`; chapter MP3s remain hidden as child choices. Verify that selecting `Download local` records the complete server-ordered supported-file batch before the first transfer, creates one active transfer row per physical audio file, preserves `files[]`/`sort_order`, excludes `metadata.json` supplements, and runs only one physical transfer at a time while queued siblings remain visible. The active transfer row must say `Downloading`; later durable FIFO entries must say `Waiting`/`Queued`. Kill or force-stop the app during the batch, relaunch it, and verify the persisted queue reconciles and restarts its FIFO head without duplicating WorkManager observers or transfers. Complete, permanently fail, or explicitly cancel the head and verify the next sibling advances; completed files remain available and retry re-enters only the selected file. Verify progress and foreground notifications change only for changed percentages and no more often than every 500 ms, except terminal completion.

After at least one file completes, verify the catalog does not fully reload while another sibling is active; after the logical batch has a completion and no active sibling remains, verify the normal catalog reload occurs. Corrupt the persisted queue JSON before startup and verify the app starts with an empty queue rather than entering a crash loop. Verify `Delete local` retains its ordinary semantics: it removes the selected multipart book's local files without treating delete as queue cancellation, and the explicit delete-versus-cancel product decision remains unimplemented.

After all files finish, enable Airplane Mode and open the audiobook from Local books. Verify every downloaded audio file appears in one ordered Media3 playlist, playback advances across file boundaries, and resuming from a later file preserves that file ID and position. Verify a partial local set opens only the available local files or reports the normal missing-local-content error, without attempting a remote stream. Cancel individual active rows and verify other files and completed files remain. Use `Delete local` and verify all local files for the selected multipart book are removed while the server book remains available for another download.

### Book Detail format and grouped-download presentation (issue #159)

On a connected device or emulator, open Book Detail for a book with multiple supported formats. Verify the selected format name remains visible after download and is followed by a separate state such as `Downloaded`, `Partially downloaded`, or `Not downloaded`; the state must not replace the format name. Verify a single-file format does not show grouped downloaded/total progress. For a grouped format, cancel or fail one physical file and verify the group remains partial, reports downloaded/total files and downloaded/total bytes when sizes are known, and keeps a `Download remaining` action. Activate that action and verify only incomplete physical files are retried or queued; completed files are not duplicated. Finish the group and verify only then that it reports `Downloaded`.

Open the grouped format picker and verify ordinary tap still selects the whole group. Verify the closed summary has no separate `Files` button. Long-press a grouped row or use its eye-shaped `View files` action, and verify a read-only list opens with every physical file in authoritative server order, its filename, size when known, local downloaded/not-downloaded status, and a per-file `Download` button for incomplete files. Tap one file’s button and verify only that physical file is queued/downloaded. While the group has active or queued members, tap the Book Detail X action and verify all active/queued files in that group are canceled and the status returns to the normal prompt to tap X to stop/cancel when a new download begins. Verify inspection does not change group selection. Repeat with EPUB, PDF, M4A, MP3, M4B, and a mixed-format book where available; formats must remain separate selectable groups.

At the current checkpoint, focused issue #156 automated tests and the full JVM suite have passed. The final Gradle/lint/APK gate has passed; physical 199-file device testing remains pending. Android-test compilation or APK assembly does not replace that device validation.

### Local-open fallback

On a connected device or emulator, complete a download, immediately enable Airplane Mode, open Local books, and tap the downloaded book before refreshing or restarting the app. Repeat for EPUB, PDF, CBZ/CBR/CB7, and audiobook files. Each valid completed local copy should open after the failed online/detail attempt. Also verify that an offline book with no local copy retains the normal network error, an invalid local file reports an integrity/preparation error, and a corrupt comic archive reports its extraction error rather than the network failure. Repeat one healthy online open to confirm the normal authoritative path remains preferred.

Automated coverage verifies the retry seam, all four media families, local file identity preservation through detail hydration, authentication handling, no-local error preservation, and corruption/error distinction. The user confirmed the airplane-mode/device behavior works.

### Series and selected-library bulk download

After the selected library has finished loading its complete catalog, verify the Browse tab places `Download library` below the filter/count row and opens file-level selection grouped by library/format. Confirm `Select all`, clear it, select individual files, and verify the summary updates. Confirm the first library warning and verify a second warning appears without starting a transfer; dismiss the second warning and verify no bulk transfer starts. Repeat and confirm both warnings, then verify aggregate progress appears while the frozen eligible files use the existing per-file download state. On a metered connection, verify one cellular-data warning describes the whole selected library and does not repeat per book.

Open a Series detail, choose `Download series`, verify the same selection behavior, and confirm transfers start in ascending series-index order with aggregate progress. When every file is current, verify the corresponding bulk-download button is absent; an available update keeps it visible. In both screens, verify `Delete local books` appears only when local copies exist, is disabled during scoped transfers, requires a count-aware confirmation, removes all scoped device copies, and leaves BookOrbit records intact. After deleting all local series copies, verify `Download series` reappears immediately without leaving the screen. Books without downloadable file IDs are skipped. Shelves bulk download remains deferred.

Automated verification covers candidate filtering, frozen selection, deterministic ordering, eligibility restoration after deletion, aggregate progress, and local-copy deduplication. Physical-device validation of the cellular warning flow remains a manual requirement.

### Library Browse options bar (issue #133)

On a connected device or emulator, open Libraries, select a library, and choose Browse. Verify the `Browse options` bar is collapsed initially: `Filter`, the series collapse/expand control, and library download/delete actions are hidden while the one-line library count remains inside the bar. Tap the bar and verify it expands, the arrow reverses, and those controls appear. Start an eligible bulk library download, collapse the bar, and verify the active aggregate progress remains visible while the controls are hidden. Switch to another library and confirm its default is collapsed; expand it, close/restart the app, return to each library, and verify each library retains its own expanded/collapsed state.

### Smart Scopes under Series (issue #101)

On a connected device or emulator, open the Series menu and choose `Smart scopes`. Verify the screen shows a loading row while `GET /api/v1/smart-scopes` is in flight, lists the returned scope names, reports `Unable to load smart scopes.` on failure, and reports `No smart scopes found.` for an empty response. Select a scope and verify the scoped Series catalog immediately shows its own loading state and then loads from the paginated `GET /api/v1/smart-scopes/:id/books` responses without requiring pull-to-refresh, groups books into Series cards locally, and shows the scope name and series count. Verify its loading, `Unable to load series.`, and `No series found.` states. The selected scope's Series catalog title is informational and does not reopen the picker; use More → `Smart scopes` to switch scopes. Confirm a successful discovery payload and each successful scoped-books page are retained independently for the configured server, Smart Scope ID, and page. Scroll the scoped Series catalog away from its initial cards and back, and verify visible covers remain present without blanking or reloading. Open a scoped book and verify it is not labeled `Local only`, retains its server-backed book/file identity, and opens the linked book correctly. Repeat a previously loaded scope while offline or with the endpoint unavailable and verify the offline catalog and Series detail reconstruct from the cached scoped book pages; cached content is best-effort and refreshes when online. Authentication failures must remain errors. The server's Smart Scope API is book-scoped here: no authoritative Series-aware Smart Scope endpoint was verified.

From that selected Smart Scope state, open More -> `Series` and verify the normal unscoped Series catalog opens with the Smart Scope selection cleared. Use More -> `Smart scopes` when switching scopes.

Focused JVM verification:

```text
./gradlew testDebugUnitTest --tests com.vangeaux.lagrange.SmartScopeTest
```

### More sheet navigation-bar inset regression (issue #115)

On a physical Android device or emulator configured for three-button navigation, open More and verify it opens directly at maximum expanded height, without requiring a swipe on the top area. Verify the entire More bottom sheet, including the `Local books` item at its end, stops above the Android navigation-bar area rather than being obscured or padded twice. Dismiss the sheet and reopen it to confirm the same whole-surface inset and expanded-state behavior remains consistent.

Focused Android compilation, including the instrumentation source, passed. The user confirmed the expanded-state and whole-sheet navigation-bar behavior works on-device; assistant-side connected instrumentation remains unavailable when no ADB device is attached.

### Refresh lifecycle

On Book Detail, Series, Authors, Local books, Statistics, and Achievements:

- explicit pull-to-refresh shows the indicator through completion;
- current content remains usable while loading;
- duplicate gestures do not create duplicate requests;
- failed refreshes preserve the last successful content where applicable;
- Local books refresh does not interrupt active downloads or destructive actions;
- automatic/background synchronization remains silent.
- Series detail pull-to-refresh reloads `seriesDetailLoader` and clears the refreshing indicator on both success and failure; Home, Libraries, and More remain visible on Series detail (issue #62). Automated coverage is complete; physical-device validation of this standalone `main`-based rebuild remains pending.

### Orientation and fold lifecycle

On a connected device or emulator, verify:

- Book Detail, Series, Author, and nested genre restore the correct selection and return destination after rotation;
- two or more consecutive rotations preserve the same restored state;
- EPUB/PDF/comic readers remain visible after Activity recreation, reopen at the exact locator/page, preserve visible controls, and contain one navigator after each transition;
- recreating `MainActivity` underneath an active reader does not launch a duplicate reader or clear persisted active-reader state;
- Back/Close emits one explicit user-close result, while configuration recreation emits no close result; Preview remains isolated;
- one reading session continues across configuration recreation without a rotation-induced pause/end pair;
- an orientation lock set before rotating is respected;
- fold/unfold transitions where hardware permits;
- process/task recreation as a separate scenario from ordinary configuration changes.

Issue #47 instrumentation coverage compiled but did not execute assistant-side because no ADB target was available. The user confirmed the integrated build works on-device through the repeated orientation/fold recreation scenarios above; keep the matrix as regression coverage for future changes.

### Offline library cache

In Options, select multiple libraries and verify Book details and Cover thumbnails can be enabled independently. Start Download/update now and verify progress can be cancelled, a later run resumes idempotently, unavailable covers are reported without discarding successful entries, and downloaded books are unchanged. After a successful run, enable airplane mode and verify selected-library catalogs, book descriptions/detail metadata, and downloaded thumbnails remain usable. Enable automatic refresh and verify WorkManager schedules unique approximately daily work constrained to an unmetered network; metered connectivity must not start that automatic work. Sign out or change server during an update and verify work stops without exposing another server's cache. Clear offline cache and verify detail/cover entries are removed while readable downloaded books remain.

The user confirmed the implemented offline library cache works on-device. Keep the procedure above for regression testing, including network-policy and cancellation edge cases.

### Reader and media regression checks

When the affected scope requires it, verify:

- normal and Preview launch isolation;
- EPUB resume and continuous-mode active-resource seeking;
- EPUB reading-direction changes preserve text alignment, punctuation, and other typography/layout settings while only reversing left/right edge tap navigation; verify both directions in paginated and continuous modes. User-confirmed on the rebuilt debug APK;
- tap-zone layouts and None/Horizontal/Vertical/Both inversion across EPUB, PDF, paginated comics, and continuous comics; the default uses equal-width Previous / Menu / Next thirds, Vertical thirds uses equal-height top / middle / bottom Previous / Menu / Next regions, changing layout or inversion from reader options re-shows the tutorial behind the options sheet, and continuous vertical swipes still scroll rather than firing a tap action;
- tutorial geometry and labels match the transformed runtime tap regions for LTR, RTL, and selected inversion;
- PDF/comic page navigation and progress;
- audiobook compact-player restoration, seeking, chapters, and speed; both the compact and full players expose the shared options `0.75x`, `1x`, `1.05x`, `1.10x`, `1.15x`, `1.20x`, `1.25x`, `1.5x`, and `2x`, with stable decimal labels;
- audiobook full-player overlay above the retained browser route: minimize restores the exact browser location, Close removes the player without implicit navigation, and metadata navigation is explicit;
- audiobook full-player Chapter/Speed/Sleep visibility and grouped responsive sizing in both portrait and landscape, including readable landscape touch targets;
- audiobook full-player theme adherence in both portrait and landscape: validate Light, Follow system/light, and at least one dark theme while the player is preparing and open, confirming the player surfaces, chapter sheet, session-history dialog, seek labels, and inactive tracks follow the selected app theme;
- the shared compact/full chapter `ModalBottomSheet`, active-chapter highlighting, seek-on-selection, Android navigation-bar containment, centered `Book progress`/`Chapter progress` labels, and overflowing chapter-title marquee scrolling;
- audiobook session history in both Book Detail and the full player: the `Session history` header, `Clear`, and `Close` remain fixed while the body scrolls; the `Local listening history` and `Server reading history` sections are independently collapsible and start expanded; local rows still seek to their exact positions and server rows remain read-only; collapsed sections retain their headers; and the server loading/error/unsupported/empty states remain non-blocking;
- cover viewer from Book Detail, the audiobook compact-player cover, and the audiobook full-player cover in portrait and landscape; rotate while open and verify the cover remains centered relative to the whole screen, preserves aspect ratio, and fits fully inside the landscape viewport;
- cover viewer long-press inside the visible cover opens a context menu without starting a download; verify long-press outside the cover does not open the menu or download; verify tapping the menu's Download action saves a valid image under `Downloads/Lagrange Reader` with a readable collision-safe title filename and a success Toast;
- cover viewer pinch zoom and pan: verify pinch zoom is bounded between 1x and 4x and pan stays bounded at zoomed scales, applying immediately; verify tapping inside the visible cover resets zoom and pan back to 1x; verify double-tap inside the cover toggles between 1x and the 2.5x preset zoom;
- cover viewer dismissal: verify tapping outside the visible cover dismisses the viewer, verify double-tapping outside the cover also dismisses it, and verify Back closes the viewer;
- cover viewer error paths: open the menu and tap Download before image data is available or while offline with an uncached cover and verify a clear failure Toast without a file; on API 26–28 verify the storage-permission request and denial message; simulate or observe a failed write and verify no partial output remains;
- server session registration for EPUB, PDF, CBZ/CBR/CB7, and audiobooks;
- explicit audiobook pause finalizes and uploads the active listening interval; resumed playback starts a new interval;
- no session POST for Preview, paused/background time, or audiobook screen-open without playback;
- offline session queue replay after connectivity returns, including stable session IDs and no duplicate delivery;
- five-minute idle rollover and final-session delivery on reader/player close;
- reader settings persistence across each lifecycle boundary: set non-default global options and distinct reader settings for at least two libraries, then verify the correct values after leaving and reopening Options, returning to `MainActivity` after changing settings in an EPUB/PDF/comic reader, a cold launch after ordinary process termination, and a launch after force-stopping the app. Confirm the first Options frame after each restart is already hydrated rather than briefly showing defaults, and confirm returning from a reader refreshes the app theme and the active library's settings without restarting. Issue #19 current-package APK-upgrade persistence was user-confirmed working on the supplied debug build; issue #142 main and Android-test Kotlin compilation passed, its instrumentation regression was not executed because no ADB target was available, and the reporter confirmed the global, per-library, reader-return, extended-background, force-stop/reopen, and multiple-library checks on the prerelease hotfix APK;
- EPUB reader options open at approximately two-thirds of the reader surface, the top handle exposes a minimum 48 dp touch target, contracts the sheet down to a handle-only strip without dismissing it, and expands it again without losing the live reader preview; the EPUB line-spacing and 0.0–1.0 rem word-spacing sliders update the preview and persist after reopening the options;
- reader-option choice groups wrap onto additional rows on narrow screens, including reading direction, typography, tap-zone layout/inversion, and format layout choices; verify that the complete set is usable without horizontal swiping;
- changing line spacing or the options-sheet height preserves the other reader settings, including theme, font, direction, margins, and layout;
- Book Detail Previous/Next navigation from second and third libraries, including current-library/current-format-family preference, same-family fallback by library order, fallback format-family selection, audiobook extensions such as M4A, M4B, and MP3, duplicate indexes, and unavailable boundaries. User-confirmed on the rebuilt debug APK.
- Book Detail available-file selection for a mixed EPUB/M4B/JSON response: EPUB is the default, M4B uses its own file ID for Play/Preview/download and remains selected after detail hydration, and JSON is not offered as a reader/media option.
- Book Detail selection when multiple EPUBs are present: tap the `Available file` control and verify the bottom-sheet rows are visually separated, filenames, sizes, and `Primary`/`Alternate` labels distinguish normal duplicates, identical visible metadata receives a short file-ID suffix, and each action still uses the selected full file ID.
- Issue #159 grouping: in one BookOrbit grouping path, verify MP3, M4A, M4B, FLAC, and Opus options are separate selectable audio groups; verify EPUB/PDF units use their normalized filename stems, while comics and unknown files remain physical-file scoped. Verify mixed EPUB/PDF/comic/audio formats never share one selectable group. Verify same-format files with identical server metadata remain one group, documenting the API's remaining stable-discriminator limitation. The current full JVM suite is the verified automated gate for this contract; lint, build, and device validation are separate and are not claimed here.
- Book Detail available-file summary renders above Synopsis, manually verified in the layout preview/emulator (not on a physical device) for: a single file, where the summary shows no chevron and is not clickable; and multiple files, where the summary shows a chevron, is clickable, and opens the bottom sheet hosted outside the lazy Book Detail content. Verify a long selected filename remains on one line and automatically scrolls in the closed summary while the size/role/offline metadata remains stationary. In the open sheet, verify unselected filenames stay on one line with ellipsis while the currently selected filename wraps to enough lines to show the complete name without ellipsis, including on narrow portrait and landscape layouts. Verify the file-type badge and metadata render correctly. Selecting a file must update the underlying summary and move the selected checkmark without closing or replacing the sheet; verify the user can inspect or change the selection again, then dismiss the sheet explicitly with Back, an outside tap, or a downward swipe.
- same-application-ID APK upgrade preserves EPUB theme/font/size/margins and the applicable PDF/comic direction, layout, and page-gap settings for more than one library;
- the reader preference store accepts legacy flat profiles and preserves valid profiles when another stored profile is malformed; migration from the retired `com.bookorbit.android` package is intentionally out of scope;
- server-missing versus locally deleted state;
- large text, accessibility, orientation, offline, and narrow-width behavior.

Focused JVM verification for the full-player theme regression:

```text
./gradlew testDebugUnitTest --tests com.vangeaux.lagrange.AudiobookFullPlayerThemeTest
```

The user has confirmed the validated reader, media, navigation, and server-session behavior works correctly. Keep these checks as the regression matrix for future changes.

### Audiobook app lifecycle

On a connected Android device or emulator with audiobook playback active, exercise these as separate scenarios:

- **Ordinary backgrounding and screen lock:** switch to another app, then turn the screen off and on. Playback remains owned by the foreground service and continues; returning to Lagrange must reconnect to the existing playback state.
- **Android app-task removal:** remove Lagrange from the recent-apps/task list. The playback service runs its cleanup path: playback stops, the foreground playback notification is removed, and the service stops. Do not treat this as ordinary backgrounding or screen lock.
- **Notification dismissal:** dismiss the audiobook playback notification as its own scenario, without removing the app task. Record the resulting playback/service and notification behavior separately; notification dismissal is not a substitute for the task-removal check.
- **Force-stop:** use Android Settings to force-stop Lagrange, then relaunch it. Treat force-stop as a separate termination path and verify playback and notification state after relaunch; do not report it as evidence for the task-removal contract.

This procedure documents the lifecycle contract; no automated or physical validation result is asserted here.

### Audiobook audio interruptions (issue #153)

On a connected Android device or emulator, open Options and verify `Pause audiobook for audio interruptions` is enabled by default. Start an audiobook, then trigger a competing app or system sound that requests Android audio focus; verify playback pauses and resumes after the competing focus ends. Disable the option, repeat the audio-focus interruption, and verify playback does not pause. Open and close the notification shade without triggering a competing audio-focus request and verify playback continues; Android does not guarantee that every notification sound requests audio focus, so use an audio-focus request for the interruption case. The user revalidated this requested interruption matrix for both online and downloaded audiobooks on the current-`main` integrated build after issue #152 integration. This was user-confirmed device validation; no assistant-side instrumentation execution is claimed.

### Audiobook seek confirmation (issue #129)

On a connected Android device or emulator with a split-file audiobook available, start playback and exercise each seek bar in compact player, full-player portrait, and full-player landscape states. In each state, test both the overall book bar and the chapter bar: drag/tap to a new position and verify the seek applies immediately, playback continues, and the in-place confirmation offers `Yes, keep position` and `No, go back`. Accept with `Yes, keep position` and verify the new position remains. Repeat a seek, choose `No, go back`, and verify the prior absolute position is restored, including when the requested position crosses a split-file boundary.

While the confirmation is open, tap outside the card and press Android Back; verify neither dismisses the modal or changes the seek, and verify an underlying player control cannot be activated. Verify the scrim/filter leaves the player visible but clearly blocks interaction. Verify the separate `Don’t show confirmations again` checkbox does not close the current modal, disables future confirmations, and points to Options. Open Options, confirm the toggle persists after leaving/reopening the screen and after an app restart/update, then re-enable it for the remaining checks. Verify playback remains continuous, the compact card is above the compact player, and the full-player card is over the cover in both portrait and landscape without covering seek or transport controls. For a split-file audiobook, verify rollback restores the prior absolute book position/file when `No, go back` is selected; single-file playback should retain its existing behavior.

Compiled Android instrumentation is not executed device validation: report Android-test compilation separately, and do not claim this procedure was device-validated unless it was actually run on a connected target.

### EPUB links and text selection

The user confirmed EPUB links work in the existing Readium HTML navigator. Verify an EPUB with an internal chapter or footnote link and an external link, then confirm the destination opens correctly and ordinary EPUB text remains selectable through the existing selection actions.

### PDF hyperlinks (issue #118)

On a connected device or emulator, open a PDF containing internal, HTTP/HTTPS, and `mailto:` links. Tap a link target and verify PDFium hit-testing takes precedence over the directional tap zones: internal destination links navigate to the linked page within the same PDF, and valid external links open through Android `ACTION_VIEW` using the installed system handler. Verify unsupported or malformed schemes are ignored and the existing directional tap handling remains available.

Focused and full automated verification passed: 599 JVM tests with 0 failures, errors, or skips; main and Android-test Kotlin compilation; lint; and debug and Android-test APK assembly. No connected device was available, so the interaction checks above remain pending device/emulator validation; compiled Android-test sources do not constitute executed instrumentation.

### Reader volume-button navigation (issue #95)

On a connected device or emulator, verify the per-library `Turn pages with volume buttons` setting is off by default and remains independently persisted per library. Enable it, then test EPUB, PDF, paginated comics, and continuous comics: by default Volume Down performs logical Next and Volume Up performs logical Previous, including when the reader's reading direction or navigation mode changes. Enable `Swap volume button actions` and verify the two mappings reverse. While the reader is active, unobstructed, and focused, confirm the system volume overlay does not appear.

Disable the setting and verify the keys adjust system volume normally. With an audiobook session active, including paused playback and while the ebook reader remains open, verify volume keys never turn ebook pages and continue to adjust system volume. Repeat while the reader is loading, showing an error, empty, unfocused, or covered by a reader/options/other overlay; each state must delegate normal volume behavior. Verify audiobook playback/player controls and external media controls retain their existing behavior.

Automated verification for issue #95 passed 580 JVM tests with 0 failures, errors, or skips, plus main/unit/Android-test compilation, lint, debug assembly, and Android-test assembly. No connected device was available, so the checks above remain pending physical-device validation.

### Multi-file streamed MP3 audiobook playback (issue #36)

The implementation and live-device validation are complete. The user confirmed that a BookOrbit audiobook composed of ordered MP3 files plays as one continuous streamed audiobook. Multi-file streamed MP3 support is included in the 1.5.0 release scope.

For regression testing, verify chapter selection and automatic advancement across at least two file boundaries; compact-player elapsed/remaining time and slider position across the complete audiobook; ±10/30-second seeks around a boundary; and online relaunch/resume in a later file using the active file ID and in-file position. Recheck a single-file M4B and downloaded/local audio. Multi-file offline download/storage remains out of scope.

### Authentication and OIDC

Verify password login, `/api/v1/auth/me` bootstrap, sign-out/session reset, session-expiry recovery, and pending-destination recovery. The interim server-hosted sign-in WebView is distinct from native AppAuth. Native AppAuth requires deployed BookOrbit mobile-redirect support and separate provider/device validation.

For logout and account-switch regression, start audiobook playback, verify that explicit logout immediately removes the compact/full player and foreground playback notification before the Login screen is usable, then sign in as a different account on the same server. Confirm the previous account's queued progress, reading-session events, annotations, cached catalog/detail state, and exact reader positions are not replayed or displayed for the new account. Confirm completed downloaded media remains available and appears in Local books for the new account, while its progress/status reflects only the new account's server state. Verify that same-user background playback still survives ordinary backgrounding or screen lock. Task removal is covered separately below. Repeat from compact playback, full-player playback, paused playback, and while the player is preparing. Automated coverage verifies coordinator teardown and durable progress-store cleanup; physical player, notification, and two-account validation require a connected device or emulator.

### Foreground WorkManager book downloads (issue #77)

The user confirmed that downloads continue after the app moves to the background and that active download rows retain the original initiating book title rather than a numeric ID. The detailed scenario list below remains useful regression and manual coverage; any edge scenarios not yet validated on a physical device must be distinguished from the user-confirmed behavior. On a connected device or emulator, verify:

- starting a large download shows the dataSync download notification, continues after switching apps or turning off the screen, and completes with the expected notification/state;
- rotating or recreating the hosting activity during an active download does not restart or duplicate the transfer, and progress keeps projecting correctly from WorkManager state;
- reopening the app after process death reattaches to an active download and shows correct in-progress or completed state;
- after switching apps, turning off the screen, activity recreation, and process recreation, the active row retains the original initiating title rather than showing a numeric book or file ID;
- losing connectivity mid-transfer surfaces a retryable failure and retry resumes the transfer;
- cancelling an active download stops the transfer and clears its WorkManager work;
- a failed update preserves the previous completed local copy rather than discarding it;
- starting multiple file downloads concurrently keys and tracks each by server URL plus file ID without cross-file interference;
- concurrent attempt persistence from separate `DownloadStore` instances retains every active download record;
- cellular policy ALWAYS, NEVER, and ASK_FOR_CONFIRMATION are each respected, including that the worker rechecks policy/network before proceeding and does not start without the required grant;
- an authentication-expiry outcome during a download routes back through foreground login recovery and resumes via `PostLoginDestination.DownloadBook`.

For issue #103 notification regression, with one or more active book downloads, open the system notification shade and verify each active notification is grouped under the BookOrbit downloads group, keeps the initiating book title, shows determinate progress when available (otherwise indeterminate), and offers a per-book `Cancel` action. Start multiple downloads and verify a summary notification reports the active count; tap an individual notification or the summary and verify the app launches. Cancel one item and verify only its existing unique WorkManager download is cancelled. Complete, fail, or cancel the remaining work and verify terminal cleanup removes the per-download and summary notifications. Deny `POST_NOTIFICATIONS` and repeat a download: notification UI may be absent, but the download must continue and its final local state must still reconcile. Repeat the background/screen-off portion of issue #77 to confirm the notification changes do not regress WorkManager execution after the app is backgrounded or the screen turns off.

The notification procedure is manual device/emulator coverage; the automated focused and full Gradle verification passes, but physical-device notification validation remains pending.

Byte-range resume is out of scope; a connectivity loss or process death mid-transfer is expected to restart the file rather than resume partway through.

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
