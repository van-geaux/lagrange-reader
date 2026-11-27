package com.vangeaux.lagrange.provider.opds

import android.content.Context
import com.vangeaux.lagrange.*
import com.vangeaux.lagrange.core.DownloadModule
import com.vangeaux.lagrange.core.ProviderFeatureAvailability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Generic OPDS 1 provider. The configured URL is the feed itself, not a guessed server root. */
internal class OpdsRepository private constructor(
    private val context: Context,
    private val baseRepository: BookOrbitRepository,
    initialFeedUrl: String?,
    private val legacyCwaProfileId: String?,
    internal val legacyCwa: Boolean
) : BookOrbitDataSource by baseRepository {
    constructor(
        context: Context,
        initialFeedUrl: String? = ServerProfileStore(context).active()
            ?.takeIf { it.providerId == PROVIDER_OPDS }?.serverUrl,
        legacyCwaProfileId: String? = null,
        legacyCwa: Boolean = legacyCwaProfileId != null
    ) : this(context, BookOrbitRepository(context), initialFeedUrl, legacyCwaProfileId, legacyCwa)

    @Volatile private var configuredFeedUrl: String? = initialFeedUrl?.trim()?.takeIf(String::isNotEmpty)
    private val profileSessions = ProfileSessionStore(context)
    private val localProgress = LegacyCwaLocalProgressStore(context)
    private val client = OpdsFeedClient(authorization = { currentAuthorization() })
    internal val catalogModule = OpdsCatalogModule(
        feedUrl = { requestFeedUrl() },
        client = client,
        legacyCwa = legacyCwa,
        legacySelectedLibraryId = { currentLegacyProfile()?.selectedLibraryId ?: baseRepository.getSelectedLibraryId() }
    )
    internal val downloadModule: DownloadModule = object : DownloadModule {
        override val handlesDownloadsDirectly: Boolean = false
        override suspend fun downloadBook(book: BookSummary, storageScopeId: String?, onProgress: (Float?) -> Unit) =
            downloadOpdsBook(book, storageScopeId, onProgress)
        override suspend fun loadAudiobookDownloadFiles(book: BookSummary) = when {
            book.mediaKind != MediaKind.AUDIO -> emptyList()
            legacyCwa -> throw UserFacingException(
                "Calibre-Web Automated audiobook downloads are not supported.",
                UserFacingReason.CAPABILITY_UNAVAILABLE
            )
            else -> listOf(book)
        }
        override suspend fun deleteLocalCopy(book: BookSummary) {
            deleteOpdsLocalCopy(book)
        }
        override suspend fun deleteLocalCopies(book: BookSummary) = setOfNotNull(book.fileId).filterTo(linkedSetOf()) {
            deleteOpdsLocalCopy(book.copy(fileId = it))
        }
    }

    override suspend fun getServerUrl(): String? = configuredFeedUrl

    override suspend fun setServerUrl(serverUrl: String) {
        configuredFeedUrl = serverUrl.trim().takeIf(String::isNotEmpty)
        if (legacyCwa) baseRepository.setServerUrl(serverUrl)
    }

    override suspend fun clearServer() {
        if (legacyCwa) baseRepository.clearServer()
        configuredFeedUrl = null
    }

    override suspend fun clearSession() {
        currentProfileId()?.let(profileSessions::clear)
    }

    override suspend fun getSessionState(): SessionState {
        val feed = requestFeedUrl() ?: return SessionState.Unavailable
        return try {
            val response = client.fetchFeed(feed, feed)
            OpdsAtomParser.parse(response.xml, response.url, feed)
            SessionState.Authenticated
        } catch (error: OpdsHttpStatusException) {
            when (error.statusCode) {
                401 -> {
                    clearSession()
                    SessionState.Unauthenticated
                }
                403 -> SessionState.Unauthenticated
                else -> SessionState.Unavailable
            }
        } catch (_: IOException) {
            SessionState.Unavailable
        }
    }

    override suspend fun login(username: String, password: String) {
        val providerLabel = if (legacyCwa) "Calibre-Web Automated" else "OPDS"
        if (username.isBlank()) throw UserFacingException("Enter your $providerLabel username.")
        if (password.isBlank()) throw UserFacingException("Enter your $providerLabel password.")
        val feed = requestFeedUrl() ?: throw UserFacingException("Enter a valid $providerLabel feed URL.")
        val profileId = currentProfileId()
            ?: throw UserFacingException("Save this $providerLabel profile before signing in.")
        try {
            val response = client.fetchFeed(feed, feed, opdsBasicAuthorization(username.trim(), password))
            OpdsAtomParser.parse(response.xml, response.url, feed)
        } catch (error: OpdsHttpStatusException) {
            when (error.statusCode) {
                401 -> {
                    clearSession()
                    if (legacyCwa) {
                        throw UserFacingException("The Calibre-Web Automated username or password was rejected.")
                    } else {
                        throw InvalidCredentialsException()
                    }
                }
                403 -> throw UserFacingException(
                    if (legacyCwa) "Calibre-Web Automated denied access to this OPDS feed."
                    else "OPDS access is forbidden. Check that the catalog is enabled and the account has access."
                )
                else -> throw error
            }
        }
        profileSessions.write(
            profileId,
            org.json.JSONObject().put("username", username.trim()).put("password", password).toString()
        )
    }

    internal suspend fun saveCurrentProfileSession(): Boolean =
        currentProfileId()?.let { !profileSessions.read(it).isNullOrBlank() } == true

    internal suspend fun restoreCurrentProfileSession(): Boolean =
        getSessionState() == SessionState.Authenticated

    internal fun currentCredentials(): Pair<String, String>? {
        val profileId = currentProfileId() ?: return null
        val raw = profileSessions.read(profileId) ?: return null
        return runCatching {
            val json = org.json.JSONObject(raw)
            val username = json.optString("username").takeIf(String::isNotBlank) ?: return null
            username to json.optString("password")
        }.getOrNull()
    }

    private fun currentAuthorization(): String? {
        val profile = if (legacyCwa) currentLegacyProfile() else currentOpdsProfile()
        if (profile == null || ServerProfileStore(context).activeId() != profile.id) return null
        return currentCredentials()?.let { (username, password) -> opdsBasicAuthorization(username, password) }
    }

    private fun currentOpdsProfile() = if (legacyCwa) null else {
        ServerProfileStore(context).active()?.takeIf {
            it.providerId == PROVIDER_OPDS && it.serverUrl == configuredFeedUrl
        }
    }

    private fun currentLegacyProfile() = if (!legacyCwa) null else {
        val profiles = ServerProfileStore(context)
        profiles.readAll().firstOrNull { it.id == legacyCwaProfileId && it.providerId == PROVIDER_CALIBRE_WEB_AUTOMATED }
            ?: profiles.active()?.takeIf {
                it.providerId == PROVIDER_CALIBRE_WEB_AUTOMATED && serverUrlsMatch(it.serverUrl, configuredFeedUrl.orEmpty())
            }
            ?: profiles.readAll().firstOrNull {
                it.providerId == PROVIDER_CALIBRE_WEB_AUTOMATED && serverUrlsMatch(it.serverUrl, configuredFeedUrl.orEmpty())
            }
    }

    private fun currentLegacyProfileId(): String? = currentLegacyProfile()?.id

    private fun currentProfileId(): String? =
        if (legacyCwa) currentLegacyProfileId() else currentOpdsProfile()?.id

    private fun requestFeedUrl(): String? {
        val configured = configuredFeedUrl ?: return null
        return if (legacyCwa) legacyCwaOpdsFeedUrl(configured) else configured
    }

    override suspend fun canReachServer(serverUrl: String): Boolean = checkServer(serverUrl) == ServerCheckResult.Reachable

    override suspend fun checkServer(serverUrl: String): ServerCheckResult = withContext(Dispatchers.IO) {
        val feed = runCatching {
            if (legacyCwa) legacyCwaOpdsFeedUrl(serverUrl.trim()) else serverUrl.trim()
        }.getOrElse { return@withContext ServerCheckResult.MalformedUrl }
        runCatching {
            val response = client.fetchFeed(feed, feed)
            OpdsAtomParser.parse(response.xml, response.url, feed)
        }.fold(
            onSuccess = { ServerCheckResult.Reachable },
            onFailure = { error ->
                when {
                    error is OpdsHttpStatusException && error.statusCode in setOf(401, 403) -> ServerCheckResult.Reachable
                    error is java.net.SocketTimeoutException -> ServerCheckResult.Timeout
                    error is javax.net.ssl.SSLException -> ServerCheckResult.TlsFailure
                    error is IOException -> ServerCheckResult.HttpFailure
                    else -> ServerCheckResult.MalformedUrl
                }
            }
        )
    }

    override suspend fun loadLibraries(): List<LibrarySummary> = catalogModule.loadLibraries()
    override suspend fun loadBooks(libraryId: String): List<BookSummary> = catalogModule.loadBooks(libraryId)
    override suspend fun loadBooksPage(libraryId: String, page: Int): LibraryBooksPage =
        catalogModule.loadBooksPage(libraryId, page)
    override suspend fun loadBooksPage(libraryId: String, page: Int, filter: BookBrowseFilter): LibraryBooksPage =
        catalogModule.loadBooksPage(libraryId, page, filter)
    override suspend fun loadRecentBooksPage(libraryId: String, section: HomeSection, page: Int): LibraryBooksPage =
        catalogModule.loadRecentBooksPage(libraryId, section, page)
    override suspend fun loadCachedLibraryCatalog(libraryId: String): LibraryBooksPage? = null
    override suspend fun refreshLibraryCatalog(libraryId: String, firstPage: LibraryBooksPage?): LibraryBooksPage =
        catalogModule.refreshLibraryCatalog(libraryId, firstPage)
    override suspend fun searchBooks(query: String): List<BookSummary> = catalogModule.searchBooks(query)
    override suspend fun loadBookCover(book: BookSummary): ByteArray? = catalogModule.loadBookCover(book)
    override suspend fun loadCatalogImage(url: String): ByteArray? = catalogModule.loadCatalogImage(url)
    override suspend fun loadBookDetail(book: BookSummary): BookDetailInfo? = catalogModule.loadBookDetail(book)
    override suspend fun loadCachedBookDetail(book: BookSummary): BookDetailInfo? = catalogModule.loadCachedBookDetail(book)
    override suspend fun setBookUserRating(book: BookSummary, rating: Int?): BookDetailInfo =
        catalogModule.setBookUserRating(book, rating)
    override suspend fun loadReaderProgress(book: BookSummary, availableFiles: List<BookFileOption>): BookSummary {
        val progress = (if (legacyCwa) currentLegacyProfileId()?.let { localProgress.read(it, book) } else null)
            ?: return book
        return book.copy(
            progressPositionMs = progress.position,
            progressPageIndex = progress.pageIndex,
            progressPercent = progress.percent,
            readerPageIndex = progress.pageIndex
        )
    }

    override suspend fun queueProgress(book: BookSummary, position: Long, pageIndex: Int, progressPercent: Float?) {
        if (!legacyCwa) throw UserFacingException(
            "Reading progress sync is not supported by this OPDS feed.",
            UserFacingReason.CAPABILITY_UNAVAILABLE
        )
        val profileId = currentLegacyProfileId()
            ?: throw UserFacingException("The active CWA profile is unavailable.")
        localProgress.write(profileId, book, position, pageIndex, progressPercent)
    }

    internal suspend fun authenticatedHeaders(url: String): Map<String, String> {
        val feed = requestFeedUrl() ?: return emptyMap()
        if (client.resolveLink(feed, url, feed) == null) return emptyMap()
        return currentAuthorization()?.let { mapOf("Authorization" to it) }.orEmpty()
    }

    internal suspend fun recoverAuthentication(): Boolean = restoreCurrentProfileSession()

    override suspend fun buildReaderState(book: BookSummary, localOnly: Boolean): ReaderState {
        val localBook = book.withOpdsLocalPath()
        if (localBook.localPath.isNullOrBlank()) {
            val provider = if (legacyCwa) "Calibre-Web Automated" else "OPDS"
            throw UserFacingException("Download this $provider publication before opening it.")
        }
        val baseState = baseRepository.buildReaderState(localBook, localOnly = true)
        val progress = (if (legacyCwa) currentLegacyProfileId()?.let { localProgress.read(it, localBook) } else null)
            ?: return baseState.copy(book = localBook, lastKnownPosition = 0L, pageIndex = 0, progressPercent = null, lastReportedProgressPercent = null)
        val progressedBook = localBook.copy(
            progressPositionMs = progress.position,
            progressPageIndex = progress.pageIndex,
            progressPercent = progress.percent,
            readerPageIndex = progress.pageIndex
        )
        return baseState.copy(
            book = progressedBook,
            lastKnownPosition = progress.position,
            pageIndex = progress.pageIndex,
            readerPageIndex = progress.pageIndex,
            progressPercent = progress.percent,
            lastReportedProgressPercent = progress.percent
        )
    }

    override suspend fun restoreActiveReaderState(
        session: com.vangeaux.lagrange.core.ActiveReaderSession,
        localOnly: Boolean
    ): ReaderState? = baseRepository.restoreActiveReaderState(session, localOnly = true)

    override suspend fun prepareEpubImageLibrarySource(
        book: BookSummary,
        allowRemoteCache: Boolean
    ): EpubImageLibrarySourceResult {
        val localBook = book.withOpdsLocalPath()
        if (localBook.localPath.isNullOrBlank()) {
            return EpubImageLibrarySourceResult.Unavailable("Download this OPDS publication before opening it.")
        }
        return baseRepository.prepareEpubImageLibrarySource(localBook, allowRemoteCache = false)
    }

    override suspend fun loadCachedBrowserState(libraryId: String?): BrowserState? = null
    override suspend fun loadHomeShelves(): HomeShelfData {
        if (!legacyCwa) return HomeShelfData()
        val libraries = catalogModule.loadLibraries()
        val savedSelection = currentLegacyProfile()?.selectedLibraryId ?: baseRepository.getSelectedLibraryId()
        val selected = savedSelection
            ?.takeIf { id -> libraries.any { it.id == id } }
            ?: libraries.firstOrNull()?.id
            ?: return HomeShelfData()
        val page = catalogModule.loadBooksPage(selected, 0)
        return HomeShelfData(booksBySection = mapOf(HomeSection.RECENTLY_ADDED_BOOKS to page.items))
    }
    override suspend fun loadCachedHomeBooks(): List<BookSummary> = emptyList()
    override suspend fun loadLocalBooks(): List<BookSummary> = withContext(Dispatchers.IO) {
        val root = configuredFeedUrl ?: return@withContext emptyList()
        val scope = downloadStorageScopeId(context, root) ?: return@withContext emptyList()
        DownloadStore(context).readAll(root, scope).map { record ->
            BookSummary(
                libraryId = "",
                id = record.bookId,
                fileId = record.fileId,
                title = record.title,
                filename = record.filename,
                format = record.mimeType,
                mediaKind = record.mediaKind,
                localPath = record.localPath,
                downloadedSourceUpdatedAtMillis = record.sourceUpdatedAtMillis,
                updatedAtMillis = record.sourceUpdatedAtMillis
            )
        }
    }

    override suspend fun downloadBook(
        book: BookSummary,
        storageScopeId: String?,
        onProgress: (Float?) -> Unit
    ): DownloadedBookFile = downloadOpdsBook(book, storageScopeId, onProgress)

    override suspend fun deleteLocalCopy(book: BookSummary) {
        deleteOpdsLocalCopy(book)
    }

    override suspend fun deleteLocalCopies(book: BookSummary): Set<String> =
        setOfNotNull(book.fileId).filterTo(linkedSetOf()) { deleteOpdsLocalCopy(book.copy(fileId = it)) }

    private suspend fun BookSummary.withOpdsLocalPath(): BookSummary {
        if (!localPath.isNullOrBlank()) return this
        val root = configuredFeedUrl ?: return this
        val fileId = fileId ?: return this
        val scope = downloadStorageScopeId(context, root) ?: return this
        val record = DownloadStore(context).find(root, fileId, scope) ?: return this
        return copy(localPath = record.localPath)
    }

    private suspend fun downloadOpdsBook(
        book: BookSummary,
        requestedStorageScopeId: String?,
        onProgress: (Float?) -> Unit
    ): DownloadedBookFile = withContext(Dispatchers.IO) {
        if (legacyCwa && book.mediaKind == MediaKind.AUDIO) {
            throw UserFacingException(
                "Calibre-Web Automated audiobook downloads are not supported.",
                UserFacingReason.CAPABILITY_UNAVAILABLE
            )
        }
        val root = configuredFeedUrl ?: throw IllegalStateException("No OPDS feed URL is configured.")
        val profile = ServerProfileStore(context).active()
            ?.takeIf {
                it.providerId == (if (legacyCwa) PROVIDER_CALIBRE_WEB_AUTOMATED else PROVIDER_OPDS) &&
                    (if (legacyCwa) serverUrlsMatch(it.serverUrl, root) else it.serverUrl == root)
            }
            ?: throw AuthenticationRequiredException()
        val currentScope = downloadStorageScopeId(context, root, profile.id)
            ?: throw AuthenticationRequiredException()
        val scope = requestedStorageScopeId ?: currentScope
        if (scope != currentScope) throw AuthenticationRequiredException()
        val feedUrl = requestFeedUrl() ?: throw IllegalStateException("No OPDS feed URL is configured.")
        val fileId = book.fileId?.takeIf(String::isNotBlank)
            ?: throw UserFacingException("This OPDS publication has no acquisition identity.")
        val acquisition = (book.downloadUrl ?: book.streamUrl)?.takeIf(String::isNotBlank)
            ?: throw UserFacingException("This OPDS publication has no acquisition link.")
        val acquisitionUrl = client.resolveLink(feedUrl, acquisition, feedUrl)
            ?: throw UserFacingException("The OPDS acquisition link is outside the configured server origin.")
        val store = DownloadStore(context)
        val existing = store.find(root, fileId, scope)
        if (existing != null && !downloadUpdateAvailable(book, existing) &&
            downloadedLocatorPassesIntegrity(context, book, existing.localPath)
        ) {
            onProgress(1f)
            return@withContext DownloadedBookFile(
                locator = existing.localPath,
                directlyReadableFile = existing.localPath.takeUnless(String::isContentLocator)?.let(::File)
            )
        }
        val attempt = store.findAttempt(root, fileId, scope)
        val policy = AutomaticDownloadPolicyStore(context).read(profile.id, root, scope)
        if (DownloadStorageSelectionStore(context).read().usesPortableFolder) {
            return@withContext downloadBookToPortableStorage(
                context = context,
                downloadStore = store,
                serverUrl = root,
                profileId = profile.id,
                storageScopeId = scope,
                book = book,
                scheduledAttempt = attempt,
                existingRecord = existing,
                storagePolicy = policy,
                requireCapturedAccountScope = {
                    val active = ServerProfileStore(context).active()
                    if (active?.id != profile.id ||
                        (if (legacyCwa) !serverUrlsMatch(active.serverUrl, root) else active.serverUrl != root)
                    ) throw AuthenticationRequiredException()
                },
                onProgress = onProgress
            ) { consume -> client.streamBlocking(acquisitionUrl, feedUrl, consume) }
        }
        val target = store.ownedTargetOrIsolated(
            serverUrl = root,
            fileId = fileId,
            title = book.title,
            mediaKind = book.mediaKind,
            formatHint = book.format ?: book.filename,
            recordedPath = existing?.localPath,
            storageScopeId = scope
        )
        val parent = target.parentFile ?: throw IOException("Unable to prepare local OPDS download storage.")
        if (!parent.exists() && !parent.mkdirs()) throw IOException("Unable to prepare local OPDS download storage.")
        val stage = File(parent, ".${target.name}.part.${java.util.UUID.randomUUID()}")
        val job = currentCoroutineContext()[Job]
        try {
            client.stream(acquisitionUrl, feedUrl, consume = { input, totalBytes ->
                input.use { source -> stage.outputStream().buffered().use { sink ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var written = 0L
                    while (true) {
                        job?.ensureActive()
                        val read = source.read(buffer)
                        if (read < 0) break
                        sink.write(buffer, 0, read)
                        written += read
                        onProgress(totalBytes?.takeIf { it > 0L }?.let { (written.toFloat() / it).coerceIn(0f, 1f) })
                    }
                } }
            })
            job?.ensureActive()
            if (!downloadedFilePassesIntegrity(book, stage)) {
                throw UserFacingException("The acquired file is empty or does not match its declared format.")
            }
            atomicReplaceDownloadedFile(stage, target)
            store.save(
                DownloadRecord(
                    serverUrl = root,
                    profileId = profile.id,
                    storageScopeId = scope,
                    fileId = fileId,
                    bookId = book.id,
                    libraryId = book.libraryId,
                    title = book.title,
                    filename = book.filename,
                    localPath = target.absolutePath,
                    mediaKind = book.mediaKind,
                    mimeType = book.format,
                    sourceUpdatedAtMillis = book.updatedAtMillis,
                    sizeBytes = target.length(),
                    origin = attempt?.origin ?: DownloadOrigin.MANUAL
                )
            )
            attempt?.let { store.removeAttemptIfOwned(root, fileId, it.requestId, storageScopeId = scope) }
            onProgress(1f)
            DownloadedBookFile(target.absolutePath, target)
        } catch (error: Throwable) {
            stage.delete()
            throw error
        }
    }

    private suspend fun deleteOpdsLocalCopy(book: BookSummary): Boolean = withContext(Dispatchers.IO) {
        val root = configuredFeedUrl ?: return@withContext false
        val fileId = book.fileId ?: return@withContext false
        DownloadStore(context).delete(root, fileId)
    }

    internal val featureAvailability = ProviderFeatureAvailability(
        serverHostedSignIn = false,
        libraryBookCatalog = true,
        seriesCatalog = false,
        achievements = false,
        statistics = false,
        smartScopes = false,
        collections = false,
        authors = false,
        userRating = false,
        extendedReadingStatuses = false,
        readingStatusOptions = emptyList()
    )
}
