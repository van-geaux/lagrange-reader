package com.vangeaux.lagrange.provider.grimmory

import android.content.Context
import com.vangeaux.lagrange.*
import com.vangeaux.lagrange.core.AuthenticatedMediaModule
import com.vangeaux.lagrange.core.DownloadModule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

internal fun grimmoryDownloadFilesForBook(book: BookSummary): List<BookSummary> {
    // Grimmory's native download route accepts a book ID, so every media type uses its primary item.
    return listOf(book)
}

internal fun grimmoryDownloadEndpoint(base: String, bookId: String, additionalFileId: String? = null): String {
    val root = base.toHttpUrlOrNull()
        ?: throw UserFacingException("Enter a valid Grimmory server URL.")
    val id = bookId.toLongOrNull()?.takeIf { it >= 0L }?.toString()
        ?: throw UserFacingException("Grimmory book identity is unavailable.")
    val path = additionalFileId?.let { rawFileId ->
        val fileId = rawFileId.toLongOrNull()?.takeIf { it >= 0L }?.toString()
            ?: throw UserFacingException("Grimmory file identity is unavailable.")
        grimmoryAdditionalFileDownloadPath(id, fileId)
    } ?: "/api/v1/books/$id/download"
    return root.newBuilder().addPathSegments(path.trimStart('/')).build().toString()
}

internal fun grimmoryAdditionalFileIdForDownload(book: BookSummary, base: String): String? {
    val fileId = book.fileId?.toLongOrNull()?.takeIf { it >= 0L }?.toString() ?: return null
    val expectedUrl = grimmoryDownloadEndpoint(base, book.id, fileId)
    return fileId.takeIf { book.downloadUrl == expectedUrl }
}

internal fun requireGrimmoryDownloadSuccess(response: Response) {
    if (response.isSuccessful) return
    if (response.code == 401 || response.code == 403) throw AuthenticationRequiredException()
    throw HttpRequestException(response.code, "download a Grimmory book")
}

internal class GrimmoryDownloadTransport(
    private val client: OkHttpClient,
    private val authenticatedMedia: AuthenticatedMediaModule
) {
    suspend fun prepare(base: String, bookId: String, additionalFileId: String? = null): Request {
        val url = grimmoryDownloadEndpoint(base, bookId, additionalFileId)
        return Request.Builder()
            .url(url)
            .apply { authenticatedMedia.requestHeaders(url).forEach { (name, value) -> header(name, value) } }
            .get()
            .build()
    }

    fun execute(request: Request): Response = client.newCall(request).execute()

    fun openBlocking(base: String, bookId: String, additionalFileId: String? = null): Response =
        runBlocking { open(base, bookId, additionalFileId) }

    suspend fun open(base: String, bookId: String, additionalFileId: String? = null): Response = withContext(Dispatchers.IO) {
        var response = execute(prepare(base, bookId, additionalFileId))
        if (response.code == 401 && authenticatedMedia.recoverAuthentication()) {
            response.close()
            response = execute(prepare(base, bookId, additionalFileId))
        }
        response
    }
}

/** Grimmory-native streaming downloads; bytes are never buffered as a whole in memory. */
internal class GrimmoryDownloadModule(
    context: Context,
    private val repository: GrimmoryRepository
) : DownloadModule {
    private val appContext = context.applicationContext
    private val downloadStore = DownloadStore(appContext)
    private val auth = repository.auth
    private val transport = GrimmoryDownloadTransport(OkHttpClient(), auth)

    override val handlesDownloadsDirectly: Boolean = true

    override suspend fun downloadBook(
        book: BookSummary,
        storageScopeId: String?,
        onProgress: (Float?) -> Unit
    ): DownloadedBookFile = withContext(Dispatchers.IO) {
        val base = normalizeServerUrl(repository.getServerUrl().orEmpty())
            ?: throw UserFacingException("Enter a valid Grimmory server URL.")
        val fileId = book.fileId ?: book.id
        val additionalFileId = grimmoryAdditionalFileIdForDownload(book, base)
        val profileId = ServerProfileStore(appContext).activeId().orEmpty()
        val currentScope = downloadStorageScopeId(appContext, base, profileId)
            ?: throw AuthenticationRequiredException()
        val capturedScope = storageScopeId ?: currentScope
        if (capturedScope != currentScope) throw AuthenticationRequiredException()
        fun requireCapturedScope() {
            if (downloadStorageScopeId(appContext, base, profileId) != capturedScope) {
                throw AuthenticationRequiredException()
            }
        }

        val existing = downloadStore.find(base, fileId, capturedScope)
        val attempt = downloadStore.findAttempt(base, fileId, capturedScope)
        val origin = attempt?.origin ?: DownloadOrigin.MANUAL
        val policy = AutomaticDownloadPolicyStore(appContext)
            .read(profileId.ifBlank { serverProfileId(base) }, base, capturedScope)

        if (DownloadStorageSelectionStore(appContext).read().usesPortableFolder) {
            return@withContext downloadBookToPortableStorage(
                context = appContext,
                downloadStore = downloadStore,
                serverUrl = base,
                profileId = profileId,
                storageScopeId = capturedScope,
                book = book,
                scheduledAttempt = attempt,
                existingRecord = existing,
                storagePolicy = policy,
                requireCapturedAccountScope = ::requireCapturedScope,
                onProgress = onProgress
            ) { consume ->
                requireCapturedScope()
                transport.openBlocking(base, book.id, additionalFileId).use { response ->
                    requireGrimmoryDownloadSuccess(response)
                    val body = response.body ?: throw UserFacingException("Grimmory returned an empty book file.")
                    body.byteStream().use { input -> consume(input, body.contentLength().takeIf { it >= 0L }) }
                }
            }
        }

        reusableDownloadedBook(appContext, book, existing)?.let { reusable ->
            downloadStore.removeAttemptIfOwned(base, fileId, attempt?.requestId.orEmpty(), storageScopeId = capturedScope)
            requireCapturedScope()
            onProgress(1f)
            return@withContext reusable
        }

        val target = downloadStore.ownedTargetOrIsolated(
            serverUrl = base,
            fileId = fileId,
            title = book.title,
            mediaKind = book.mediaKind,
            formatHint = book.format,
            recordedPath = existing?.localPath,
            storageScopeId = capturedScope
        )
        val hadUsableExisting = existing != null && downloadedLocatorPassesIntegrity(appContext, book, existing.localPath)
        if (attempt == null) {
            downloadStore.saveAttemptIfAbsent(
                DownloadAttempt(
                    serverUrl = base,
                    requestId = "",
                    profileId = profileId,
                    storageScopeId = capturedScope,
                    fileId = fileId,
                    bookId = book.id,
                    libraryId = book.libraryId,
                    title = book.title,
                    filename = book.filename,
                    targetPath = target.absolutePath,
                    existingLocalPath = existing?.localPath.takeIf { hadUsableExisting },
                    mediaKind = book.mediaKind,
                    mimeType = book.format,
                    sourceUpdatedAtMillis = book.updatedAtMillis,
                    origin = DownloadOrigin.MANUAL,
                    requiresQueueOwnership = false
                )
            )
        }

        target.parentFile?.mkdirs()
        val stageId = attempt?.requestId?.takeIf(String::isNotBlank) ?: UUID.randomUUID().toString()
        val temporary = File(target.parentFile, ".${target.name}.part.$stageId")
        val reservationId = "$base\u0000$fileId\u0000$stageId"
        var retainStage = false
        if (!downloadStore.markAttemptStateIfOwned(
                base, fileId, attempt?.requestId.orEmpty(), DownloadAttemptState.STAGING,
                temporary, capturedScope
            )
        ) throw CancellationException("This download was replaced by a newer request.")
        if (temporary.exists() && !temporary.delete()) {
            throw UserFacingException("Unable to replace the interrupted Grimmory download stage.")
        }

        try {
            requireCapturedScope()
            transport.open(base, book.id, additionalFileId).use { response ->
                requireGrimmoryDownloadSuccess(response)
                val body = response.body ?: throw UserFacingException("Grimmory returned an empty book file.")
                val total = body.contentLength().takeIf { it > 0L }
                if (total != null && downloadIsIndividuallyOverCap(total, origin, policy)) {
                    throw LocalBookStorageLimitException("This file is larger than the maximum local-book storage limit.")
                }
                val (capacity, initialAllowance) = AutomaticDownloadStorage.prepareTransfer(
                    context = appContext,
                    serverUrl = base,
                    profileId = profileId,
                    policy = policy,
                    declaredBytes = total,
                    existingFinalBytes = existing?.let { LocalDownloadAccess(appContext).length(it.localPath) }?.coerceAtLeast(0L) ?: 0L,
                    protectedFileIds = setOf(fileId),
                    allowRemoval = true,
                    reservationId = reservationId,
                    stagedPath = temporary.absolutePath,
                    origin = origin
                )
                if (!capacity.allowed || (total != null && initialAllowance.maximumAdditionalBytes != Long.MAX_VALUE && total > initialAllowance.maximumAdditionalBytes)) {
                    throw LocalBookStorageLimitException(capacity.message ?: "This download would exceed the configured storage limits.")
                }
                onProgress(0f.takeIf { total != null })
                var copied = 0L
                var allowance = initialAllowance
                body.byteStream().use { input ->
                    FileOutputStream(temporary).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            requireCapturedScope()
                            val count = input.read(buffer)
                            if (count < 0) break
                            var permitted = allowance.permitsCurrentPolicyAndReserve(appContext, temporary, copied, count, origin)
                            if (!permitted && total == null) {
                                val (extendedCapacity, extendedAllowance) = AutomaticDownloadStorage.extendUnknownTransfer(
                                    context = appContext,
                                    serverUrl = base,
                                    profileId = profileId,
                                    policy = policy,
                                    bytesWritten = copied,
                                    protectedFileIds = setOf(fileId),
                                    reservationId = reservationId,
                                    stagedPath = temporary.absolutePath
                                )
                                if (extendedCapacity.allowed) allowance = extendedAllowance
                                permitted = extendedCapacity.allowed && allowance.permitsCurrentPolicyAndReserve(
                                    appContext, temporary, copied, count, origin
                                )
                            }
                            if (!permitted) {
                                throw LocalBookStorageLimitException("The download stopped before crossing the configured local-book storage limits.")
                            }
                            output.write(buffer, 0, count)
                            copied += count
                            onProgress(total?.let { (copied.toFloat() / it).coerceIn(0f, 1f) })
                        }
                    }
                }
                if (!temporary.isFile || temporary.length() <= 0L || (total != null && copied != total)) {
                    throw UserFacingException("The downloaded Grimmory file was empty or incomplete.")
                }
                if (!downloadedFilePassesIntegrity(book, temporary)) {
                    val suffix = if (hadUsableExisting) " The previous local copy was kept." else ""
                    throw UserFacingException("The downloaded Grimmory file did not pass integrity checks.$suffix")
                }
            }

            val record = DownloadRecord(
                serverUrl = base,
                profileId = profileId,
                storageScopeId = capturedScope,
                fileId = fileId,
                bookId = book.id,
                libraryId = book.libraryId,
                title = book.title,
                filename = book.filename ?: target.name,
                localPath = target.absolutePath,
                mediaKind = book.mediaKind,
                mimeType = book.format,
                sourceUpdatedAtMillis = book.updatedAtMillis,
                downloadedAtMillis = existing?.downloadedAtMillis ?: System.currentTimeMillis(),
                lastAccessedAtMillis = listOfNotNull(existing?.lastAccessedAtMillis, book.lastReadAtMillis).maxOrNull(),
                lastKnownCompleted = when {
                    book.isRead -> true
                    book.readStatus != null -> book.readStatus.isCompletedStatus()
                    else -> existing?.lastKnownCompleted
                },
                sizeBytes = temporary.length().coerceAtLeast(0L),
                origin = existing?.origin ?: attempt?.origin ?: DownloadOrigin.MANUAL,
                status = DownloadRecordStatus.COMPLETE
            )
            existing?.takeUnless { localDownloadLocatorsMatch(it.localPath, target.absolutePath) }
                ?.let { PortableDownloadCleanup.defer(appContext, it) }
            requireCapturedScope()
            val committed = try {
                verifyAndCommitDownloadWhenReaderInactive(
                    context = appContext,
                    downloadStore = downloadStore,
                    serverUrl = base,
                    storageScopeId = capturedScope,
                    fileId = fileId,
                    requestId = attempt?.requestId.orEmpty(),
                    stagedFile = temporary,
                    targetFile = target,
                    record = record
                )
            } catch (deferred: DownloadReplacementDeferredException) {
                retainStage = true
                throw deferred
            }
            if (!committed) throw CancellationException("This download was replaced by a newer request.")
            onProgress(1f)
        } finally {
            AutomaticDownloadStorage.releaseTransfer(appContext, reservationId)
            if (!retainStage && temporary.exists()) temporary.delete()
        }
        PortableDownloadCleanup.schedule(appContext)
        DownloadedBookFile(target.absolutePath, target)
    }

    override suspend fun loadAudiobookDownloadFiles(book: BookSummary): List<BookSummary> =
        grimmoryDownloadFilesForBook(book)

    override suspend fun deleteLocalCopy(book: BookSummary) {
        val base = normalizeServerUrl(repository.getServerUrl().orEmpty()) ?: return
        downloadStore.delete(base, book.fileId ?: book.id)
    }

    override suspend fun deleteLocalCopies(book: BookSummary): Set<String> {
        val fileId = book.fileId ?: book.id
        deleteLocalCopy(book)
        return setOf(fileId)
    }
}
