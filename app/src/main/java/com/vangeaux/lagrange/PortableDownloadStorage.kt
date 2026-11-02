package com.vangeaux.lagrange

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock

internal data class DownloadStorageSelection(
    val treeUri: String? = null,
    val managedDirectoryUri: String? = null,
    val displayName: String? = null,
    val volumeId: String? = null,
    val generation: Long = 0L
) {
    val usesPortableFolder: Boolean
        get() = !treeUri.isNullOrBlank() && !managedDirectoryUri.isNullOrBlank()
}

internal enum class PortableStorageAvailability {
    AVAILABLE,
    TEMPORARILY_UNAVAILABLE,
    PERMISSION_REVOKED,
    NOT_CONFIGURED
}

internal class PortableStorageUnavailableException(message: String) : IOException(message)

internal data class PortableReaderCacheEntry(
    val name: String,
    val bytes: Long,
    val lastAccessMillis: Long,
    val protected: Boolean = false
)

internal fun planPortableReaderCacheEvictions(
    entries: List<PortableReaderCacheEntry>,
    targetBytes: Long,
    copyBytes: Long,
    availableBytes: Long,
    maximumRetainedBytes: Long,
    reserveBytes: Long
): List<String>? {
    require(targetBytes >= 0L && copyBytes >= 0L && availableBytes >= 0L)
    require(maximumRetainedBytes >= 0L && reserveBytes >= 0L)
    val protectedBytes = entries.filter(PortableReaderCacheEntry::protected)
        .fold(0L) { total, entry -> portableSaturatingAdd(total, entry.bytes.coerceAtLeast(0L)) }
    val allowedCacheBytes = portableSaturatingAdd(
        protectedBytes,
        maxOf(maximumRetainedBytes, targetBytes)
    )
    var retainedBytes = entries.fold(targetBytes) { total, entry ->
        portableSaturatingAdd(total, entry.bytes.coerceAtLeast(0L))
    }
    var reclaimedBytes = 0L
    val requiredAvailableBytes = portableSaturatingAdd(copyBytes, reserveBytes)
    val evictions = mutableListOf<String>()
    val candidates = entries.filterNot(PortableReaderCacheEntry::protected)
        .sortedWith(compareBy(PortableReaderCacheEntry::lastAccessMillis, PortableReaderCacheEntry::name))
        .iterator()
    while (
        retainedBytes > allowedCacheBytes ||
        (copyBytes > 0L && portableSaturatingAdd(availableBytes, reclaimedBytes) < requiredAvailableBytes)
    ) {
        if (!candidates.hasNext()) return null
        val candidate = candidates.next()
        evictions += candidate.name
        val bytes = candidate.bytes.coerceAtLeast(0L)
        retainedBytes = (retainedBytes - bytes).coerceAtLeast(0L)
        reclaimedBytes = portableSaturatingAdd(reclaimedBytes, bytes)
    }
    return evictions
}

private object PortableReaderCacheCoordinator {
    val monitor = Any()
    val materializedPathsByLocator = mutableMapOf<String, String>()
}

internal class DownloadStorageSelectionStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun read(): DownloadStorageSelection = DownloadStorageSelection(
        treeUri = preferences.getString(TREE_URI, null),
        managedDirectoryUri = preferences.getString(MANAGED_DIRECTORY_URI, null),
        displayName = preferences.getString(DISPLAY_NAME, null),
        volumeId = preferences.getString(VOLUME_ID, null),
        generation = preferences.getLong(GENERATION, 0L)
    )

    fun select(treeUri: Uri): DownloadStorageSelection {
        require(DocumentsContract.isTreeUri(treeUri)) { "Choose a writable folder." }
        val volumeId = documentVolumeId(treeUri)
        require(
            isSupportedDownloadVolume(
                volumeId = volumeId,
                removableVolumeAvailable = volumeId?.let {
                    removableVolumeDirectory(appContext, it) != null
                } == true
            )
        ) { "Choose a writable folder on phone storage or a mounted removable SD card." }
        val resolver = appContext.contentResolver
        resolver.takePersistableUriPermission(
            treeUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        check(hasPersistedReadWritePermission(resolver, treeUri)) {
            "The selected folder did not grant lasting read and write access."
        }
        val selected = DocumentFile.fromTreeUri(appContext, treeUri)
            ?: error("The selected folder is unavailable.")
        check(selected.exists() && selected.isDirectory && selected.canRead() && selected.canWrite()) {
            "The selected folder is not writable."
        }
        val existingManaged = findPortableChild(appContext, treeUri, MANAGED_DIRECTORY_NAME)
        val managed = when {
            existingManaged == null -> createPortableDirectory(
                appContext,
                treeUri,
                MANAGED_DIRECTORY_NAME
            ) ?: error("Lagrange could not create its download folder in this location.")
            existingManaged.isDirectory -> existingManaged
            else -> error("The selected folder already contains a file named $MANAGED_DIRECTORY_NAME.")
        }
        check(managed.canRead() && managed.canWrite()) {
            "Lagrange cannot write to its download folder."
        }
        verifyWritable(managed)
        val next = DownloadStorageSelection(
            treeUri = treeUri.toString(),
            // Preserve the child document URI under the original granted tree. Rewriting the
            // child as a new tree root is not covered by Android's persisted permission.
            managedDirectoryUri = managed.uri.toString(),
            displayName = selected.name?.takeIf(String::isNotBlank) ?: "Selected folder",
            volumeId = volumeId,
            generation = read().generation + 1L
        )
        check(
            preferences.edit()
                .putString(TREE_URI, next.treeUri)
                .putString(MANAGED_DIRECTORY_URI, next.managedDirectoryUri)
                .putString(DISPLAY_NAME, next.displayName)
                .putString(VOLUME_ID, next.volumeId)
                .putLong(GENERATION, next.generation)
                .putStringSet(
                    KNOWN_MANAGED_DIRECTORY_URIS,
                    knownManagedDirectoryUris() + requireNotNull(next.managedDirectoryUri)
                )
                .commit()
        ) { "Unable to save the download location." }
        return next
    }

    fun useInternalStorage(): DownloadStorageSelection {
        val nextGeneration = read().generation + 1L
        check(
            preferences.edit()
                .remove(TREE_URI)
                .remove(MANAGED_DIRECTORY_URI)
                .remove(DISPLAY_NAME)
                .remove(VOLUME_ID)
                .putLong(GENERATION, nextGeneration)
                .commit()
        ) { "Unable to save the download location." }
        // Existing records can still refer to the old tree. Keep its persisted grant until those
        // records are removed rather than stranding already-downloaded books.
        return DownloadStorageSelection(generation = nextGeneration)
    }

    fun availability(selection: DownloadStorageSelection = read()): PortableStorageAvailability {
        if (!selection.usesPortableFolder) return PortableStorageAvailability.NOT_CONFIGURED
        val treeUri = selection.treeUri?.let(Uri::parse)
            ?: return PortableStorageAvailability.NOT_CONFIGURED
        if (!hasPersistedReadWritePermission(appContext.contentResolver, treeUri)) {
            return PortableStorageAvailability.PERMISSION_REVOKED
        }
        val managedUri = selection.managedDirectoryUri?.let(Uri::parse)
            ?: return PortableStorageAvailability.TEMPORARILY_UNAVAILABLE
        val managed = DocumentFile.fromSingleUri(appContext, managedUri)
        return if (
            managed?.exists() == true && managed.isDirectory && managed.canRead() && managed.canWrite()
        ) {
            PortableStorageAvailability.AVAILABLE
        } else {
            PortableStorageAvailability.TEMPORARILY_UNAVAILABLE
        }
    }

    fun availableBytes(selection: DownloadStorageSelection = read()): Long? {
        if (!selection.usesPortableFolder) {
            return runCatching { StatFs(appContext.filesDir.absolutePath).availableBytes.coerceAtLeast(0L) }
                .getOrNull()
        }
        if (availability(selection) != PortableStorageAvailability.AVAILABLE) return null
        val volumeId = selection.volumeId?.takeIf(String::isNotBlank) ?: return null
        val candidate = if (volumeId.equals("primary", ignoreCase = true)) {
            Environment.getExternalStorageDirectory()
        } else {
            removableVolumeDirectory(appContext, volumeId) ?: return null
        }
        return runCatching { StatFs(candidate.absolutePath).availableBytes.coerceAtLeast(0L) }
            .getOrNull()
    }

    fun knownManagedDirectoryUris(): Set<String> =
        preferences.getStringSet(KNOWN_MANAGED_DIRECTORY_URIS, emptySet())
            ?.filterTo(linkedSetOf(), String::isNotBlank)
            .orEmpty()

    private fun verifyWritable(directory: DocumentFile) {
        val probe = createPortableFile(
            appContext,
            directory.uri,
            "application/octet-stream",
            "lagrange-write-test-${UUID.randomUUID()}"
        )
            ?: error("Lagrange could not create a file in this folder.")
        try {
            appContext.contentResolver.openOutputStream(probe.uri, "wt")?.use { output ->
                output.write(byteArrayOf(0x4c, 0x52))
                output.flush()
            } ?: error("Lagrange could not write to this folder.")
        } finally {
            probe.delete()
        }
    }

    private companion object {
        const val PREFERENCES = "download_storage_selection"
        const val TREE_URI = "tree_uri"
        const val MANAGED_DIRECTORY_URI = "managed_directory_uri"
        const val DISPLAY_NAME = "display_name"
        const val VOLUME_ID = "volume_id"
        const val GENERATION = "generation"
        const val KNOWN_MANAGED_DIRECTORY_URIS = "known_managed_directory_uris"
        const val MANAGED_DIRECTORY_NAME = "Lagrange Reader"
    }
}

internal class LocalDownloadAccess(private val context: Context) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    fun exists(locator: String): Boolean = when {
        locator.isContentLocator() -> runCatching {
            resolver.openAssetFileDescriptor(Uri.parse(locator), "r")?.use { true } ?: false
        }.getOrDefault(false)
        else -> File(locator).isFile
    }

    fun length(locator: String): Long = when {
        locator.isContentLocator() -> queryDocumentSize(resolver, Uri.parse(locator)) ?: 0L
        else -> File(locator).length().coerceAtLeast(0L)
    }

    fun displayName(locator: String): String? = when {
        locator.isContentLocator() -> queryDocumentName(resolver, Uri.parse(locator))
        else -> File(locator).name.takeIf(String::isNotBlank)
    }

    fun openInput(locator: String): InputStream = when {
        locator.isContentLocator() -> resolver.openInputStream(Uri.parse(locator))
            ?: throw PortableStorageUnavailableException("The selected storage file is unavailable.")
        else -> File(locator).inputStream()
    }

    fun playbackUri(locator: String): Uri = if (locator.isContentLocator()) {
        Uri.parse(locator)
    } else {
        Uri.fromFile(File(locator))
    }

    fun availability(record: DownloadRecord): PortableStorageAvailability {
        if (!record.localPath.isContentLocator()) {
            return if (File(record.localPath).isFile) {
                PortableStorageAvailability.AVAILABLE
            } else {
                PortableStorageAvailability.TEMPORARILY_UNAVAILABLE
            }
        }
        val treeUri = record.storageTreeUri?.let(Uri::parse)
            ?: return PortableStorageAvailability.PERMISSION_REVOKED
        if (!hasPersistedReadWritePermission(resolver, treeUri)) {
            return PortableStorageAvailability.PERMISSION_REVOKED
        }
        val managedUri = record.storageRootUri?.let(Uri::parse)
            ?: return PortableStorageAvailability.TEMPORARILY_UNAVAILABLE
        val managed = DocumentFile.fromSingleUri(appContext, managedUri)
        if (managed?.exists() != true) return PortableStorageAvailability.TEMPORARILY_UNAVAILABLE
        return if (exists(record.localPath)) {
            PortableStorageAvailability.AVAILABLE
        } else {
            PortableStorageAvailability.TEMPORARILY_UNAVAILABLE
        }
    }

    fun isDefinitelyMissing(record: DownloadRecord): Boolean {
        if (!record.localPath.isContentLocator()) return !File(record.localPath).isFile
        val treeUri = record.storageTreeUri?.let(Uri::parse) ?: return false
        if (!hasPersistedReadWritePermission(resolver, treeUri)) return false
        val managedUri = record.storageRootUri?.let(Uri::parse) ?: return false
        val managed = DocumentFile.fromSingleUri(appContext, managedUri)
        if (managed?.exists() != true || !managed.isDirectory || !managed.canRead()) return false
        return !exists(record.localPath)
    }

    fun deleteOwned(record: DownloadRecord): Boolean {
        if (!record.localPath.isContentLocator()) return !File(record.localPath).exists() || File(record.localPath).delete()
        val root = record.storageRootUri?.let(Uri::parse) ?: return false
        val document = Uri.parse(record.localPath)
        if (!documentBelongsToRoot(root, document)) return false
        return runCatching { DocumentsContract.deleteDocument(resolver, document) }.getOrDefault(false)
    }

    fun materialize(locator: String, fileId: String, formatHint: String?): File =
        synchronized(PortableReaderCacheCoordinator.monitor) {
            if (!locator.isContentLocator()) return File(locator)
            val size = length(locator)
            if (size <= 0L) throw PortableStorageUnavailableException("The selected storage file is unavailable.")
            val extension = portableFileExtension(formatHint)
            val identity = portableReaderCacheIdentity(fileId, locator, size)
            val directory = File(appContext.cacheDir, "portable-reader").apply { mkdirs() }
            val target = File(directory, "$identity.$extension")
            directory.listFiles()?.filter { it.isFile && it.name.endsWith(".part") }
                ?.forEach { it.delete() }
            if (target.exists() && (!target.isFile || target.length() != size) && !target.delete()) {
                throw UserFacingException("Lagrange could not clear an incomplete internal working copy.")
            }
            val protectedPaths = LocalBookReaderLeaseStore(appContext).readLive()
                .mapNotNullTo(mutableSetOf()) { lease ->
                    lease.localPath?.let(PortableReaderCacheCoordinator.materializedPathsByLocator::get)
                }
            val cacheEntries = directory.listFiles().orEmpty()
                .filter { it.isFile && it != target && !it.name.endsWith(".part") }
                .map { candidate ->
                    PortableReaderCacheEntry(
                        name = candidate.name,
                        bytes = candidate.length().coerceAtLeast(0L),
                        lastAccessMillis = candidate.lastModified().coerceAtLeast(0L),
                        protected = candidate.canonicalPath in protectedPaths
                    )
                }
            val cachedTarget = target.isFile && target.length() == size
            val internalAvailable = runCatching {
                StatFs(directory.absolutePath).availableBytes.coerceAtLeast(0L)
            }.getOrDefault(0L)
            val evictions = planPortableReaderCacheEvictions(
                entries = cacheEntries,
                targetBytes = size,
                copyBytes = if (cachedTarget) 0L else size,
                availableBytes = internalAvailable,
                maximumRetainedBytes = PORTABLE_READER_CACHE_MAX_RETAINED_BYTES,
                reserveBytes = PORTABLE_READER_FREE_MARGIN_BYTES
            ) ?: throw UserFacingException(
                "This book is stored in the selected folder, but there is not enough internal working space to open it."
            )
            evictions.forEach { name ->
                val candidate = File(directory, name)
                if (candidate.exists() && !candidate.delete()) {
                    throw UserFacingException("Lagrange could not clear an old internal working copy.")
                }
            }
            if (cachedTarget) {
                target.setLastModified(System.currentTimeMillis())
                PortableReaderCacheCoordinator.materializedPathsByLocator[locator] = target.canonicalPath
                return target
            }
            val availableAfterEviction = runCatching {
                StatFs(directory.absolutePath).availableBytes.coerceAtLeast(0L)
            }.getOrDefault(0L)
            if (availableAfterEviction < portableSaturatingAdd(size, PORTABLE_READER_FREE_MARGIN_BYTES)) {
                throw UserFacingException(
                    "This book is stored in the selected folder, but there is not enough internal working space to open it."
                )
            }
            val staged = File(directory, ".$identity.${UUID.randomUUID()}.part")
            try {
                openInput(locator).use { input ->
                    FileOutputStream(staged).use { output -> input.copyTo(output) }
                }
                if (staged.length() != size) {
                    throw PortableStorageUnavailableException("The selected folder was unavailable or changed while it was being opened.")
                }
                atomicReplaceDownloadedFile(staged, target)
                directory.listFiles()?.forEach { candidate ->
                    if (candidate.isFile && candidate != target && candidate.name.startsWith(identity)) {
                        candidate.delete()
                    }
                }
                target.setLastModified(System.currentTimeMillis())
                PortableReaderCacheCoordinator.materializedPathsByLocator[locator] = target.canonicalPath
                return target
            } finally {
                if (staged.exists()) staged.delete()
            }
        }
}

internal fun DownloadedBookFile.readableFile(
    context: Context,
    fileId: String,
    formatHint: String?
): File = directlyReadableFile?.takeIf(File::isFile)
    ?: LocalDownloadAccess(context).materialize(locator, fileId, formatHint)

internal fun reusableDownloadedBook(
    context: Context,
    book: BookSummary,
    record: DownloadRecord?
): DownloadedBookFile? {
    record ?: return null
    if (downloadUpdateAvailable(book, record) ||
        !downloadedLocatorPassesIntegrity(context, book, record.localPath)
    ) return null
    return DownloadedBookFile(
        locator = record.localPath,
        directlyReadableFile = record.localPath
            .takeUnless(String::isContentLocator)
            ?.let(::File)
            ?.takeIf(File::isFile)
    )
}

internal data class PortableDownloadTarget(
    val treeUri: String,
    val storageRootUri: String,
    val stageUri: String,
    val finalName: String,
    val selectionGeneration: Long
)

internal class PortableDownloadWriter(private val context: Context) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val selectionStore = DownloadStorageSelectionStore(appContext)

    fun createTarget(
        storageScopeId: String,
        fileId: String,
        title: String,
        filename: String?,
        formatHint: String?,
        requestId: String
    ): PortableDownloadTarget {
        val selection = selectionStore.read()
        when (selectionStore.availability(selection)) {
            PortableStorageAvailability.AVAILABLE -> Unit
            PortableStorageAvailability.PERMISSION_REVOKED -> throw PortableStorageUnavailableException(
                "Access to the selected folder was revoked. Choose the folder again in Downloads & Offline."
            )
            else -> throw PortableStorageUnavailableException(
                "The selected folder is unavailable. Choose another download location."
            )
        }
        val rootUri = Uri.parse(requireNotNull(selection.managedDirectoryUri))
        val root = DocumentFile.fromSingleUri(appContext, rootUri)
            ?: throw PortableStorageUnavailableException("The selected folder is unavailable.")
        if (!root.exists() || !root.isDirectory || !root.canRead() || !root.canWrite()) {
            throw PortableStorageUnavailableException("The selected folder is unavailable.")
        }
        val scopeName = "account-${sha256Hex(storageScopeId).take(20)}"
        val existingScope = findPortableChild(appContext, rootUri, scopeName)
        val scope = when {
            existingScope == null -> createPortableDirectory(appContext, rootUri, scopeName)
                ?: throw PortableStorageUnavailableException(
                    "Lagrange could not create an account folder in the selected folder."
                )
            existingScope.isDirectory -> existingScope
            else -> throw PortableStorageUnavailableException(
                "The selected download folder contains an invalid account entry."
            )
        }
        val filenameExtension = portableFileExtension(filename)
        val extension = filenameExtension.takeUnless { it == "bin" }
            ?: portableFileExtension(formatHint)
        val safeTitle = portableSafeFileName(title).take(80).ifBlank { "book" }
        val requestToken = sha256Hex(requestId.ifBlank { UUID.randomUUID().toString() }).take(12)
        val finalName = "$safeTitle-${sha256Hex(fileId).take(20)}-$requestToken.$extension"
        // The document URI itself is the durable staging identity. Using its final unique name
        // from the start avoids an unrecoverable process-death window between SAF rename and
        // persisting the renamed URI; it remains invisible to Lagrange until verification and
        // record commit complete.
        val stage = createPortableFile(appContext, scope.uri, portableMimeType(extension), finalName)
            ?: throw PortableStorageUnavailableException("Lagrange could not create a download in the selected folder.")
        return PortableDownloadTarget(
            treeUri = requireNotNull(selection.treeUri),
            storageRootUri = rootUri.toString(),
            stageUri = stage.uri.toString(),
            finalName = finalName,
            selectionGeneration = selection.generation
        )
    }

    fun openOutput(target: PortableDownloadTarget): OutputStream {
        requireCurrentSelection(target)
        val output = try {
            resolver.openOutputStream(Uri.parse(target.stageUri), "wt")
        } catch (error: IOException) {
            throw PortableStorageUnavailableException(
                "The selected folder refused the download write."
            ).also { it.addSuppressed(error) }
        } ?: throw PortableStorageUnavailableException("The selected folder refused the download write.")
        return output.asPortableDownloadOutput()
    }

    fun stageLength(target: PortableDownloadTarget): Long =
        queryDocumentSize(resolver, Uri.parse(target.stageUri)) ?: 0L

    fun openStageInput(target: PortableDownloadTarget): InputStream =
        resolver.openInputStream(Uri.parse(target.stageUri))
            ?: throw PortableStorageUnavailableException("The staged selected-folder download is unavailable.")

    fun publish(target: PortableDownloadTarget): String {
        requireCurrentSelection(target)
        val stage = DocumentFile.fromSingleUri(appContext, Uri.parse(target.stageUri))
            ?: throw PortableStorageUnavailableException("The staged selected-folder download is unavailable.")
        if (!stage.exists() || !stage.isFile) {
            throw PortableStorageUnavailableException("The staged selected-folder download is unavailable.")
        }
        return stage.uri.toString()
    }

    fun discard(target: PortableDownloadTarget) {
        runCatching {
            val uri = Uri.parse(target.stageUri)
            if (documentBelongsToRoot(Uri.parse(target.storageRootUri), uri)) {
                DocumentsContract.deleteDocument(resolver, uri)
            }
        }
    }

    fun discardPublished(target: PortableDownloadTarget, locator: String) {
        runCatching {
            val uri = Uri.parse(locator)
            if (documentBelongsToRoot(Uri.parse(target.storageRootUri), uri)) {
                DocumentsContract.deleteDocument(resolver, uri)
            }
        }
    }

    fun availableBytes(target: PortableDownloadTarget): Long? {
        requireCurrentSelection(target)
        val selection = selectionStore.read()
        return selectionStore.availableBytes(selection)
    }

    fun requireSelectionUnchanged(target: PortableDownloadTarget) {
        val selection = selectionStore.read()
        if (
            selection.generation != target.selectionGeneration ||
            selection.treeUri != target.treeUri ||
            selection.managedDirectoryUri != target.storageRootUri
        ) {
            throw PortableStorageUnavailableException(
                "The download location changed while this file was being saved. Try the download again."
            )
        }
    }

    private fun requireCurrentSelection(target: PortableDownloadTarget) {
        val selection = selectionStore.read()
        if (
            selection.generation != target.selectionGeneration ||
            selection.treeUri != target.treeUri ||
            selection.managedDirectoryUri != target.storageRootUri
        ) {
            throw PortableStorageUnavailableException(
                "The download location changed while this file was being saved. Try the download again."
            )
        }
        if (selectionStore.availability(selection) != PortableStorageAvailability.AVAILABLE) {
            throw PortableStorageUnavailableException("The selected folder is unavailable.")
        }
    }
}

internal suspend fun downloadBookToPortableStorage(
    context: Context,
    downloadStore: DownloadStore,
    serverUrl: String,
    profileId: String,
    storageScopeId: String,
    book: BookSummary,
    scheduledAttempt: DownloadAttempt?,
    existingRecord: DownloadRecord?,
    storagePolicy: AutomaticDownloadPolicy,
    requireCapturedAccountScope: () -> Unit,
    onProgress: (Float?) -> Unit,
    transfer: ((InputStream, Long?) -> Unit) -> Unit
): DownloadedBookFile {
    val access = LocalDownloadAccess(context)
    val existingIsUsable = existingRecord?.let { record ->
        downloadedLocatorPassesIntegrity(context, book, record.localPath)
    } == true
    if (
        existingRecord != null &&
        !downloadUpdateAvailable(book, existingRecord) &&
        existingIsUsable
    ) {
        requireCapturedAccountScope()
        onProgress(1f)
        return DownloadedBookFile(
            locator = existingRecord.localPath,
            directlyReadableFile = existingRecord.localPath
                .takeUnless(String::isContentLocator)
                ?.let(::File)
        )
    }

    val requestId = scheduledAttempt?.requestId.orEmpty()
    val stagingId = requestId.takeIf(String::isNotBlank) ?: UUID.randomUUID().toString()
    val writer = PortableDownloadWriter(context)
    lateinit var target: PortableDownloadTarget
    lateinit var attempt: DownloadAttempt
    val prepared = localCopyDeletionGuard.withLock {
        target = writer.createTarget(
            storageScopeId = storageScopeId,
            fileId = book.fileId ?: book.id,
            title = book.title,
            filename = book.filename,
            formatHint = book.format,
            requestId = stagingId
        )
        attempt = DownloadAttempt(
        serverUrl = serverUrl,
        requestId = requestId,
        profileId = profileId,
        storageScopeId = storageScopeId,
        fileId = book.fileId ?: book.id,
        bookId = book.id,
        libraryId = book.libraryId,
        title = book.title,
        filename = book.filename,
        targetPath = target.stageUri,
        storageTreeUri = target.treeUri,
        storageRootUri = target.storageRootUri,
        existingLocalPath = existingRecord?.localPath?.takeIf { existingIsUsable },
        mediaKind = book.mediaKind,
        mimeType = book.format,
        sourceUpdatedAtMillis = book.updatedAtMillis,
        expectedSizeBytes = scheduledAttempt?.expectedSizeBytes,
        origin = scheduledAttempt?.origin ?: DownloadOrigin.MANUAL,
        policyGeneration = scheduledAttempt?.policyGeneration,
        requiresQueueOwnership = scheduledAttempt?.requiresQueueOwnership ?: false,
        startedAtMillis = scheduledAttempt?.startedAtMillis ?: System.currentTimeMillis(),
        state = DownloadAttemptState.QUEUED
        )
        val saved = if (scheduledAttempt == null) {
            downloadStore.saveAttemptIfAbsent(attempt)
        } else {
            downloadStore.saveAttempt(attempt)
            true
        }
        saved && downloadStore.markPortableAttemptStateIfOwned(
            serverUrl = serverUrl,
            fileId = attempt.fileId,
            requestId = requestId,
            state = DownloadAttemptState.STAGING,
            target = target,
            storageScopeId = storageScopeId
        )
    }
    if (!prepared) {
        writer.discard(target)
        throw kotlinx.coroutines.CancellationException("This download was replaced by a newer request.")
    }

    val reservationId = "$serverUrl\u0000${attempt.fileId}\u0000$stagingId"
    var publishedLocator: String? = null
    val downloadContext = currentCoroutineContext()
    try {
        transfer { input, totalBytes ->
            requireCapturedAccountScope()
            if (totalBytes != null && downloadIsIndividuallyOverCap(
                    totalBytes,
                    attempt.origin,
                    storagePolicy
                )
            ) {
                throw LocalBookStorageLimitException(
                    "This file is larger than the maximum local-book storage limit."
                )
            }
            val availableBefore = writer.availableBytes(target)
            if (
                totalBytes != null && availableBefore != null &&
                totalBytes > (availableBefore - PORTABLE_WRITE_FREE_MARGIN_BYTES).coerceAtLeast(0L)
            ) {
                throw LocalBookStorageLimitException("The selected folder does not have enough free space for this download.")
            }
            val (capacity, initialAllowance) = runBlocking {
                AutomaticDownloadStorage.prepareTransfer(
                    context = context,
                    serverUrl = serverUrl,
                    profileId = profileId,
                    policy = storagePolicy,
                    declaredBytes = totalBytes,
                    existingFinalBytes = existingRecord?.let { access.length(it.localPath) } ?: 0L,
                    protectedFileIds = setOf(attempt.fileId),
                    allowRemoval = true,
                    reservationId = reservationId,
                    stagedPath = target.stageUri,
                    origin = attempt.origin
                )
            }
            if (!capacity.allowed ||
                (totalBytes != null &&
                    initialAllowance.maximumAdditionalBytes != Long.MAX_VALUE &&
                    totalBytes > initialAllowance.maximumAdditionalBytes)
            ) {
                throw LocalBookStorageLimitException(
                    capacity.message ?: "This download would exceed the configured storage limits."
                )
            }
            onProgress(0f.takeIf { totalBytes != null })
            val digest = MessageDigest.getInstance("SHA-256")
            var allowance = initialAllowance
            var copiedBytes = 0L
            var lastReportedPercent = -1
            var lastSpaceCheckAt = 0L
            var lastKnownAvailableBytes = availableBefore
            writer.openOutput(target).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        downloadContext.ensureActive()
                        requireCapturedAccountScope()
                        writer.requireSelectionUnchanged(target)
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (copiedBytes - lastSpaceCheckAt >= PORTABLE_SPACE_RECHECK_BYTES) {
                            lastKnownAvailableBytes = writer.availableBytes(target)
                            lastSpaceCheckAt = copiedBytes
                        }
                        val physicalFree = lastKnownAvailableBytes?.minus(
                            copiedBytes - lastSpaceCheckAt
                        )
                        if (physicalFree != null &&
                            physicalFree - count < PORTABLE_WRITE_FREE_MARGIN_BYTES
                        ) {
                            throw LocalBookStorageLimitException(
                                "The selected folder is too full to finish this download safely."
                            )
                        }
                        var permitted = allowance.permitsCurrentPolicyAndReserve(
                            context = context,
                            availableBytes = { physicalFree },
                            bytesWritten = copiedBytes,
                            nextChunkBytes = count,
                            origin = attempt.origin
                        )
                        if (!permitted && totalBytes == null) {
                            val extended = runBlocking {
                                AutomaticDownloadStorage.extendUnknownTransfer(
                                    context = context,
                                    serverUrl = serverUrl,
                                    profileId = profileId,
                                    policy = storagePolicy,
                                    bytesWritten = copiedBytes,
                                    protectedFileIds = setOf(attempt.fileId),
                                    reservationId = reservationId,
                                    stagedPath = target.stageUri
                                )
                            }
                            if (extended.first.allowed) allowance = extended.second
                            permitted = extended.first.allowed &&
                                allowance.permitsCurrentPolicyAndReserve(
                                    context = context,
                                    availableBytes = { physicalFree },
                                    bytesWritten = copiedBytes,
                                    nextChunkBytes = count,
                                    origin = attempt.origin
                                )
                        }
                        if (!permitted) {
                            throw LocalBookStorageLimitException(
                                "The download stopped before crossing the configured storage limits."
                            )
                        }
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        copiedBytes += count
                        val percent = totalBytes?.let { total ->
                            ((copiedBytes * 100L) / total).coerceIn(0L, 100L).toInt()
                        }
                        if (percent != null && percent != lastReportedPercent) {
                            lastReportedPercent = percent
                            onProgress(percent / 100f)
                        }
                    }
                    output.flush()
            }
            val storedSize = writer.stageLength(target)
            if (storedSize <= 0L || storedSize != copiedBytes) {
                throw PortableStorageUnavailableException(
                    "The selected folder did not retain the complete download. The previous copy was kept."
                )
            }
            val openStage = { writer.openStageInput(target) }
            val valid = ReaderFileValidator.isReadable(book.mediaKind, storedSize, openStage)
            if (!valid) {
                val suffix = if (existingIsUsable) " The previous local copy was kept." else ""
                throw UserFacingException("The downloaded file did not pass integrity checks.$suffix")
            }
            val digestHex = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
            if (!runBlocking {
                    downloadStore.markPortableAttemptStateIfOwned(
                        serverUrl = serverUrl,
                        fileId = attempt.fileId,
                        requestId = requestId,
                        state = DownloadAttemptState.VERIFIED,
                        target = target,
                        verifiedSizeBytes = storedSize,
                        verifiedSha256 = digestHex,
                        storageScopeId = storageScopeId
                    )
                }
            ) {
                throw kotlinx.coroutines.CancellationException("This download was replaced by a newer request.")
            }
            onProgress(1f)
        }
        requireCapturedAccountScope()
        val published = localCopyDeletionGuard.withLock {
            val locator = writer.publish(target)
            publishedLocator = locator
            locator
        }
        val completedRecord = DownloadRecord(
            serverUrl = serverUrl,
            profileId = profileId,
            storageScopeId = storageScopeId,
            fileId = attempt.fileId,
            bookId = book.id,
            libraryId = book.libraryId,
            title = book.title,
            filename = book.filename ?: target.finalName,
            localPath = published,
            storageTreeUri = target.treeUri,
            storageRootUri = target.storageRootUri,
            mediaKind = book.mediaKind,
            mimeType = book.format,
            sourceUpdatedAtMillis = listOfNotNull(
                existingRecord?.sourceUpdatedAtMillis,
                book.updatedAtMillis
            ).maxOrNull(),
            downloadedAtMillis = existingRecord?.downloadedAtMillis ?: System.currentTimeMillis(),
            lastAccessedAtMillis = listOfNotNull(
                existingRecord?.lastAccessedAtMillis,
                book.lastReadAtMillis
            ).maxOrNull(),
            lastKnownCompleted = when {
                book.isRead -> true
                book.readStatus != null -> book.readStatus.isCompletedStatus()
                else -> existingRecord?.lastKnownCompleted
            },
            sizeBytes = access.length(published).takeIf { it > 0L }
                ?: attempt.expectedSizeBytes,
            origin = existingRecord?.origin ?: attempt.origin,
            status = DownloadRecordStatus.COMPLETE
        )
        existingRecord
            ?.takeUnless { localDownloadLocatorsMatch(it.localPath, published) }
            ?.let { PortableDownloadCleanup.defer(context, it) }
        val committed = localCopyDeletionGuard.withLock {
            downloadStore.commitPortableDownloadIfOwned(
                serverUrl = serverUrl,
                fileId = attempt.fileId,
                requestId = requestId,
                publishedLocator = published,
                record = completedRecord,
                storageScopeId = storageScopeId
            )
        }
        if (!committed) {
            writer.discardPublished(target, published)
            publishedLocator = null
            throw kotlinx.coroutines.CancellationException("This download was replaced by a newer request.")
        }
        PortableDownloadCleanup.schedule(context)
        return DownloadedBookFile(published)
    } catch (error: Throwable) {
        if (publishedLocator == null) writer.discard(target)
        throw error
    } finally {
        AutomaticDownloadStorage.releaseTransfer(context, reservationId)
    }
}

/**
 * Removes abandoned app-owned documents without ever treating a temporarily missing card as a
 * deletion. Every selected managed root is remembered, while live records, attempts, and reader
 * leases form the keep-set. Scans are serialized with stage creation and publication.
 */
internal object PortableDownloadCleanup {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val scheduled = AtomicBoolean(false)
    private val deferredMonitor = Any()

    fun defer(context: Context, record: DownloadRecord) {
        val preferences = context.applicationContext.getSharedPreferences(
            DEFERRED_CLEANUP_PREFERENCES,
            Context.MODE_PRIVATE
        )
        synchronized(deferredMonitor) {
            val values = readDeferred(preferences).filterNot {
                it.locator == record.localPath
            }.toMutableList()
            values += DeferredCopy(
                serverUrl = record.serverUrl,
                storageScopeId = record.storageScopeId,
                fileId = record.fileId,
                locator = record.localPath,
                storageRootUri = record.storageRootUri
            )
            writeDeferred(preferences, values)
        }
    }

    fun schedule(context: Context) {
        if (!scheduled.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        scope.launch {
            try {
                cleanupNow(appContext)
            } finally {
                scheduled.set(false)
            }
        }
    }

    suspend fun cleanupNow(context: Context): Int = localCopyDeletionGuard.withLock {
        val appContext = context.applicationContext
        val inventories = readDownloadStorageInventories(appContext)
        val allRecords = inventories.flatMap(DownloadStorageInventory::records)
        val liveLeases = LocalBookReaderLeaseStore(appContext).readLive()
        val referenced = buildSet {
            allRecords
                .map(DownloadRecord::localPath)
                .filter(String::isContentLocator)
                .forEach(::add)
            inventories.flatMap(DownloadStorageInventory::attempts).forEach { attempt ->
                listOfNotNull(attempt.targetPath, attempt.stagedPath)
                    .filter(String::isContentLocator)
                    .forEach(::add)
            }
            liveLeases
                .mapNotNull(LocalBookReaderLease::localPath)
                .filter(String::isContentLocator)
                .forEach(::add)
        }.toMutableSet()
        var removed = 0
        val deferredPreferences = appContext.getSharedPreferences(
            DEFERRED_CLEANUP_PREFERENCES,
            Context.MODE_PRIVATE
        )
        synchronized(deferredMonitor) {
            val remaining = readDeferred(deferredPreferences).filter { copy ->
                val stillCurrent = allRecords.any { record ->
                    record.serverUrl == copy.serverUrl &&
                        record.storageScopeId == copy.storageScopeId &&
                        record.fileId == copy.fileId &&
                        localDownloadLocatorsMatch(record.localPath, copy.locator)
                }
                val protected = liveLeases.any { lease ->
                    localReaderLeaseBlocksReplacement(
                        lease = lease,
                        serverUrl = copy.serverUrl,
                        storageScopeId = copy.storageScopeId,
                        fileId = copy.fileId,
                        targetPath = copy.locator
                    )
                }
                if (stillCurrent || protected) {
                    if (copy.locator.isContentLocator()) referenced += copy.locator
                    return@filter true
                }
                val deleted = deleteDeferredCopy(appContext, copy)
                if (deleted) removed++
                if (!deleted && copy.locator.isContentLocator()) referenced += copy.locator
                !deleted
            }
            writeDeferred(deferredPreferences, remaining)
        }
        DownloadStorageSelectionStore(appContext).knownManagedDirectoryUris().forEach { rootValue ->
            val rootUri = runCatching { Uri.parse(rootValue) }.getOrNull() ?: return@forEach
            val grantedTreeUri = persistedTreeUriForDocument(rootUri) ?: return@forEach
            if (!hasPersistedReadWritePermission(appContext.contentResolver, grantedTreeUri)) {
                return@forEach
            }
            DocumentFile.fromSingleUri(appContext, rootUri)
                ?.takeIf { it.exists() && it.isDirectory && it.canRead() && it.canWrite() }
                ?: return@forEach
            val accountDirectories = runCatching {
                listPortableChildren(appContext, rootUri)
            }.getOrNull() ?: return@forEach
            accountDirectories
                .asSequence()
                .filter { directory ->
                    directory.isDirectory &&
                        directory.name?.matches(Regex("account-[0-9a-f]{20}")) == true &&
                        documentBelongsToRoot(rootUri, directory.uri)
                }
                .flatMap { directory ->
                    runCatching { listPortableChildren(appContext, directory.uri) }
                        .getOrDefault(emptyList())
                        .asSequence()
                }
                .take(MAX_PORTABLE_CLEANUP_DOCUMENTS)
                .filter { document ->
                    document.isFile &&
                        document.uri.toString() !in referenced &&
                        documentBelongsToRoot(rootUri, document.uri)
                }
                .forEach { document ->
                    if (runCatching { document.delete() }.getOrDefault(false)) removed++
                }
        }
        removed
    }

    private fun deleteDeferredCopy(context: Context, copy: DeferredCopy): Boolean {
        if (copy.locator.isContentLocator()) {
            val root = copy.storageRootUri?.let(Uri::parse) ?: return false
            val document = Uri.parse(copy.locator)
            if (!documentBelongsToRoot(root, document)) return false
            val access = LocalDownloadAccess(context)
            if (!access.exists(copy.locator)) return true
            return runCatching {
                DocumentsContract.deleteDocument(context.contentResolver, document)
            }.getOrDefault(false)
        }
        val file = runCatching { File(copy.locator).canonicalFile }.getOrNull() ?: return false
        val owned = managedDownloadStorageRoots(context.filesDir).any { storageRoot ->
            val downloadRoot = runCatching { File(storageRoot, "downloads").canonicalFile }
                .getOrNull() ?: return@any false
            file.path.startsWith(downloadRoot.path + File.separator)
        }
        if (!owned) return false
        return !file.exists() || file.delete()
    }

    private fun readDeferred(preferences: android.content.SharedPreferences): List<DeferredCopy> {
        val raw = preferences.getString(DEFERRED_CLEANUP_KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val value = array.optJSONObject(index) ?: continue
                    val serverUrl = value.optString("serverUrl")
                    val storageScopeId = value.optString("storageScopeId")
                    val fileId = value.optString("fileId")
                    val locator = value.optString("locator")
                    if (serverUrl.isBlank() || storageScopeId.isBlank() ||
                        fileId.isBlank() || locator.isBlank()
                    ) continue
                    add(
                        DeferredCopy(
                            serverUrl = serverUrl,
                            storageScopeId = storageScopeId,
                            fileId = fileId,
                            locator = locator,
                            storageRootUri = value.optString("storageRootUri")
                                .takeIf(String::isNotBlank)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun writeDeferred(
        preferences: android.content.SharedPreferences,
        values: List<DeferredCopy>
    ) {
        val array = JSONArray()
        values.distinctBy(DeferredCopy::locator).forEach { copy ->
            array.put(
                JSONObject()
                    .put("serverUrl", copy.serverUrl)
                    .put("storageScopeId", copy.storageScopeId)
                    .put("fileId", copy.fileId)
                    .put("locator", copy.locator)
                    .put("storageRootUri", copy.storageRootUri)
            )
        }
        check(preferences.edit().putString(DEFERRED_CLEANUP_KEY, array.toString()).commit()) {
            "Unable to save deferred download cleanup."
        }
    }

    private data class DeferredCopy(
        val serverUrl: String,
        val storageScopeId: String,
        val fileId: String,
        val locator: String,
        val storageRootUri: String?
    )
}

internal fun String.isContentLocator(): Boolean =
    startsWith("content://", ignoreCase = true)

private fun OutputStream.asPortableDownloadOutput(): OutputStream = object : OutputStream() {
    override fun write(value: Int) = portableWrite { this@asPortableDownloadOutput.write(value) }

    override fun write(buffer: ByteArray, offset: Int, length: Int) = portableWrite {
        this@asPortableDownloadOutput.write(buffer, offset, length)
    }

    override fun flush() = portableWrite { this@asPortableDownloadOutput.flush() }

    override fun close() = portableWrite { this@asPortableDownloadOutput.close() }

    private fun portableWrite(action: () -> Unit) {
        try {
            action()
        } catch (error: IOException) {
            throw PortableStorageUnavailableException(
                "The selected folder could not save this download. Check that it is writable and has free space."
            ).also { it.addSuppressed(error) }
        }
    }
}

internal fun localDownloadLocatorsMatch(first: String, second: String): Boolean {
    if (first.isContentLocator() || second.isContentLocator()) return first == second
    return runCatching { File(first).canonicalFile == File(second).canonicalFile }
        .getOrDefault(false)
}

internal fun hasPersistedReadWritePermission(resolver: ContentResolver, treeUri: Uri): Boolean =
    resolver.persistedUriPermissions.any { permission ->
        permission.uri == treeUri && permission.isReadPermission && permission.isWritePermission
    }

internal fun persistedTreeUriForDocument(documentUri: Uri): Uri? = runCatching {
    val authority = documentUri.authority ?: return@runCatching null
    DocumentsContract.buildTreeDocumentUri(
        authority,
        DocumentsContract.getTreeDocumentId(documentUri)
    )
}.getOrNull()

private fun findPortableChild(context: Context, parentUri: Uri, displayName: String): DocumentFile? {
    val matches = listPortableChildren(context, parentUri).filter { it.name == displayName }
    if (matches.size > 1) {
        throw PortableStorageUnavailableException(
            "The selected folder contains duplicate entries named $displayName."
        )
    }
    return matches.singleOrNull()
}

private fun createPortableDirectory(
    context: Context,
    parentUri: Uri,
    displayName: String
): DocumentFile? = createPortableDocument(
    context = context,
    parentUri = parentUri,
    mimeType = DocumentsContract.Document.MIME_TYPE_DIR,
    displayName = displayName
)

private fun createPortableFile(
    context: Context,
    parentUri: Uri,
    mimeType: String,
    displayName: String
): DocumentFile? = createPortableDocument(context, parentUri, mimeType, displayName)

private fun createPortableDocument(
    context: Context,
    parentUri: Uri,
    mimeType: String,
    displayName: String
): DocumentFile? = runCatching {
    DocumentsContract.createDocument(
        context.contentResolver,
        portableDocumentUri(parentUri),
        mimeType,
        displayName
    )?.let { created ->
        val scoped = DocumentsContract.buildDocumentUriUsingTree(
            parentUri,
            DocumentsContract.getDocumentId(created)
        )
        DocumentFile.fromSingleUri(context, scoped)
    }
}.getOrNull()

private fun listPortableChildren(
    context: Context,
    parentUri: Uri,
    limit: Int = MAX_PORTABLE_CLEANUP_DOCUMENTS
): List<DocumentFile> = try {
    val documentParent = portableDocumentUri(parentUri)
    val parentDocumentId = DocumentsContract.getDocumentId(documentParent)
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
        documentParent,
        parentDocumentId
    )
    buildList {
        val cursor = context.contentResolver.query(
            childrenUri,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
            null,
            null,
            null
        ) ?: throw PortableStorageUnavailableException(
            "The selected folder could not be read."
        )
        cursor.use {
            val documentIdColumn = cursor.getColumnIndexOrThrow(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID
            )
            while (size < limit && cursor.moveToNext()) {
                val childUri = DocumentsContract.buildDocumentUriUsingTree(
                    documentParent,
                    cursor.getString(documentIdColumn)
                )
                DocumentFile.fromSingleUri(context, childUri)?.let(::add)
            }
        }
    }
} catch (error: PortableStorageUnavailableException) {
    throw error
} catch (error: Exception) {
    throw PortableStorageUnavailableException("The selected folder could not be read.").also {
        it.addSuppressed(error)
    }
}

private fun portableDocumentUri(uri: Uri): Uri =
    if (uri.path.orEmpty().contains("/document/")) {
        uri
    } else {
        DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
    }

internal fun documentBelongsToRoot(rootUri: Uri, documentUri: Uri): Boolean = runCatching {
    if (rootUri.authority != documentUri.authority) return@runCatching false
    val rootId = if (rootUri.path.orEmpty().contains("/document/")) {
        DocumentsContract.getDocumentId(rootUri)
    } else {
        DocumentsContract.getTreeDocumentId(rootUri)
    }
    val documentId = if (DocumentsContract.isTreeUri(documentUri) &&
        !documentUri.path.orEmpty().contains("/document/")) {
        DocumentsContract.getTreeDocumentId(documentUri)
    } else {
        DocumentsContract.getDocumentId(documentUri)
    }
    documentId == rootId || documentId.startsWith("$rootId/")
}.getOrDefault(false)

internal fun documentVolumeId(treeUri: Uri): String? = runCatching {
    DocumentsContract.getTreeDocumentId(treeUri).substringBefore(':').takeIf(String::isNotBlank)
}.getOrNull()

internal fun isSupportedDownloadVolume(
    volumeId: String?,
    removableVolumeAvailable: Boolean
): Boolean {
    val normalized = volumeId?.trim().orEmpty()
    if (normalized.isBlank()) return false
    return normalized.equals("primary", ignoreCase = true) || removableVolumeAvailable
}

internal fun portableSafeFileName(value: String): String = value
    .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), " ")
    .replace(Regex("\\s+"), " ")
    .trim(' ', '.')

internal fun portableFileExtension(value: String?): String {
    val token = value.orEmpty().substringBefore(';').trim().lowercase()
    val supportedExtensions = setOf(
        "azw3", "azw", "mobi", "epub", "pdf", "m4b", "m4a", "mp3",
        "ogg", "oga", "opus", "flac", "cbz", "cbr", "cb7"
    )
    if (token in supportedExtensions) return token
    val extension = token.substringAfterLast('/', token).substringAfterLast('.', "")
        .filter(Char::isLetterOrDigit)
        .take(8)
    if (extension in supportedExtensions) return extension
    return when (token) {
        "application/epub+zip", "application/epub" -> "epub"
        "application/pdf" -> "pdf"
        "application/x-mobipocket-ebook", "application/vnd.amazon.ebook" -> "mobi"
        "application/vnd.comicbook+zip", "application/x-cbz" -> "cbz"
        "application/vnd.comicbook-rar", "application/x-cbr" -> "cbr"
        "audio/mpeg" -> "mp3"
        "audio/mp4", "audio/x-m4a" -> "m4a"
        "audio/ogg" -> "ogg"
        "audio/opus" -> "opus"
        "audio/flac", "audio/x-flac" -> "flac"
        else -> extension.takeIf { it.length in 2..8 } ?: "bin"
    }
}

private fun portableMimeType(extension: String): String = when (extension.lowercase()) {
    "epub" -> "application/epub+zip"
    "pdf" -> "application/pdf"
    "cbz" -> "application/vnd.comicbook+zip"
    "cbr" -> "application/vnd.comicbook-rar"
    "mp3" -> "audio/mpeg"
    "m4a", "m4b" -> "audio/mp4"
    "ogg", "oga", "opus" -> "audio/ogg"
    "flac" -> "audio/flac"
    else -> "application/octet-stream"
}

private fun queryDocumentSize(resolver: ContentResolver, uri: Uri): Long? = runCatching {
    resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst() || cursor.isNull(0)) null else cursor.getLong(0).takeIf { it >= 0L }
    }
}.getOrNull()

private fun queryDocumentName(resolver: ContentResolver, uri: Uri): String? = runCatching {
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) null else cursor.getString(0)?.takeIf(String::isNotBlank)
    }
}.getOrNull()

private fun removableVolumeDirectory(context: Context, volumeId: String): File? {
    val normalized = volumeId.lowercase()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val manager = context.getSystemService(android.os.storage.StorageManager::class.java)
        manager?.storageVolumes?.firstOrNull { volume ->
            volume.isRemovable && volume.uuid?.lowercase() == normalized
        }?.directory?.takeIf { it.exists() }?.let { return it }
    }
    return context.getExternalFilesDirs(null).firstOrNull { directory ->
        directory != null &&
            Environment.getExternalStorageState(directory) == Environment.MEDIA_MOUNTED &&
            directory.absolutePath.replace('\\', '/').lowercase().contains("/storage/$normalized/")
    }
}

private fun sha256Hex(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8))
    .joinToString("") { byte -> "%02x".format(byte) }

internal fun portableReaderCacheIdentity(fileId: String, locator: String, size: Long): String =
    sha256Hex("$fileId\u0000$locator\u0000$size").take(32)

private fun portableSaturatingAdd(first: Long, second: Long): Long =
    if (Long.MAX_VALUE - first < second) Long.MAX_VALUE else first + second

private const val PORTABLE_READER_CACHE_MAX_RETAINED_BYTES = 512L * 1024L * 1024L
private const val PORTABLE_READER_FREE_MARGIN_BYTES = 512L * 1024L * 1024L
private const val PORTABLE_WRITE_FREE_MARGIN_BYTES = 8L * 1024L * 1024L
private const val PORTABLE_SPACE_RECHECK_BYTES = 4L * 1024L * 1024L
private const val MAX_PORTABLE_CLEANUP_DOCUMENTS = 20_000
private const val DEFERRED_CLEANUP_PREFERENCES = "portable_download_cleanup"
private const val DEFERRED_CLEANUP_KEY = "deferred_copies"
