package com.vangeaux.lagrange.provider.grimmory

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test

class GrimmoryIdentityClientTest {
    @Test
    fun currentUserRequestPersistsNativeStableIdAndUsesProfileBearerSession() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"id":37,"username":"reader"}"""))
        server.start()
        try {
            val tokenStore = MemoryTokenStore().apply {
                write("profile-a", GrimmoryTokenSet("access-token", "refresh-token", 60_000_000L, false))
            }
            val auth = GrimmoryAuthClient(OkHttpClient(), tokenStore, profileId = { "profile-a" }, nowMillis = { 1L })
            val persisted = mutableListOf<Pair<String, String>>()
            val identity = GrimmoryIdentityClient(
                client = OkHttpClient(),
                auth = auth,
                profileId = { "profile-a" },
                persistAccount = { profileId, principal -> persisted += profileId to principal.canonical }
            )

            val result = identity.fetchAndPersist(server.url("/lab/").toString())
            val request = server.takeRequest()

            assertEquals(200, result.statusCode)
            assertEquals("id:37", result.principal?.canonical)
            assertEquals(listOf("profile-a" to "id:37"), persisted)
            assertEquals("GET", request.method)
            assertEquals("/lab/api/v1/users/me", request.path)
            assertEquals("Bearer access-token", request.getHeader("Authorization"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun missingStableUserIdDoesNotCreateAccountScope() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"username":"reader"}"""))
        server.start()
        try {
            val tokenStore = MemoryTokenStore().apply {
                write("profile-a", GrimmoryTokenSet("access-token", "refresh-token", 60_000_000L, false))
            }
            val auth = GrimmoryAuthClient(OkHttpClient(), tokenStore, profileId = { "profile-a" }, nowMillis = { 1L })
            val persisted = mutableListOf<Pair<String, String>>()
            val identity = GrimmoryIdentityClient(
                client = OkHttpClient(),
                auth = auth,
                profileId = { "profile-a" },
                persistAccount = { profileId, principal -> persisted += profileId to principal.canonical }
            )

            val result = identity.fetchAndPersist(server.url("/").toString())

            assertEquals(200, result.statusCode)
            assertEquals(null, result.principal)
            assertEquals(emptyList<Pair<String, String>>(), persisted)
        } finally {
            server.shutdown()
        }
    }

    private class MemoryTokenStore : GrimmoryTokenStore {
        private val values = mutableMapOf<String, GrimmoryTokenSet>()
        override fun read(profileId: String) = values[profileId]
        override fun write(profileId: String, tokenSet: GrimmoryTokenSet) { values[profileId] = tokenSet }
        override fun clear(profileId: String) { values.remove(profileId) }
    }
}
