package com.vangeaux.lagrange.provider.kavita

import com.vangeaux.lagrange.EpubBookmark
import com.vangeaux.lagrange.EpubBookmarkLocation
import com.vangeaux.lagrange.EpubBookmarkPosition
import com.vangeaux.lagrange.R
import com.vangeaux.lagrange.UserFacingException
import com.vangeaux.lagrange.core.EpubBookmarkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

private const val XHTML_MEDIA_TYPE = "application/xhtml+xml"
private const val BOOK_READER_BODY_SCOPE =
    "//BODY/APP-ROOT[1]/DIV[1]/DIV[1]/DIV[1]/APP-BOOK-READER[1]/DIV[1]/DIV[2]/DIV[1]/DIV[1]/DIV[1]"

internal data class KavitaPToCDeleteKey(val pageNumber: Int, val title: String)

internal fun kavitaPToCPageNumber(position: EpubBookmarkPosition): Int? {
    var pageNumber = 0
    for (resource in position.readingOrder) {
        if (!resource.mediaType.equals(XHTML_MEDIA_TYPE, ignoreCase = true)) continue
        if (resource.href == position.href) return pageNumber
        pageNumber++
    }
    return null
}

internal fun kavitaBookScrollIdFromCssSelector(cssSelector: String?): String? {
    val parts = cssSelector?.trim()?.split(Regex("\\s*>\\s*")) ?: return null
    if (parts.isEmpty() || !parts.first().equals("body", ignoreCase = true)) return null
    val xpathParts = parts.drop(1).map { part ->
        val match = Regex("^([A-Za-z][A-Za-z0-9-]*):nth-of-type\\(([1-9][0-9]*)\\)$").matchEntire(part)
            ?: return null
        "/${match.groupValues[1].uppercase()}[${match.groupValues[2]}]"
    }
    return BOOK_READER_BODY_SCOPE + xpathParts.joinToString("")
}

internal fun kavitaCssSelectorFromBookScrollId(bookScrollId: String?): String? {
    var xpath = bookScrollId?.trim()?.takeIf(String::isNotEmpty) ?: return null
    if (xpath.startsWith(BOOK_READER_BODY_SCOPE, ignoreCase = true)) {
        xpath = "//BODY" + xpath.substring(BOOK_READER_BODY_SCOPE.length)
    }
    if (!xpath.startsWith("//BODY", ignoreCase = true)) return null
    val suffix = xpath.substring("//BODY".length).trimStart('/')
    if (suffix.isEmpty()) return "body"
    val cssParts = suffix.split('/').map { part ->
        val match = Regex("^([A-Za-z][A-Za-z0-9_-]*)(?:\\[([1-9][0-9]*)\\])?$").matchEntire(part)
            ?: return null
        val tag = match.groupValues[1].lowercase()
        val index = match.groupValues[2].ifEmpty { "1" }
        "$tag:nth-of-type($index)"
    }
    return (listOf("body") + cssParts).joinToString(" > ")
}

internal fun kavitaPToCBookmarkId(pageNumber: Int, title: String): String =
    JSONObject().put("pageNumber", pageNumber).put("title", title).toString()

internal fun kavitaPToCDeleteKey(id: String): KavitaPToCDeleteKey? = runCatching {
    val value = JSONObject(id)
    val pageNumber = value.optInt("pageNumber", -1)
    val title = value.optString("title").trim()
    if (pageNumber < 0 || title.isBlank()) return null
    KavitaPToCDeleteKey(pageNumber, title)
}.getOrNull()

internal fun kavitaPToCPayload(
    context: KavitaChapterContext,
    pageNumber: Int,
    title: String,
    bookScrollId: String
): JSONObject = JSONObject()
    .put("chapterId", context.chapterId)
    .put("volumeId", context.volumeId)
    .put("seriesId", context.seriesId)
    .put("libraryId", context.libraryId)
    .put("pageNumber", pageNumber)
    .put("title", title)
    .put("bookScrollId", bookScrollId)
    .put("selectedText", "")

internal fun parseKavitaPToCBookmarks(payload: String, chapterId: Int): List<EpubBookmark> {
    val value = runCatching { JSONTokener(payload).nextValue() }.getOrElse {
        throw UserFacingException("Kavita returned an invalid EPUB bookmark list.")
    }
    val array = when (value) {
        is JSONArray -> value
        is JSONObject -> value.optJSONArray("items") ?: value.optJSONArray("data")
        else -> null
    } ?: throw UserFacingException("Kavita returned an invalid EPUB bookmark list.")

    return buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index)
                ?: throw UserFacingException("Kavita returned an invalid EPUB bookmark entry.")
            val pageNumber = item.optInt("pageNumber", item.optInt("pageNum", -1))
            val title = item.optString("title").trim()
            if (pageNumber < 0 || title.isBlank()) {
                throw UserFacingException("Kavita returned an EPUB bookmark without a valid location or title.")
            }
            add(
                EpubBookmark(
                    id = kavitaPToCBookmarkId(pageNumber, title),
                    bookId = chapterId.toString(),
                    cfi = null,
                    title = title,
                    location = EpubBookmarkLocation(
                        readingOrderIndex = pageNumber,
                        cssSelector = kavitaCssSelectorFromBookScrollId(item.optString("bookScrollId"))
                    )
                )
            )
        }
    }
}

internal class KavitaEpubBookmarkModuleImpl(
    private val auth: KavitaAuthModule,
    private val client: OkHttpClient = OkHttpClient(),
    private val serverUrlProvider: () -> String? = { auth.serverUrl() }
) : EpubBookmarkModule {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    override val isAvailable: Boolean = true
    override val supportsEditing: Boolean = false

    override suspend fun loadBookmarks(bookId: String, readerFileId: String): List<EpubBookmark> = withContext(Dispatchers.IO) {
        val chapterId = chapterId(bookId, readerFileId)
        val base = serverBase()
        val url = "$base/api/Reader/ptoc?chapterId=$chapterId"
        val request = requestBuilder(url).get().build()
        client.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw bookmarkHttpError(response.code)
            parseKavitaPToCBookmarks(payload, chapterId)
        }
    }

    override suspend fun createBookmark(
        bookId: String,
        bookFileId: String,
        position: EpubBookmarkPosition,
        title: String
    ): EpubBookmark = withContext(Dispatchers.IO) {
        val chapterId = chapterId(bookId, bookFileId)
        val pageNumber = kavitaPToCPageNumber(position)
            ?: throw UserFacingException(auth.localizedMessage(
                R.string.perror_kavita_bookmark_location_unavailable,
                "This EPUB location cannot be mapped to a Kavita reading-order position."
            ))
        val bookScrollId = kavitaBookScrollIdFromCssSelector(position.cssSelector)
            ?: throw UserFacingException(auth.localizedMessage(
                R.string.perror_kavita_bookmark_location_unavailable,
                "This EPUB location cannot be mapped to a Kavita reading-order position."
            ))
        val context = loadChapterContext(chapterId)
        val base = serverBase()
        val payload = kavitaPToCPayload(context, pageNumber, title, bookScrollId)
        val request = requestBuilder("$base/api/Reader/create-ptoc")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()
        client.newCall(request).execute().use { response ->
            response.body?.close()
            if (!response.isSuccessful) throw bookmarkHttpError(response.code)
        }
        EpubBookmark(
            id = kavitaPToCBookmarkId(pageNumber, title),
            bookId = chapterId.toString(),
            cfi = null,
            title = title,
            location = EpubBookmarkLocation(pageNumber, kavitaCssSelectorFromBookScrollId(bookScrollId))
        )
    }

    override suspend fun updateBookmark(bookId: String, bookmarkId: String, title: String?, note: String?) {
        throw UserFacingException(auth.localizedMessage(
            R.string.perror_kavita_bookmark_edit_unsupported,
            "Kavita PToC bookmarks do not support title or note editing."
        ))
    }

    override suspend fun deleteBookmark(bookId: String, bookmarkId: String) = withContext(Dispatchers.IO) {
        val chapterId = chapterId(bookId, null)
        val key = kavitaPToCDeleteKey(bookmarkId)
            ?: throw UserFacingException("Kavita bookmark identity is unavailable.")
        val url = "${serverBase()}/api/Reader/ptoc".toHttpUrl().newBuilder()
            .addQueryParameter("chapterId", chapterId.toString())
            .addQueryParameter("pageNum", key.pageNumber.toString())
            .addQueryParameter("title", key.title)
            .build()
        val request = requestBuilder(url.toString()).delete().build()
        client.newCall(request).execute().use { response ->
            response.body?.close()
            if (!response.isSuccessful) throw bookmarkHttpError(response.code)
        }
    }

    private suspend fun loadChapterContext(chapterId: Int): KavitaChapterContext {
        val base = serverBase()
        val request = requestBuilder("$base/api/Series/chapter?chapterId=$chapterId").get().build()
        return client.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw UserFacingException(auth.localizedMessage(
                    R.string.perror_kavita_hierarchy_http,
                    "Unable to resolve Kavita chapter hierarchy (HTTP ${response.code}).",
                    response.code
                ))
            }
            kavitaChapterContext(JSONObject(payload))
                ?: throw UserFacingException(auth.localizedMessage(
                    R.string.perror_kavita_hierarchy_incomplete,
                    "Kavita returned incomplete chapter hierarchy information."
                ))
        }
    }

    private fun requestBuilder(url: String): Request.Builder = Request.Builder()
        .url(url)
        .header("Accept", "application/json")
        .apply { auth.requestHeaders().forEach { (name, value) -> header(name, value) } }

    private fun chapterId(bookId: String, fallbackFileId: String?): Int =
        kavitaChapterId(bookId, fallbackFileId)
            ?: throw UserFacingException(auth.localizedMessage(
                R.string.perror_kavita_chapter_identity,
                "Kavita chapter identity is unavailable."
            ))

    private fun serverBase(): String = serverUrlProvider()
        ?: throw UserFacingException(auth.localizedMessage(R.string.perror_kavita_server_url, "Enter a valid Kavita server URL."))

    private fun bookmarkHttpError(code: Int) = UserFacingException(auth.localizedMessage(
        R.string.perror_kavita_bookmarks_http,
        "Unable to access Kavita EPUB bookmarks (HTTP $code).",
        code
    ))
}
