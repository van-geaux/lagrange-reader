package com.vangeaux.lagrange.provider.grimmory

import com.vangeaux.lagrange.AuthenticationRequiredException
import com.vangeaux.lagrange.BookReadStatus
import com.vangeaux.lagrange.BookSummary
import com.vangeaux.lagrange.MediaKind
import com.vangeaux.lagrange.UserFacingException
import com.vangeaux.lagrange.UserFacingReason
import com.vangeaux.lagrange.core.AuthenticatedMediaModule
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GrimmoryReadingStatusModuleTest {
    @Test
    fun `writes each advertised status to native single book endpoint`() = runBlocking {
        MockWebServer().use { server ->
            repeat(5) { server.enqueue(MockResponse().setResponseCode(204)) }
            server.start()
            val auth = TestStatusAuth()
            val module = GrimmoryReadingStatusModule(
                client = OkHttpClient(),
                auth = auth,
                serverUrlProvider = { server.url("/library/").toString().trimEnd('/') }
            )
            val book = testBook()
            val expected = listOf(
                BookReadStatus.UNREAD to "UNREAD",
                BookReadStatus.READING to "READING",
                BookReadStatus.REREADING to "RE_READING",
                BookReadStatus.READ to "READ",
                BookReadStatus.ABANDONED to "ABANDONED"
            )

            expected.forEach { (status, wireValue) ->
                if (status == BookReadStatus.UNREAD) {
                    module.resetBookReadingState(book)
                } else {
                    module.setBookReadingStatus(book, status)
                }
                val request = server.takeRequest()
                assertEquals("PUT", request.method)
                assertEquals("/library/api/v1/app/books/42/status", request.path)
                assertEquals("Bearer status-test-token", request.getHeader("Authorization"))
                assertEquals(wireValue, JSONObject(request.body.readUtf8()).optString("status"))
            }
            assertEquals(expected.size, server.requestCount)
        }
    }

    @Test
    fun `status writes refresh authentication once after unauthorized response`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(401))
            server.enqueue(MockResponse().setResponseCode(204))
            server.start()
            val auth = TestStatusAuth(recover = true)
            val module = GrimmoryReadingStatusModule(
                client = OkHttpClient(),
                auth = auth,
                serverUrlProvider = { server.url("/").toString().trimEnd('/') }
            )

            module.markBookAsRead(testBook())

            assertEquals("Bearer status-test-token", server.takeRequest().getHeader("Authorization"))
            val retried = server.takeRequest()
            assertEquals("PUT", retried.method)
            assertEquals("/api/v1/app/books/42/status", retried.path)
            assertEquals("Bearer refreshed-status-token", retried.getHeader("Authorization"))
            assertEquals(1, auth.recoveryCalls)
        }
    }

    @Test
    fun `unsupported shared statuses fail explicitly without making a request`() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val module = GrimmoryReadingStatusModule(
                client = OkHttpClient(),
                auth = TestStatusAuth(),
                serverUrlProvider = { server.url("/").toString().trimEnd('/') }
            )

            listOf(BookReadStatus.WANT_TO_READ, BookReadStatus.ON_HOLD, BookReadStatus.SKIMMED).forEach { status ->
                val error = runCatching { module.setBookReadingStatus(testBook(), status) }.exceptionOrNull()
                assertTrue("$status should be explicitly unavailable", error is UserFacingException)
                assertEquals(UserFacingReason.CAPABILITY_UNAVAILABLE, (error as UserFacingException).reason)
            }
            assertEquals(0, server.requestCount)
        }
    }

    @Test
    fun `server failures are not reported as successful status updates`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(503))
            server.start()
            val module = GrimmoryReadingStatusModule(
                client = OkHttpClient(),
                auth = TestStatusAuth(),
                serverUrlProvider = { server.url("/").toString().trimEnd('/') }
            )

            val error = runCatching { module.setBookReadingStatus(testBook(), BookReadStatus.READ) }.exceptionOrNull()
            assertTrue(error is GrimmoryHttpException)
            assertEquals(503, (error as GrimmoryHttpException).statusCode)
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun `forbidden status writes require authentication`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(403))
            server.start()
            val module = GrimmoryReadingStatusModule(
                client = OkHttpClient(),
                auth = TestStatusAuth(),
                serverUrlProvider = { server.url("/").toString().trimEnd('/') }
            )

            val error = runCatching { module.setBookReadingStatus(testBook(), BookReadStatus.READ) }.exceptionOrNull()
            assertTrue(error is AuthenticationRequiredException)
            assertEquals(1, server.requestCount)
        }
    }

    private fun testBook(id: String = "42") = BookSummary(
        libraryId = "7",
        id = id,
        fileId = "99",
        title = "A Story",
        format = "EPUB",
        mediaKind = MediaKind.EPUB
    )

    private class TestStatusAuth(private val recover: Boolean = false) : AuthenticatedMediaModule {
        var recoveryCalls = 0
            private set

        override suspend fun requestHeaders(url: String): Map<String, String> = mapOf(
            "Authorization" to if (recoveryCalls > 0) "Bearer refreshed-status-token" else "Bearer status-test-token"
        )

        override suspend fun recoverAuthentication(): Boolean {
            recoveryCalls += 1
            return recover && recoveryCalls == 1
        }
    }
}
