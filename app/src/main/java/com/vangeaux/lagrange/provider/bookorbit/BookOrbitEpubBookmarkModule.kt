package com.vangeaux.lagrange.provider.bookorbit

import com.vangeaux.lagrange.EpubBookmark
import com.vangeaux.lagrange.EpubBookmarkPosition
import com.vangeaux.lagrange.UserFacingException
import com.vangeaux.lagrange.core.EpubBookmarkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal class BookOrbitEpubBookmarkModule(
    private val requestExecutor: BookOrbitAuthenticatedJsonRequestExecutor?
) : EpubBookmarkModule {
    override val isAvailable: Boolean get() = requestExecutor != null

    override suspend fun loadBookmarks(bookId: String, readerFileId: String): List<EpubBookmark> = withContext(Dispatchers.IO) {
        val payload = execute(bookOrbitBookmarksPath(bookId, readerFileId), "GET", null)
        parseBookOrbitBookmarks(payload)
    }

    override suspend fun createBookmark(
        bookId: String,
        bookFileId: String,
        position: EpubBookmarkPosition,
        title: String
    ): EpubBookmark = withContext(Dispatchers.IO) {
        val payload = buildBookOrbitCreateBookmarkPayload(position.cfi, bookFileId, title)
        parseBookOrbitBookmark(
            execute(bookOrbitCreateBookmarkPath(bookId), "POST", payload.toString())
        )
    }

    override suspend fun updateBookmark(bookId: String, bookmarkId: String, title: String?, note: String?) =
        withContext(Dispatchers.IO) {
            val payload = buildBookOrbitUpdateBookmarkPayload(title, note)
            execute(bookOrbitBookmarkPath(bookId, bookmarkId), "PATCH", payload.toString())
            Unit
        }

    override suspend fun deleteBookmark(bookId: String, bookmarkId: String) = withContext(Dispatchers.IO) {
        execute(bookOrbitBookmarkPath(bookId, bookmarkId), "DELETE", null)
        Unit
    }

    private suspend fun execute(path: String, method: String, jsonBody: String?): String =
        (requestExecutor ?: unavailable()).executeAuthenticatedJsonRequest(path, method, jsonBody)

    private fun unavailable(): Nothing =
        throw UserFacingException("BookOrbit bookmarks are unavailable.")
}

internal fun bookOrbitBookmarksPath(bookId: String, readerFileId: String): String =
    "/api/v1/books/${encodeBookOrbitPathSegment(bookId)}/bookmarks?readerFileId=${encodeBookOrbitPathSegment(readerFileId)}"

internal fun bookOrbitCreateBookmarkPath(bookId: String): String =
    "/api/v1/books/${encodeBookOrbitPathSegment(bookId)}/bookmarks"

internal fun bookOrbitBookmarkPath(bookId: String, bookmarkId: String): String =
    "/api/v1/books/${encodeBookOrbitPathSegment(bookId)}/bookmarks/${encodeBookOrbitPathSegment(bookmarkId)}"

private fun encodeBookOrbitPathSegment(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

internal fun buildBookOrbitCreateBookmarkPayload(cfi: String, bookFileId: String, title: String): JSONObject {
    val numericBookFileId = bookFileId.toIntOrNull()?.takeIf { it > 0 }
        ?: throw IllegalArgumentException("A positive BookOrbit file id is required to create a bookmark.")
    require(cfi.length in 1..2_000) { "Bookmark CFI must contain 1 to 2,000 characters." }
    require(cfi.startsWith("epubcfi(") && cfi.endsWith(")")) { "Bookmark CFI must be a complete EPUB CFI." }
    require(title.isNotBlank() && title.length <= 500) { "Bookmark title must contain 1 to 500 characters." }
    return JSONObject().apply {
        put("cfi", cfi)
        put("bookFileId", numericBookFileId)
        put("title", title)
    }
}

internal fun buildBookOrbitUpdateBookmarkPayload(title: String?, note: String?): JSONObject {
    require(title == null || title.isNotBlank() && title.length <= 500) {
        "Bookmark title must contain 1 to 500 characters."
    }
    require(note == null || note.length <= 4_000) { "Bookmark note must not exceed 4,000 characters." }
    return JSONObject().apply {
        title?.let { put("title", it) }
        put("note", note ?: JSONObject.NULL)
    }
}

internal fun parseBookOrbitBookmarks(payload: String): List<EpubBookmark> {
    val value = parseBookOrbitJson(payload)
    val array = when (value) {
        is JSONArray -> value
        is JSONObject -> listOf("data", "items", "bookmarks", "results")
            .firstNotNullOfOrNull(value::optJSONArray)
            ?: throw UserFacingException("The server returned an invalid bookmark list.")
        else -> throw UserFacingException("The server returned an invalid bookmark list.")
    }
    return buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index)
                ?: throw UserFacingException("The server returned an invalid bookmark entry.")
            val id = item.bookmarkString("id", "_id")
                ?: throw UserFacingException("The server returned a bookmark without an id.")
            val bookId = item.bookmarkString("bookId", "book_id")
                ?: throw UserFacingException("The server returned a bookmark without a book id.")
            val title = item.bookmarkString("title")
                ?: throw UserFacingException("The server returned a bookmark without a title.")
            add(
                EpubBookmark(
                    id = id,
                    bookId = bookId,
                    cfi = item.bookmarkString("cfi"),
                    title = title,
                    note = item.bookmarkString("note"),
                    createdAt = item.bookmarkString("createdAt", "created_at"),
                    updatedAt = item.bookmarkString("updatedAt", "updated_at")
                )
            )
        }
    }
}

internal fun parseBookOrbitBookmark(payload: String): EpubBookmark {
    val root = parseBookOrbitJson(payload) as? JSONObject
        ?: throw UserFacingException("The server response was missing a bookmark.")
    val item = root.optJSONObject("data") ?: root
    val id = item.bookmarkString("id", "_id")
        ?: throw UserFacingException("The server response was missing a bookmark id.")
    val bookId = item.bookmarkString("bookId", "book_id")
        ?: throw UserFacingException("The server response was missing a book id.")
    val title = item.bookmarkString("title")
        ?: throw UserFacingException("The server response was missing a bookmark title.")
    return EpubBookmark(
        id = id,
        bookId = bookId,
        cfi = item.bookmarkString("cfi"),
        title = title,
        note = item.bookmarkString("note"),
        createdAt = item.bookmarkString("createdAt", "created_at"),
        updatedAt = item.bookmarkString("updatedAt", "updated_at")
    )
}

private fun parseBookOrbitJson(payload: String): Any = runCatching {
    JSONTokener(payload).nextValue()
}.getOrElse {
    throw UserFacingException("The server returned malformed bookmark data.")
}

private fun JSONObject.bookmarkString(vararg keys: String): String? {
    keys.forEach { key ->
        when (val value = opt(key)) {
            is String -> if (value.isNotBlank()) return value
            is Number -> return value.toString()
            is JSONObject -> value.bookmarkString("id", "_id", "value", "name", "title")
                ?.takeIf(String::isNotBlank)
                ?.let { return it }
        }
    }
    return null
}
