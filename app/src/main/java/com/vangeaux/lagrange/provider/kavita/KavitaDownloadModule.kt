package com.vangeaux.lagrange.provider.kavita

import android.content.Context
import com.vangeaux.lagrange.*
import com.vangeaux.lagrange.core.DownloadModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

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

    override suspend fun downloadBook(book: BookSummary, onProgress: (Float?) -> Unit): File =
        withContext(Dispatchers.IO) {
            val base = auth.serverUrl() ?: throw UserFacingException("Enter a valid Kavita server URL.")
            val fileId = book.fileId ?: chapterId(book)
            val existing = downloadStore.find(base, fileId)
            val target = existing?.localPath?.let(::File)
                ?: downloadStore.downloadTarget(fileId, book.title, book.mediaKind, book.format)
            if (existing != null && downloadedFilePassesIntegrity(book, target)) {
                onProgress(1f)
                return@withContext target
            }

            target.parentFile?.mkdirs()
            val temporary = File(target.parentFile, ".${target.name}.part")
            if (temporary.exists()) temporary.delete()
            val request = Request.Builder()
                .url(kavitaDownloadEndpoint(base, chapterId(book)))
                .apply { auth.requestHeaders().forEach { (name, value) -> header(name, value) } }
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw UserFacingException("Unable to download Kavita chapter (HTTP ${response.code}).")
                }
                val body = response.body ?: throw UserFacingException("Kavita returned an empty chapter file.")
                val total = body.contentLength().takeIf { it > 0L }
                var copied = 0L
                onProgress(0f.takeIf { total != null })
                body.byteStream().use { input ->
                    FileOutputStream(temporary).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            copied += count
                            onProgress(total?.let { (copied.toFloat() / it).coerceIn(0f, 1f) })
                        }
                    }
                }
                if (!downloadedFilePassesIntegrity(book, temporary)) {
                    throw UserFacingException("The downloaded Kavita chapter failed integrity validation.")
                }
            }
            atomicReplaceDownloadedFile(temporary, target)
            downloadStore.save(
                DownloadRecord(
                    serverUrl = base,
                    fileId = fileId,
                    bookId = book.id,
                    title = book.title,
                    filename = book.filename,
                    localPath = target.absolutePath,
                    mediaKind = book.mediaKind,
                    mimeType = book.format,
                    sourceUpdatedAtMillis = book.updatedAtMillis
                )
            )
            onProgress(1f)
            target
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
        .ifBlank { throw UserFacingException("Kavita chapter identity is unavailable.") }
}
