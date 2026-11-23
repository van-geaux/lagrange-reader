package com.vangeaux.lagrange.provider.grimmory

import com.vangeaux.lagrange.AuthenticationRequiredException
import com.vangeaux.lagrange.BookSummary
import com.vangeaux.lagrange.EpubReaderScope
import com.vangeaux.lagrange.MediaKind
import com.vangeaux.lagrange.ReaderFileValidator
import com.vangeaux.lagrange.core.AuthenticatedMediaModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class GrimmoryEpubReaderContentTest {
    @Test
    fun `reader cache state keeps temporary file outside the downloaded book identity`() {
        val book = epubBook()
        val cachedFile = File("/private-cache/grimmory/epub-cache.epub")

        val state = grimmoryEpubReaderState(book, cachedFile)

        assertSame(book, state.book)
        assertEquals(cachedFile, state.localFile)
        assertNull(state.streamUrl)
        assertFalse(state.book.isDownloaded)
    }

    @Test
    fun `materializes native authenticated EPUB content without making the book downloaded`() = runBlocking {
        val bytes = epubBytes()
        val cacheDirectory = Files.createTempDirectory("grimmory-reader-cache").toFile()
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(Buffer().write(bytes)))
            server.start()
            val media = RecordingAuthenticatedMedia(server.url("/mount/").toString())
            val cache = GrimmoryEpubReaderContentCache(
                cacheDirectory = cacheDirectory,
                transport = GrimmoryEpubReaderContentTransport(OkHttpClient(), media)
            )
            val book = epubBook()

            val file = cache.prepare(scope(server.url("/mount/").toString()), book)

            assertTrue(ReaderFileValidator.isReadable(MediaKind.EPUB, file))
            assertArrayEquals(bytes, file.readBytes())
            assertFalse(book.isDownloaded)
            assertTrue(file.canonicalPath.startsWith(cacheDirectory.canonicalPath))
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/mount/api/v1/books/42/content?bookType=EPUB", request.path)
            assertEquals("Bearer reader-token", request.getHeader("Authorization"))
            assertEquals(server.url("/mount/api/v1/books/42/content?bookType=EPUB").toString(), media.requestedUrl)
        }
    }

    @Test
    fun `reuses content cache only within the same profile and account scope`() = runBlocking {
        val cacheDirectory = Files.createTempDirectory("grimmory-reader-cache-scope").toFile()
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(Buffer().write(epubBytes())))
            server.enqueue(MockResponse().setBody(Buffer().write(epubBytes())))
            server.start()
            val cache = GrimmoryEpubReaderContentCache(
                cacheDirectory = cacheDirectory,
                transport = GrimmoryEpubReaderContentTransport(
                    OkHttpClient(),
                    RecordingAuthenticatedMedia(server.url("/").toString())
                )
            )
            val book = epubBook()
            val firstScope = scope(server.url("/").toString(), account = "account-a")
            val sameScope = firstScope.copy()
            val otherAccount = firstScope.copy(accountScope = "account-b")

            val first = cache.prepare(firstScope, book)
            val reused = cache.prepare(sameScope, book)
            val isolated = cache.prepare(otherAccount, book)

            assertEquals(first.canonicalPath, reused.canonicalPath)
            assertNotEquals(first.canonicalPath, isolated.canonicalPath)
            assertEquals(2, server.requestCount)
        }
    }

    @Test
    fun `rejects non EPUB response and removes its staging file`() = runBlocking {
        val cacheDirectory = Files.createTempDirectory("grimmory-reader-cache-invalid").toFile()
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("not an EPUB"))
            server.start()
            val cache = GrimmoryEpubReaderContentCache(
                cacheDirectory = cacheDirectory,
                transport = GrimmoryEpubReaderContentTransport(
                    OkHttpClient(),
                    RecordingAuthenticatedMedia(server.url("/").toString())
                )
            )

            val failure = runCatching { cache.prepare(scope(server.url("/").toString()), epubBook()) }
                .exceptionOrNull()

            assertTrue("Expected invalid EPUB content to fail", failure != null)
            assertTrue(cacheDirectory.listFiles().orEmpty().isEmpty())
        }
    }

    @Test
    fun `maps forbidden native content response to authentication required`() = runBlocking {
        val cacheDirectory = Files.createTempDirectory("grimmory-reader-cache-auth").toFile()
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(403))
            server.start()
            val cache = GrimmoryEpubReaderContentCache(
                cacheDirectory = cacheDirectory,
                transport = GrimmoryEpubReaderContentTransport(
                    OkHttpClient(),
                    RecordingAuthenticatedMedia(server.url("/").toString())
                )
            )

            val failure = runCatching { cache.prepare(scope(server.url("/").toString()), epubBook()) }
                .exceptionOrNull()

            assertTrue(failure is AuthenticationRequiredException)
            assertTrue(cacheDirectory.listFiles().orEmpty().isEmpty())
        }
    }

    @Test
    fun `cancelling reader preparation cancels the pending native HTTP request`() = runBlocking {
        val cacheDirectory = Files.createTempDirectory("grimmory-reader-cache-cancel").toFile()
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            server.start()
            val client = OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS).build()
            val cache = GrimmoryEpubReaderContentCache(
                cacheDirectory = cacheDirectory,
                transport = GrimmoryEpubReaderContentTransport(
                    client,
                    RecordingAuthenticatedMedia(server.url("/").toString())
                )
            )
            supervisorScope {
                val preparation = async(Dispatchers.IO) {
                    cache.prepare(scope(server.url("/").toString()), epubBook())
                }

                assertTrue(server.takeRequest(2, TimeUnit.SECONDS) != null)
                preparation.cancel()
                val completedPromptly = withTimeoutOrNull(1_000) {
                    preparation.join()
                    true
                } ?: false

                assertTrue("Cancelled reader preparation left the HTTP call running", completedPromptly)
            }
        }
    }

    private fun scope(serverUrl: String, account: String = "account-a") = EpubReaderScope(
        serverUrl = serverUrl.trimEnd('/'),
        profileId = "profile-a",
        providerId = "grimmory",
        accountScope = account
    )

    private fun epubBook() = BookSummary(
        libraryId = "7",
        id = "42",
        fileId = "99",
        title = "A Story",
        format = "EPUB",
        mediaKind = MediaKind.EPUB
    )

    private fun epubBytes(): ByteArray = ByteArrayOutputStream().use { output ->
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("META-INF/container.xml"))
            zip.write("<container/>".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("OPS/chapter.xhtml"))
            zip.write("<html><body>content</body></html>".toByteArray())
            zip.closeEntry()
        }
        output.toByteArray()
    }

    private class RecordingAuthenticatedMedia(private val expectedBase: String) : AuthenticatedMediaModule {
        var requestedUrl: String? = null

        override suspend fun requestHeaders(url: String): Map<String, String> {
            require(url.startsWith(expectedBase.trimEnd('/')))
            requestedUrl = url
            return mapOf("Authorization" to "Bearer reader-token")
        }

        override suspend fun recoverAuthentication(): Boolean = false
    }
}
