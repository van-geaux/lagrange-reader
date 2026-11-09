package com.vangeaux.lagrange.provider.komga

import com.vangeaux.lagrange.ServerCheckResult
import com.vangeaux.lagrange.SessionState
import com.vangeaux.lagrange.parseComicPageCount
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class KomgaCoverModuleTest {
    @Test
    fun `page list request accepts JSON`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("""[{"number":0},{"number":1}]""")
            )
            val module = KomgaCoverModuleImpl(FakeKomgaAuth(server.url("/").toString().trimEnd('/')))

            val response = module.loadCatalogImage(
                server.url("/komga/api/v1/books/book-1/pages?zero_based=true").toString()
            )

            assertNotNull(response)
            assertEquals(2, parseComicPageCount(response!!))
            assertEquals("application/json", server.takeRequest().getHeader("Accept"))
        }
    }

    @Test
    fun `page image request accepts images`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "image/png")
                    .setBody("png")
            )
            val module = KomgaCoverModuleImpl(FakeKomgaAuth(server.url("/").toString().trimEnd('/')))

            val response = module.loadCatalogImage(
                server.url("/komga/api/v1/books/book-1/pages/0?zero_based=true").toString()
            )

            assertNotNull(response)
            assertEquals("image/*", server.takeRequest().getHeader("Accept"))
        }
    }

    private class FakeKomgaAuth(private val serverUrl: String) : KomgaAuthModule {
        override fun credentialOrigin(): String = serverUrl
        override fun requestHeaders(serverUrl: String): Map<String, String> =
            mapOf("Authorization" to "Basic test")
        override fun localizedMessage(messageRes: Int, fallback: String, vararg formatArgs: Any): String = fallback
        override suspend fun checkServer(serverUrl: String) = ServerCheckResult.Reachable
        override suspend fun login(serverUrl: String, username: String, password: String) = Unit
        override suspend fun sessionState(serverUrl: String) = SessionState.Unauthenticated
        override suspend fun clearSession() = Unit
    }
}
