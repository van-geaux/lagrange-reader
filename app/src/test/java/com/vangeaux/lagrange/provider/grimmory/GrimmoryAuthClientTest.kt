package com.vangeaux.lagrange.provider.grimmory

import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GrimmoryAuthClientTest {
    @Test
    fun loginPostsPrimaryCredentialsAndStoresNativeTokenResponse() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                "{\"accessToken\":\"access-a\",\"refreshToken\":\"refresh-a\",\"expires\":900000,\"isDefaultPassword\":false}"
            )
        )
        server.start()
        try {
            val store = MemoryGrimmoryTokenStore()
            val auth = GrimmoryAuthClient(OkHttpClient(), store, profileId = { "profile-a" })
            auth.login(server.url("/lab/").toString(), "primary-user", "primary-pass")

            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/lab/api/v1/auth/login", request.path)
            val body = JSONObject(request.body.readUtf8())
            assertEquals("primary-user", body.optString("username"))
            assertEquals("primary-pass", body.optString("password"))
            assertEquals("access-a", store.read("profile-a")?.accessToken)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun requestHeadersRefreshesExpiredAccessTokenAndPersistsRotation() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                "{\"accessToken\":\"access-b\",\"refreshToken\":\"refresh-b\",\"expires\":900000}"
            )
        )
        server.start()
        try {
            val store = MemoryGrimmoryTokenStore().apply {
                write("profile-a", GrimmoryTokenSet("expired-access", "refresh-a", 100L, false))
            }
            val auth = GrimmoryAuthClient(
                OkHttpClient(), store, profileId = { "profile-a" }, nowMillis = { 10_000L }
            )

            val headers = auth.requestHeaders(server.url("/lab/").toString())

            assertEquals("Bearer access-b", headers["Authorization"])
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/lab/api/v1/auth/refresh", request.path)
            assertEquals("refresh-a", JSONObject(request.body.readUtf8()).optString("refreshToken"))
            assertEquals("refresh-b", store.read("profile-a")?.refreshToken)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun rejectedRefreshClearsExpiredProfileTokens() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(401).setBody("{\"message\":\"invalid credentials\"}"))
        server.start()
        try {
            val store = MemoryGrimmoryTokenStore().apply {
                write("profile-a", GrimmoryTokenSet("expired-access", "refresh-a", 100L, false))
            }
            val auth = GrimmoryAuthClient(
                OkHttpClient(), store, profileId = { "profile-a" }, nowMillis = { 10_000L }
            )

            val result = runCatching { auth.requestHeaders(server.url("/").toString()) }

            assertEquals(true, result.exceptionOrNull() is com.vangeaux.lagrange.AuthenticationRequiredException)
            assertNull(store.read("profile-a"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun simultaneousExpiredRequestsShareOneRotatingRefresh() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                "{\"accessToken\":\"access-b\",\"refreshToken\":\"refresh-b\",\"expires\":900000}"
            ).setBodyDelay(100, java.util.concurrent.TimeUnit.MILLISECONDS)
        )
        server.start()
        try {
            val store = MemoryGrimmoryTokenStore().apply {
                write("profile-a", GrimmoryTokenSet("expired-access", "refresh-a", 100L, false))
            }
            val auth = GrimmoryAuthClient(
                OkHttpClient(), store, profileId = { "profile-a" }, nowMillis = { 10_000L }
            )

            val first = async { auth.requestHeaders(server.url("/").toString()) }
            val second = async { auth.requestHeaders(server.url("/").toString()) }
            assertEquals("Bearer access-b", first.await()["Authorization"])
            assertEquals("Bearer access-b", second.await()["Authorization"])

            assertEquals(1, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun rejectedLoginDoesNotPersistTokens() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))
        server.start()
        try {
            val store = MemoryGrimmoryTokenStore()
            val auth = GrimmoryAuthClient(OkHttpClient(), store, profileId = { "profile-a" })
            runCatching { auth.login(server.url("/").toString(), "user", "wrong") }
            assertNull(store.read("profile-a"))
        } finally {
            server.shutdown()
        }
    }

    private class MemoryGrimmoryTokenStore : GrimmoryTokenStore {
        private val values = mutableMapOf<String, GrimmoryTokenSet>()
        override fun read(profileId: String) = values[profileId]
        override fun write(profileId: String, tokenSet: GrimmoryTokenSet) { values[profileId] = tokenSet }
        override fun clear(profileId: String) { values.remove(profileId) }
    }
}
