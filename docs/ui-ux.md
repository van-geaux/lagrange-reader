# UI/UX Workstream

This document contains current interaction contracts, design rules, and unresolved UX decisions. Dated feedback, superseded alternatives, and historical implementation notes are preserved in [`docs/ui-ux-archive.md`](ui-ux-archive.md).

## Current product direction

- Lagrange uses a native Android shell with Home, Libraries, and More as primary destinations.
- Home aggregates server-wide reading shelves; selected-library scope belongs to Libraries.
- The interface uses compact, touch-safe cards and controls with accessibility semantics, large-text support, and clear offline/error states.
- Startup should show usable cached content as soon as possible and avoid blocking/global progress indicators when content is already available.
- Native username/password and the implemented server-hosted sign-in WebView are supported; native AppAuth remains deferred by the upstream redirect contract.

## Current navigation and action contracts

- Book Detail keeps Read/Preview visible and exposes download/update/delete actions according to file state.
- Book Detail shows an `Available file` control when a book has multiple supported files. Tapping it opens a bottom-sheet picker with separated rows; each option shows a format badge, filename, size, and `Primary`/`Alternate` role, while an identical visible option receives a short stable file-ID suffix. The selected EPUB/PDF/comic or audio file supplies the existing Read/Preview/Play and download actions; unknown attachments such as JSON are not selectable media versions. The selector is keyed by file ID, while `Other versions` remains reserved for separate same-series, same-index records.
- Book Detail, Series, Author, and nested genre return state survive saved-instance-state recreation.
- Book Detail Previous/Next series navigation resolves each adjacent series index by preferring the current library and format family, then the same family across libraries in the user-visible library order. If that family is unavailable everywhere, it uses the first library with a target and the fallback families EPUB/KEPUB, PDF, CBZ/CBR/CB7, then audiobook; audiobook extensions such as M4A, M4B, and MP3 are one family.
- Offline Previous/Next must replace the visible Book Detail state using the selected target book/file identity without waiting for an online detail response. Cached series screens combine cached books from all available libraries rather than only the currently selected library.
- `Mark as...` exposes the complete BookOrbit status contract.
- Local books uses the completed-copy state, not an in-progress attempt, to decide whether `Delete local` is available.
- Active and failed download rows are conditional and remain separate from completed local content.
- On the Browse tab, collection actions sit below the library filter/count row. `Download library` appears only when the complete selected-library catalog contains a missing or updateable local file; it opens file-level selection grouped by library/format, then requires two sequential storage/data confirmations. Series detail uses the same eligibility rule and selection flow, with one whole-series confirmation and ascending series-index dispatch. Active series/library transfers show aggregate progress using the Book Detail progress treatment. `Delete local books` appears when the current series/library has local copies and always requires a count-aware confirmation; it removes only device files, not BookOrbit records. Metered bulk downloads show one cellular warning for the whole operation, never one warning per book. Dismissal or cancellation starts nothing; Shelves bulk download remains deferred because the current API/model path does not expose usable Shelf content.
- Pull-to-refresh is user-initiated, non-blocking, duplicate-safe, and independent from background synchronization and destructive download actions.
- The browser Home/Libraries/More navigation bar remains visible on Series detail, and Series detail uses the shared `PullToRefreshLayout` to reload its loader on pull while clearing the refreshing state on success or failure.
- Options exposes an Offline library cache card with multi-library selection, independent global Book details and Cover thumbnails switches, manual download/update, cancellation, progress/results, explicit clearing, and an optional approximately daily unmetered-network refresh. The control states that readable book files are not downloaded; deselecting a library does not silently delete its existing cache.
- Server-missing catalog records remain visible with a yellow `Missing!` overlay; this is distinct from a locally deleted copy.
- About contains one `Check for updates` button. It shows checking and result states; when an update is available, the existing update card is shown again.
- EPUB-only reader font controls include Publisher default, built-in normal/accessibility choices, and one imported custom font slot.
- EPUB reader options open at approximately two-thirds of the available reader surface. The top handle is fixed outside the scrollable settings, exposes a minimum 48 dp touch target, and resizes the session-only sheet within bounded limits. EPUB line spacing is a persisted 1.0×–2.0× live-preview option, and default word spacing is a persisted 0.0–1.0 rem live-preview option; both are applied through Readium’s user CSS preferences.
- Reader tap zones are persisted per library. The default uses equal-width Previous / Menu / Next thirds; Vertical thirds provides full-width top / middle / bottom Previous / Menu / Next regions; Kindle, L-shape, Edge, and Menu-only layouts are also available, with independent None, Horizontal, Vertical, and Both inversion modes. Reading direction changes the horizontal action mapping without changing EPUB typography. Changing the tap-zone layout or inversion from reader options re-shows the tutorial over the reading surface while keeping the options sheet above it.
- Audiobook session history is local and exact-position; server reading history is analytics context and is not used as a seek position.
- A connected split-MP3 audiobook is presented as one continuous audiobook: the compact player's time and slider use the complete duration, chapter and ±10/30-second seeks may cross file boundaries, and playback automatically continues into the next ordered file. Single-file and downloaded/local audio behavior is unchanged.
- EPUB/PDF/comic readers may recreate for orientation, screen-size, and fold changes, but must remain visible by reopening one navigator at the exact saved locator. Configuration recreation must not look like user closure, duplicate the reader, or clear active-reader state; process recovery is validated separately.

## Current visual rules

- Preserve meaningful touch targets and TalkBack semantics even when compacting visual layout.
- Prefer reusable Compose components and theme tokens over screen-local spacing and styling.
- Keep action rows stable at narrow widths; move optional actions into More rather than wrapping required actions.
- Wrap reader-option choice groups onto additional rows when the available width is insufficient; do not require horizontal swiping to discover the complete set of choices.
- Preserve current cover aspect ratios and bottom alignment for portrait and square covers.
- Keep reader controls, tutorial regions, orientation behavior, keep-awake behavior, and Preview isolation consistent across supported formats.
- Treat unavailable media as an explicit state rather than a generic empty or local-download failure.

## Current validation priorities

- Repeated orientation/fold recreation for issue #47 and the download lifecycle are user-confirmed on-device; retain both in the regression matrix.
- Reconcile any additional device checks against [`docs/testing.md`](testing.md) before requesting a new APK.
- Confirm any material new layout, accessibility, or interaction decision with the user before implementation.

## Source of truth

- Current completion and validation: [`CHECKLIST.md`](../CHECKLIST.md)
- Active priorities: [`docs/roadmap.md`](roadmap.md)
- Reusable procedures: [`docs/testing.md`](testing.md)
- Historical design decisions: [`docs/ui-ux-archive.md`](ui-ux-archive.md)
