package com.vangeaux.lagrange.provider.kavita

import android.content.Context
import com.vangeaux.lagrange.*
import java.io.File

internal class KavitaRepository(context: Context) : BookOrbitDataSource, ProfileSessionAware {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences("kavita_connection", Context.MODE_PRIVATE)
    private val authModule = KavitaAuthModuleImpl(appContext)
    private val libraryModule = KavitaLibraryModuleImpl(authModule)

    override suspend fun getServerUrl(): String? = preferences.getString(SERVER_URL_KEY, null)

    override suspend fun setServerUrl(serverUrl: String) {
        val normalized = normalizeServerUrl(serverUrl)
            ?: throw UserFacingException("Enter a valid Kavita server URL.")
        preferences.edit().putString(SERVER_URL_KEY, normalized).apply()
    }

    override suspend fun clearServer() {
        preferences.edit().remove(SERVER_URL_KEY).remove(SELECTED_LIBRARY_KEY).apply()
        authModule.clearSession()
    }

    override suspend fun clearSession() = authModule.clearSession()

    override suspend fun getSelectedLibraryId(): String? =
        preferences.getString(SELECTED_LIBRARY_KEY, null)

    override suspend fun setSelectedLibraryId(libraryId: String) {
        preferences.edit().putString(SELECTED_LIBRARY_KEY, libraryId).apply()
    }

    override suspend fun getSessionState(): SessionState =
        authModule.sessionState(getServerUrl().orEmpty())

    override suspend fun login(username: String, password: String): Unit =
        throw UserFacingException("Kavita requires an Auth Key, not a username and password.")

    override suspend fun loadLibraries(): List<LibrarySummary> = libraryModule.loadLibraries()

    override suspend fun loadBooks(libraryId: String): List<BookSummary> = unavailable()
    override suspend fun loadCachedBrowserState(libraryId: String?): BrowserState? = null
    override suspend fun buildReaderState(book: BookSummary, localOnly: Boolean): ReaderState = unavailable()
    override suspend fun saveActiveReader(book: BookSummary, launchMode: ReaderLaunchMode) = unavailable<Unit>()
    override suspend fun clearActiveReader() = unavailable<Unit>()
    override suspend fun restoreActiveReaderState(localOnly: Boolean): ReaderState? = null
    override suspend fun downloadBook(book: BookSummary, onProgress: (Float?) -> Unit) = unavailable<File>()
    override suspend fun deleteLocalCopy(book: BookSummary) = unavailable<Unit>()
    override suspend fun queueProgress(book: BookSummary, position: Long, pageIndex: Int, progressPercent: Float?) = unavailable<Unit>()
    override suspend fun pendingProgressCount(): Int = 0
    override suspend fun syncPendingProgress(): SyncAttemptResult = SyncAttemptResult.Unsupported

    override suspend fun canReachServer(serverUrl: String): Boolean =
        checkServer(serverUrl) == ServerCheckResult.Reachable

    override suspend fun checkServer(serverUrl: String): ServerCheckResult =
        authModule.checkServer(serverUrl)

    internal suspend fun authenticate(apiKey: String) {
        authModule.authenticate(getServerUrl().orEmpty(), apiKey)
    }

    internal fun requestHeaders(): Map<String, String> = authModule.requestHeaders()

    override suspend fun saveCurrentProfileSession() {
        authModule.saveCurrentProfileSession()
    }

    override suspend fun restoreCurrentProfileSession(): Boolean =
        authModule.restoreCurrentProfileSession()

    private fun <T> unavailable(): T =
        throw UserFacingException("Kavita catalog and reading support are not implemented yet.")

    private companion object {
        const val SERVER_URL_KEY = "server_url"
        const val SELECTED_LIBRARY_KEY = "selected_library_id"
    }
}
