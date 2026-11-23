package com.vangeaux.lagrange.provider.grimmory

import com.vangeaux.lagrange.AuthenticationRequiredException
import com.vangeaux.lagrange.UserFacingException
import com.vangeaux.lagrange.normalizeServerUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

internal interface GrimmoryTokenStore {
    fun read(profileId: String): GrimmoryTokenSet?
    fun write(profileId: String, tokenSet: GrimmoryTokenSet)
    fun clear(profileId: String)
}

internal class GrimmoryAuthClient(
    private val client: OkHttpClient,
    private val tokenStore: GrimmoryTokenStore,
    private val profileId: () -> String?,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    private var loadedProfileId: String? = null
    private var currentTokenSet: GrimmoryTokenSet? = null
    private val refreshMutex = Mutex()

    suspend fun login(serverUrl: String, username: String, password: String) = withContext(Dispatchers.IO) {
        val base = normalizeServerUrl(serverUrl)
            ?: throw UserFacingException("Enter a valid Grimmory server URL.")
        if (username.isBlank()) throw UserFacingException("Enter your Grimmory username.")
        if (password.isBlank()) throw UserFacingException("Enter your Grimmory password.")
        val (status, payload) = postJson(
            grimmoryApiUrl(base, "/api/v1/auth/login"),
            grimmoryLoginPayload(username, password)
        )
        if (status == 401 || status == 403) throw UserFacingException("Grimmory rejected this username or password.")
        if (status !in 200..299) throw UserFacingException("Grimmory sign-in failed (HTTP $status).")
        val tokens = grimmoryTokenSet(payload)
            ?: throw UserFacingException("Grimmory returned an invalid token response.")
        val loginProfileId = profileId()
        refreshMutex.withLock {
            if (profileId() != loginProfileId) throw AuthenticationRequiredException()
            store(tokens, loginProfileId)
        }
    }

    suspend fun requestHeaders(serverUrl: String): Map<String, String> = withContext(Dispatchers.IO) {
        val tokens = refreshMutex.withLock { currentTokens() } ?: throw AuthenticationRequiredException()
        val usable = if (tokens.expiresAtMillis != null && tokens.expiresAtMillis <= nowMillis() + TOKEN_REFRESH_SKEW_MS) {
            refresh(serverUrl, tokens) ?: throw AuthenticationRequiredException()
        } else tokens
        mapOf("Authorization" to grimmoryBearerAuthorizationHeader(usable.accessToken))
    }

    suspend fun recoverAuthentication(serverUrl: String): Boolean = withContext(Dispatchers.IO) {
        val tokens = refreshMutex.withLock { currentTokens() } ?: return@withContext false
        refresh(serverUrl, tokens) != null
    }

    suspend fun clearSession() = withContext(Dispatchers.IO) {
        refreshMutex.withLock {
            val id = profileId()
            id?.let(tokenStore::clear)
            currentTokenSet = null
            loadedProfileId = id
        }
    }

    suspend fun saveCurrentProfileSession(): Boolean = withContext(Dispatchers.IO) {
        refreshMutex.withLock {
            val id = profileId() ?: return@withLock false
            val tokens = currentTokens() ?: return@withLock false
            tokenStore.write(id, tokens)
            true
        }
    }

    suspend fun restoreCurrentProfileSession(): Boolean = withContext(Dispatchers.IO) {
        refreshMutex.withLock {
            val id = profileId() ?: return@withLock false
            currentTokenSet = tokenStore.read(id)
            loadedProfileId = id
            currentTokenSet != null
        }
    }

    private fun currentTokens(): GrimmoryTokenSet? {
        val activeId = profileId() ?: return null
        if (activeId != loadedProfileId) {
            currentTokenSet = tokenStore.read(activeId)
            loadedProfileId = activeId
        }
        return currentTokenSet
    }

    private fun store(tokens: GrimmoryTokenSet, id: String?) {
        currentTokenSet = tokens
        loadedProfileId = id
        if (id != null) tokenStore.write(id, tokens)
    }

    private suspend fun refresh(serverUrl: String, prior: GrimmoryTokenSet): GrimmoryTokenSet? =
        refreshMutex.withLock {
            val id = profileId() ?: return@withLock null
            val current = currentTokens() ?: return@withLock null
            if (current != prior) return@withLock current
            val base = normalizeServerUrl(serverUrl) ?: return@withLock null
            val result = runCatching {
                postJson(grimmoryApiUrl(base, "/api/v1/auth/refresh"), grimmoryRefreshPayload(prior.refreshToken))
            }.getOrNull() ?: return@withLock null
            if (profileId() != id) return@withLock null
            if (result.first == 401 || result.first == 403) {
                tokenStore.clear(id)
                currentTokenSet = null
                loadedProfileId = id
                return@withLock null
            }
            if (result.first !in 200..299) return@withLock null
            val updated = grimmoryTokenSet(result.second) ?: return@withLock null
            store(updated, id)
            updated
        }

    private fun postJson(url: String, payload: String): Pair<Int, String> {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        return client.newCall(request).execute().use { it.code to it.body?.string().orEmpty() }
    }

    private companion object {
        const val TOKEN_REFRESH_SKEW_MS = 60_000L
    }
}
