package com.vangeaux.lagrange.provider.grimmory

import com.vangeaux.lagrange.BookSummary
import com.vangeaux.lagrange.MediaKind
import com.vangeaux.lagrange.core.AuthenticatedMediaModule
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GrimmoryDownloadModuleTest {
    @Test
    fun downloadTransportUsesNativeRouteAndLeavesBodyStreaming() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setChunkedBody("native-download-payload", 4))
        server.start()
        try {
            val transport = GrimmoryDownloadTransport(OkHttpClient(), object : AuthenticatedMediaModule {
                override suspend fun requestHeaders(url: String): Map<String, String> {
                    assertEquals(grimmoryDownloadEndpoint(server.url("/lab/").toString(), "42"), url)
                    return mapOf("Authorization" to "Bearer access-token")
                }
                override suspend fun recoverAuthentication() = false
            })
            val response = transport.openBlocking(server.url("/lab/").toString(), "42")
            response.use {
                assertEquals(200, it.code)
                val body = requireNotNull(it.body)
                assertEquals("chunked", it.header("Transfer-Encoding"))
                val input = body.byteStream()
                val first = ByteArray(4)
                assertEquals(4, input.read(first))
                assertEquals("nati", String(first))
                assertEquals("ve-download-payload", input.readBytes().decodeToString())
            }

            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/lab/api/v1/books/42/download", request.path)
            assertEquals("Bearer access-token", request.getHeader("Authorization"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `alternate file download uses its exact file id`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("alternate-file"))
        server.start()
        try {
            val transport = GrimmoryDownloadTransport(OkHttpClient(), object : AuthenticatedMediaModule {
                override suspend fun requestHeaders(url: String) = emptyMap<String, String>()
                override suspend fun recoverAuthentication() = false
            })
            transport.openBlocking(server.url("/lab/").toString(), "42", "101").use { response ->
                assertEquals(200, response.code)
                assertEquals("alternate-file", requireNotNull(response.body).string())
            }

            assertEquals(
                "/lab/api/v1/books/42/files/101/download",
                server.takeRequest().path
            )
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun downloadTransportMapsAuthenticationAndHttpFailures() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(403))
        server.enqueue(MockResponse().setResponseCode(503))
        server.start()
        try {
            val transport = GrimmoryDownloadTransport(OkHttpClient(), object : AuthenticatedMediaModule {
                override suspend fun requestHeaders(url: String) = emptyMap<String, String>()
                override suspend fun recoverAuthentication() = false
            })
            val forbidden = transport.open(server.url("/").toString(), "42")
            val authError = runCatching { requireGrimmoryDownloadSuccess(forbidden) }.exceptionOrNull()
            forbidden.close()
            assertTrue(authError is com.vangeaux.lagrange.AuthenticationRequiredException)

            val unavailable = transport.open(server.url("/").toString(), "42")
            val httpError = runCatching { requireGrimmoryDownloadSuccess(unavailable) }.exceptionOrNull()
            unavailable.close()
            assertTrue(httpError is com.vangeaux.lagrange.HttpRequestException)
            assertEquals(503, (httpError as com.vangeaux.lagrange.HttpRequestException).code)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `selected alternate file download identity resolves to file-specific endpoint`() {
        val base = "https://books.example/lab"
        val alternate = BookSummary(
            libraryId = "7",
            id = "42",
            fileId = "101",
            title = "A Story",
            format = "M4B",
            mediaKind = MediaKind.AUDIO,
            downloadUrl = grimmoryDownloadEndpoint(base, "42", "101")
        )
        val primary = alternate.copy(downloadUrl = grimmoryDownloadEndpoint(base, "42"))

        assertEquals("101", grimmoryAdditionalFileIdForDownload(alternate, base))
        assertEquals(null, grimmoryAdditionalFileIdForDownload(primary, base))
    }

    @Test
    fun `native download discovery keeps EPUB as the single item`() {
        val epub = BookSummary(
            libraryId = "library",
            id = "epub-book",
            fileId = "epub-file",
            title = "An EPUB",
            format = "EPUB",
            mediaKind = MediaKind.EPUB
        )
        val audiobook = BookSummary(
            libraryId = "library",
            id = "audio-book",
            fileId = "audio-file",
            title = "An audiobook",
            format = "M4B",
            mediaKind = MediaKind.AUDIO
        )

        assertEquals(listOf(epub), grimmoryDownloadFilesForBook(epub))
        assertEquals(listOf(audiobook), grimmoryDownloadFilesForBook(audiobook))
    }
}
