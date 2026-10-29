package com.vangeaux.lagrange.provider.kavita

import android.content.Context
import com.vangeaux.lagrange.*
import com.vangeaux.lagrange.core.DownloadModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

internal fun kavitaDownloadEndpoint(base: String, chapterId: String): String =
    "$base/api/Download/chapter?chapterId=$chapterId"

internal class KavitaDownloadModuleImpl(
    context: Context,
    private val auth: KavitaAuthModule
) : DownloadModule {
    private val appContext = context.applicationContext
    private val client = OkHttpClient()
    private val downloadStore = DownloadStore(appContext)

    override val handlesDownloadsDirectly: Boolean = true

    override suspend fun downloadBook(
        book: BookSummary,
        storageScopeId: String?,
        onProgress: (Float?) -> Unit
    ): DownloadedBookFile =
        withContext(Dispatchers.IO) {
            val base = auth.serverUrl() ?: throw UserFacingException(auth.localizedMessage(R.string.perror_kavita_server_url, "Enter a valid Kavita server URL."))
            val fileId = book.fileId ?: chapterId(book)
            val profileId = ServerProfileStore(appContext).activeId().orEmpty()
            val currentStorageScopeId = downloadStorageScopeId(appContext, base, profileId)
                ?: throw AuthenticationRequiredException()
            val capturedStorageScopeId = storageScopeId ?: currentStorageScopeId
            if (capturedStorageScopeId != currentStorageScopeId) throw AuthenticationRequiredException()
            fun requireCapturedAccountScope() {
                if (downloadStorageScopeId(appContext, base, profileId) != capturedStorageScopeId) {
                    throw AuthenticationRequiredException()
                }
            }
            val existing = downloadStore.find(base, fileId, capturedStorageScopeId)
            val scheduledAttempt = downloadStore.findAttempt(base, fileId, capturedStorageScopeId)
            val attemptOrigin = scheduledAttempt?.origin ?: DownloadOrigin.MANUAL
            val storagePolicy = AutomaticDownloadPolicyStore(appContext)
                .read(profileId, base, capturedStorageScopeId)
            if (DownloadStorageSelectionStore(appContext).read().usesPortableFolder) {
                return@withContext downloadBookToPortableStorage(
                    context = appContext,
                    downloadStore = downloadStore,
                    serverUrl = base,
                    profileId = profileId,
                    storageScopeId = capturedStorageScopeId,
                    book = book,
                    scheduledAttempt = scheduledAttempt,
                    existingRecord = existing,
                    storagePolicy = storagePolicy,
                    requireCapturedAccountScope = ::requireCapturedAccountScope,
                    onProgress = onProgress
                ) { consume ->
                    requireCapturedAccountScope()
                    val request = Request.Builder()
                        .url(kavitaDownloadEndpoint(base, chapterId(book)))
                        .apply { auth.requestHeaders().forEach { (name, value) -> header(name, value) } }
                        .get()
                        .build()
                    client.newCall(request).execute().use { response ->
                        if (response.code == 401) throw AuthenticationRequiredException()
                        if (!response.isSuccessful) {
                            throw HttpRequestException(response.code, "download the Kavita chapter")
                        }
                        val body = response.body
                            ?: throw UserFacingException("Kavita returned an empty chapter file.")
                        body.byteStream().use { input ->
                            consume(input, body.contentLength().takeIf { it > 0L })
                        }
                    }
                }
            }
            reusableDownloadedBook(appContext, book, existing)?.let { reusable ->
                downloadStore.removeAttemptIfOwned(
                    base,
                    fileId,
                    scheduledAttempt?.requestId.orEmpty(),
                    storageScopeId = capturedStorageScopeId
                )
                requireCapturedAccountScope()
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
                storageScopeId = capturedStorageScopeId
            )
            val hadUsableExisting = existing != null &&
                downloadedLocatorPassesIntegrity(appContext, book, existing.localPath)
            if (scheduledAttempt == null) {
                downloadStore.saveAttemptIfAbsent(
                    DownloadAttempt(
                        serverUrl = base,
                        requestId = "",
                        profileId = profileId,
                        storageScopeId = capturedStorageScopeId,
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
            val stagingId = scheduledAttempt?.requestId?.takeIf(String::isNotBlank)
                ?: UUID.randomUUID().toString()
            val temporary = File(target.parentFile, ".${target.name}.part.$stagingId")
            val storageReservationId = "$base\u0000$fileId\u0000$stagingId"
            var retainStagedForRetry = false
            if (!downloadStore.markAttemptStateIfOwned(
                    base,
                    fileId,
                    scheduledAttempt?.requestId.orEmpty(),
                    DownloadAttemptState.STAGING,
                    temporary,
                    capturedStorageScopeId
                )
            ) {
                throw CancellationException("This download was replaced by a newer request.")
            }
            if (temporary.exists() && !temporary.delete()) {
                throw UserFacingException(auth.localizedMessage(R.string.perror_download_stage_replace, "Unable to replace the interrupted download stage."))
            }
            requireCapturedAccountScope()
            val request = Request.Builder()
                .url(kavitaDownloadEndpoint(base, chapterId(book)))
                .apply { auth.requestHeaders().forEach { (name, value) -> header(name, value) } }
                .get()
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    if (response.code == 401) {
                        throw AuthenticationRequiredException()
                    }
                    if (!response.isSuccessful) {
                        throw HttpRequestException(response.code, "download the Kavita chapter")
                    }
                    val body = response.body
                        ?: throw UserFacingException(auth.localizedMessage(R.string.perror_kavita_empty_chapter, "Kavita returned an empty chapter file."))
                    val total = body.contentLength().takeIf { it > 0L }
                    if (total != null && downloadIsIndividuallyOverCap(
                            total,
                            attemptOrigin,
                            storagePolicy
                        )
                    ) {
                        throw LocalBookStorageLimitException(
                            "This file is larger than the maximum local-book storage limit."
                        )
                    }
                    val (capacity, initialAllowance) = runBlocking {
                        AutomaticDownloadStorage.prepareTransfer(
                            context = appContext,
                            serverUrl = base,
                            profileId = profileId,
                            policy = storagePolicy,
                            declaredBytes = total,
                            existingFinalBytes = existing
                                ?.let { LocalDownloadAccess(appContext).length(it.localPath) }
                                ?.coerceAtLeast(0L)
                                ?: 0L,
                            protectedFileIds = setOf(fileId),
                            allowRemoval = true,
                            reservationId = storageReservationId,
                            stagedPath = temporary.absolutePath,
                            origin = attemptOrigin
                        )
                    }
                    if (!capacity.allowed ||
                        (total != null && initialAllowance.maximumAdditionalBytes != Long.MAX_VALUE &&
                            total > initialAllowance.maximumAdditionalBytes)
                    ) {
                        throw LocalBookStorageLimitException(
                            capacity.message
                                ?: "This download would exceed the configured storage limits."
                        )
                    }
                    var copied = 0L
                    var allowance = initialAllowance
                    onProgress(0f.takeIf { total != null })
                    body.byteStream().use { input ->
                        FileOutputStream(temporary).use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                requireCapturedAccountScope()
                                val count = input.read(buffer)
                                if (count < 0) break
                                var permitted = allowance.permitsCurrentPolicyAndReserve(
                                    appContext,
                                    temporary,
                                    copied,
                                    count,
                                    attemptOrigin
                                )
                                if (!permitted && total == null) {
                                    val (extendedCapacity, extendedAllowance) = runBlocking {
                                        AutomaticDownloadStorage.extendUnknownTransfer(
                                            context = appContext,
                                            serverUrl = base,
                                            profileId = profileId,
                                            policy = storagePolicy,
                                            bytesWritten = copied,
                                            protectedFileIds = setOf(fileId),
                                            reservationId = storageReservationId,
                                            stagedPath = temporary.absolutePath
                                        )
                                    }
                                    if (extendedCapacity.allowed) allowance = extendedAllowance
                                    permitted = extendedCapacity.allowed &&
                                        allowance.permitsCurrentPolicyAndReserve(
                                            appContext,
                                            temporary,
                                            copied,
                                            count,
                                            attemptOrigin
                                        )
                                }
                                if (!permitted) {
                                    throw LocalBookStorageLimitException(
                                        "The download stopped before crossing the configured local-book storage limits."
                                    )
                                }
                                output.write(buffer, 0, count)
                                copied += count
                                onProgress(total?.let { (copied.toFloat() / it).coerceIn(0f, 1f) })
                            }
                        }
                    }
                    if (!temporary.isFile || temporary.length() <= 0L) {
                        throw UserFacingException(auth.localizedMessage(R.string.perror_kavita_empty_chapter, "Kavita returned an empty chapter file."))
                    }
                    if (!downloadedFilePassesIntegrity(book, temporary)) {
                        val suffix = if (hadUsableExisting) " The previous local copy was kept." else ""
                        throw UserFacingException(
                            auth.localizedMessage(if (hadUsableExisting) R.string.perror_kavita_download_integrity_kept else R.string.perror_kavita_download_integrity, "The downloaded Kavita file did not pass integrity checks.$suffix")
                        )
                    }
                    onProgress(1f)
                }
                val completedRecord = DownloadRecord(
                    serverUrl = base,
                    profileId = profileId,
                    storageScopeId = capturedStorageScopeId,
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
                    lastAccessedAtMillis = listOfNotNull(
                        existing?.lastAccessedAtMillis,
                        book.lastReadAtMillis
                    ).maxOrNull(),
                    lastKnownCompleted = when {
                        book.isRead -> true
                        book.readStatus != null -> book.readStatus.isCompletedStatus()
                        else -> existing?.lastKnownCompleted
                    },
                    sizeBytes = temporary.length().coerceAtLeast(0L),
                    origin = existing?.origin
                        ?: scheduledAttempt?.origin
                        ?: DownloadOrigin.MANUAL,
                    status = DownloadRecordStatus.COMPLETE
                )
                existing
                    ?.takeUnless { localDownloadLocatorsMatch(it.localPath, target.absolutePath) }
                    ?.let { PortableDownloadCleanup.defer(appContext, it) }
                requireCapturedAccountScope()
                val committed = try {
                    verifyAndCommitDownloadWhenReaderInactive(
                        context = appContext,
                        downloadStore = downloadStore,
                        serverUrl = base,
                        storageScopeId = capturedStorageScopeId,
                        fileId = fileId,
                        requestId = scheduledAttempt?.requestId.orEmpty(),
                        stagedFile = temporary,
                        targetFile = target,
                        record = completedRecord
                    )
                } catch (deferred: DownloadReplacementDeferredException) {
                    retainStagedForRetry = true
                    throw deferred
                }
                if (!committed) {
                    throw CancellationException("This download was replaced by a newer request.")
                }
            } finally {
                runBlocking {
                    AutomaticDownloadStorage.releaseTransfer(appContext, storageReservationId)
                }
                if (!retainStagedForRetry && temporary.exists()) temporary.delete()
            }
            PortableDownloadCleanup.schedule(appContext)
            DownloadedBookFile(target.absolutePath, target)
        }

    override suspend fun loadAudiobookDownloadFiles(book: BookSummary): List<BookSummary> = emptyList()

    override suspend fun deleteLocalCopy(book: BookSummary) {
        val base = auth.serverUrl() ?: return
        downloadStore.delete(base, book.fileId ?: chapterId(book))
    }

    override suspend fun deleteLocalCopies(book: BookSummary): Set<String> {
        deleteLocalCopy(book)
        return setOf(book.fileId ?: chapterId(book))
    }

    private fun chapterId(book: BookSummary): String = book.id.removePrefix("chapter:")
        .substringBefore(':')
        .ifBlank { throw UserFacingException(auth.localizedMessage(R.string.perror_kavita_chapter_identity, "Kavita chapter identity is unavailable.")) }
}
