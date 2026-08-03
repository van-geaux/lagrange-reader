package com.vangeaux.lagrange

import org.json.JSONObject

enum class LibraryReadingDirection(val displayName: String) {
    LEFT_TO_RIGHT("Left to right"),
    RIGHT_TO_LEFT("Right to left")
}

enum class ReaderLayoutMode(val displayName: String) {
    PAGINATED("Paginated"),
    CONTINUOUS("Continuous")
}

enum class EpubReaderFontFamily(val displayName: String) {
    PUBLISHER_DEFAULT("Publisher default"),
    SYSTEM_SERIF("System serif"),
    SYSTEM_SANS_SERIF("System sans-serif"),
    SYSTEM_MONOSPACE("System monospace"),
    ACCESSIBLE_DFA("AccessibleDFA"),
    OPEN_DYSLEXIC("OpenDyslexic"),
    CUSTOM("Custom font")
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
internal const val DEFAULT_EPUB_LINE_SPACING = 1f
internal const val MAX_EPUB_LINE_SPACING = 2f
internal const val DEFAULT_EPUB_WORD_SPACING = 0f
internal const val MAX_EPUB_WORD_SPACING = 1f

private const val READER_PREFERENCES_STORAGE_VERSION = 1
private const val READER_PREFERENCES_PROFILES_KEY = "profiles"

data class LibraryReaderPreferences(
    val readingDirection: LibraryReadingDirection = LibraryReadingDirection.LEFT_TO_RIGHT,
    val theme: EpubReaderTheme = EpubReaderTheme.Sepia,
    val fontFamily: EpubReaderFontFamily = EpubReaderFontFamily.PUBLISHER_DEFAULT,
    val customFont: CustomFontRecord? = null,
    val fontScale: Float = 1f,
    val lineSpacing: Float = DEFAULT_EPUB_LINE_SPACING,
    val wordSpacing: Float = DEFAULT_EPUB_WORD_SPACING,
    val padding: EpubPaddingPercentages = EpubPaddingPercentages(),
    val epubLayoutMode: ReaderLayoutMode = ReaderLayoutMode.PAGINATED,
    val pdfLayoutMode: ReaderLayoutMode = ReaderLayoutMode.CONTINUOUS,
    val pdfPageGapDp: Float = DEFAULT_READER_PAGE_GAP_DP,
    val comicLayoutMode: ReaderLayoutMode = ReaderLayoutMode.PAGINATED,
    val comicPageGapDp: Float = DEFAULT_READER_PAGE_GAP_DP
) {
    fun normalized(): LibraryReaderPreferences = copy(
        fontScale = fontScale.coerceIn(0.9f, 1.5f),
        lineSpacing = lineSpacing.coerceIn(DEFAULT_EPUB_LINE_SPACING, MAX_EPUB_LINE_SPACING),
        wordSpacing = wordSpacing.coerceIn(DEFAULT_EPUB_WORD_SPACING, MAX_EPUB_WORD_SPACING),
        padding = padding.normalizedReaderPadding(),
        pdfPageGapDp = pdfPageGapDp.coerceIn(0f, MAX_READER_PAGE_GAP_DP),
        comicPageGapDp = comicPageGapDp.coerceIn(0f, MAX_READER_PAGE_GAP_DP)
    )
}

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
            put("theme", epubReaderThemeStorageValue(normalized.theme))
            put("fontFamily", epubReaderFontFamilyStorageValue(normalized.fontFamily))
            normalized.customFont?.let { put("customFont", customFontStorageValue(it)) }
            put("fontScale", normalized.fontScale.toDouble())
            put("lineSpacing", normalized.lineSpacing.toDouble())
            put("wordSpacing", normalized.wordSpacing.toDouble())
            put("top", normalized.padding.top.toDouble())
            put("bottom", normalized.padding.bottom.toDouble())
            put("left", normalized.padding.left.toDouble())
            put("right", normalized.padding.right.toDouble())
            put("epubLayout", readerLayoutModeStorageValue(normalized.epubLayoutMode))
            put("pdfLayout", readerLayoutModeStorageValue(normalized.pdfLayoutMode))
            put("pdfPageGapDp", normalized.pdfPageGapDp.toDouble())
            put("comicLayout", readerLayoutModeStorageValue(normalized.comicLayoutMode))
            put("comicPageGapDp", normalized.comicPageGapDp.toDouble())
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
                    LibraryReaderPreferences(
                        readingDirection = libraryReadingDirectionFromStorage(item.optString("readingDirection")),
                        theme = epubReaderThemeFromStorage(item.optString("theme")),
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
                        pdfLayoutMode = readerLayoutModeFromStorage(
                            item.optString("pdfLayout"),
                            ReaderLayoutMode.CONTINUOUS
                        ),
                        pdfPageGapDp = item.optDouble(
                            "pdfPageGapDp",
                            DEFAULT_READER_PAGE_GAP_DP.toDouble()
                        ).toFloat(),
                        comicLayoutMode = readerLayoutModeFromStorage(
                            item.optString("comicLayout"),
                            ReaderLayoutMode.PAGINATED
                        ),
                        comicPageGapDp = item.optDouble(
                            "comicPageGapDp",
                            DEFAULT_READER_PAGE_GAP_DP.toDouble()
                        ).toFloat()
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
