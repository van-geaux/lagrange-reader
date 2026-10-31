package com.vangeaux.lagrange

import androidx.annotation.StringRes
import org.json.JSONObject

enum class LibraryReadingDirection(
    val displayName: String,
    @StringRes val displayNameRes: Int
) {
    LEFT_TO_RIGHT("Left to right", R.string.label_reading_direction_ltr),
    RIGHT_TO_LEFT("Right to left", R.string.label_reading_direction_rtl)
}

enum class ReaderLayoutMode(
    val displayName: String,
    @StringRes val displayNameRes: Int
) {
    PAGINATED("Paginated", R.string.label_layout_paginated),
    CONTINUOUS("Continuous", R.string.label_layout_continuous)
}

enum class EpubReaderFontFamily(
    val displayName: String,
    @StringRes val displayNameRes: Int
) {
    PUBLISHER_DEFAULT("Publisher default", R.string.label_font_publisher_default),
    SYSTEM_SERIF("System serif", R.string.label_font_system_serif),
    SYSTEM_SANS_SERIF("System sans-serif", R.string.label_font_system_sans_serif),
    SYSTEM_MONOSPACE("System monospace", R.string.label_font_system_monospace),
    ACCESSIBLE_DFA("AccessibleDFA", R.string.label_font_accessible_dfa),
    OPEN_DYSLEXIC("OpenDyslexic", R.string.label_font_open_dyslexic),
    CUSTOM("Custom font", R.string.label_font_custom)
}

enum class EpubTextAlignment(
    val displayName: String,
    @StringRes val displayNameRes: Int
) {
    START("Left", R.string.label_align_left),
    JUSTIFY("Justified", R.string.label_align_justified)
}

/** AUTO hyphenates justified text only, which is when ragged gaps would otherwise appear. */
enum class EpubHyphenation(
    val displayName: String,
    @StringRes val displayNameRes: Int
) {
    AUTO("Auto", R.string.label_hyphenation_auto),
    ON("On", R.string.label_hyphenation_on),
    OFF("Off", R.string.label_hyphenation_off)
}

internal fun epubHyphenationActive(alignment: EpubTextAlignment, hyphenation: EpubHyphenation): Boolean =
    when (hyphenation) {
        EpubHyphenation.ON -> true
        EpubHyphenation.OFF -> false
        EpubHyphenation.AUTO -> alignment == EpubTextAlignment.JUSTIFY
    }

internal fun epubTextAlignmentStorageValue(value: EpubTextAlignment): String = value.name.lowercase()

internal fun epubTextAlignmentFromStorage(value: String?): EpubTextAlignment =
    if (value?.trim()?.lowercase() == "justify") EpubTextAlignment.JUSTIFY else EpubTextAlignment.START

internal fun epubHyphenationStorageValue(value: EpubHyphenation): String = value.name.lowercase()

internal fun epubHyphenationFromStorage(value: String?): EpubHyphenation =
    when (value?.trim()?.lowercase()) {
        "on" -> EpubHyphenation.ON
        "off" -> EpubHyphenation.OFF
        else -> EpubHyphenation.AUTO
    }

internal val EPUB_NORMAL_FONT_FAMILY_OPTIONS = listOf(
    EpubReaderFontFamily.PUBLISHER_DEFAULT,
    EpubReaderFontFamily.SYSTEM_SERIF,
    EpubReaderFontFamily.SYSTEM_SANS_SERIF,
    EpubReaderFontFamily.SYSTEM_MONOSPACE
)

internal val EPUB_ACCESSIBILITY_FONT_FAMILY_OPTIONS = listOf(
    EpubReaderFontFamily.ACCESSIBLE_DFA,
    EpubReaderFontFamily.OPEN_DYSLEXIC
)

internal const val DEFAULT_READER_PAGE_GAP_DP = 16f
internal const val MAX_READER_PAGE_GAP_DP = 48f
internal const val MIN_EPUB_FONT_SCALE = 0.9f
internal const val MAX_EPUB_FONT_SCALE = 2f
internal const val DEFAULT_EPUB_LINE_SPACING = 1f
internal const val MAX_EPUB_LINE_SPACING = 2f
internal const val DEFAULT_EPUB_WORD_SPACING = 0f
internal const val MAX_EPUB_WORD_SPACING = 1f
internal const val DEFAULT_EPUB_PARAGRAPH_SPACING = 0f
internal const val MAX_EPUB_PARAGRAPH_SPACING = 2f

private const val READER_PREFERENCES_STORAGE_VERSION = 3
private const val READER_PREFERENCES_PROFILES_KEY = "profiles"

data class LibraryReaderPreferences(
    val readingDirection: LibraryReadingDirection = LibraryReadingDirection.LEFT_TO_RIGHT,
    val volumeButtonPageNavigation: Boolean = false,
    val reverseVolumeButtonNavigation: Boolean = false,
    val tapZoneLayout: ReaderTapZoneLayout = ReaderTapZoneLayout.CURRENT_EDGES,
    val tapZoneInvertMode: ReaderTapZoneInvertMode = ReaderTapZoneInvertMode.NONE,
    val theme: EpubReaderTheme = EpubReaderTheme.Sepia,
    val backgroundTheme: EpubReaderTheme = EpubReaderTheme.Sepia,
    val customBackgroundColor: Int? = null,
    val useCustomBackground: Boolean = false,
    val customTextColor: Int? = null,
    val useCustomTextColor: Boolean = false,
    val fontFamily: EpubReaderFontFamily = EpubReaderFontFamily.PUBLISHER_DEFAULT,
    val customFont: CustomFontRecord? = null,
    val fontScale: Float = 1f,
    val lineSpacing: Float = DEFAULT_EPUB_LINE_SPACING,
    val wordSpacing: Float = DEFAULT_EPUB_WORD_SPACING,
    val paragraphSpacing: Float = DEFAULT_EPUB_PARAGRAPH_SPACING,
    val textAlignment: EpubTextAlignment = EpubTextAlignment.START,
    val hyphenation: EpubHyphenation = EpubHyphenation.AUTO,
    val preferServerProgressOnReopen: Boolean = false,
    val padding: EpubPaddingPercentages = EpubPaddingPercentages(),
    val epubLayoutMode: ReaderLayoutMode = ReaderLayoutMode.PAGINATED,
    val showPageProgress: Boolean = true,
    val pdfLayoutMode: ReaderLayoutMode = ReaderLayoutMode.CONTINUOUS,
    val pdfPageGapDp: Float = DEFAULT_READER_PAGE_GAP_DP,
    val joinPdfFacingPages: Boolean = false,
    val invertPdfColors: Boolean = false,
    val comicLayoutMode: ReaderLayoutMode = ReaderLayoutMode.PAGINATED,
    val comicPageGapDp: Float = DEFAULT_READER_PAGE_GAP_DP,
    val twoPageSpread: ReaderSpreadMode = ReaderSpreadMode.AUTO
) {
    fun normalized(): LibraryReaderPreferences = copy(
        customBackgroundColor = normalizedCustomReaderBackground(customBackgroundColor),
        useCustomBackground = useCustomBackground && customBackgroundColor != null,
        customTextColor = normalizedReaderTextColor(customTextColor),
        useCustomTextColor = useCustomTextColor && customTextColor != null,
        fontScale = fontScale.coerceIn(MIN_EPUB_FONT_SCALE, MAX_EPUB_FONT_SCALE),
        lineSpacing = lineSpacing.coerceIn(DEFAULT_EPUB_LINE_SPACING, MAX_EPUB_LINE_SPACING),
        wordSpacing = wordSpacing.coerceIn(DEFAULT_EPUB_WORD_SPACING, MAX_EPUB_WORD_SPACING),
        paragraphSpacing = paragraphSpacing.coerceIn(
            DEFAULT_EPUB_PARAGRAPH_SPACING,
            MAX_EPUB_PARAGRAPH_SPACING
        ),
        padding = padding.normalizedReaderPadding(),
        pdfPageGapDp = pdfPageGapDp.coerceIn(0f, MAX_READER_PAGE_GAP_DP),
        comicPageGapDp = comicPageGapDp.coerceIn(0f, MAX_READER_PAGE_GAP_DP)
    )
}

internal data class ReaderSystemBarsPolicy(
    val showStatusBar: Boolean = true,
    val showNavigationBar: Boolean
)

internal fun readerSystemBarsPolicy(
    hideNavigationBar: Boolean,
    hideStatusBar: Boolean = false
): ReaderSystemBarsPolicy = ReaderSystemBarsPolicy(
    showStatusBar = !hideStatusBar,
    showNavigationBar = !hideNavigationBar
)

internal fun readerViewportBottomInset(
    navigationBarBottomInset: Int,
    hideNavigationBar: Boolean
): Int = if (hideNavigationBar) 0 else navigationBarBottomInset.coerceAtLeast(0)

internal fun AppPreferences.readerPreferencesFor(libraryId: String): LibraryReaderPreferences =
    libraryReaderPreferences[libraryId]?.normalized() ?: LibraryReaderPreferences()

internal fun AppPreferences.withReaderPreferences(
    libraryId: String,
    value: LibraryReaderPreferences
): AppPreferences {
    if (libraryId.isBlank()) return this
    return copy(libraryReaderPreferences = libraryReaderPreferences + (libraryId to value.normalized()))
}

internal fun libraryReadingDirectionStorageValue(value: LibraryReadingDirection): String =
    value.name.lowercase()

internal fun libraryReadingDirectionFromStorage(value: String?): LibraryReadingDirection =
    if (value?.trim()?.lowercase() == "right_to_left") {
        LibraryReadingDirection.RIGHT_TO_LEFT
    } else {
        LibraryReadingDirection.LEFT_TO_RIGHT
    }

internal fun readerLayoutModeStorageValue(value: ReaderLayoutMode): String =
    value.name.lowercase()

internal fun readerLayoutModeFromStorage(
    value: String?,
    default: ReaderLayoutMode
): ReaderLayoutMode = when (value?.trim()?.lowercase()) {
    "paginated" -> ReaderLayoutMode.PAGINATED
    "continuous" -> ReaderLayoutMode.CONTINUOUS
    else -> default
}

internal fun epubReaderFontFamilyStorageValue(value: EpubReaderFontFamily): String =
    value.name.lowercase()

internal fun epubReaderFontFamilyFromStorage(value: String?): EpubReaderFontFamily =
    when (value?.trim()?.lowercase()) {
        "system_serif" -> EpubReaderFontFamily.SYSTEM_SERIF
        "system_sans_serif" -> EpubReaderFontFamily.SYSTEM_SANS_SERIF
        "system_monospace" -> EpubReaderFontFamily.SYSTEM_MONOSPACE
        "accessible_dfa" -> EpubReaderFontFamily.ACCESSIBLE_DFA
        "open_dyslexic" -> EpubReaderFontFamily.OPEN_DYSLEXIC
        "custom" -> EpubReaderFontFamily.CUSTOM
        else -> EpubReaderFontFamily.PUBLISHER_DEFAULT
    }

internal fun libraryReaderPreferencesStorageValue(
    values: Map<String, LibraryReaderPreferences>
): String = JSONObject().apply {
    put("version", READER_PREFERENCES_STORAGE_VERSION)
    put(READER_PREFERENCES_PROFILES_KEY, JSONObject().apply {
        values.toSortedMap().forEach { (libraryId, rawValue) ->
            if (libraryId.isBlank()) return@forEach
            val value = rawValue.normalized()
            put(libraryId, libraryReaderPreferenceStorageValue(value))
        }
    })
}.toString()

private fun libraryReaderPreferenceStorageValue(value: LibraryReaderPreferences): JSONObject =
    value.normalized().let { normalized ->
        JSONObject().apply {
            put("readingDirection", libraryReadingDirectionStorageValue(normalized.readingDirection))
            put("volumeButtonPageNavigation", normalized.volumeButtonPageNavigation)
            put("reverseVolumeButtonNavigation", normalized.reverseVolumeButtonNavigation)
            put("tapZoneLayout", readerTapZoneLayoutStorageValue(normalized.tapZoneLayout))
            put("tapZoneInvertMode", readerTapZoneInvertModeStorageValue(normalized.tapZoneInvertMode))
            put("theme", epubReaderThemeStorageValue(normalized.theme))
            put("backgroundTheme", epubReaderThemeStorageValue(normalized.backgroundTheme))
            put("useCustomBackground", normalized.useCustomBackground)
            normalized.customBackgroundColor?.let {
                put("customBackgroundColor", customReaderBackgroundHex(it))
            }
            put("useCustomTextColor", normalized.useCustomTextColor)
            normalized.customTextColor?.let {
                put("customTextColor", customReaderBackgroundHex(it))
            }
            put("fontFamily", epubReaderFontFamilyStorageValue(normalized.fontFamily))
            normalized.customFont?.let { put("customFont", customFontStorageValue(it)) }
            put("fontScale", normalized.fontScale.toDouble())
            put("lineSpacing", normalized.lineSpacing.toDouble())
            put("wordSpacing", normalized.wordSpacing.toDouble())
            put("paragraphSpacing", normalized.paragraphSpacing.toDouble())
            put("textAlignment", epubTextAlignmentStorageValue(normalized.textAlignment))
            put("hyphenation", epubHyphenationStorageValue(normalized.hyphenation))
            put("preferServerProgressOnReopen", normalized.preferServerProgressOnReopen)
            put("top", normalized.padding.top.toDouble())
            put("bottom", normalized.padding.bottom.toDouble())
            put("left", normalized.padding.left.toDouble())
            put("right", normalized.padding.right.toDouble())
            put("epubLayout", readerLayoutModeStorageValue(normalized.epubLayoutMode))
            put("showPageProgress", normalized.showPageProgress)
            put("pdfLayout", readerLayoutModeStorageValue(normalized.pdfLayoutMode))
            put("pdfPageGapDp", normalized.pdfPageGapDp.toDouble())
            put("joinPdfFacingPages", normalized.joinPdfFacingPages)
            put("invertPdfColors", normalized.invertPdfColors)
            put("comicLayout", readerLayoutModeStorageValue(normalized.comicLayoutMode))
            put("comicPageGapDp", normalized.comicPageGapDp.toDouble())
            put("twoPageSpread", readerSpreadModeStorageValue(normalized.twoPageSpread))
        }
    }

internal fun libraryReaderPreferencesFromStorage(value: String?): Map<String, LibraryReaderPreferences> {
    if (value.isNullOrBlank()) return emptyMap()
    return runCatching {
        val root = JSONObject(value)
        val profiles = root.optJSONObject(READER_PREFERENCES_PROFILES_KEY) ?: root
        buildMap {
            profiles.keys().forEach { libraryId ->
                if (libraryId.isBlank()) return@forEach
                val item = profiles.optJSONObject(libraryId) ?: return@forEach
                runCatching {
                    val theme = epubReaderThemeFromStorage(item.optString("theme"))
                    val customBackgroundColor = item.optString("customBackgroundColor")
                        .takeIf { it.isNotBlank() }
                        ?.toIntOrNull(16)
                        ?.let(::normalizedCustomReaderBackground)
                    val customTextColor = item.optString("customTextColor")
                        .takeIf { it.isNotBlank() }
                        ?.toIntOrNull(16)
                        ?.let(::normalizedReaderTextColor)
                    LibraryReaderPreferences(
                        readingDirection = libraryReadingDirectionFromStorage(item.optString("readingDirection")),
                        volumeButtonPageNavigation = item.optBoolean("volumeButtonPageNavigation", false),
                        reverseVolumeButtonNavigation = item.optBoolean("reverseVolumeButtonNavigation", false),
                        tapZoneLayout = readerTapZoneLayoutFromStorage(item.optString("tapZoneLayout")),
                        tapZoneInvertMode = readerTapZoneInvertModeFromStorage(item.optString("tapZoneInvertMode")),
                        theme = theme,
                        backgroundTheme = if (item.has("backgroundTheme")) {
                            epubReaderThemeFromStorage(item.optString("backgroundTheme"))
                        } else {
                            theme
                        },
                        customBackgroundColor = customBackgroundColor,
                        useCustomBackground = item.optBoolean(
                            "useCustomBackground",
                            customBackgroundColor != null
                        ),
                        customTextColor = customTextColor,
                        useCustomTextColor = item.optBoolean(
                            "useCustomTextColor",
                            customTextColor != null
                        ),
                        fontFamily = epubReaderFontFamilyFromStorage(item.optString("fontFamily")),
                        customFont = customFontFromStorage(item.optString("customFont")),
                        fontScale = item.optDouble("fontScale", 1.0).toFloat(),
                        lineSpacing = item.optDouble(
                            "lineSpacing",
                            DEFAULT_EPUB_LINE_SPACING.toDouble()
                        ).toFloat(),
                        wordSpacing = item.optDouble(
                            "wordSpacing",
                            DEFAULT_EPUB_WORD_SPACING.toDouble()
                        ).toFloat(),
                        paragraphSpacing = item.optDouble(
                            "paragraphSpacing",
                            DEFAULT_EPUB_PARAGRAPH_SPACING.toDouble()
                        ).toFloat(),
                        textAlignment = epubTextAlignmentFromStorage(item.optString("textAlignment")),
                        hyphenation = epubHyphenationFromStorage(item.optString("hyphenation")),
                        preferServerProgressOnReopen = item.optBoolean("preferServerProgressOnReopen", false),
                        padding = EpubPaddingPercentages(
                            top = item.optDouble("top", EPUB_DEFAULT_TOP_PADDING_PERCENT.toDouble()).toFloat(),
                            bottom = item.optDouble("bottom", EPUB_DEFAULT_PADDING_PERCENT.toDouble()).toFloat(),
                            left = item.optDouble("left", EPUB_DEFAULT_PADDING_PERCENT.toDouble()).toFloat(),
                            right = item.optDouble("right", EPUB_DEFAULT_PADDING_PERCENT.toDouble()).toFloat()
                        ),
                        epubLayoutMode = readerLayoutModeFromStorage(
                            item.optString("epubLayout"),
                            ReaderLayoutMode.PAGINATED
                        ),
                        showPageProgress = item.optBoolean("showPageProgress", true),
                        pdfLayoutMode = readerLayoutModeFromStorage(
                            item.optString("pdfLayout"),
                            ReaderLayoutMode.CONTINUOUS
                        ),
                        pdfPageGapDp = item.optDouble(
                            "pdfPageGapDp",
                            DEFAULT_READER_PAGE_GAP_DP.toDouble()
                        ).toFloat(),
                        joinPdfFacingPages = item.optBoolean("joinPdfFacingPages", false),
                        invertPdfColors = item.optBoolean("invertPdfColors", false),
                        comicLayoutMode = readerLayoutModeFromStorage(
                            item.optString("comicLayout"),
                            ReaderLayoutMode.PAGINATED
                        ),
                        comicPageGapDp = item.optDouble(
                            "comicPageGapDp",
                            DEFAULT_READER_PAGE_GAP_DP.toDouble()
                        ).toFloat(),
                        twoPageSpread = readerSpreadModeFromStorage(item.optString("twoPageSpread"))
                    ).normalized()
                }.getOrNull()?.let { decoded -> put(libraryId, decoded) }
            }
        }
    }.getOrDefault(emptyMap())
}

internal fun EpubPaddingPercentages.normalizedReaderPadding(): EpubPaddingPercentages = copy(
    top = top.coerceIn(0f, 100f),
    bottom = bottom.coerceIn(0f, 100f),
    left = left.coerceIn(0f, 100f),
    right = right.coerceIn(0f, 100f)
)
