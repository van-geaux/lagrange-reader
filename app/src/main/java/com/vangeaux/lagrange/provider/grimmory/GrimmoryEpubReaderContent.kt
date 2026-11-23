package com.vangeaux.lagrange.provider.grimmory

import com.vangeaux.lagrange.AuthenticationRequiredException
import com.vangeaux.lagrange.BookSummary
import com.vangeaux.lagrange.EpubReaderScope
import com.vangeaux.lagrange.MediaKind
import com.vangeaux.lagrange.ReaderFileValidator
import com.vangeaux.lagrange.UserFacingException
import com.vangeaux.lagrange.normalizeServerUrl
import com.vangeaux.lagrange.unambiguousPersistenceKey
import com.vangeaux.lagrange.core.AuthenticatedMediaModule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.security.MessageDigest
import java.util.UUID

internal fun grimmoryEpubContentUrl(scope: EpubReaderScope, book: BookSummary): String {
    val base = normalizeServerUrl(scope.serverUrl)
        ?: throw UserFacingException("Enter a valid Grimmory server URL.")
    val bookId = book.id.toLongOrNull()?.takeIf { it >= 0L }?.toString()
        ?: throw UserFacingException("Grimmory book identity is unavailable.")
    val url = grimmoryApiUrl(base, "/api/v1/books/$bookId/content").toHttpUrlOrNull()
        ?: throw UserFacingException("Grimmory returned an invalid EPUB content URL.")
    return url.newBuilder()
        .addQueryParameter("bookType", "EPUB")
        .build()
        .toString()
}

internal class GrimmoryEpubReaderContentTransport(
    private val client: OkHttpClient,
    private val authenticatedMedia: AuthenticatedMediaModule
) {
    @OptIn(InternalCoroutinesApi::class)
    suspend fun open(scope: EpubReaderScope, book: BookSummary): Response = withContext(Dispatchers.IO) {
        val url = grimmoryEpubContentUrl(scope, book)
        suspend fun request(): Request = Request.Builder()
            .url(url)
            .header("Accept", "*/*")
            .apply { authenticatedMedia.requestHeaders(url).forEach { (name, value) -> header(name, value) } }
            .get()
            .build()

        suspend fun executeRequest(): Response {
            val call = client.newCall(request())
            currentCoroutineContext().job.invokeOnCompletion(
                onCancelling = true,
                invokeImmediately = true
            ) { cause ->
                if (cause is CancellationException) call.cancel()
            }
            return call.execute()
        }

        var response = executeRequest()
        if (response.code == 401 && authenticatedMedia.recoverAuthentication()) {
            response.close()
            response = executeRequest()
        }
        response
    }
}

/** Private, account-scoped cache used only to hand a valid EPUB file to Readium. */
internal class GrimmoryEpubReaderContentCache(
    private val cacheDirectory: File,
    private val transport: GrimmoryEpubReaderContentTransport
) {
    private val mutex = Mutex()

    suspend fun find(scope: EpubReaderScope, book: BookSummary): File? = withContext(Dispatchers.IO) {
        mutex.withLock { validCachedFile(scope, book) }
    }

    suspend fun prepare(scope: EpubReaderScope, book: BookSummary): File = withContext(Dispatchers.IO) {
        if (book.mediaKind != MediaKind.EPUB) {
            throw UserFacingException("Grimmory reader preparation is available only for EPUB books.")
        }
        mutex.withLock {
            validCachedFile(scope, book)?.let { return@withLock it }
            if (!cacheDirectory.exists() && !cacheDirectory.mkdirs()) {
                throw UserFacingException("Unable to prepare private Grimmory reader storage.")
            }
            val target = cacheFile(scope, book)
            if (target.exists() && !target.delete()) {
                throw UserFacingException("Unable to replace the cached Grimmory EPUB.")
            }
            val staged = File(cacheDirectory, ".${target.name}.${UUID.randomUUID()}.part")
            try {
                transport.open(scope, book).use { response ->
                    if (!response.isSuccessful) {
                        if (response.code == 401 || response.code == 403) {
                            throw AuthenticationRequiredException()
                        }
                        throw GrimmoryHttpException(response.code)
                    }
                    val body = response.body
                        ?: throw UserFacingException("Grimmory returned an empty EPUB file.")
                    val expectedLength = body.contentLength().takeIf { it >= 0L }
                    var copiedLength = 0L
                    body.byteStream().use { input ->
                        FileOutputStream(staged).use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                if (count == 0) continue
                                output.write(buffer, 0, count)
                                copiedLength += count
                            }
                        }
                    }
                    if (
                        !staged.isFile || copiedLength <= 0L ||
                        (expectedLength != null && copiedLength != expectedLength)
                    ) {
                        throw UserFacingException("Grimmory returned an empty or incomplete EPUB file.")
                    }
                    if (!ReaderFileValidator.isReadable(MediaKind.EPUB, staged)) {
                        throw UserFacingException("Grimmory returned content that is not a readable EPUB.")
                    }
                }
                try {
                    Files.move(
                        staged.toPath(),
                        target.toPath(),
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING
                    )
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(
                        staged.toPath(),
                        target.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING
                    )
                }
                target
            } finally {
                staged.delete()
            }
        }
    }

    private fun validCachedFile(scope: EpubReaderScope, book: BookSummary): File? {
        val target = cacheFile(scope, book)
        if (!target.isFile) return null
        if (ReaderFileValidator.isReadable(MediaKind.EPUB, target)) return target
        target.delete()
        return null
    }

    private fun cacheFile(scope: EpubReaderScope, book: BookSummary): File {
        val identity = unambiguousPersistenceKey(
            normalizeServerUrl(scope.serverUrl).orEmpty(),
            scope.profileId,
            scope.providerId,
            scope.accountScope,
            book.id,
            book.fileId.orEmpty(),
            book.updatedAtMillis?.toString().orEmpty(),
            "EPUB"
        )
        val token = MessageDigest.getInstance("SHA-256")
            .digest(identity.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }
        return File(cacheDirectory, "$token.epub")
    }
}
