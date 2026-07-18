package com.bookorbit.android

import android.content.Context

/** Persists the four reader insets independently for each book/file target. */
internal class EpubReaderPaddingStore(context: Context) {
    private val preferences = context.getSharedPreferences(
        "epub_reader_padding",
        Context.MODE_PRIVATE
    )

    fun read(key: String): EpubPaddingPercentages {
        return EpubPaddingPercentages(
            top = preferences.getFloat(componentKey(key, "top"), EPUB_DEFAULT_TOP_PADDING_PERCENT),
            bottom = preferences.getFloat(componentKey(key, "bottom"), EPUB_DEFAULT_PADDING_PERCENT),
            left = preferences.getFloat(componentKey(key, "left"), EPUB_DEFAULT_PADDING_PERCENT),
            right = preferences.getFloat(componentKey(key, "right"), EPUB_DEFAULT_PADDING_PERCENT)
        ).coerceToReaderRange()
    }

    fun save(key: String, padding: EpubPaddingPercentages) {
        val normalized = padding.coerceToReaderRange()
        preferences.edit()
            .putFloat(componentKey(key, "top"), normalized.top)
            .putFloat(componentKey(key, "bottom"), normalized.bottom)
            .putFloat(componentKey(key, "left"), normalized.left)
            .putFloat(componentKey(key, "right"), normalized.right)
            .apply()
    }

    private fun componentKey(bookKey: String, edge: String): String {
        return "$bookKey:$edge"
    }
}

private fun EpubPaddingPercentages.coerceToReaderRange(): EpubPaddingPercentages {
    return copy(
        top = top.coerceIn(0f, 100f),
        bottom = bottom.coerceIn(0f, 100f),
        left = left.coerceIn(0f, 100f),
        right = right.coerceIn(0f, 100f)
    )
}
