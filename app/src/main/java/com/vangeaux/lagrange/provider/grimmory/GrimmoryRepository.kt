package com.vangeaux.lagrange.provider.grimmory

import android.content.Context
import com.vangeaux.lagrange.*
import okhttp3.OkHttpClient

/** Native Grimmory transport. Core-owned persistence remains in the legacy data-source delegate. */
internal class GrimmoryRepository private constructor(
    private val delegate: BookOrbitRepository,
    context: Context
) : BookOrbitDataSource by delegate, ProfileSessionAware {
    constructor(context: Context) : this(BookOrbitRepository(context), context)

    internal val appContext = context.applicationContext
    internal val auth = GrimmoryAuthModuleImpl(appContext)
    private val client = OkHttpClient()
    private val nativeApi = GrimmoryNativeApiClient(client, { getServerUrl() }, auth)

    override suspend fun checkServer(serverUrl: String): ServerCheckResult = auth.checkServer(serverUrl)
    override suspend fun canReachServer(serverUrl: String): Boolean = checkServer(serverUrl) == ServerCheckResult.Reachable
    override suspend fun getSessionState(): SessionState = auth.getSessionState()
    override suspend fun login(username: String, password: String) = auth.login(username, password)
    override suspend fun clearSession() = auth.clearSession()

    override suspend fun setServerUrl(serverUrl: String) {
        val normalized = normalizeServerUrl(serverUrl)
            ?: throw UserFacingException("Enter a valid Grimmory server URL.")
        delegate.setServerUrl(normalized)
    }

    override suspend fun saveCurrentProfileSession() {
        auth.saveCurrentProfileSession()
    }

    override suspend fun restoreCurrentProfileSession(): Boolean = auth.restoreCurrentProfileSession()

    override suspend fun loadBookCover(book: BookSummary): ByteArray? = null

    override suspend fun loadCatalogImage(url: String): ByteArray? = null

    override suspend fun restoreActiveReaderState(localOnly: Boolean): ReaderState? =
        if (localOnly) delegate.restoreActiveReaderState(localOnly = true) else null

    override suspend fun restoreActiveReaderState(
        session: com.vangeaux.lagrange.core.ActiveReaderSession,
        localOnly: Boolean
    ): ReaderState? = if (localOnly) delegate.restoreActiveReaderState(session, localOnly = true) else null

    internal suspend fun nativeJson(path: String, query: Map<String, String> = emptyMap()): org.json.JSONObject =
        nativeApi.json(path, query)

    internal suspend fun nativeJsonArray(path: String, query: Map<String, String> = emptyMap()): org.json.JSONArray =
        nativeApi.jsonArray(path, query)

    internal suspend fun nativeBytes(path: String, query: Map<String, String> = emptyMap()): ByteArray =
        nativeApi.bytes(path, query)

    internal suspend fun nativeImageBytes(path: String, query: Map<String, String> = emptyMap()): ByteArray =
        nativeApi.imageBytes(path, query)

    internal suspend fun nativePutJson(path: String, payload: String) {
        nativeApi.putJson(path, payload)
    }
}

internal class GrimmoryHttpException(val statusCode: Int) : java.io.IOException(
    "Grimmory API request failed (HTTP $statusCode)."
)
