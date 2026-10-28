# Reader appearance: backgrounds and text colors

Status: implemented for EPUB readers; targeting `main` after the 2.1.0 / localization work.

## Overview

Before this change, the EPUB reader had one preset row: a single `theme` controlled both the reader
chrome and the page background, and there was no custom background or text color. This change keeps
that preset row for the chrome and adds an independent reading-background axis with optional custom
colors.

Reader appearance is therefore split into two independent axes:

1. **Reader options** (`theme`) — the preset used by the reader chrome: top controls, option
   sheets, dialogs, and progress controls.
2. **Reading background** (`backgroundTheme` + optional custom RGB) — the page surface used by
   Readium, the window, the system bars, and the progress footer.

Text color is separate from both. It can follow the active reading background (default) or use a
retained custom RGB value.

All values are stored in the existing versioned per-library reader profile
(`LibraryReaderPreferences`); no new preference store or server API is involved.

## Data model

`LibraryReaderPreferences` adds/changes these fields. Only `theme` exists in released profiles; the
other fields and the custom-color UI are new in this change:

| Field | Meaning | Stored key | Default |
| --- | --- | --- | --- |
| `theme` | Reader chrome/options preset | `theme` | `Sepia` |
| `backgroundTheme` | Reading-surface preset | `backgroundTheme` | `Sepia` |
| `customBackgroundColor` | Last custom page RGB, retained even when inactive | `customBackgroundColor` (6-digit hex) | `null` |
| `useCustomBackground` | Whether the custom page color is active | `useCustomBackground` | `false` |
| `customTextColor` | Last custom text RGB, retained even when inactive | `customTextColor` (6-digit hex) | `null` |
| `useCustomTextColor` | Whether the custom text color is active | `useCustomTextColor` | `false` |

### Migration

Profiles are still read through `libraryReaderPreferencesFromStorage` and remain tolerant of older
JSON (storage version 3):

- released profiles only contain `theme` and the previously existing keys; missing
  `backgroundTheme` falls back to `theme`, preserving the old unified appearance;
- missing custom colors/flags → preset background + default text color;
- if a custom RGB is present without its activation flag, the parser treats it as active for
  tolerant handling of partially written/intermediate data. Released profiles never contain these
  keys, so normal upgrades start with both custom modes inactive.


`normalized()` strips alpha to opaque RGB and deactivates a custom mode when its retained color is
missing. Switching to a preset only changes the active flag; it never deletes the retained RGB,
so selecting custom again restores the previous value.

## Resolution rules

### Background

```text
effectiveBackground =
    useCustomBackground && customBackgroundColor != null
        ? customBackgroundColor
        : backgroundTheme.backgroundColor
```

Reader chrome still uses `theme`; changing it does not change the page.

### Text color

```text
if useCustomTextColor && customTextColor != null:
    customTextColor
else:
    defaultTextColor
```

`defaultTextColor`:

| Active background | Default text color |
| --- | --- |
| Preset (`backgroundTheme`) | `backgroundTheme.foregroundCss` (the preset's own readable foreground) |
| Custom RGB | Device system theme: dark → white, light → black, unknown/unavailable → white |

The system-mode fallback is deliberate: a custom background may be any luminance, so following the
device light/dark setting is the user-visible rule; a custom text color remains available for exact
control.

### Readium mapping

`readiumPreferences(...)` receives the resolved colors and maps the effective background to a
Readium theme:

- custom background → `Light`/`Dark` derived from its luminance (link/selection colors);
- preset background → its corresponding `Light`/`Sepia`/`Dark` theme.

It passes explicit `backgroundColor` and `textColor`, so the page does not depend on the chrome
theme. `publisherStyles = false` remains required for the user text color to apply.

## Rendering surfaces

On the reading surface:

- `rootView`, `readerViewport`, and `readerContainer` use the effective reading background;
- `EpubReaderProgressFooter` uses `backgroundTheme` (or the active custom color) and its readable
  foreground;
- `configureSystemBars()` uses the effective reading background for:
  - `window.statusBarColor`
  - `window.navigationBarColor`
  - `window.setBackgroundDrawable(...)` (Android 15+ draws the status-bar strip from the window
    background);
  - status/navigation icon appearance based on background luminance.

On Android 10+ the window disables the system contrast scrim
(`isStatusBarContrastEnforced = false`, `isNavigationBarContrastEnforced = false`) so an extra
system layer does not re-color the custom surface. The old Android 15 status-bar strip and the
footer/navigation strips are therefore covered by the same effective background.

Reader chrome (top bar, option sheets, dialogs) intentionally remains on `theme`.

## Interaction

### Reader options

**Menu & options** (`theme`)

- A preset chip row; selecting a preset only changes reader chrome/options.
- No custom RGB for chrome in this change.

**Reading background** (`backgroundTheme`, custom)

- A preset chip row plus a **Custom** chip.
- First **Custom** selection with no retained color: opens the RGB dialog immediately.
- Applying the dialog activates the custom background and an inline “custom color” row appears
  below the chips.
- The inline row reopens the RGB dialog for later edits.
- Selecting a preset deactivates custom mode and hides the inline row, but retains the RGB value.
- Selecting **Custom** again reactivates the retained value without opening the dialog; the inline
  row returns.
- Reset clears the retained color and returns to the preset.

**Text color**

- Two chips: **Default** and **Custom**.
- **Custom** with no retained color opens the RGB dialog; applying it activates custom text and
  shows the inline text-color row.
- **Custom** with a retained color reactivates it; the inline row can reopen the dialog.
- **Default** hides the row and uses the resolution rules above; the retained RGB is kept, so
  switching back to Custom restores it.
- The dialog preview uses the active page background and the default/custom text color.

### Settings → Library

Reader options and **Options → Library → reader profile** use shared controls:

- `ReaderAppearanceSettings` — Menu & options and Reading background blocks, including the custom
  background dialog/state;
- `ReaderTextColorSettings` — Default/Custom text color and the RGB dialog.

The Library editor also gained the previously missing volume-page-navigation, page-progress, and
PDF facing-page controls. Reading direction, tap zones, font size, layout modes, margins, spread,
and EPUB alignment/hyphenation were already present.

Still reader-options-only (pre-existing divergence, tracked as a follow-up):

- font family selection;
- custom font import/remove;
- line-spacing and word-spacing sliders.

The tap-zone tutorial toggle is a reader-session behavior rather than a stored profile field and
remains specific to the reader surface.

## Compatibility

- Works on the app's minimum API 26+; custom colors are EPUB-only (PDF/comic readers are unchanged).
- API 26–34: status/navigation colors and icon appearance are applied directly.
- API 35/36 (edge-to-edge): status/navigation colors are deprecated/ignored by the platform; the
  window background and root reading views provide the surface. Gesture navigation is transparent;
  some 3-button navigation/OEM skins may still impose their own scrim or contrast behavior.
- No custom menu/chrome palette; reader chrome follows `theme`.
- Custom colors are not automatically contrast-validated; the dialog preview is the user-facing
  aid, and an explicit custom text color can override the default.

## Verification

```text
python3 tools/validate_android_localizations.py
./gradlew testDebugUnitTest lintDebug assembleDebug
```

The branch adds JVM tests for RGB parsing/normalization, background/text resolution including system
light/dark fallback, retained custom values, storage round-trips, and legacy-profile migration.
Instrumented tests still require a locale-pinned device/emulator; visual validation of system-bar
and footer color behavior remains a device-manual step.

## Follow-ups

- Sync font family, custom font, line spacing, and word spacing into Options → Library.
- Device matrix validation for system bars across API 26/29/33/35, gesture and 3-button navigation,
  status/navigation bars shown and hidden.
- Decide whether reader chrome should also support a custom RGB palette.
