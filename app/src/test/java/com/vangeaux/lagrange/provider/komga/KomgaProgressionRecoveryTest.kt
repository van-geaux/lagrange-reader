package com.vangeaux.lagrange.provider.komga

import com.vangeaux.lagrange.BookSummary
import com.vangeaux.lagrange.ServerCheckResult
import com.vangeaux.lagrange.SessionState
import com.vangeaux.lagrange.UserFacingException
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KomgaProgressionRecoveryTest {
    @Test
    fun `snap chooses the last known point in the same resource not ahead of the reader`() {
        val positions = JSONObject(POSITIONS).getJSONArray("positions")
        val wanted = JSONObject(
            """{"href":"OEBPS/chapter.xhtml","locations":{"progression":0.6,"totalProgression":0.6}}"""
        )

        val snapped = komgaSnapLocator(positions, wanted)

        assertEquals(0.4, snapped!!.getJSONObject("locations").getDouble("progression"), 1e-9)
    }

    @Test
    fun `snap uses book progression when the wanted resource is absent`() {
        val positions = JSONObject(POSITIONS).getJSONArray("positions")
        val wanted = JSONObject(
            """{"href":"missing.xhtml","locations":{"progression":0.6,"totalProgression":0.51}}"""
        )

        val snapped = komgaSnapLocator(positions, wanted)

        assertEquals("OEBPS/other.xhtml", snapped!!.getString("href"))
    }

    @Test
    fun `rejected locator does not snap forward within the same resource`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(400))
            server.enqueue(
                MockResponse().setBody(
                    """{"positions":[{"href":"OEBPS/chapter.xhtml","locations":{"progression":0.8}}]}"""
                )
            )
            val module = progressModule(server)
            val wanted = """{"href":"OEBPS/chapter.xhtml","locations":{"progression":0.4}}"""

            val failure = runCatching {
                module.queueProgress(progressBook(wanted), 0, 0, 0.4f)
            }.exceptionOrNull()

            assertNotNull(failure)
            assertTrue(failure is UserFacingException)
            assertEquals(2, server.requestCount)
        }
    }

    @Test
    fun `snap refuses to guess without a matching resource or total progression`() {
        val positions = JSONObject(POSITIONS).getJSONArray("positions")
        val wanted = JSONObject(
            """{"href":"missing.xhtml","locations":{"progression":0.6}}"""
        )

        assertNull(komgaSnapLocator(positions, wanted))
        assertNull(komgaSnapLocator(JSONArray(), wanted))
        assertNull(
            komgaSnapLocator(
                JSONArray().put(JSONObject("""{"href":"other.xhtml"}""")),
                JSONObject("""{"href":"missing.xhtml","locations":{"totalProgression":0.6}}""")
            )
        )
    }

    @Test
    fun `conflicting progression is not treated as an unplaceable locator`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(409))
            val module = progressModule(server)

            val failure = runCatching {
                module.queueProgress(progressBook(), 0, 0, 0.6f)
            }.exceptionOrNull()

            assertTrue(failure is UserFacingException)
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun `rejected locator is snapped and retried once using Komga positions`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(400).setBody("Invalid progression"))
            server.enqueue(MockResponse().setBody(POSITIONS))
            server.enqueue(MockResponse().setResponseCode(204))
            val module = progressModule(server)
            val book = progressBook()

            module.queueProgress(book, position = 0, pageIndex = 0, progressPercent = 0.6f)

            val original = server.takeRequest()
            assertEquals("PUT", original.method)
            assertEquals("/api/v1/books/book-1/progression", original.path)
            assertEquals("Basic test", original.getHeader("Authorization"))
            val positions = server.takeRequest()
            assertEquals("GET", positions.method)
            assertEquals("/api/v1/books/book-1/positions", positions.path)
            val retry = server.takeRequest()
            assertEquals("PUT", retry.method)
            val sentLocator = JSONObject(retry.body.readUtf8()).getJSONObject("locator")
            assertEquals("OEBPS/chapter.xhtml", sentLocator.getString("href"))
            assertEquals(0.4, sentLocator.getJSONObject("locations").getDouble("progression"), 1e-9)
            assertEquals(3, server.requestCount)
        }
    }

    @Test
    fun `unplaceable rejected locator remains an explicit failure`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(400))
            server.enqueue(MockResponse().setBody("""{"positions":[]}"""))
            val module = progressModule(server)

            val failure = runCatching {
                module.queueProgress(progressBook("""{"href":"missing.xhtml","locations":{"progression":0.6}}"""), 0, 0, 0.6f)
            }.exceptionOrNull()

            assertNotNull(failure)
            assertTrue(failure is UserFacingException)
            assertEquals(2, server.requestCount)
        }
    }

    @Test
    fun `rejected snapped retry is not treated as success`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(400))
            server.enqueue(MockResponse().setBody(POSITIONS))
            server.enqueue(MockResponse().setResponseCode(400))
            val module = progressModule(server)

            val failure = runCatching {
                module.queueProgress(progressBook(), 0, 0, 0.6f)
            }.exceptionOrNull()

            assertTrue(failure is UserFacingException)
            assertEquals(3, server.requestCount)
        }
    }

    private fun progressModule(server: MockWebServer) = KomgaReadingProgressModuleImpl(
        auth = FakeKomgaAuth(),
        serverUrlProvider = { server.url("/").toString().trimEnd('/') },
        client = OkHttpClient()
    )

    private fun progressBook(locator: String =
        """{"href":"OEBPS/chapter.xhtml","type":"application/xhtml+xml","locations":{"progression":0.6,"totalProgression":0.6}}"""
    ) = BookSummary("library-1", "book-1", "file-1", "Book", readerLocatorJson = locator)

    private class FakeKomgaAuth : KomgaAuthModule {
        override suspend fun checkServer(serverUrl: String) = ServerCheckResult.Reachable
        override suspend fun login(serverUrl: String, username: String, password: String) = Unit
        override suspend fun sessionState(serverUrl: String) = SessionState.Authenticated
        override suspend fun clearSession() = Unit
        override fun localizedMessage(messageRes: Int, fallback: String, vararg formatArgs: Any) = fallback
        override fun requestHeaders(serverUrl: String) = mapOf("Authorization" to "Basic test")
        override fun credentialOrigin(): String? = null
    }

    private companion object {
        val POSITIONS = """
            {"positions":[
              {"href":"OEBPS/chapter.xhtml","locations":{"progression":0.0,"totalProgression":0.1,"position":1}},
              {"href":"OEBPS/chapter.xhtml","locations":{"progression":0.4,"totalProgression":0.4,"position":2}},
              {"href":"OEBPS/chapter.xhtml","locations":{"progression":0.8,"totalProgression":0.7,"position":3}},
              {"href":"OEBPS/other.xhtml","locations":{"progression":0.2,"totalProgression":0.5,"position":4}}
            ]}
        """.trimIndent()
    }
}
