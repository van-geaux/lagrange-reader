package com.vangeaux.lagrange

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/*
 * True OLED design for the EPUB reader: every surface is #000000 so OLED pixels stay off, and
 * primary text and icons are #FFFFFF. Secondary text and hairline outlines use neutral greys on
 * black; selection states invert to white-on-black. Comics and PDFs are intentionally unaffected.
 */

internal const val OLED_BLACK_ARGB = 0xFF000000.toInt()
internal const val OLED_WHITE_ARGB = 0xFFFFFFFF.toInt()
internal const val OLED_MUTED_ARGB = 0xFFBDBDBD.toInt()
internal const val OLED_OUTLINE_ARGB = 0xFF8A8A8A.toInt()
/** Hairlines and slider tracks only; never used as a surface fill. */
internal const val OLED_HAIRLINE_ARGB = 0xFF333333.toInt()
internal const val OLED_ERROR_ARGB = 0xFFFF8A80.toInt()

internal fun EpubReaderTheme.isOled(): Boolean = this == EpubReaderTheme.OledBlack

internal fun oledReaderColorScheme(): ColorScheme {
    val black = Color(OLED_BLACK_ARGB)
    val white = Color(OLED_WHITE_ARGB)
    val muted = Color(OLED_MUTED_ARGB)
    val outline = Color(OLED_OUTLINE_ARGB)
    val hairline = Color(OLED_HAIRLINE_ARGB)
    val error = Color(OLED_ERROR_ARGB)
    return darkColorScheme(
        primary = white,
        onPrimary = black,
        primaryContainer = black,
        onPrimaryContainer = white,
        inversePrimary = black,
        secondary = white,
        onSecondary = black,
        secondaryContainer = white,
        onSecondaryContainer = black,
        tertiary = white,
        onTertiary = black,
        tertiaryContainer = black,
        onTertiaryContainer = white,
        background = black,
        onBackground = white,
        surface = black,
        onSurface = white,
        surfaceVariant = hairline,
        onSurfaceVariant = muted,
        // No tonal elevation tint: elevated surfaces must stay pure black.
        surfaceTint = black,
        inverseSurface = white,
        inverseOnSurface = black,
        error = error,
        onError = black,
        errorContainer = black,
        onErrorContainer = error,
        outline = outline,
        outlineVariant = hairline,
        scrim = black,
        surfaceBright = black,
        surfaceContainer = black,
        surfaceContainerHigh = black,
        surfaceContainerHighest = black,
        surfaceContainerLow = black,
        surfaceContainerLowest = black,
        surfaceDim = black
    )
}

/**
 * Material theme for EPUB reader overlays. OLED replaces the app colour scheme with the pure
 * black/white scheme; every other reader theme keeps the existing app theme unchanged.
 */
@Composable
internal fun EpubReaderMaterialTheme(
    theme: EpubReaderTheme,
    content: @Composable () -> Unit
) {
    BookOrbitTheme {
        if (theme.isOled()) {
            MaterialTheme(
                colorScheme = oledReaderColorScheme(),
                typography = MaterialTheme.typography,
                shapes = MaterialTheme.shapes
            ) {
                CompositionLocalProvider(LocalEpubReaderOledOutlines provides true, content = content)
            }
        } else {
            content()
        }
    }
}

/** True inside OLED reader overlays, where shadows and tonal fills are invisible on black. */
internal val LocalEpubReaderOledOutlines = staticCompositionLocalOf { false }

/** Hairline edge for floating reader panels on OLED; null keeps the existing look elsewhere. */
@Composable
internal fun epubReaderOledPanelBorder(): BorderStroke? =
    if (LocalEpubReaderOledOutlines.current) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    } else {
        null
    }

/**
 * Fill for floating reader panels. Material3's filled card uses `surfaceVariant`, which is the grey
 * hairline colour in the OLED scheme, so OLED panels are filled with the black surface instead.
 */
@Composable
internal fun epubReaderOledPanelColors(): CardColors =
    if (LocalEpubReaderOledOutlines.current) {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    } else {
        CardDefaults.cardColors()
    }

/** Android view theme for the EPUB reader activity; applied before the window is created. */
internal fun epubReaderActivityThemeRes(theme: EpubReaderTheme): Int =
    if (theme.isOled()) R.style.Theme_BookOrbit_Reader_Oled else R.style.Theme_BookOrbit_Reader

/** Switching into or out of OLED changes the activity theme, which requires recreation. */
internal fun epubReaderThemeChangeRequiresRecreate(
    previous: EpubReaderTheme,
    next: EpubReaderTheme
): Boolean = previous.isOled() != next.isOled()

/**
 * In-page OLED overrides. ReadiumCSS night mode forces blue links and a pale-blue text selection;
 * these rules are more specific than ReadiumCSS's `:root[style*=readium-night-on] a:link` and keep
 * the page strictly white on black. Images are left untouched (see the image colour preservation
 * script), so illustrations keep their original colours.
 */
internal fun readiumEpubOledTextStyleScript(): String = """
    (function() {
      const styleId = 'lagrange-readium-epub-oled-text';
      if (document.getElementById(styleId)) return 'already-installed';
      const style = document.createElement('style');
      style.id = styleId;
      style.textContent = `
        html:root[style] a:link,
        html:root[style] a:link *,
        html:root[style] a:visited,
        html:root[style] a:visited * {
          color: #FFFFFF !important;
          text-decoration-line: underline !important;
        }
        html:root[style] ::selection {
          background-color: #FFFFFF !important;
          color: #000000 !important;
        }
        html:root[style] hr {
          border-color: #FFFFFF !important;
        }
      `;
      (document.head || document.documentElement).appendChild(style);
      return 'installed';
    })();
""".trimIndent()
