package com.vangeaux.lagrange.provider.kavita

import com.vangeaux.lagrange.*
import com.vangeaux.lagrange.core.BookCoverModule
import com.vangeaux.lagrange.core.BookDetailModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.Instant

internal class KavitaBookDetailModuleImpl(private val auth: KavitaAuthModule) : BookDetailModule {
    private val client = OkHttpClient()

    override suspend fun loadBookDetail(book: BookSummary): BookDetailInfo = withContext(Dispatchers.IO) {
        val base = auth.serverUrl() ?: throw UserFacingException(auth.localizedMessage(R.string.perror_kavita_server_url, "Enter a valid Kavita server URL."))
        val chapterId = book.id.removePrefix("chapter:").substringBefore(':').ifBlank {
            book.fileId ?: book.id.substringAfterLast(':')
        }
        val request = Request.Builder()
            .url("$base/api/Series/chapter?chapterId=$chapterId")
            .apply { auth.requestHeaders().forEach { (name, value) -> header(name, value) } }
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw UserFacingException(auth.localizedMessage(R.string.perror_kavita_chapter_details_http, "Unable to load Kavita chapter details (HTTP ${response.code}).", response.code))
            }
            val chapter = JSONObject(payload)
            val projection = kavitaChapterProjection(chapter)
                ?: throw UserFacingException(auth.localizedMessage(R.string.perror_kavita_invalid_chapter, "Kavita returned an invalid chapter response."))
            val resolvedBook = book.copy(
                id = book.id,
                fileId = projection.fileId,
                title = projection.title,
                filename = projection.filename ?: book.filename,
                author = projection.author ?: book.author,
                format = projection.format ?: book.format,
                mediaKind = kavitaMediaKind(projection.format ?: book.format),
                coverUrl = "$base/api/Image/chapter-cover?chapterId=${projection.id}",
                progressPageIndex = projection.pagesRead.takeIf { it > 0 },
                progressPercent = projection.pages?.let { pages ->
                    (projection.pagesRead.toFloat() / pages.toFloat() * 100f).coerceIn(0f, 100f)
                } ?: book.progressPercent,
                readerPageCount = projection.pages ?: book.readerPageCount,
                readStatus = when {
                    projection.pages != null && projection.pagesRead >= projection.pages -> BookReadStatus.READ
                    projection.pagesRead > 0 -> BookReadStatus.READING
                    else -> BookReadStatus.UNREAD
                },
                isRead = projection.pages != null && projection.pagesRead >= projection.pages,
                genres = projection.genres
            )
            val files = chapter.optJSONArray("files")
            val options = buildList {
                for (index in 0 until (files?.length() ?: 0)) {
                    val file = files?.optJSONObject(index) ?: continue
                    val fileFormat = kavitaFormat(
                        file.optString("format").takeIf { it.isNotBlank() },
                        file.optString("extension").takeIf { it.isNotBlank() }
                    ) ?: projection.format
                    add(
                        BookFileOption(
                            book = resolvedBook.copy(
                                fileId = projection.id,
                                filename = file.optString("filePath")
                                    .takeIf { it.isNotBlank() }
                                    ?.substringAfterLast('/')
                                    ?: resolvedBook.filename,
                                format = fileFormat,
                                mediaKind = kavitaMediaKind(fileFormat)
                            ),
                            filename = file.optString("filePath")
                                .takeIf { it.isNotBlank() }
                                ?.substringAfterLast('/'),
                            sizeBytes = file.optLong("bytes", 0L).takeIf { it > 0L },
                            role = "PRIMARY"
                        )
                    )
                }
            }.ifEmpty {
                listOf(BookFileOption(book = resolvedBook, filename = resolvedBook.filename, role = "PRIMARY"))
            }
            BookDetailInfo(
                book = resolvedBook,
                synopsis = chapter.optString("summary").takeIf { it.isNotBlank() },
                publisher = chapter.optJSONArray("publishers")?.optJSONObject(0)?.optString("name")
                    ?.takeIf { it.isNotBlank() },
                publishedDate = chapter.optString("releaseDate").takeIf { it.isNotBlank() },
                language = chapter.optString("language").takeIf { it.isNotBlank() },
                pageCount = projection.pages,
                isbn13 = chapter.optString("isbn").takeIf { it.isNotBlank() },
                genres = projection.genres,
                tags = chapter.optJSONArray("tags").jsonTitles(),
                fileCount = options.size,
                availableFiles = options,
                totalSizeBytes = options.mapNotNull { it.sizeBytes }.takeIf { it.size == options.size }?.sum()
            )
        }
    }

    override suspend fun loadCachedBookDetail(book: BookSummary): BookDetailInfo? = null

    override suspend fun setBookUserRating(book: BookSummary, rating: Int?): BookDetailInfo =
        throw UserFacingException(auth.localizedMessage(R.string.perror_kavita_ratings_unimplemented, "Kavita book ratings are not implemented yet."))
}

internal class KavitaCoverModuleImpl(private val auth: KavitaAuthModule) : BookCoverModule {
    private val client = OkHttpClient()

    override suspend fun loadBookCover(book: BookSummary): ByteArray? = withContext(Dispatchers.IO) {
        loadImage(book.coverUrl ?: return@withContext null)
    }

    override suspend fun loadCatalogImage(url: String): ByteArray? = withContext(Dispatchers.IO) {
        loadImage(url)
    }

    private fun loadImage(url: String): ByteArray? {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "image/*")
            .apply { auth.requestHeaders().forEach { (name, value) -> header(name, value) } }
            .get()
            .build()
        return client.newCall(request).execute().use { response ->
            if (response.isSuccessful) response.body?.bytes() else null
        }
    }
}
