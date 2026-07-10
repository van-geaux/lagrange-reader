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

Implementation candidate: the post-login screen now opens on a native Home feed with a hamburger drawer and integrated search. The drawer contains Home, Libraries, library children, and sign-in/log-out. Home supports Keep Reading, On Deck, Recently Added Books, Recently Added Series, Recently Updated Series, and Recently Read Books when the loaded BookOrbit payload contains the required progress, series, and timestamp metadata. Home shelves are scoped to the selected library page returned by BookOrbit.

Current refinement: shelf cards are reduced to roughly two-thirds of the first candidate size, search now uses BookOrbit's global query endpoint, and covers load through the authenticated API client. Series shelf cards open an ordered series detail list. Book selections open a detail screen with Read/Continue, Download, and local-copy actions instead of launching content immediately. The Android status bar is hidden with transient swipe reveal for an immersive window.

### Checkpoint 4: EPUB reader - ready with current sample content

- Refine reading chrome, chapter navigation, theme controls, typography controls, and distraction-free states.
- Preserve the validated resume, local-image, offline, and progress behavior.
- Test changes against the available EPUB files before merging.

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
