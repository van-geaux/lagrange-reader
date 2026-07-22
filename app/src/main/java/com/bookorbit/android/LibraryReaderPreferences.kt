package com.bookorbit.android

import org.json.JSONObject

enum class LibraryReadingDirection(val displayName: String) {
    LEFT_TO_RIGHT("Left to right"),
    RIGHT_TO_LEFT("Right to left")
}

data class LibraryReaderPreferences(
    val readingDirection: LibraryReadingDirection = LibraryReadingDirection.LEFT_TO_RIGHT,
    val theme: EpubReaderTheme = EpubReaderTheme.Sepia,
    val fontScale: Float = 1f,
    val padding: EpubPaddingPercentages = EpubPaddingPercentages()
) {
    fun normalized(): LibraryReaderPreferences = copy(
        fontScale = fontScale.coerceIn(0.9f, 1.5f),
        padding = padding.normalizedReaderPadding()
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
                        )
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
