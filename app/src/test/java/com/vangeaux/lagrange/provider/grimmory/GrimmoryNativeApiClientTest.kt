package com.vangeaux.lagrange.provider.grimmory

import com.vangeaux.lagrange.core.AuthenticatedMediaModule
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test

class GrimmoryNativeApiClientTest {
    @Test
    fun pageListRequestUsesJsonAcceptAndProductionParser() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("[1,2,3]")
            )
            val client = client(server)

            val pages = parseGrimmoryCbxPageNumbers(
                client.jsonArray("/api/v1/cbx/42/pages")
            )
            val request = server.takeRequest()

            assertEquals(listOf(1, 2, 3), pages)
            assertEquals("GET", request.method)
            assertEquals("/mount/api/v1/cbx/42/pages", request.path)
            assertEquals("application/json", request.getHeader("Accept"))
            assertEquals("Bearer fake-test-token", request.getHeader("Authorization"))
        }
    }

    @Test
    fun pageImageRequestUsesImageAcceptAndAuthenticatedNativeRoute() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "image/jpeg")
                    .setBody("image-payload")
            )
            val client = client(server)

            val image = client.imageBytes("/api/v1/media/book/42/cbx/pages/1")
            val request = server.takeRequest()

            assertEquals("image-payload", image.toString(Charsets.UTF_8))
            assertEquals("GET", request.method)
            assertEquals("/mount/api/v1/media/book/42/cbx/pages/1", request.path)
            assertEquals("image/*", request.getHeader("Accept"))
            assertEquals("Bearer fake-test-token", request.getHeader("Authorization"))
        }
    }

    @Test
    fun audiobookCoverImageRequestUsesNativeRouteAndAuthenticatedImageHeaders() = runBlocking {
        val server = MockWebServer()
        server.use {
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "image/jpeg")
                    .setBody("audiobook-cover-payload")
            )
            val client = client(server)

            val image = client.imageBytes(grimmoryAudiobookCoverPath("42"))
            val request = server.takeRequest()

            assertEquals("audiobook-cover-payload", image.toString(Charsets.UTF_8))
            assertEquals("GET", request.method)
            assertEquals("/mount/api/v1/media/book/42/audiobook-cover", request.path)
            assertEquals("image/*", request.getHeader("Accept"))
            assertEquals("Bearer fake-test-token", request.getHeader("Authorization"))
        }
    }

    private fun client(server: MockWebServer) = GrimmoryNativeApiClient(
        client = OkHttpClient(),
        serverUrlProvider = { server.url("/mount/").toString() },
        auth = object : AuthenticatedMediaModule {
            override suspend fun requestHeaders(url: String): Map<String, String> =
                mapOf("Authorization" to "Bearer fake-test-token")

            override suspend fun recoverAuthentication(): Boolean = false
        }
    )
}
