package com.vangeaux.lagrange.provider.kavita

import com.vangeaux.lagrange.*
import com.vangeaux.lagrange.core.ActiveReaderSession
import com.vangeaux.lagrange.core.ReaderModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File

internal class KavitaReaderModuleImpl(
    private val auth: KavitaAuthModule,
    context: android.content.Context
) : ReaderModule {
    private val appContext = context.applicationContext
    private val client = OkHttpClient()
    private val downloadStore = DownloadStore(appContext)
    private val downloadModule = KavitaDownloadModuleImpl(appContext, auth)

    override suspend fun loadReaderProgress(
        book: BookSummary,
        availableFiles: List<BookFileOption>
    ): BookSummary = book

    override suspend fun buildReaderState(book: BookSummary, localOnly: Boolean): ReaderState =
        withContext(Dispatchers.IO) {
            if (localOnly) {
                kavitaLocalFile(book)?.let { localFile ->
                    val localBook = book.copy(localPath = localFile.absolutePath)
                    return@withContext ReaderState(
                        book = localBook,
                        localFile = localFile,
                        pageIndex = localBook.progressPageIndex ?: 0,
                        progressPercent = localBook.progressPercent,
                        serverProgressAuthoritative = false
                    )
                }
            }
            val base = auth.serverUrl() ?: throw UserFacingException(auth.localizedMessage(R.string.perror_kavita_server_url, "Enter a valid Kavita server URL."))
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
            if (resolvedBook.mediaKind == MediaKind.EPUB) {
                val download = downloadModule.downloadBook(resolvedBook) { }
                val localFile = download.readableFile(
                    appContext,
                    resolvedBook.fileId ?: resolvedBook.id,
                    resolvedBook.format
                )
                val localBook = resolvedBook.copy(localPath = download.locator)
                return@withContext ReaderState(
                    book = localBook,
                    localFile = localFile,
                    pageIndex = 0,
                    readerPageIndex = book.progressPageIndex ?: 0,
                    progressPercent = book.progressPercent,
                    serverProgressAuthoritative = true
                )
            }
            when (resolvedBook.mediaKind) {
                MediaKind.COMIC -> {
                    val localFile = kavitaLocalFile(resolvedBook)
                        ?: downloadModule.downloadBook(resolvedBook) { }.readableFile(
                            appContext,
                            resolvedBook.fileId ?: resolvedBook.id,
                            resolvedBook.format
                        )
                    ReaderState(
                        book = resolvedBook.copy(localPath = localFile.absolutePath),
                        localFile = localFile,
                        pageIndex = 0,
                        readerPageIndex = book.progressPageIndex ?: 0,
                        progressPercent = book.progressPercent,
                        serverProgressAuthoritative = true
                    )
                }
                MediaKind.PDF -> ReaderState(
                    book = resolvedBook,
                    streamUrl = "$base/api/Reader/pdf?chapterId=$chapterId",
                    pageIndex = 0,
                    readerPageIndex = book.progressPageIndex ?: 0,
                    progressPercent = book.progressPercent,
                    serverProgressAuthoritative = true
                )
                else -> throw UserFacingException(
                    auth.localizedMessage(
                        R.string.perror_kavita_reader_unavailable,
                        "Kavita reader support is not available for ${resolvedBook.format ?: "this format"} chapters.",
                        resolvedBook.format ?: "this format"
                    ),
                    UserFacingReason.CAPABILITY_UNAVAILABLE
                )
            }
        }

    private suspend fun kavitaLocalFile(book: BookSummary): File? {
        val record = downloadStore.find(auth.serverUrl().orEmpty(), book.fileId ?: book.id)
            ?.takeIf { it.status == DownloadRecordStatus.COMPLETE }
        return listOfNotNull(record?.localPath, book.localPath)
            .asSequence()
            .mapNotNull { locator ->
                if (locator.isContentLocator()) {
                    runCatching {
                        LocalDownloadAccess(appContext).materialize(
                            locator,
                            book.fileId ?: book.id,
                            book.filename ?: book.format
                        )
                    }.getOrNull()
                } else {
                    File(locator).takeIf(File::isFile)
                }
            }
            .firstOrNull()
    }

    override suspend fun prepareEpubImageLibrarySource(
        book: BookSummary,
        allowRemoteCache: Boolean
    ): EpubImageLibrarySourceResult = withContext(Dispatchers.IO) {
        val localFile = kavitaLocalFile(book)
        if (localFile != null) return@withContext EpubImageLibrarySourceResult.Ready(localFile)
        if (!allowRemoteCache) return@withContext EpubImageLibrarySourceResult.RemoteConsentRequired
        runCatching {
            EpubImageLibrarySourceResult.Ready(
                downloadModule.downloadBook(book) { }.readableFile(
                    appContext,
                    book.fileId ?: book.id,
                    book.format
                )
            )
        }.getOrElse { error ->
            EpubImageLibrarySourceResult.Error(
                error.message ?: "Unable to prepare the Kavita EPUB image library."
            )
        }
    }

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
                throw UserFacingException(auth.localizedMessage(R.string.perror_kavita_reader_info_http, "Unable to load Kavita reader information (HTTP ${response.code}).", response.code))
            }
            return JSONObject(payload)
        }
    }
}
