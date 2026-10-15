package com.vangeaux.lagrange.provider.kavita

import com.vangeaux.lagrange.*
import com.vangeaux.lagrange.core.ActiveReaderSession
import com.vangeaux.lagrange.core.ReaderModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

internal class KavitaReaderModuleImpl(private val auth: KavitaAuthModule) : ReaderModule {
    private val client = OkHttpClient()

    override suspend fun loadReaderProgress(
        book: BookSummary,
        availableFiles: List<BookFileOption>
    ): BookSummary = book

    override suspend fun buildReaderState(book: BookSummary, localOnly: Boolean): ReaderState =
        withContext(Dispatchers.IO) {
            if (localOnly) {
                throw UserFacingException("This Kavita chapter is not available for offline reading.")
            }
            val base = auth.serverUrl() ?: throw UserFacingException("Enter a valid Kavita server URL.")
            val chapterId = book.id.removePrefix("chapter:").substringBefore(':').ifBlank {
                book.fileId ?: book.id.substringAfterLast(':')
            }
            val info = loadChapterInfo(base, chapterId)
            val format = kavitaFormat(
                info.optString("seriesFormat").takeIf { it.isNotBlank() },
                book.filename
            ) ?: book.format
            val resolvedBook = book.copy(
                title = info.optString("chapterTitle").takeIf { it.isNotBlank() }
                    ?: info.optString("title").takeIf { it.isNotBlank() }
                    ?: book.title,
                format = format,
                mediaKind = kavitaMediaKind(format),
                seriesId = info.optInt("seriesId", -1).takeIf { it >= 0 }?.toString() ?: book.seriesId,
                seriesName = info.optString("seriesName").takeIf { it.isNotBlank() } ?: book.seriesName,
                readerPageCount = info.optInt("pages", 0).takeIf { it > 0 } ?: book.readerPageCount
            )
            when (resolvedBook.mediaKind) {
                MediaKind.COMIC -> ReaderState(
                    book = resolvedBook,
                    comicPagesUrl = "$base/api/Reader/image?chapterId=$chapterId&page={page}",
                    pageIndex = 0,
                    readerPageIndex = book.progressPageIndex ?: 0,
                    progressPercent = book.progressPercent,
                    serverProgressAuthoritative = true
                )
                MediaKind.PDF -> ReaderState(
                    book = resolvedBook,
                    streamUrl = "$base/api/Reader/pdf?chapterId=$chapterId",
                    pageIndex = 0,
                    readerPageIndex = book.progressPageIndex ?: 0,
                    progressPercent = book.progressPercent,
                    serverProgressAuthoritative = true
                )
                else -> throw UserFacingException("Kavita reader support is not available for ${resolvedBook.format ?: "this format"} chapters.")
            }
        }

    override suspend fun prepareEpubImageLibrarySource(
        book: BookSummary,
        allowRemoteCache: Boolean
    ): EpubImageLibrarySourceResult = EpubImageLibrarySourceResult.Unavailable(
        "Kavita does not expose an EPUB reader endpoint through this provider module yet."
    )

    override suspend fun restoreActiveReaderState(
        session: ActiveReaderSession,
        localOnly: Boolean
    ): ReaderState? = null

    private fun loadChapterInfo(base: String, chapterId: String): JSONObject {
        val request = Request.Builder()
            .url("$base/api/Reader/chapter-info?chapterId=$chapterId&includeDimensions=false")
            .apply { auth.requestHeaders().forEach { (name, value) -> header(name, value) } }
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw UserFacingException("Unable to load Kavita reader information (HTTP ${response.code}).")
            }
            return JSONObject(payload)
        }
    }
}
