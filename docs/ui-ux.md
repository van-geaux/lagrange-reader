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
- `Mark as...` exposes the complete BookOrbit status contract.
- Local books uses the completed-copy state, not an in-progress attempt, to decide whether `Delete local` is available.
- Active and failed download rows are conditional and remain separate from completed local content.
- Pull-to-refresh is user-initiated, non-blocking, duplicate-safe, and independent from background synchronization and destructive download actions.
- Server-missing catalog records remain visible with a yellow `Missing!` overlay; this is distinct from a locally deleted copy.
- EPUB-only reader font controls include Publisher default, built-in normal/accessibility choices, and one imported custom font slot.
- EPUB reader options open at approximately two-thirds of the available reader surface. The top handle is fixed outside the scrollable settings, exposes a minimum 48 dp touch target, and resizes the session-only sheet within bounded limits. EPUB line spacing is a persisted 1.0×–2.0× live-preview option and is applied through Readium’s user CSS preferences.
- Audiobook session history is local and exact-position; server reading history is analytics context and is not used as a seek position.

## Current visual rules

- Preserve meaningful touch targets and TalkBack semantics even when compacting visual layout.
- Prefer reusable Compose components and theme tokens over screen-local spacing and styling.
- Keep action rows stable at narrow widths; move optional actions into More rather than wrapping required actions.
- Preserve current cover aspect ratios and bottom alignment for portrait and square covers.
- Keep reader controls, tutorial regions, orientation behavior, keep-awake behavior, and Preview isolation consistent across supported formats.
- Treat unavailable media as an explicit state rather than a generic empty or local-download failure.

## Current validation priorities

- Physical validation of the complete interrupted-download and failed-update lifecycle remains the primary open device-validation item.
- Reconcile any additional device checks against [`docs/testing.md`](testing.md) before requesting a new APK.
- Confirm any material new layout, accessibility, or interaction decision with the user before implementation.

## Source of truth

- Current completion and validation: [`CHECKLIST.md`](../CHECKLIST.md)
- Active priorities: [`docs/roadmap.md`](roadmap.md)
- Reusable procedures: [`docs/testing.md`](testing.md)
- Historical design decisions: [`docs/ui-ux-archive.md`](ui-ux-archive.md)
