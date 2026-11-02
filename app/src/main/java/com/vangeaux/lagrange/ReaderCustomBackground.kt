package com.vangeaux.lagrange

import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Custom EPUB page background support.
 *
 * The reader keeps [LibraryReaderPreferences.theme] for chrome and window defaults, but when
 * [LibraryReaderPreferences.customBackgroundColor] is set the page background uses that RGB colour.
 * Text colour and Readium's internal theme are derived from the background luminance so contrast
 * stays readable for both light and dark custom backgrounds.
 *
 * Keep this file free of Android framework dependencies so it can be unit-tested on the JVM.
 */

/** Text this dark is used on light custom backgrounds (matches the built-in light reader text). */
internal const val CUSTOM_READER_BACKGROUND_DARK_TEXT_ARGB: Long = 0xFF1F1C17L

/** Text this light is used on dark custom backgrounds (matches the built-in dark reader text). */
internal const val CUSTOM_READER_BACKGROUND_LIGHT_TEXT_ARGB: Long = 0xFFECE4D8L

/**
 * Relative luminance (WCAG) at which black text starts to win over white text.
 * White text is preferred when the background is darker than this value.
 */
internal const val CUSTOM_READER_BACKGROUND_DARK_TEXT_LUMINANCE = 0.1791

internal fun parseCustomReaderHex(value: String): Int? {
    val hex = value.trim().removePrefix("#")
    if (hex.length != 6 || hex.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) {
        return null
    }
    return hex.toIntOrNull(16)?.let { 0xFF000000.toInt() or it }
}

internal data class ReaderHsvColor(
    val hue: Float,
    val saturation: Float,
    val brightness: Float
)

internal fun readerColorFromHsv(hue: Float, saturation: Float, brightness: Float): Int {
    val normalizedHue = ((hue % 360f) + 360f) % 360f
    val normalizedSaturation = saturation.coerceIn(0f, 1f)
    val normalizedBrightness = brightness.coerceIn(0f, 1f)
    val chroma = normalizedBrightness * normalizedSaturation
    val hueSection = normalizedHue / 60f
    val secondary = chroma * (1f - abs(hueSection % 2f - 1f))
    val (red, green, blue) = when (hueSection.toInt()) {
        0 -> Triple(chroma, secondary, 0f)
        1 -> Triple(secondary, chroma, 0f)
        2 -> Triple(0f, chroma, secondary)
        3 -> Triple(0f, secondary, chroma)
        4 -> Triple(secondary, 0f, chroma)
        else -> Triple(chroma, 0f, secondary)
    }
    val offset = normalizedBrightness - chroma
    fun channel(value: Float): Int = ((value + offset) * 255f).roundToInt().coerceIn(0, 255)
    return (0xFF shl 24) or (channel(red) shl 16) or (channel(green) shl 8) or channel(blue)
}

internal fun readerColorToHsv(color: Int): ReaderHsvColor {
    val red = ((color shr 16) and 0xFF) / 255f
    val green = ((color shr 8) and 0xFF) / 255f
    val blue = (color and 0xFF) / 255f
    val maximum = max(red, max(green, blue))
    val minimum = min(red, min(green, blue))
    val delta = maximum - minimum
    val hue = when {
        delta == 0f -> 0f
        maximum == red -> 60f * (((green - blue) / delta) % 6f)
        maximum == green -> 60f * (((blue - red) / delta) + 2f)
        else -> 60f * (((red - green) / delta) + 4f)
    }.let { if (it < 0f) it + 360f else it }
    return ReaderHsvColor(
        hue = hue,
        saturation = if (maximum == 0f) 0f else delta / maximum,
        brightness = maximum
    )
}

/** Strips any alpha so stored colours are always opaque RGB. */
internal fun normalizedCustomReaderBackground(color: Int?): Int? =
    color?.let { 0xFF000000.toInt() or (it and 0xFFFFFF) }

/** Six-digit CSS hex for a stored custom background (no leading `#`). */
internal fun customReaderBackgroundHex(color: Int): String =
    String.format(Locale.US, "%06X", color and 0xFFFFFF)

internal fun customReaderBackgroundCss(color: Int): String = "#${customReaderBackgroundHex(color)}"

/** WCAG relative luminance of the RGB part of [color], in 0..1. */
internal fun customReaderBackgroundLuminance(color: Int): Double {
    fun channel(component: Int): Double {
        val value = component.coerceIn(0, 255) / 255.0
        return if (value <= 0.03928) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel((color shr 16) and 0xFF) +
        0.7152 * channel((color shr 8) and 0xFF) +
        0.0722 * channel(color and 0xFF)
}

internal fun isLightCustomReaderBackground(color: Int): Boolean =
    customReaderBackgroundLuminance(color) > CUSTOM_READER_BACKGROUND_DARK_TEXT_LUMINANCE

/** Text colour with the better contrast against [color] (WCAG luminance comparison). */
internal fun customReaderBackgroundTextColor(color: Int): Int =
    if (isLightCustomReaderBackground(color)) {
        CUSTOM_READER_BACKGROUND_DARK_TEXT_ARGB.toInt()
    } else {
        CUSTOM_READER_BACKGROUND_LIGHT_TEXT_ARGB.toInt()
    }

/**
 * Readium theme used for a custom background. The theme only supplies links/selection colours now
 * that the background and text colours are passed explicitly.
 */
internal fun customReaderBackgroundReadiumTheme(color: Int): EpubReaderTheme =
    if (isLightCustomReaderBackground(color)) EpubReaderTheme.Light else EpubReaderTheme.Dark

/** Normalizes a stored custom text colour to opaque RGB. */
internal fun normalizedReaderTextColor(color: Int?): Int? =
    color?.let { 0xFF000000.toInt() or (it and 0xFFFFFF) }

internal const val DEFAULT_READER_TEXT_ON_DARK_ARGB: Long = 0xFFFFFFFFL
internal const val DEFAULT_READER_TEXT_ON_LIGHT_ARGB: Long = 0xFF000000L

/**
 * Default text colour when the user has not selected a custom text colour.
 *
 * - preset background: the preset's own foreground colour;
 * - custom background: white on a dark system theme, black on a light system theme, and white
 *   when the system mode cannot be determined.
 */
internal fun defaultReaderTextColor(
    customBackgroundActive: Boolean,
    presetTextColor: Int,
    systemDarkMode: Boolean?
): Int = if (!customBackgroundActive) {
    presetTextColor
} else {
    when (systemDarkMode) {
        true -> DEFAULT_READER_TEXT_ON_DARK_ARGB.toInt()
        false -> DEFAULT_READER_TEXT_ON_LIGHT_ARGB.toInt()
        null -> DEFAULT_READER_TEXT_ON_DARK_ARGB.toInt()
    }
}

/**
 * Resolves the EPUB content text colour. An active custom colour wins; otherwise [defaultTextColor]
 * is used.
 */
internal fun readerTextColor(
    customTextColor: Int?,
    useCustomTextColor: Boolean,
    defaultTextColor: Int
): Int = if (useCustomTextColor) customTextColor ?: defaultTextColor else defaultTextColor

/** The retained custom background colour, but only while the custom background is active. */
internal fun activeCustomReaderBackground(preferences: LibraryReaderPreferences): Int? =
    preferences.customBackgroundColor?.takeIf { preferences.useCustomBackground }

/** Window/content background: active custom colour, otherwise the selected reading background theme. */
internal fun readerContentBackgroundColor(preferences: LibraryReaderPreferences): Int =
    activeCustomReaderBackground(preferences) ?: preferences.backgroundTheme.backgroundColor

/** Contrast ratio (1..21) between two opaque colours; used by tests. */
internal fun contrastRatio(first: Int, second: Int): Double {
    val firstLuminance = customReaderBackgroundLuminance(first)
    val secondLuminance = customReaderBackgroundLuminance(second)
    return (max(firstLuminance, secondLuminance) + 0.05) /
        (min(firstLuminance, secondLuminance) + 0.05)
}
