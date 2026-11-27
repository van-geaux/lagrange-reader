package com.vangeaux.lagrange.provider.opds

import com.vangeaux.lagrange.BookBrowseFilter
import com.vangeaux.lagrange.BookDetailInfo
import com.vangeaux.lagrange.BookFileOption
import com.vangeaux.lagrange.BookSummary
import com.vangeaux.lagrange.CatalogNavigationEntry
import com.vangeaux.lagrange.HomeSection
import com.vangeaux.lagrange.LibraryBooksPage
import com.vangeaux.lagrange.LibrarySummary
import com.vangeaux.lagrange.MediaKind
import com.vangeaux.lagrange.core.BookCatalogModule
import com.vangeaux.lagrange.core.BookCoverModule
import com.vangeaux.lagrange.core.BookDetailModule
import com.vangeaux.lagrange.core.LibraryModule
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.URI
import java.time.Instant
import java.util.Locale

internal const val OPDS_ROOT_LIBRARY_ID = "opds-root"

/** OPDS 1 catalog projection; server continuation links, not guessed page paths, drive paging. */
internal class OpdsCatalogModule(
    private val feedUrl: suspend () -> String?,
    private val client: OpdsFeedClient = OpdsFeedClient(),
    private val legacyCwa: Boolean = false,
    private val legacySelectedLibraryId: suspend () -> String? = { null }
) : BookCatalogModule, LibraryModule, BookCoverModule, BookDetailModule {
    private data class CachedPage(val result: LibraryBooksPage, val nextUrl: String?)
    private val mutex = Mutex()
    private val pagesByTarget = mutableMapOf<String, MutableList<CachedPage>>()
    private val publicationsByBook = mutableMapOf<Pair<String, String>, OpdsEntry.Publication>()
    private val feedTitles = mutableMapOf<String, String>()

    override suspend fun loadLibraries(): List<LibrarySummary> {
        val root = configuredFeedUrl()
        val page = loadPage(root, root, 0)
        val title = page.title ?: root
        if (!legacyCwa) {
            feedTitles[root] = title
            return listOf(LibrarySummary(OPDS_ROOT_LIBRARY_ID, title))
        }
        val saved = legacySelectedLibraryId()?.let { client.resolveLink(root, it, root) }
        val allBooks = page.navigationEntries.firstOrNull { it.title.contains("all books", ignoreCase = true) }
            ?: page.navigationEntries.singleOrNull()
        val target = saved ?: allBooks?.targetId
            ?: if (page.items.isNotEmpty()) root
            else "${root.substringBefore('?').trimEnd('/')}/books/letter/00"
        val targetTitle = allBooks?.title ?: page.title ?: "All books"
        feedTitles[target] = targetTitle
        return listOf(LibrarySummary(target, targetTitle))
    }

    override suspend fun loadBooks(libraryId: String): List<BookSummary> {
        val all = mutableListOf<BookSummary>()
        var page = 0
        while (true) {
            val result = loadBooksPage(libraryId, page)
            all += result.items
            if (result.isComplete) break
            page++
        }
        return all.distinctBy { it.id to it.fileId }
    }

    override suspend fun loadBooksPage(
        libraryId: String,
        page: Int,
        filter: BookBrowseFilter
    ): LibraryBooksPage {
        require(page >= 0) { "OPDS page must not be negative." }
        val root = configuredFeedUrl()
        val target = if (libraryId == OPDS_ROOT_LIBRARY_ID) root else {
            client.resolveLink(root, libraryId, root)
                ?: throw IllegalArgumentException("The OPDS navigation target is outside the configured origin.")
        }
        return loadPage(target, root, page)
    }

    override suspend fun loadRecentBooksPage(libraryId: String, section: HomeSection, page: Int) =
        LibraryBooksPage(page = page, isComplete = true)

    override suspend fun loadCachedLibraryCatalog(libraryId: String): LibraryBooksPage? = null

    override suspend fun refreshLibraryCatalog(libraryId: String, firstPage: LibraryBooksPage?): LibraryBooksPage {
        val root = configuredFeedUrl()
        val target = if (libraryId == OPDS_ROOT_LIBRARY_ID) root else {
            client.resolveLink(root, libraryId, root)
                ?: throw IllegalArgumentException("The OPDS navigation target is outside the configured origin.")
        }
        mutex.withLock { pagesByTarget.remove(target) }
        return loadPage(target, root, 0)
    }

    override suspend fun searchBooks(query: String): List<BookSummary> = emptyList()

    override suspend fun loadBookCover(book: BookSummary): ByteArray? =
        book.coverUrl?.let { loadCatalogImage(it) }

    override suspend fun loadCatalogImage(url: String): ByteArray? {
        val root = configuredFeedUrl()
        val safeUrl = client.resolveLink(root, url, root) ?: return null
        return client.fetchImage(safeUrl, root)
    }

    override suspend fun loadBookDetail(book: BookSummary): BookDetailInfo? {
        val root = configuredFeedUrl()
        val publication = mutex.withLock { publicationsByBook[root to book.id] } ?: return BookDetailInfo(book)
        val options = publication.acquisitions.mapIndexedNotNull { index, acquisition ->
            val detailBook = publication.toBook(acquisition, legacyCwa, book.libraryId)
            if (legacyCwa && detailBook.mediaKind == MediaKind.AUDIO) return@mapIndexedNotNull null
            BookFileOption(
                book = detailBook,
                filename = detailBook.filename,
                sizeBytes = acquisition.lengthBytes,
                role = if (index == 0) "primary" else "alternate"
            )
        }
        val selected = options.firstOrNull { it.book.fileId == book.fileId }?.book
            ?: options.firstOrNull()?.book
            ?: book
        return BookDetailInfo(
            book = selected,
            libraryName = feedTitles[root] ?: feedTitles[book.libraryId],
            publisher = publication.publisher,
            synopsis = publication.summary,
            publishedDate = publication.updated,
            language = publication.language,
            genres = publication.categories,
            fileCount = options.size,
            availableFiles = options,
            totalSizeBytes = options.mapNotNull { it.sizeBytes }.takeIf { it.isNotEmpty() }?.sum()
        )
    }

    override suspend fun loadCachedBookDetail(book: BookSummary): BookDetailInfo? = loadBookDetail(book)

    override suspend fun setBookUserRating(book: BookSummary, rating: Int?): BookDetailInfo =
        throw UnsupportedOperationException("OPDS feeds do not define user-rating mutations.")

    private suspend fun loadPage(target: String, root: String, page: Int): LibraryBooksPage = mutex.withLock {
        val cache = pagesByTarget.getOrPut(target) { mutableListOf() }
        while (cache.size <= page) {
            val requestUrl = if (cache.isEmpty()) target else cache.last().nextUrl ?: break
            val response = client.fetchFeed(requestUrl, root)
            val feed = OpdsAtomParser.parse(response.xml, response.url, root)
            val publications = feed.entries.filterIsInstance<OpdsEntry.Publication>()
            val items = publications.mapNotNull { publication ->
                val acquisition = publication.acquisitions.firstOrNull { candidate ->
                    !legacyCwa || inferMediaKind(acquisitionFormat(candidate.mediaType, candidate.href), filenameFromUrl(candidate.href)) != MediaKind.AUDIO
                } ?: return@mapNotNull null
                publication.toBook(acquisition, legacyCwa, target).also { book ->
                    publicationsByBook[root to book.id] = publication
                }
            }
            val navigation = feed.entries.filterIsInstance<OpdsEntry.Navigation>()
                .map { CatalogNavigationEntry(it.id, it.title, it.href) }
            val title = feed.title
            val loaded = LibraryBooksPage(
                items = items,
                total = feed.totalResults,
                page = cache.size,
                size = items.size,
                isComplete = feed.nextUrl == null,
                title = title,
                navigationEntries = navigation
            )
            cache += CachedPage(loaded, feed.nextUrl)
            title?.let { feedTitles[target] = it }
        }
        cache.getOrNull(page)?.result ?: LibraryBooksPage(
            page = page,
            isComplete = cache.lastOrNull()?.nextUrl == null,
            title = cache.lastOrNull()?.result?.title
        )
    }

    private suspend fun configuredFeedUrl(): String = feedUrl()?.trim()?.takeIf(String::isNotEmpty)
        ?: throw IllegalStateException("No OPDS feed URL is configured.")

    private fun OpdsEntry.Publication.toBook(
        acquisition: OpdsAcquisition,
        useLegacyCwaIdentity: Boolean = legacyCwa,
        libraryId: String = OPDS_ROOT_LIBRARY_ID
    ): BookSummary {
        val format = acquisitionFormat(acquisition.mediaType, acquisition.href)
        val filename = if (useLegacyCwaIdentity) "$title.$format" else filenameFromUrl(acquisition.href)
        return BookSummary(
            libraryId = libraryId,
            id = id,
            fileId = if (useLegacyCwaIdentity) "$id:${acquisition.href}" else acquisition.href,
            title = title,
            filename = filename,
            author = author,
            format = format,
            mediaKind = inferMediaKind(format, filename),
            downloadUrl = acquisition.href,
            streamUrl = acquisition.href,
            coverUrl = coverUrl,
            updatedAtMillis = updated?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() },
            genres = categories,
            availableFormats = acquisitions.mapNotNull { candidate ->
                val candidateFormat = acquisitionFormat(candidate.mediaType, candidate.href)
                if (useLegacyCwaIdentity && inferMediaKind(candidateFormat, filenameFromUrl(candidate.href)) == MediaKind.AUDIO) null
                else candidateFormat
            }.distinct()
        )
    }

    private fun acquisitionFormat(mediaType: String?, url: String): String {
        val mime = mediaType.orEmpty().substringBefore(';').trim().lowercase(Locale.ROOT)
        when {
            mime.contains("epub") -> return "epub"
            mime == "application/pdf" -> return "pdf"
            mime.contains("comicbook-rar") || mime.contains("x-rar") -> return "cbr"
            mime.contains("comicbook") || mime == "application/zip" -> return "cbz"
            mime.contains("mobi") -> return "mobi"
            mime.startsWith("audio/") -> return mime.substringAfter('/').substringBefore('+')
        }
        return filenameFromUrl(url).substringAfterLast('.', "").takeIf(String::isNotBlank)?.lowercase(Locale.ROOT)
            ?: mime.takeIf(String::isNotBlank)
            ?: "book"
    }

    private fun inferMediaKind(format: String?, filename: String): MediaKind {
        val token = (format?.substringAfterLast('/') ?: filename.substringAfterLast('.', ""))
            .substringBefore(';').trim().lowercase(Locale.ROOT)
        return when (token) {
            "epub", "epub+zip" -> MediaKind.EPUB
            "pdf" -> MediaKind.PDF
            "cbz", "cbr", "cb7" -> MediaKind.COMIC
            "mp3", "m4a", "m4b", "opus", "mpeg", "ogg" -> MediaKind.AUDIO
            else -> MediaKind.UNKNOWN
        }
    }

    private fun filenameFromUrl(value: String): String = runCatching {
        URI(value).path.substringAfterLast('/').ifBlank { "publication" }
    }.getOrDefault("publication")
}
