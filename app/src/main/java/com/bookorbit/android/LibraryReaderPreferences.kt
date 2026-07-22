package com.bookorbit.android

import org.json.JSONObject

enum class LibraryReadingDirection(val displayName: String) {
    LEFT_TO_RIGHT("Left to right"),
    RIGHT_TO_LEFT("Right to left")
}

enum class ReaderLayoutMode(val displayName: String) {
    PAGINATED("Paginated"),
    CONTINUOUS("Continuous")
}

internal const val DEFAULT_READER_PAGE_GAP_DP = 16f
internal const val MAX_READER_PAGE_GAP_DP = 48f

data class LibraryReaderPreferences(
    val readingDirection: LibraryReadingDirection = LibraryReadingDirection.LEFT_TO_RIGHT,
    val theme: EpubReaderTheme = EpubReaderTheme.Sepia,
    val fontScale: Float = 1f,
    val padding: EpubPaddingPercentages = EpubPaddingPercentages(),
    val epubLayoutMode: ReaderLayoutMode = ReaderLayoutMode.PAGINATED,
    val pdfLayoutMode: ReaderLayoutMode = ReaderLayoutMode.CONTINUOUS,
    val pdfPageGapDp: Float = DEFAULT_READER_PAGE_GAP_DP,
    val comicLayoutMode: ReaderLayoutMode = ReaderLayoutMode.PAGINATED,
    val comicPageGapDp: Float = DEFAULT_READER_PAGE_GAP_DP
) {
    fun normalized(): LibraryReaderPreferences = copy(
        fontScale = fontScale.coerceIn(0.9f, 1.5f),
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

internal fun libraryReaderPreferencesStorageValue(
    values: Map<String, LibraryReaderPreferences>
): String = JSONObject().apply {
    values.toSortedMap().forEach { (libraryId, rawValue) ->
        if (libraryId.isBlank()) return@forEach
        val value = rawValue.normalized()
        put(libraryId, JSONObject().apply {
            put("readingDirection", libraryReadingDirectionStorageValue(value.readingDirection))
            put("theme", epubReaderThemeStorageValue(value.theme))
            put("fontScale", value.fontScale.toDouble())
            put("top", value.padding.top.toDouble())
            put("bottom", value.padding.bottom.toDouble())
            put("left", value.padding.left.toDouble())
            put("right", value.padding.right.toDouble())
            put("epubLayout", readerLayoutModeStorageValue(value.epubLayoutMode))
            put("pdfLayout", readerLayoutModeStorageValue(value.pdfLayoutMode))
            put("pdfPageGapDp", value.pdfPageGapDp.toDouble())
            put("comicLayout", readerLayoutModeStorageValue(value.comicLayoutMode))
            put("comicPageGapDp", value.comicPageGapDp.toDouble())
        })
    }
}.toString()

internal fun libraryReaderPreferencesFromStorage(value: String?): Map<String, LibraryReaderPreferences> {
    if (value.isNullOrBlank()) return emptyMap()
    return runCatching {
        val root = JSONObject(value)
        buildMap {
            root.keys().forEach { libraryId ->
                if (libraryId.isBlank()) return@forEach
                val item = root.optJSONObject(libraryId) ?: return@forEach
                put(
                    libraryId,
                    LibraryReaderPreferences(
                        readingDirection = libraryReadingDirectionFromStorage(item.optString("readingDirection")),
                        theme = epubReaderThemeFromStorage(item.optString("theme")),
                        fontScale = item.optDouble("fontScale", 1.0).toFloat(),
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
                )
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
