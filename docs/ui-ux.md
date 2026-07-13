# UI/UX Workstream

The functional prototype is stable enough for UI/UX work to begin. EPUB is the currently available representative content type; audiobook, PDF, and CBZ-specific validation remains deferred until sample files are available.

## Checkpoints

### Checkpoint 0: Functional baseline - complete

- Core setup, login, browser, download, offline, sync, and reader flows are implemented.
- Manual device testing confirms EPUB download, offline reopen, progress sync, and last-session restore.
- JVM tests pass and the Compose instrumentation target compiles.
- UI/UX discussion can start now without waiting for other media samples.

### Checkpoint 1: Product direction and design system - ready now

- Agree on the visual character, density, and accessibility goals.
- Define color, typography, spacing, shape, elevation, and motion tokens.
- Confirm the information hierarchy and navigation model for setup, login, library browsing, and reading.
- Coding can begin with theme tokens and shared app-shell components once the direction is agreed.

Implementation candidate: an editorial-observatory direction is now coded for review, using navy ink, warm paper surfaces, amber accents, serif display typography, restrained rounded geometry, and shared top-bar/status components. Setup, login, and browser surfaces use the candidate without changing flow behavior. Checkpoint 1 remains open until the direction is reviewed on device.

### Checkpoint 2: Setup, login, and app shell - ready after Checkpoint 1

- Refine server setup guidance, validation feedback, and retry states.
- Improve the login container, server identity, progress feedback, and change-server action.
- Establish consistent top bars, loading states, error surfaces, and offline/session indicators.

### Checkpoint 3: Library browser - ready after shared components exist

- Rework library selection, book-card hierarchy, download state, and primary actions.
- Add intentional empty, loading, offline, and failure presentations.
- Keep actions usable with long titles, large font scales, and narrow phone widths.

The first browser-shell candidate opened on a native Home feed with a hamburger drawer and integrated search. Device review found that interaction too web-like, so it is superseded by an approved Plex-inspired direction: standard Android status-bar space, bottom navigation for primary destinations, a top-level Libraries view with a library-change control, and a dedicated search layer opened from an icon. Home must put Currently reading first, while completed/recently read books remain available separately. Home shelves remain scoped to the selected library page returned by BookOrbit.

The first Home shelf now renders as Currently reading. It recognizes active percentage, page, position, label, or timestamped progress and excludes completed books; Recently read books remains a separate history shelf.

Current refinement: shelf cards are reduced to roughly two-thirds of the first candidate size, search uses BookOrbit's global query endpoint, and covers load through the authenticated API client. Series shelf cards open an ordered series detail list. Book selections open a detail screen with Read/Continue, Download, and local-copy actions instead of launching content immediately. The browser shell now uses Home, Libraries, and More in a bottom bar; More expands to Series, Authors, and Options. The top bar carries the launcher mark, search, and profile actions, and the Libraries flow has a top-level picker plus Change control.

Detail refinement candidate: book details now mirror the reader-relevant content of BookOrbit's main detail page, including identity, synopsis, genres/tags, publication data, identifiers, rating, library, format, and file metadata. Series details load the complete server series, authors, read completion, possible gaps, first-book synopsis, and ordered books. This is ready for UI discussion and device adjustment now; validate hierarchy, density, long metadata, and primary-action placement before marking Checkpoint 3 complete.

### Checkpoint 4: EPUB reader - ready with current sample content

- Refine reading chrome, chapter navigation, theme controls, typography controls, and distraction-free states.
- Preserve the validated resume, local-image, offline, and progress behavior.
- Test changes against the available EPUB files before merging.

Implementation candidate: EPUB follows Komga's paginated interaction pattern. Reading uses transient controls; left/right outer-quarter taps change pages and center taps toggle Back, location, chapters, theme, text size, and Compact/Comfortable/Wide padding controls. Comfortable is the default, and changing padding repaginates the current chapter. Device validation is required for typography, page breaks, images, chapter boundaries, tap-zone comfort, and the default text inset before Checkpoint 4 is complete.

### Device feedback workplan

- [x] Make Currently reading the first Home shelf and retain Recently read books as a distinct history shelf.
- [x] Replace the hamburger/drawer interaction with Plex-inspired bottom navigation and a top library selector/change action.
- [x] Replace Home's large persistent search field with a search icon and dedicated search layer.
- [x] Keep the Android status bar visible and add intentional Home top spacing.
- [x] Add reader padding controls and a larger default padding value.
- [x] Replace the launch spinner with the branded adaptive-icon loading state; physical-device validation remains open.

### Checkpoint 5: Other media readers - deferred

- Adjust audiobook, PDF, and CBZ-specific UX only after representative files are available.
- Shared reader tokens and navigation patterns may be implemented earlier, but format-specific behavior must remain marked unvalidated.

### Checkpoint 6: Polish and release validation - after primary screens

- Run accessibility, large-text, rotation, dark/light theme, and small-screen checks.
- Execute Compose instrumentation tests on a connected emulator or device.
- Re-run debug, unit, instrumentation-compile, lint, and release build checks.

## Guardrails

- UI work must not weaken offline behavior, session recovery, reader resume, or progress sync.
- Prefer reusable Compose components and theme tokens over screen-local styling.
- Keep interactive controls accessible and preserve meaningful semantics in tests.
- Treat unavailable media types as deferred validation, not as completed UX work.
