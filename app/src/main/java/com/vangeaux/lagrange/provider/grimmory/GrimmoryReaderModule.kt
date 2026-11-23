package com.vangeaux.lagrange.provider.grimmory

import com.vangeaux.lagrange.*
import com.vangeaux.lagrange.core.ActiveReaderSession
import com.vangeaux.lagrange.core.ReaderModule
import okhttp3.OkHttpClient
import java.io.File

internal fun grimmoryEpubReaderState(book: BookSummary, localFile: File): ReaderState = ReaderState(
    book = book,
    localFile = localFile,
    streamUrl = null,
    progressPercent = book.progressPercent,
    serverProgressAuthoritative = book.serverProgressPercent != null,
    initialLocatorJson = book.readerLocatorJson
)

internal class GrimmoryReaderModule(
    private val serverUrlProvider: suspend () -> String?,
    private val scopeResolver: suspend (String) -> EpubReaderScope?,
    private val storageScopeIdProvider: (EpubReaderScope) -> String?,
    private val restoreLocalReader: suspend (ActiveReaderSession) -> ReaderState?,
    private val contentCache: GrimmoryEpubReaderContentCache,
    private val progressModule: GrimmoryProgressModule? = null,
    private val comicPageNumbersProvider: suspend (String) -> List<Int> = { emptyList() }
) : ReaderModule {
    constructor(repository: GrimmoryRepository, progressModule: GrimmoryProgressModule? = null) : this(
        serverUrlProvider = { repository.getServerUrl() },
        scopeResolver = { serverUrl -> resolveEpubReaderScope(repository.appContext, serverUrl) },
        storageScopeIdProvider = { scope ->
            downloadStorageScopeId(repository.appContext, scope.serverUrl, scope.profileId)
        },
        restoreLocalReader = { session -> repository.restoreActiveReaderState(session, localOnly = true) },
        contentCache = GrimmoryEpubReaderContentCache(
            cacheDirectory = File(repository.appContext.cacheDir, "grimmory-epub-reader-cache"),
            transport = GrimmoryEpubReaderContentTransport(OkHttpClient(), repository.auth)
        ),
        progressModule = progressModule,
        comicPageNumbersProvider = { bookId ->
            parseGrimmoryCbxPageNumbers(repository.nativeJsonArray(grimmoryCbxPageListPath(bookId)))
        }
    )

    override suspend fun loadReaderProgress(
        book: BookSummary,
        availableFiles: List<BookFileOption>
    ): BookSummary = progressModule?.loadReaderProgress(book, availableFiles) ?: book

    override suspend fun buildReaderState(book: BookSummary, localOnly: Boolean): ReaderState {
        val localFile = localFile(book)
        if (book.mediaKind == MediaKind.EPUB) {
            val readableFile = localFile?.takeIf { ReaderFileValidator.isReadable(MediaKind.EPUB, it) }
                ?: run {
                    val base = normalizeServerUrl(serverUrlProvider().orEmpty())
                        ?: throw UserFacingException("Enter a valid Grimmory server URL.")
                    val scope = requireGrimmoryScope(base)
                    if (localOnly) {
                        contentCache.find(scope, book)
                            ?: throw UserFacingException("This Grimmory EPUB is not available for offline reading.")
                    } else {
                        contentCache.prepare(scope, book)
                    }
                }
            return grimmoryEpubReaderState(book, readableFile)
        }
        if (localOnly && localFile == null) {
            throw UserFacingException(
                "This Grimmory book is not downloaded for offline reading.",
                UserFacingReason.LOCAL_READER_UNAVAILABLE
            )
        }
        if (book.mediaKind == MediaKind.COMIC) {
            if (localFile != null) {
                return ReaderState(
                    book = book,
                    localFile = localFile,
                    pageIndex = book.progressPageIndex ?: 0,
                    readerPageIndex = book.readerPageIndex ?: book.progressPageIndex ?: 0,
                    progressPercent = book.progressPercent,
                    serverProgressAuthoritative = book.serverProgressPercent != null
                )
            }
            val base = normalizeServerUrl(serverUrlProvider().orEmpty())
                ?: throw UserFacingException("Enter a valid Grimmory server URL.")
            requireGrimmoryScope(base)
            val id = book.id.toLongOrNull()?.takeIf { it >= 0L }?.toString()
                ?: throw UserFacingException("Grimmory book identity is unavailable.")
            val pageCount = comicPageNumbersProvider(id).size
            if (pageCount <= 0) {
                throw UserFacingException("Grimmory did not return any pages for this comic.")
            }
            return ReaderState(
                book = book,
                comicPagesUrl = grimmoryApiUrl(base, grimmoryCbxPageImageTemplatePath(id)),
                comicPageCount = pageCount,
                pageIndex = book.progressPageIndex ?: 0,
                readerPageIndex = book.readerPageIndex ?: book.progressPageIndex ?: 0,
                progressPercent = book.progressPercent,
                serverProgressAuthoritative = book.serverProgressPercent != null
            )
        }
        val streamUrl = if (localFile == null) {
            book.streamUrl ?: "${serverUrlProvider()?.trimEnd('/')}/api/v1/books/${book.id}/content"
        } else null
        return ReaderState(
            book = book,
            localFile = localFile,
            streamUrl = streamUrl,
            pageIndex = book.progressPageIndex ?: 0,
            readerPageIndex = book.readerPageIndex ?: book.progressPageIndex ?: 0,
            progressPercent = book.progressPercent,
            lastKnownPosition = if (book.mediaKind == MediaKind.AUDIO) {
                book.audioProgressPosition?.absolutePositionMs ?: book.progressPositionMs ?: 0L
            } else 0L,
            serverProgressAuthoritative = book.serverProgressPercent != null
        )
    }

    override suspend fun prepareEpubImageLibrarySource(
        book: BookSummary,
        allowRemoteCache: Boolean
    ): EpubImageLibrarySourceResult {
        val localFile = localFile(book)
        return if (localFile != null) EpubImageLibrarySourceResult.Ready(localFile)
        else if (allowRemoteCache) {
            EpubImageLibrarySourceResult.Unavailable("Download this EPUB before scanning its images.")
        } else {
            EpubImageLibrarySourceResult.RemoteConsentRequired
        }
    }

    override suspend fun restoreActiveReaderState(
        session: ActiveReaderSession,
        localOnly: Boolean
    ): ReaderState? {
        val scope = scopeResolver(session.serverUrl) ?: return null
        if (
            session.profileId != scope.profileId ||
            session.providerId != PROVIDER_GRIMMORY ||
            session.providerId != scope.providerId ||
            session.accountScope != scope.accountScope ||
            session.storageScopeId != storageScopeIdProvider(scope)
        ) return null

        val directFile = localFile(session.book)
        if (session.book.mediaKind == MediaKind.EPUB) {
            val cachedFile = contentCache.find(scope, session.book)
            val sharedLocalFile = if (directFile == null && cachedFile == null && localOnly) {
                restoreLocalReader(session)
                    ?.localFile
                    ?.takeIf { it.isFile && ReaderFileValidator.isReadable(MediaKind.EPUB, it) }
            } else null
            val readableFile = directFile?.takeIf { ReaderFileValidator.isReadable(MediaKind.EPUB, it) }
                ?: cachedFile
                ?: sharedLocalFile
                ?: if (localOnly) null else contentCache.prepare(scope, session.book)
            return readableFile?.let { grimmoryEpubReaderState(session.book, it) }
        }

        if (localOnly) return restoreLocalReader(session)
        if (session.book.mediaKind == MediaKind.COMIC && directFile == null) {
            return buildReaderState(session.book, localOnly = false).copy(launchMode = session.launchMode)
        }
        return grimmoryRestoredReaderState(session, scope.serverUrl, directFile)
    }

    private suspend fun requireGrimmoryScope(serverUrl: String): EpubReaderScope {
        val scope = scopeResolver(serverUrl)
            ?: throw AuthenticationRequiredException()
        if (scope.providerId != PROVIDER_GRIMMORY) throw AuthenticationRequiredException()
        return scope
    }

    private fun localFile(book: BookSummary): File? = book.localPath
        ?.takeUnless(String::isContentLocator)
        ?.let(::File)
        ?.takeIf(File::isFile)
}
