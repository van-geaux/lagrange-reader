# Roadmap

This roadmap summarizes the next practical engineering sequence for the project.

## Completed foundation

- Android project scaffold exists
- Live BookOrbit login contract validated
- Live library and book payloads validated
- Local build is working on this machine
- Download and sync scaffolding are implemented
- EPUB fallback replaced with a basic real EPUB reader path
- Queue compaction and auth-vs-transient sync failure handling implemented
- Reader/player progress events are throttled before queue persistence
- Reader startup loading, reader error messages, unsupported-format messaging, and PDF cache-open support are implemented
- Server-reported page/time progress is mapped into reader resume state
- Library and book browsing now has loading, empty, error, and refresh UI states
- Active download progress, retry, and cancel controls are implemented
- Download filenames preserve more specific format extensions from BookOrbit metadata
- User-facing error messages are normalized from typed repository/network failures
- Last successful libraries and per-library book lists are cached locally for browser fallback
- Downloaded items can now remove their local copy and persisted download record
- Missing local download files are now pruned from persisted download state and cached browser snapshots
- Zero-byte local download and reader-cache files are now treated as invalid and redownloaded
- Debug builds expose the pending sync queue count in the browser view
- Progress sync worker now applies exponential backoff for transient server failures
- Last-synced progress markers now suppress duplicate or stale queue submissions
- Offline startup and login-refresh paths now fall back to cached browser state when available
- Cached offline browser views now disable live-only actions for books without a local copy

## Next execution order

### 1. UI/UX direction and design system

- Agree on the product's visual character, density, and accessibility goals
- Define Compose theme tokens for color, typography, spacing, shape, elevation, and motion
- Establish reusable app-shell, loading, error, offline, and session-state components
- Use [ui-ux.md](./ui-ux.md) as the checkpoint source of truth

### 2. Primary screen refinement

- Refine server setup and the login WebView container
- Refine library selection, book-card hierarchy, and download/offline actions
- Refine the EPUB reader using the currently available sample content
- Validate the fullscreen paginated EPUB candidate and add exact in-chapter page restore after layout behavior is stable
- Preserve existing session, offline, progress-sync, and reader-resume behavior

### 3. Automated coverage and recovery hardening

- Add integration coverage for login bootstrap and library/book loading
- Add integration coverage around offline queue replay
- Execute Compose instrumentation tests on a connected emulator or device
- Validate server-forced session expiry and return-to-intended-action recovery on a real deployment

### 4. Deferred media-specific validation

- Obtain representative audiobook, PDF, and CBZ files
- Validate offline reopen, restart resume, streaming/range behavior, and progress sync for each format
- Adjust format-specific controls only after their behavior can be exercised on device

### 5. Release readiness

- Complete accessibility, large-text, narrow-screen, rotation, and theme checks
- Run unit, lint, debug, instrumentation-compile, and release build gates
- Confirm no secrets or internal URLs are committed
- Create the first tagged release

## User feedback workplan — 2026-07-12

The latest debug APK passed login, session relaunch, browsing, EPUB reading, download/offline reopen, and progress-sync testing on the target device. The remaining device feedback is ordered as follows:

1. Completed: put currently reading books in the first Home shelf, with completed/recently read titles remaining available as a separate shelf.
2. Completed: replace the hamburger/drawer browser shell with a Plex-inspired native bottom-navigation layout. Libraries open at the top-level library view, with a library-change control at the top of that screen; More expands to Series, Authors, and Options.
3. Completed: add breathing room below the system status bar on Home, replace the persistent large search field with a top search icon, and open search in a dedicated layer. The standard Android status bar remains visible.
4. Completed: add Compact, Comfortable, and Wide reader padding options, using Comfortable as the more generous default and repaginating when the setting changes.
5. Implemented: keep the branded splash state through bootstrap and replace the post-splash spinner with the branded loading mark; validate the result on a physical device.

Each item must preserve session recovery, offline behavior, progress sync, Preview isolation, and reader resume.

## Latest device feedback workplan — 2026-07-13

1. Implemented: restore the exact EPUB page within the current chapter on close/reopen and full app restart, while preserving the existing server progress percentage and chapter-boundary behavior; physical-device validation remains required.
2. Implemented: change the selected-library book browser from list rows to the adaptive poster-card grid used by Series and Authors, preserving metadata, offline-disabled cards, and detail actions; physical-device validation remains required.
3. Implemented: rename the visible app brand to `Lagrange` and add the subtitle `a BookOrbit reader` to the logo/launch presentation. BookOrbit remains the connected server/product name; physical-device branding validation remains required.
4. Implemented: add an About destination after Options in the More menu, using placeholder app/version/server information until the About content is reviewed.

## Latest device feedback workplan — 2026-07-13 (continued)

1. Implemented: show the `a BookOrbit reader` subtitle only on the splash/loading presentation; the opened app top bar and About screen now show the Lagrange name without the subtitle. Physical-device presentation validation remains required.
2. Implemented: add a Libraries content control that collapses books belonging to the same series into one representative series card, with a Show all action to restore the full book grid; physical-device validation remains required.
3. Implemented: add Local books to More immediately before Options, loading valid downloaded titles from local storage and presenting them with the same poster-card grid.

## Latest device feedback workplan — 2026-07-13 (library refinement)

1. Implemented: reduce the current book poster-card minimum width to 75% of its current value, which is half of the original candidate size; physical-device density validation remains required.
2. Implemented: add paginated library loading with automatic near-end page fetching in the Browse tab using the library response total/page metadata.
3. Implemented: show the Lagrange logo only on Home; Library now opens the first/selected library directly, and its library name replaces the logo as a tappable library selector.
4. Implemented: split the selected Library view into Recommended, reusing the Home-style shelves for that library, and Browse, containing the complete paginated book grid.

## Latest device feedback workplan — 2026-07-13 (final browser polish)

1. Implemented: make the Library, Series, and Authors poster-card grids visibly smaller on narrow phones by lowering their adaptive minimum width; physical-device density validation remains required.
2. Implemented: add a downward-triangle affordance beside the selected Library name so its tap-to-select behavior is discoverable.
3. Implemented: remove the Library refresh arrow and add swipe-down-to-refresh to the Library content tabs.
4. Implemented: persist fetched book cover thumbnails locally and read that cache before attempting the server, including for Local books.

## Latest device feedback workplan — 2026-07-13 (follow-up polish)

1. Implemented: rename the collapsed-series action from Show all to Expand series while preserving the full-grid behavior.
2. Implemented: improve cover-thumbnail decoding and retry behavior so slow or failed cover loads do not permanently remain as first-letter placeholders.
3. Implemented: add extra bottom spacing to the More sheet so About remains comfortably above the Android navigation controls.
4. Implemented: show the full library book total in the paginated Browse header, with optional server-provided series totals instead of only the currently loaded page count.
5. Implemented: make the top-left Libraries title tappable while the picker is open so it returns to the selected library page.
6. Implemented: cache full BookDetailInfo metadata for downloaded titles and prefer that cache when opening Local books.

## Latest device feedback workplan — 2026-07-13 (lazy library navigation)

1. Implemented: replace Browse's Load more button with automatic page loading as the user approaches the end of the current grid.
2. Implemented: add a right-side title-initial jump rail for fast movement through loaded library content.

## Latest device feedback workplan â€” 2026-07-13 (reader spacing)

1. Implemented: keep the existing overall Compact/Comfortable/Wide reader padding presets and add independent Top and Bottom controls using the same levels.
2. Implemented: keep Comfortable as the default for all three controls and repaginate the current EPUB chapter whenever any padding value changes.

## Latest device feedback workplan â€” 2026-07-13 (percentage padding and recovery)

1. Implemented: replace named reader padding presets with four independent percentage sliders for Top, Bottom, Left, and Right. Each slider ranges from 0–100%, where 100% maps to one quarter of the relevant viewport dimension; every edge defaults to 15%.
2. Implemented: make edge changes independent and apply the new values when the slider is released, then repaginate the current EPUB chapter.
3. Implemented: keep the jump rail visible for # and every A–Z label even before those titles are loaded; group every non-alphabetic title under # and place it first.
4. Implemented: alphabetize collapsed library representatives by their series name and restore the nearest book/series scroll anchor when switching between collapsed and expanded views.
5. Implemented: route authentication failures from background search, cover, catalog, detail, and paginated library loads through the login recovery screen instead of silently returning empty content. Physical-device expiry testing remains required.

## Source of truth

Detailed checkpoint status is tracked in:

- [../CHECKLIST.md](../CHECKLIST.md)
- [ui-ux.md](./ui-ux.md)
