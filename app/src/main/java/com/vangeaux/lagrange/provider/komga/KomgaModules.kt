package com.vangeaux.lagrange.provider.komga

import com.vangeaux.lagrange.*
import com.vangeaux.lagrange.core.*

import android.content.Context
import android.webkit.CookieManager
import android.util.Log
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64

import com.vangeaux.lagrange.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.File
import java.io.FileOutputStream
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale
import kotlin.coroutines.resume
import java.time.Instant
import java.time.ZonedDateTime
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.net.ssl.SSLException

private const val PAGE_SIZE = 100

internal fun komgaSessionHeaders(authorization: String?, cookie: String?): Map<String, String> = buildMap {
    authorization?.takeIf { it.isNotBlank() }?.let { put("Authorization", it) }
    cookie?.takeIf { it.isNotBlank() }?.let { put("Cookie", it) }
}

internal fun komgaAuthenticatedPrincipal(payload: String): AuthenticatedAccountPrincipal? =
    authenticatedAccountPrincipal(
        payload = payload,
        stableFields = listOf("id", "userId")
    )

internal fun komgaParseInstantMillis(value: String?): Long? = value
    ?.takeIf { it.isNotBlank() }
    ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

internal fun komgaReadActivityMillis(progress: JSONObject?): Long? =
    komgaParseInstantMillis(progress?.optString("lastModified"))
        ?: komgaParseInstantMillis(progress?.optString("readDate"))

internal data class KomgaReadState(
    val status: BookReadStatus,
    val isRead: Boolean,
    val page: Int?,
    val progressPercent: Float?
)

internal fun komgaReadState(progress: JSONObject?, pageCount: Int? = null): KomgaReadState {
    val completed = progress?.optBoolean("completed", false) == true
    val page = progress?.optInt("page", 0)?.takeIf { it > 0 }
    return KomgaReadState(
        status = when {
            completed -> BookReadStatus.READ
            progress != null -> BookReadStatus.READING
            else -> BookReadStatus.UNREAD
        },
        isRead = completed,
        page = page,
        progressPercent = when {
            completed -> 100f
            page != null && pageCount != null && pageCount > 0 ->
                (page.toFloat() / pageCount * 100f).coerceIn(0f, 100f)
            else -> null
        }
    )
}

internal fun komgaReaderProgressBook(book: BookSummary, payload: String): BookSummary {
    val root = JSONObject(payload)
    val progress = root.optJSONObject("readProgress") ?: root
    val page = progress.optInt("page", 0).takeIf { it > 0 }
    val completed = progress.optBoolean("completed", false)
    return book.copy(
        progressLabel = page?.let { "Page $it" } ?: book.progressLabel,
        progressPageIndex = page?.minus(1)?.coerceAtLeast(0) ?: book.progressPageIndex,
        readStatus = if (completed) BookReadStatus.READ else BookReadStatus.READING,
        isRead = completed,
        lastReadAtMillis = komgaReadActivityMillis(progress) ?: book.lastReadAtMillis
    )
}

internal fun komgaProgressionLocatorJson(payload: String): String? =
    JSONObject(payload).optJSONObject("locator")?.toString()

internal fun komgaProgressEndpoint(base: String, bookId: String, hasLocator: Boolean): String =
    if (hasLocator) "$base/api/v1/books/$bookId/progression"
    else "$base/api/v1/books/$bookId/read-progress"

internal fun komgaBookSearchPayload(query: String): String =
    JSONObject().put("fullTextSearch", query.trim()).toString()

internal fun komgaBookSearchPath(): String =
    "/api/v1/books/list?page=0&size=$PAGE_SIZE&sort=metadata.title,asc"

internal fun komgaSeriesSearchPayload(filter: SeriesCatalogFilter, libraryId: String?): String =
    JSONObject().apply {
        filter.query?.trim()?.takeIf { it.isNotBlank() }?.let { put("fullTextSearch", it) }
        val conditions = JSONArray()
        (libraryId ?: filter.libraryId)?.takeIf { it.isNotBlank() }?.let {
            conditions.put(JSONObject().put("libraryId", JSONObject().put("operator", "is").put("value", it)))
        }
        filter.genre?.trim()?.takeIf { it.isNotBlank() }?.let {
            conditions.put(JSONObject().put("genre", JSONObject().put("operator", "is").put("value", it)))
        }
        if (filter.completion == SeriesCompletionFilter.COMPLETE) {
            conditions.put(JSONObject().put("complete", JSONObject().put("operator", "isTrue")))
        }
        if (conditions.length() > 0) put("condition", JSONObject().put("allOf", conditions))
    }.toString()

internal fun komgaSeriesSearchPath(): String =
    "/api/v1/series/list?unpaged=true&sort=metadata.title,asc"

internal fun komgaHomeSeriesPath(updated: Boolean): String =
    if (updated) "/api/v1/series/updated?page=0&size=8" else "/api/v1/series/new?page=0&size=8"

internal fun komgaSeriesCatalogPage(
    items: List<SeriesSummary>,
    filter: SeriesCatalogFilter,
    page: Int
): SeriesCatalogPage {
    val filtered = filterAndSortSeriesCatalog(items, filter)
    val from = (page * PAGE_SIZE).coerceAtMost(filtered.size)
    val to = (from + PAGE_SIZE).coerceAtMost(filtered.size)
    return SeriesCatalogPage(
        items = filtered.subList(from, to),
        total = filtered.size,
        page = page,
        size = PAGE_SIZE,
        jumpIndex = filtered.takeIf { page == 0 && filter.includeCounts }
    )
}

private fun komgaFormat(format: String?, filename: String?): String? {
    val filenameExtension = filename?.substringAfterLast('.', "")?.lowercase(Locale.US)
    when (filenameExtension) {
        "epub" -> return "epub"
        "pdf" -> return "pdf"
        "cbz" -> return "cbz"
        "cbr" -> return "cbr"
        "cb7" -> return "cb7"
        "m4b" -> return "m4b"
        "m4a" -> return "m4a"
        "mp3" -> return "mp3"
        "flac" -> return "flac"
        "opus" -> return "opus"
        "ogg" -> return "ogg"
        "wav" -> return "wav"
    }
    val value = format.orEmpty().lowercase(Locale.US)
    return when {
        value.contains("epub") -> "epub"
        value.contains("pdf") -> "pdf"
        value.contains("cbz") -> "cbz"
        value.contains("cbr") -> "cbr"
        value.contains("cb7") || value.contains("7z") -> "cb7"
        value == "application/zip" -> "cbz"
        value.contains("rar") -> "cbr"
        value.contains("m4b") -> "m4b"
        value.contains("m4a") -> "m4a"
        value.contains("mp3") || value.contains("mpeg") -> "mp3"
        value.contains("flac") -> "flac"
        value.contains("opus") -> "opus"
        value.contains("ogg") -> "ogg"
        value.contains("wav") -> "wav"
        else -> filenameExtension?.takeIf { it.isNotBlank() }
    }
}

internal data class KomgaBookProjection(
    val filename: String?,
    val format: String?,
    val mediaKind: MediaKind,
    val readState: KomgaReadState,
    val availableFormats: List<String>
)

internal fun komgaBookProjection(item: JSONObject): KomgaBookProjection {
    val media = item.optJSONObject("media") ?: JSONObject()
    val primaryFile = media.optJSONArray("files")?.optJSONObject(0)
    val filename = primaryFile?.optString("fileName")?.takeIf { it.isNotBlank() }
        ?: media.optString("fileName").takeIf { it.isNotBlank() }
        ?: item.optString("name").takeIf { it.isNotBlank() }
    val format = komgaFormat(
        primaryFile?.optString("mediaType")?.takeIf { it.isNotBlank() }
            ?: media.optString("mediaType").takeIf { it.isNotBlank() },
        filename
    )
    val mediaKind = komgaMediaKind(format, filename)
    val readState = komgaReadState(
        item.optJSONObject("readProgress"),
        media.optInt("pagesCount", 0).takeIf { it > 0 }
    )
    return KomgaBookProjection(
        filename = filename,
        format = format,
        mediaKind = mediaKind,
        readState = readState,
        availableFormats = normalizedAvailableFormats(listOf(format to mediaKind))
    )
}

internal fun komgaHttpClientFollowsRedirects(): Boolean = false

private class KomgaHttpClient {
    val client = OkHttpClient.Builder()
        .followRedirects(komgaHttpClientFollowsRedirects())
        .followSslRedirects(komgaHttpClientFollowsRedirects())
        .build()
}

private class KomgaCredentialStore(context: Context) {
    private val preferences = context.getSharedPreferences("komga_auth", Context.MODE_PRIVATE)

    fun read(profileId: String): String? {
        val encoded = preferences.getString(profileKey(profileId), null)
            ?: preferences.getString(AUTH_KEY, null)
            ?: return null
        val packed = Base64.decode(encoded, Base64.DEFAULT)
        val iv = packed.copyOfRange(0, GCM_IV_LENGTH)
        val encrypted = packed.copyOfRange(GCM_IV_LENGTH, packed.size)
        return Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(GCM_TAG_LENGTH, iv))
        }.doFinal(encrypted).toString(Charsets.UTF_8)
    }

    fun write(profileId: String, value: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val packed = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        if (!preferences.edit()
                .putString(profileKey(profileId), Base64.encodeToString(packed, Base64.NO_WRAP))
                .commit()
        ) {
            throw IllegalStateException("Komga credentials could not be persisted")
        }
    }

    fun clear(profileId: String) {
        preferences.edit().remove(profileKey(profileId)).remove(AUTH_KEY).apply()
    }

    private fun profileKey(profileId: String): String =
        "${AUTH_KEY}_${Base64.encodeToString(profileId.toByteArray(Charsets.UTF_8), Base64.NO_WRAP or Base64.URL_SAFE)}"

    private fun key(): java.security.Key {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!store.containsAlias(KEY_ALIAS)) {
            val generator = javax.crypto.KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            generator.init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
            generator.generateKey()
        }
        return (store.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
    }

    private companion object {
        const val AUTH_KEY = "encrypted_basic_auth"
        const val KEY_ALIAS = "lagrange_komga_auth"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_LENGTH = 12
        const val GCM_TAG_LENGTH = 128
    }
}

class KomgaAuthModuleImpl(context: Context) : KomgaAuthModule {
    private val appContext = context.applicationContext
    private val client = KomgaHttpClient().client
    private val credentialStore = KomgaCredentialStore(appContext)
    private val authPreferences = appContext.getSharedPreferences("komga_auth", Context.MODE_PRIVATE)
    private var credentialPersistenceFailed = false
    private var serverUrl: String? = null
    private var authorization: String? = ServerProfileStore(appContext).active()?.id?.let(::readCredential)

    /** Localizes a provider error message, falling back to the original English text. */
    internal fun localizedMessage(messageRes: Int, fallback: String, vararg formatArgs: Any): String =
        providerErrorMessage(appContext, messageRes, fallback, *formatArgs)

    override suspend fun checkServer(serverUrl: String): ServerCheckResult = withContext(Dispatchers.IO) {
        val base = normalizeServerUrl(serverUrl) ?: return@withContext ServerCheckResult.MalformedUrl
        try {
            val request = Request.Builder().url("$base/api/v1/libraries").get().build()
            client.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful || response.code == 401 || response.code == 403 -> ServerCheckResult.Reachable
                    response.isRedirect -> ServerCheckResult.Redirected
                    else -> ServerCheckResult.HttpFailure
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            when (error) {
                is UnknownHostException -> ServerCheckResult.UnreachableHost
                is SocketTimeoutException -> ServerCheckResult.Timeout
                is SSLException -> ServerCheckResult.TlsFailure
                is IOException -> ServerCheckResult.NetworkFailure
                else -> ServerCheckResult.NetworkFailure
            }
        }
    }

    override suspend fun login(serverUrl: String, username: String, password: String) = withContext(Dispatchers.IO) {
        val base = normalizeServerUrl(serverUrl)
            ?: throw UserFacingException(
                localizedMessage(
                    R.string.perror_komga_server_url,
                    "Enter a valid Komga server URL."
                )
            )
        val request = Request.Builder()
            .url("$base/api/v2/users/me")
            .header("Authorization", Credentials.basic(username, password))
            .get()
            .build()
        var authenticatedPrincipal: AuthenticatedAccountPrincipal? = null
        client.newCall(request).execute().use { response ->
            if (response.code == 401 || response.code == 403) {
                throw UserFacingException(localizedMessage(R.string.perror_komga_credentials, "Komga did not accept this username or password."))
            }
            if (!response.isSuccessful) {
                throw UserFacingException(localizedMessage(R.string.perror_komga_signin_http, "Komga sign-in failed with HTTP ${response.code}.", response.code))
            }
            authenticatedPrincipal = komgaAuthenticatedPrincipal(response.body?.string().orEmpty())
        }
        this@KomgaAuthModuleImpl.serverUrl = base
        authorization = Credentials.basic(username, password)
        activeProfileId()?.let { profileId ->
            try {
                credentialStore.write(profileId, authorization!!)
                credentialPersistenceFailed = false
            } catch (_: Throwable) {
                credentialPersistenceFailed = true
                this@KomgaAuthModuleImpl.serverUrl = null
                authorization = null
                throw UserFacingException(
                    localizedMessage(
                        R.string.perror_komga_credential_persistence,
                        "Komga signed in, but the credentials could not be saved on this device."
                    )
                )
            }
            authenticatedPrincipal?.let { principal ->
                persistAuthenticatedAccount(profileId, principal)
            }
        }
        Unit
    }

    override suspend fun sessionState(serverUrl: String): SessionState = withContext(Dispatchers.IO) {
        val base = normalizeServerUrl(serverUrl) ?: return@withContext SessionState.Unavailable
        ensureCookieScope(activeProfileId())
        val headers = requestHeaders(base)
        if (headers.isEmpty()) return@withContext SessionState.Unauthenticated
        try {
            val request = Request.Builder()
                .url("$base/api/v2/users/me")
                .apply { headers.forEach { (name, value) -> header(name, value) } }
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> {
                        val principal = komgaAuthenticatedPrincipal(response.body?.string().orEmpty())
                        val profileId = activeProfileId()
                        if (principal != null && profileId != null) {
                            markCookieProfileIfPresent(profileId, base)
                            persistAuthenticatedAccount(profileId, principal)
                            SessionState.Authenticated
                        } else {
                            SessionState.Unavailable
                        }
                    }
                    response.code == 401 || response.code == 403 -> SessionState.Unauthenticated
                    else -> SessionState.Unavailable
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            SessionState.Unavailable
        }
    }

    override suspend fun clearSession() {
        serverUrl = null
        authorization = null
        activeProfileId()?.let(credentialStore::clear)
        authPreferences.edit().remove(COOKIE_PROFILE_KEY).apply()
        credentialPersistenceFailed = false
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
    }

    internal suspend fun saveCurrentProfileSession() {
        activeProfileId()?.let { profileId ->
            authorization?.let {
                try {
                    credentialStore.write(profileId, it)
                    credentialPersistenceFailed = false
                } catch (_: Throwable) {
                    credentialPersistenceFailed = true
                }
            }
        }
    }

    internal suspend fun restoreCurrentProfileSession(): Boolean {
        val profileId = activeProfileId()
        ensureCookieScope(profileId)
        authorization = profileId?.let(::readCredential)
        if (profileId != null && AuthenticatedAccountScopeStore(appContext).read(profileId) == null) {
            val profile = ServerProfileStore(appContext).active()
            val remotePrincipal = profile?.serverUrl?.let { configured ->
                val base = normalizeServerUrl(configured) ?: return@let null
                try {
                    val request = Request.Builder()
                        .url("$base/api/v2/users/me")
                        .apply {
                            requestHeaders(base).forEach { (name, value) -> header(name, value) }
                        }
                        .get()
                        .build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) null else {
                            komgaAuthenticatedPrincipal(response.body?.string().orEmpty())
                        }
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Throwable) {
                    null
                }
            }
            remotePrincipal?.let { principal ->
                persistAuthenticatedAccount(profileId, principal)
            }
        }
        val activeServerUrl = ServerProfileStore(appContext).active()?.serverUrl.orEmpty()
        val hasSession = !authorization.isNullOrBlank() || hasWebSession()
        if (hasSession && profileId != null && AuthenticatedAccountScopeStore(appContext).read(profileId) != null) {
            markCookieProfileIfPresent(profileId, activeServerUrl)
            DownloadStore(appContext).quarantineLegacyUnscopedDownloads()
        }
        return hasSession
    }

    private suspend fun persistAuthenticatedAccount(
        profileId: String,
        principal: AuthenticatedAccountPrincipal
    ) {
        AuthenticatedAccountScopeStore(appContext).writeAuthoritative(
            profileId = profileId,
            providerId = PROVIDER_KOMGA,
            principal = principal.canonical,
            equivalentPrincipals = principal.provenAliases
        )
        DownloadStore(appContext).quarantineLegacyUnscopedDownloads()
    }

    internal suspend fun clearRuntimeSession() = withContext(Dispatchers.Main.immediate) {
        serverUrl = null
        authorization = null
        authPreferences.edit().remove(COOKIE_PROFILE_KEY).apply()
        suspendCancellableCoroutine { continuation ->
            CookieManager.getInstance().removeAllCookies {
                CookieManager.getInstance().flush()
                if (continuation.isActive) continuation.resume(Unit)
            }
        }
    }

    private fun activeProfileId(): String? = ServerProfileStore(appContext).active()?.id

    internal fun requestHeaders(serverUrl: String): Map<String, String> = komgaSessionHeaders(
        authorization = authorization,
        cookie = CookieManager.getInstance().getCookie(serverUrl)
    )

    internal fun credentialOrigin(): String? = serverUrl
        ?: ServerProfileStore(appContext).active()?.serverUrl

    internal fun hasSession(serverUrl: String): Boolean = requestHeaders(serverUrl).isNotEmpty()

    private fun readCredential(profileId: String): String? = try {
        credentialStore.read(profileId).also { credentialPersistenceFailed = false }
    } catch (_: Throwable) {
        credentialPersistenceFailed = true
        null
    }

    private suspend fun ensureCookieScope(profileId: String?) = withContext(Dispatchers.Main.immediate) {
        val owner = authPreferences.getString(COOKIE_PROFILE_KEY, null)
        if (profileId == null || owner != profileId) {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            authPreferences.edit().remove(COOKIE_PROFILE_KEY).apply()
        }
    }

    private fun markCookieProfileIfPresent(profileId: String, serverUrl: String) {
        if (CookieManager.getInstance().getCookie(serverUrl).isNullOrBlank()) return
        authPreferences.edit().putString(COOKIE_PROFILE_KEY, profileId).apply()
    }

    private fun hasWebSession(): Boolean =
        ServerProfileStore(appContext).active()?.serverUrl?.let { requestHeaders(it)["Cookie"] != null } == true

    private companion object {
        const val COOKIE_PROFILE_KEY = "cookie_profile_id"
    }
}

class KomgaLibraryModuleImpl(private val auth: KomgaAuthModuleImpl) : KomgaLibraryModule {
    private val client = KomgaHttpClient().client

    override suspend fun loadLibraries(serverUrl: String): List<LibrarySummary> = withContext(Dispatchers.IO) {
        val base = normalizeServerUrl(serverUrl) ?: throw UserFacingException(auth.localizedMessage(R.string.perror_komga_server_url, "Enter a valid Komga server URL."))
        val request = Request.Builder().url("$base/api/v1/libraries").get()
            .apply { auth.requestHeaders(base).forEach { (name, value) -> header(name, value) } }
            .build()
        client.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw UserFacingException(auth.localizedMessage(R.string.perror_komga_libraries_http, "Unable to load Komga libraries (HTTP ${response.code}).", response.code))
            val array = runCatching { JSONArray(payload) }.getOrElse {
                val root = JSONObject(payload)
                root.optJSONArray("libraries")
                    ?: root.optJSONArray("content")
                    ?: root.optJSONObject("_embedded")?.optJSONArray("libraries")
                    ?: JSONArray()
            }
            val libraries = buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
                    add(LibrarySummary(id, item.optString("name", id), item.optString("description").takeIf { it.isNotBlank() }))
                }
            }
            libraries
        }
    }
}

class KomgaBookCatalogModuleImpl(private val auth: KomgaAuthModuleImpl) : KomgaBookCatalogModule {
    private val client = KomgaHttpClient().client

    override suspend fun searchBooks(serverUrl: String, query: String): List<BookSummary> = withContext(Dispatchers.IO) {
        val base = normalizeServerUrl(serverUrl)
            ?: throw UserFacingException(auth.localizedMessage(R.string.perror_komga_server_url, "Enter a valid Komga server URL."))
        if (query.isBlank()) return@withContext emptyList()
        val request = Request.Builder()
            .url("$base${komgaBookSearchPath()}")
            .header("Content-Type", "application/json")
            .apply { auth.requestHeaders(base).forEach { (name, value) -> header(name, value) } }
            .post(komgaBookSearchPayload(query).toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw UserFacingException(auth.localizedMessage(R.string.perror_komga_books_http, "Unable to search Komga books (HTTP ${response.code}).", response.code))
            }
            val content = JSONObject(payload).optJSONArray("content") ?: JSONArray()
            buildList {
                for (index in 0 until content.length()) {
                    content.optJSONObject(index)?.let { parseBookSummary(it, base) }?.let(::add)
                }
            }
        }
    }

    override suspend fun loadSeriesBooks(serverUrl: String, seriesId: String): List<BookSummary> = withContext(Dispatchers.IO) {
        val base = normalizeServerUrl(serverUrl)
            ?: throw UserFacingException(auth.localizedMessage(R.string.perror_komga_server_url, "Enter a valid Komga server URL."))
        buildList {
            var page = 0
            while (page < 1000) {
                val request = Request.Builder()
                    .url("$base/api/v1/series/$seriesId/books?page=$page&size=$PAGE_SIZE&sort=metadata.number,asc")
                    .apply { auth.requestHeaders(base).forEach { (name, value) -> header(name, value) } }
                    .get()
                    .build()
                val result = client.newCall(request).execute().use { response ->
                    val payload = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw UserFacingException(auth.localizedMessage(R.string.perror_komga_books_http, "Unable to load Komga series books (HTTP ${response.code}).", response.code))
                    }
                    val root = JSONObject(payload)
                    val content = root.optJSONArray("content") ?: JSONArray()
                    val books = buildList {
                        for (index in 0 until content.length()) {
                            content.optJSONObject(index)?.let { parseBookSummary(it, base) }?.let(::add)
                        }
                    }
                    books to root.optBoolean("last", true)
                }
                addAll(result.first)
                if (result.second || result.first.isEmpty()) break
                page++
            }
        }
    }

    override suspend fun loadBooks(serverUrl: String, libraryId: String, page: Int): LibraryBooksPage = withContext(Dispatchers.IO) {
        val base = normalizeServerUrl(serverUrl) ?: throw UserFacingException(auth.localizedMessage(R.string.perror_komga_server_url, "Enter a valid Komga server URL."))
        val url = "$base/api/v1/books?library_id=$libraryId&page=$page&size=$PAGE_SIZE&sort=metadata.title,asc"
        val request = Request.Builder().url(url).get()
            .apply { auth.requestHeaders(base).forEach { (name, value) -> header(name, value) } }
            .build()
        client.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw UserFacingException(auth.localizedMessage(R.string.perror_komga_books_http, "Unable to load Komga books (HTTP ${response.code}).", response.code))
            val root = JSONObject(payload)
            val content = root.optJSONArray("content") ?: JSONArray()
            val items = buildList {
                for (index in 0 until content.length()) {
                    val item = content.optJSONObject(index) ?: continue
                    parseBookSummary(item, base, libraryId)?.let(::add)
                }
            }
            LibraryBooksPage(
                items = items,
                total = root.optInt("totalElements", items.size),
                page = root.optInt("number", page),
                size = root.optInt("size", PAGE_SIZE),
                isComplete = root.optBoolean("last", true)
            )
        }
    }

    private fun parseBookSummary(item: JSONObject, base: String, libraryIdOverride: String? = null): BookSummary? {
        val id = item.optString("id").takeIf { it.isNotBlank() } ?: return null
        val metadata = item.optJSONObject("metadata") ?: JSONObject()
        val projection = komgaBookProjection(item)
        val readState = projection.readState
        return BookSummary(
            libraryId = libraryIdOverride ?: item.optString("libraryId"),
            id = id,
            fileId = id,
            title = metadata.optString("title", item.optString("name", id)),
            author = metadata.optJSONArray("authors")?.optJSONObject(0)?.optString("name"),
            filename = projection.filename,
            format = projection.format,
            mediaKind = projection.mediaKind,
            availableFormats = projection.availableFormats,
            coverUrl = "$base/api/v1/books/$id/thumbnail",
            seriesId = item.optString("seriesId").takeIf { it.isNotBlank() }
                ?: metadata.optString("seriesId").takeIf { it.isNotBlank() },
            seriesName = item.optString("seriesTitle").takeIf { it.isNotBlank() }
                ?: metadata.optString("series").takeIf { it.isNotBlank() }
                ?: metadata.optJSONObject("series")?.optString("name")?.takeIf { it.isNotBlank() },
            seriesIndex = item.optDouble("number", Double.NaN).takeIf { !it.isNaN() },
            progressLabel = readState.page?.let { "Page $it" },
            progressPercent = readState.progressPercent,
            progressPageIndex = readState.page,
            readStatus = readState.status,
            isRead = readState.isRead,
            addedAtMillis = komgaParseInstantMillis(item.optString("created")),
            updatedAtMillis = komgaParseInstantMillis(item.optString("lastModified")),
            lastReadAtMillis = komgaReadActivityMillis(item.optJSONObject("readProgress")),
            genres = metadata.optJSONArray("genres").toStringList()
        )
    }
}

class KomgaHomeShelfModuleImpl(private val auth: KomgaAuthModuleImpl) {
    private val client = KomgaHttpClient().client

    suspend fun loadHomeShelves(serverUrl: String): HomeShelfData = withContext(Dispatchers.IO) {
        val base = normalizeServerUrl(serverUrl) ?: throw UserFacingException(auth.localizedMessage(R.string.perror_komga_server_url, "Enter a valid Komga server URL."))
        suspend fun <T> loadShelfSafely(section: Any, block: () -> T): T? = try {
            currentCoroutineContext().ensureActive()
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Log.w("KomgaHome", "shelf $section failed: ${error.message}")
            null
        }

        val booksBySection = linkedMapOf<HomeSection, List<BookSummary>>()
        listOf(
            HomeSection.CURRENTLY_READING to "/api/v1/books?read_status=IN_PROGRESS&page=0&size=8&sort=readProgress.lastModified,desc",
            HomeSection.ON_DECK to "/api/v1/books/ondeck?page=0&size=8",
            HomeSection.RECENTLY_READ to "/api/v1/books?read_status=READ&page=0&size=8&sort=readProgress.readDate,desc",
            HomeSection.RECENTLY_ADDED_BOOKS to "/api/v1/books/latest?page=0&size=8"
        ).forEach { (section, path) ->
            loadShelfSafely(section) { loadBooks(base, path) }?.let { booksBySection[section] = it }
        }
        loadShelfSafely(HomeSection.RECENTLY_RELEASED_BOOKS) { loadRecentlyReleasedBooks(base) }
            ?.let { booksBySection[HomeSection.RECENTLY_RELEASED_BOOKS] = it }

        val seriesBySection = linkedMapOf<HomeSection, List<BookSummary>>()
        loadShelfSafely(HomeSection.RECENTLY_ADDED_SERIES) { loadSeries(base, komgaHomeSeriesPath(updated = false)) }
            ?.let { seriesBySection[HomeSection.RECENTLY_ADDED_SERIES] = it }
        loadShelfSafely(HomeSection.RECENTLY_UPDATED_SERIES) { loadSeries(base, komgaHomeSeriesPath(updated = true)) }
            ?.let { seriesBySection[HomeSection.RECENTLY_UPDATED_SERIES] = it }

        HomeShelfData(
            booksBySection = booksBySection,
            seriesBySection = seriesBySection,
            isServerProvided = true,
            deferInitialCatalogLoad = true
        )
    }

    private fun loadBooks(base: String, path: String): List<BookSummary> {
        val request = Request.Builder().url("$base$path").get()
            .apply { auth.requestHeaders(base).forEach { (name, value) -> header(name, value) } }
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw UserFacingException(auth.localizedMessage(R.string.perror_komga_home_http, "Unable to load Komga Home shelf (HTTP ${response.code}).", response.code))
            val content = JSONObject(response.body?.string().orEmpty()).optJSONArray("content") ?: JSONArray()
            Log.d("KomgaHome", "response code=${response.code} items=${content.length()}")
            buildList {
                for (index in 0 until content.length()) {
                    content.optJSONObject(index)?.let { parseBook(it, base) }?.let(::add)
                }
            }
        }
    }

    private fun loadRecentlyReleasedBooks(base: String): List<BookSummary> {
        val request = Request.Builder()
            .url("$base/api/v1/books/list?page=0&size=8&sort=metadata.releaseDate,desc")
            .header("Content-Type", "application/json")
            .apply { auth.requestHeaders(base).forEach { (name, value) -> header(name, value) } }
            .post(
                JSONObject()
                    .put(
                        "condition",
                        JSONObject().put(
                            "allOf",
                            JSONArray().put(
                                JSONObject().put(
                                    "releaseDate",
                                    JSONObject()
                                        .put("operator", "after")
                                        .put("dateTime", ZonedDateTime.now().minusMonths(1).toInstant().toString())
                                )
                            )
                        )
                    )
                    .toString()
                    .toRequestBody("application/json; charset=utf-8".toMediaType())
            )
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w("KomgaHome", "recently released error=${response.body?.string()?.take(400).orEmpty()}")
                throw UserFacingException(auth.localizedMessage(R.string.perror_komga_home_http, "Unable to load Komga Home shelf (HTTP ${response.code}).", response.code))
            }
            val content = JSONObject(response.body?.string().orEmpty()).optJSONArray("content") ?: JSONArray()
            Log.d("KomgaHome", "recently released code=${response.code} items=${content.length()}")
            buildList {
                for (index in 0 until content.length()) {
                    content.optJSONObject(index)?.let { parseBook(it, base) }?.let(::add)
                }
            }
        }
    }

    private fun loadSeries(base: String, path: String): List<BookSummary> {
        val request = Request.Builder().url("$base$path").get()
            .apply { auth.requestHeaders(base).forEach { (name, value) -> header(name, value) } }
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw UserFacingException(auth.localizedMessage(R.string.perror_komga_home_http, "Unable to load Komga Home shelf (HTTP ${response.code}).", response.code))
            val content = JSONObject(response.body?.string().orEmpty()).optJSONArray("content") ?: JSONArray()
            Log.d("KomgaHome", "response code=${response.code} items=${content.length()}")
            buildList {
                for (index in 0 until content.length()) {
                    val item = content.optJSONObject(index) ?: continue
                    val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
                    val name = item.optJSONObject("metadata")?.optString("title")?.takeIf { it.isNotBlank() }
                        ?: item.optString("name", id)
                    add(BookSummary(
                        libraryId = item.optString("libraryId"), id = "series:$id", fileId = null,
                        title = name, coverUrl = "$base/api/v1/series/$id/thumbnail",
                        seriesId = id, seriesName = name,
                        addedAtMillis = parseInstantMillis(item.optString("created")),
                        updatedAtMillis = parseInstantMillis(item.optString("lastModified"))
                    ))
                }
            }
        }
    }

    private fun parseBook(item: JSONObject, base: String): BookSummary? {
        val id = item.optString("id").takeIf { it.isNotBlank() } ?: return null
        val metadata = item.optJSONObject("metadata") ?: JSONObject()
        val projection = komgaBookProjection(item)
        val readState = projection.readState
        return BookSummary(
            libraryId = item.optString("libraryId"), id = id, fileId = id,
            title = metadata.optString("title", item.optString("name", id)), filename = projection.filename,
            author = metadata.optJSONArray("authors")?.optJSONObject(0)?.optString("name"),
            format = projection.format, mediaKind = projection.mediaKind,
            availableFormats = projection.availableFormats,
            coverUrl = "$base/api/v1/books/$id/thumbnail",
            seriesId = item.optString("seriesId").takeIf { it.isNotBlank() },
            seriesName = item.optString("seriesTitle").takeIf { it.isNotBlank() },
            seriesIndex = item.optDouble("number", Double.NaN).takeIf { !it.isNaN() },
            progressLabel = readState.page?.let { "Page $it" }, progressPercent = readState.progressPercent,
            progressPageIndex = readState.page,
            readStatus = readState.status,
            isRead = readState.isRead,
            addedAtMillis = parseInstantMillis(item.optString("created")),
            updatedAtMillis = parseInstantMillis(item.optString("lastModified")),
            lastReadAtMillis = komgaReadActivityMillis(item.optJSONObject("readProgress"))
        )
    }

    private fun parseInstantMillis(value: String?): Long? = value?.takeIf { it.isNotBlank() }
        ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
}

class KomgaReadingProgressModuleImpl(
    private val auth: KomgaAuthModuleImpl,
    private val serverUrlProvider: suspend () -> String
) : ReadingProgressModule {
    private val client = KomgaHttpClient().client
    private val json = "application/json; charset=utf-8".toMediaType()

    override suspend fun queueProgress(
        book: BookSummary,
        position: Long,
        pageIndex: Int,
        progressPercent: Float?
    ) = withContext(Dispatchers.IO) {
        val base = normalizeServerUrl(serverUrlProvider())
            ?: throw UserFacingException(auth.localizedMessage(R.string.perror_komga_progress_url_unavailable, "Komga server URL is unavailable for progress synchronization."))
        val page = (pageIndex + 1).coerceAtLeast(1)
        val completed = progressPercent != null && progressPercent >= 99.5f
        val locator = book.readerLocatorJson
            ?.let { runCatching { JSONObject(it) }.getOrNull() }
        val endpoint: String
        val payload = if (locator != null) {
            endpoint = komgaProgressEndpoint(base, book.id, hasLocator = true)
            JSONObject()
                .put("modified", Instant.now().toString())
                .put("device", JSONObject().put("id", "lagrange-android").put("name", "Lagrange Android"))
                .put("locator", locator)
        } else {
            endpoint = komgaProgressEndpoint(base, book.id, hasLocator = false)
            JSONObject()
                .put("page", page)
                .put("completed", completed)
        }
        val request = Request.Builder()
            .url(endpoint)
            .header("Content-Type", "application/json")
            .apply { auth.requestHeaders(base).forEach { (name, value) -> header(name, value) } }
            .method(if (locator != null) "PUT" else "PATCH", payload.toString().toRequestBody(json))
            .build()
        client.newCall(request).execute().use { response ->
            Log.d("KomgaProgress", "book=${book.id} endpoint=${endpoint.substringAfter("/api/v1/")} code=${response.code} page=$page completed=$completed")
            if (!response.isSuccessful) {
                Log.w("KomgaProgress", "error=${response.body?.string()?.take(400).orEmpty()}")
                throw UserFacingException(auth.localizedMessage(R.string.perror_komga_progress_http, "Unable to synchronize Komga reading progress (HTTP ${response.code}).", response.code))
            }
        }
    }

    override suspend fun pendingProgressCount(): Int = 0

    override suspend fun syncPendingProgress(): SyncAttemptResult = SyncAttemptResult.Unsupported
}

class KomgaReadingStatusModuleImpl(
    private val auth: KomgaAuthModuleImpl,
    private val serverUrlProvider: suspend () -> String
) : ReadingStatusModule {
    private val client = KomgaHttpClient().client
    private val json = "application/json; charset=utf-8".toMediaType()

    override suspend fun setBookReadingStatus(book: BookSummary, status: BookReadStatus) {
        when (status) {
            BookReadStatus.READ -> markBookAsRead(book)
            BookReadStatus.UNREAD -> resetBookReadingState(book)
            else -> throw UserFacingException(auth.localizedMessage(R.string.perror_komga_status_unsupported, "Komga supports only read and unread book status."))
        }
    }

    override suspend fun markBookAsRead(book: BookSummary) = withContext(Dispatchers.IO) {
        val base = requireServerUrl()
        val endpoint = "$base/api/v1/books/${book.id}/read-progress"
        val request = Request.Builder()
            .url(endpoint)
            .header("Content-Type", "application/json")
            .apply { auth.requestHeaders(base).forEach { (name, value) -> header(name, value) } }
            .patch(JSONObject().put("completed", true).toString().toRequestBody(json))
            .build()
        execute(endpoint, request)
    }

    override suspend fun resetBookReadingState(book: BookSummary) = withContext(Dispatchers.IO) {
        val base = requireServerUrl()
        val endpoint = "$base/api/v1/books/${book.id}/read-progress"
        val request = Request.Builder()
            .url(endpoint)
            .apply { auth.requestHeaders(base).forEach { (name, value) -> header(name, value) } }
            .delete()
            .build()
        execute(endpoint, request)
    }

    private suspend fun requireServerUrl(): String =
        normalizeServerUrl(serverUrlProvider())
            ?: throw UserFacingException(auth.localizedMessage(R.string.perror_komga_status_url_unavailable, "Komga server URL is unavailable for reading-status synchronization."))

    private fun execute(endpoint: String, request: Request) {
        client.newCall(request).execute().use { response ->
            Log.d("KomgaReadingStatus", "endpoint=${endpoint.substringAfter("/api/v1/")} code=${response.code}")
            if (!response.isSuccessful) {
                Log.w("KomgaReadingStatus", "error=${response.body?.string()?.take(400).orEmpty()}")
                throw UserFacingException(auth.localizedMessage(R.string.perror_komga_status_http, "Unable to synchronize Komga reading status (HTTP ${response.code}).", response.code))
            }
        }
    }
}

class KomgaSeriesModuleImpl(
    private val auth: KomgaAuthModuleImpl,
    private val catalogModule: KomgaBookCatalogModuleImpl
) : KomgaSeriesModule {
    private val client = KomgaHttpClient().client

    override suspend fun loadSeriesCatalog(serverUrl: String, libraryId: String?, filter: SeriesCatalogFilter, page: Int): SeriesCatalogPage = withContext(Dispatchers.IO) {
        val base = normalizeServerUrl(serverUrl)
            ?: throw UserFacingException(auth.localizedMessage(R.string.perror_komga_server_url, "Enter a valid Komga server URL."))
        val effectiveFilter = if (libraryId.isNullOrBlank()) filter else filter.copy(libraryId = libraryId)
        val request = Request.Builder()
            .url("$base${komgaSeriesSearchPath()}")
            .header("Content-Type", "application/json")
            .apply { auth.requestHeaders(base).forEach { (name, value) -> header(name, value) } }
            .post(komgaSeriesSearchPayload(effectiveFilter, libraryId).toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        val items = client.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw UserFacingException(auth.localizedMessage(R.string.perror_komga_series_http, "Unable to load Komga series (HTTP ${response.code}).", response.code))
            }
            val content = JSONObject(payload).optJSONArray("content") ?: JSONArray()
            buildList {
                for (index in 0 until content.length()) {
                    val item = content.optJSONObject(index) ?: continue
                    val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
                    val metadata = item.optJSONObject("metadata") ?: JSONObject()
                    val booksMetadata = item.optJSONObject("booksMetadata") ?: JSONObject()
                    val authors = buildList {
                        booksMetadata.optJSONArray("authors")?.let { authorsArray ->
                            for (authorIndex in 0 until authorsArray.length()) {
                                authorsArray.optJSONObject(authorIndex)?.optString("name")
                                    ?.takeIf { it.isNotBlank() }?.let(::add)
                            }
                        }
                    }.distinct().sorted()
                    add(
                        SeriesSummary(
                            id = id,
                            name = metadata.optString("title", item.optString("name", id)),
                            authors = authors,
                            bookCount = item.optInt("booksCount", 0),
                            readCount = item.optInt("booksReadCount", 0),
                            availableFormats = emptyList(),
                            downloadedFormats = emptyList(),
                            genres = metadata.optJSONArray("genres").toStringList(),
                            coverUrl = "$base/api/v1/series/$id/thumbnail",
                            lastAddedAtMillis = komgaParseInstantMillis(item.optString("created"))
                                ?: komgaParseInstantMillis(item.optString("fileLastModified"))
                        )
                    )
                }
            }
        }
        komgaSeriesCatalogPage(items, effectiveFilter, page)
    }

    override suspend fun loadSeriesDetail(serverUrl: String, seriesId: String): SeriesDetailInfo? {
        val books = catalogModule.loadSeriesBooks(serverUrl, seriesId)
            .sortedWith(compareBy<BookSummary> { it.seriesIndex ?: Double.MAX_VALUE }.thenBy { it.title })
        if (books.isEmpty()) return null
        val name = books.firstNotNullOfOrNull { it.seriesName?.takeIf(String::isNotBlank) } ?: "Series"
        val firstBook = books.firstOrNull()?.let { book ->
            try {
                KomgaBookDetailModuleImpl(auth).loadBookDetail(serverUrl, book)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                null
            }
        }
        return SeriesDetailInfo(
            id = seriesId,
            name = name,
            bookCount = books.size,
            readCount = books.count { it.isRead || (it.progressPercent ?: 0f) >= 99.5f },
            authors = books.flatMap { it.author.orEmpty().split(",") }.map(String::trim)
                .filter(String::isNotBlank).distinct().sorted(),
            books = books,
            firstBook = firstBook
        )
    }
}

private fun komgaMediaKind(format: String?, filename: String?): MediaKind {
    val value = (format ?: filename?.substringAfterLast('.')).orEmpty().lowercase(Locale.US)
    return when {
        value.contains("epub") -> MediaKind.EPUB
        value == "pdf" -> MediaKind.PDF
        value in setOf("cbz", "cbr", "cb7") -> MediaKind.COMIC
        value in setOf("mp3", "m4b", "m4a", "flac", "ogg", "opus", "wav") -> MediaKind.AUDIO
        else -> MediaKind.UNKNOWN
    }
}

class KomgaBookDetailModuleImpl(private val auth: KomgaAuthModuleImpl) : KomgaBookDetailModule {
    private val client = KomgaHttpClient().client

    override suspend fun loadBookDetail(serverUrl: String, book: BookSummary): BookDetailInfo = withContext(Dispatchers.IO) {
        val base = normalizeServerUrl(serverUrl) ?: throw UserFacingException(auth.localizedMessage(R.string.perror_komga_server_url, "Enter a valid Komga server URL."))
        val request = Request.Builder().url("$base/api/v1/books/${book.id}").get()
            .apply { auth.requestHeaders(base).forEach { (name, value) -> header(name, value) } }
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw UserFacingException(auth.localizedMessage(R.string.perror_komga_details_http, "Unable to load Komga book details (HTTP ${response.code}).", response.code))
            val root = JSONObject(response.body?.string().orEmpty())
            val metadata = root.optJSONObject("metadata") ?: JSONObject()
            val media = root.optJSONObject("media") ?: JSONObject()
            val primaryFile = media.optJSONArray("files")?.optJSONObject(0)
            val filename = primaryFile?.optString("fileName")?.takeIf { it.isNotBlank() }
                ?: media.optString("fileName").takeIf { it.isNotBlank() }
                ?: root.optString("name").takeIf { it.isNotBlank() }
            val rawFormat = primaryFile?.optString("mediaType")?.takeIf { it.isNotBlank() }
                ?: media.optString("mediaType").takeIf { it.isNotBlank() }
                ?: filename?.substringAfterLast('.', "")
            val format = komgaFormat(rawFormat, filename)
            val mediaKind = komgaMediaKind(format, filename)
            val progressionLocatorJson = if (mediaKind == MediaKind.EPUB) {
                runCatching {
                    val progressionRequest = Request.Builder()
                        .url("$base/api/v1/books/${book.id}/progression")
                        .header("Accept", "application/vnd.readium.progression+json")
                        .apply { auth.requestHeaders(base).forEach { (name, value) -> header(name, value) } }
                        .build()
                    client.newCall(progressionRequest).execute().use { progressionResponse ->
                        if (progressionResponse.isSuccessful) {
                            komgaProgressionLocatorJson(progressionResponse.body?.string().orEmpty())
                        } else {
                            null
                        }
                    }
                }.getOrNull()
            } else {
                null
            }
            val readProgressObject = root.optJSONObject("readProgress")
            val readProgress = komgaReadState(readProgressObject)
            val resolvedBook = book.copy(
                title = metadata.optString("title", book.title),
                filename = filename ?: book.filename,
                format = komgaFormat(format, filename) ?: book.format,
                mediaKind = mediaKind,
                streamUrl = "$base/api/v1/books/${book.id}/file",
                downloadUrl = "$base/api/v1/books/${book.id}/file",
                coverUrl = "$base/api/v1/books/${book.id}/thumbnail",
                author = metadata.optJSONArray("authors")?.optJSONObject(0)?.optString("name") ?: book.author,
                seriesName = root.optString("seriesTitle").takeIf { it.isNotBlank() }
                    ?: metadata.optString("series").takeIf { it.isNotBlank() }
                    ?: book.seriesName,
                seriesId = root.optString("seriesId").takeIf { it.isNotBlank() }
                    ?: metadata.optString("seriesId").takeIf { it.isNotBlank() }
                    ?: book.seriesId,
                seriesIndex = root.optDouble("number", Double.NaN).takeIf { !it.isNaN() }
                    ?: book.seriesIndex,
                readerPageCount = media.optInt("pagesCount").takeIf { it > 0 },
                progressLabel = readProgress.page?.let { "Page $it" } ?: book.progressLabel,
                progressPageIndex = readProgress.page?.minus(1)?.coerceAtLeast(0) ?: book.progressPageIndex,
                readStatus = readProgress.status,
                isRead = readProgress.isRead,
                lastReadAtMillis = komgaReadActivityMillis(readProgressObject)
                    ?: book.lastReadAtMillis,
                readerLocatorJson = progressionLocatorJson ?: book.readerLocatorJson
            )
            val fileOption = BookFileOption(
                book = resolvedBook,
                filename = filename,
                sizeBytes = root.optLong("sizeBytes", 0L).takeIf { it > 0 }
                    ?: primaryFile?.optLong("sizeBytes", 0L)
                    ?.takeIf { it > 0 }
                    ?: primaryFile?.optLong("fileSize", 0L)?.takeIf { it > 0 }
                    ?: media.optLong("sizeBytes", 0L).takeIf { it > 0 }
                    ?: media.optLong("fileSize", 0L).takeIf { it > 0 },
                role = "PRIMARY"
            )
            BookDetailInfo(
                book = resolvedBook,
                synopsis = metadata.optString("summary").takeIf { it.isNotBlank() },
                publisher = metadata.optString("publisher").takeIf { it.isNotBlank() },
                publishedDate = metadata.optString("releaseDate").takeIf { it.isNotBlank() },
                language = metadata.optString("language").takeIf { it.isNotBlank() },
                pageCount = media.optInt("pagesCount").takeIf { it > 0 },
                genres = metadata.optJSONArray("genres").toStringList(),
                tags = metadata.optJSONArray("tags").toStringList(),
                fileCount = 1,
                availableFiles = listOf(fileOption),
                totalSizeBytes = fileOption.sizeBytes
            )
        }
    }
}

private fun JSONArray?.toStringList(): List<String> = buildList {
    val array = this@toStringList ?: return@buildList
    for (index in 0 until array.length()) {
        array.optString(index).takeIf { it.isNotBlank() }?.let(::add)
    }
}

class KomgaCoverModuleImpl(private val auth: KomgaAuthModuleImpl) : KomgaCoverModule {
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .addNetworkInterceptor { chain ->
            val incoming = chain.request()
            val latch = incoming.tag(CredentialTrustLatch::class.java)
                ?: CredentialTrustLatch(null)
            val tagged = incoming.newBuilder()
                .tag(CredentialTrustLatch::class.java, latch)
                .build()
            chain.proceed(originBoundRequest(tagged, latch.credentialOrigin, latch))
        }
        .build()

    override suspend fun loadBookCover(serverUrl: String, book: BookSummary): ByteArray? = withContext(Dispatchers.IO) {
        val base = normalizeServerUrl(serverUrl) ?: return@withContext null
        val thumbnailUrl = book.coverUrl?.takeIf { it.isNotBlank() } ?: if (book.id.startsWith("series:")) {
            "$base/api/v1/series/${book.id.removePrefix("series:")}/thumbnail"
        } else {
            "$base/api/v1/books/${book.id}/thumbnail"
        }
        loadImage(thumbnailUrl, base)
    }

    override suspend fun loadCatalogImage(url: String): ByteArray? = withContext(Dispatchers.IO) {
        loadImage(url, auth.credentialOrigin())
    }

    private fun loadImage(url: String, credentialOrigin: String?): ByteArray? {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "image/*")
            .apply {
                if (sameHttpOrigin(url, credentialOrigin.orEmpty())) {
                    auth.requestHeaders(url).forEach { (name, value) -> header(name, value) }
                }
            }
            .tag(CredentialTrustLatch::class.java, CredentialTrustLatch(credentialOrigin))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body ?: return ByteArray(0)
            return body.byteStream().use { input ->
                readBoundedCatalogImageBytes(
                    input = input,
                    declaredLength = body.contentLength(),
                    maxBytes = catalogImageResponseLimit(
                        url = response.request.url.toString(),
                        serverUrl = credentialOrigin.orEmpty()
                    )
                )
            }
        }
    }
}

class KomgaDownloadModuleImpl(
    private val context: Context,
    private val auth: KomgaAuthModuleImpl
) : KomgaDownloadModule {
    private val client = KomgaHttpClient().client
    private val downloadStore = DownloadStore(context)

    override suspend fun downloadBook(
        serverUrl: String,
        book: BookSummary,
        expectedStorageScopeId: String?,
        onProgress: (Float?) -> Unit
    ): DownloadedBookFile = withContext(Dispatchers.IO) {
        val base = normalizeServerUrl(serverUrl) ?: throw UserFacingException(auth.localizedMessage(R.string.perror_komga_server_url, "Enter a valid Komga server URL."))
        if (!auth.hasSession(base)) {
            auth.restoreCurrentProfileSession()
        }
        if (!auth.hasSession(base)) {
            throw AuthenticationRequiredException()
        }
        val fileId = book.fileId ?: book.id
        val profileId = ServerProfileStore(context).activeId().orEmpty()
        val currentStorageScopeId = downloadStorageScopeId(context, serverUrl, profileId)
            ?: throw AuthenticationRequiredException()
        val storageScopeId = expectedStorageScopeId ?: currentStorageScopeId
        if (storageScopeId != currentStorageScopeId) throw AuthenticationRequiredException()
        fun requireCapturedAccountScope() {
            if (downloadStorageScopeId(context, serverUrl, profileId) != storageScopeId) {
                throw AuthenticationRequiredException()
            }
        }
        val existingRecord = downloadStore.find(serverUrl, fileId, storageScopeId)
        val scheduledAttempt = downloadStore.findAttempt(serverUrl, fileId, storageScopeId)
        val attemptOrigin = scheduledAttempt?.origin ?: DownloadOrigin.MANUAL
        val storagePolicy = AutomaticDownloadPolicyStore(context)
            .read(profileId, serverUrl, storageScopeId)
        if (DownloadStorageSelectionStore(context).read().usesPortableFolder) {
            return@withContext downloadBookToPortableStorage(
                context = context,
                downloadStore = downloadStore,
                serverUrl = serverUrl,
                profileId = profileId,
                storageScopeId = storageScopeId,
                book = book,
                scheduledAttempt = scheduledAttempt,
                existingRecord = existingRecord,
                storagePolicy = storagePolicy,
                requireCapturedAccountScope = ::requireCapturedAccountScope,
                onProgress = onProgress
            ) { consume ->
                requireCapturedAccountScope()
                val request = Request.Builder().url("$base/api/v1/books/${book.id}/file").get()
                    .apply { auth.requestHeaders(base).forEach { (name, value) -> header(name, value) } }
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw HttpRequestException(response.code, "download the Komga book")
                    }
                    val body = response.body
                        ?: throw UserFacingException(
                            auth.localizedMessage(
                                R.string.perror_komga_empty_file,
                                "Komga returned an empty book file."
                            )
                        )
                    body.byteStream().use { input ->
                        consume(input, body.contentLength().takeIf { it > 0L })
                    }
                }
            }
        }
        reusableDownloadedBook(context, book, existingRecord)?.let { reusable ->
            downloadStore.removeAttemptIfOwned(
                serverUrl,
                fileId,
                scheduledAttempt?.requestId.orEmpty(),
                storageScopeId = storageScopeId
            )
            requireCapturedAccountScope()
            onProgress(1f)
            return@withContext reusable
        }
        val target = DownloadStore(context).ownedTargetOrIsolated(
            serverUrl = serverUrl,
            fileId = fileId,
            title = book.title,
            mediaKind = book.mediaKind,
            formatHint = book.format,
            recordedPath = existingRecord?.localPath,
            storageScopeId = storageScopeId
        )
        val hadUsableExisting = existingRecord != null &&
            downloadedLocatorPassesIntegrity(context, book, existingRecord.localPath)
        if (scheduledAttempt == null) {
            downloadStore.saveAttemptIfAbsent(
                DownloadAttempt(
                serverUrl = serverUrl,
                requestId = "",
                profileId = profileId,
                storageScopeId = storageScopeId,
                fileId = fileId,
                bookId = book.id,
                libraryId = book.libraryId,
                title = book.title,
                filename = book.filename,
                targetPath = target.absolutePath,
                existingLocalPath = existingRecord?.localPath.takeIf { hadUsableExisting },
                mediaKind = book.mediaKind,
                mimeType = book.format,
                sourceUpdatedAtMillis = book.updatedAtMillis,
                origin = DownloadOrigin.MANUAL,
                requiresQueueOwnership = false
                )
            )
        }
        target.parentFile?.mkdirs()
        val stagingId = scheduledAttempt?.requestId?.takeIf(String::isNotBlank)
            ?: java.util.UUID.randomUUID().toString()
        val temporary = File(target.parentFile, ".${target.name}.part.$stagingId")
        val storageReservationId = "$serverUrl\u0000$fileId\u0000$stagingId"
        var retainStagedForRetry = false
        if (!downloadStore.markAttemptStateIfOwned(
                serverUrl,
                fileId,
                scheduledAttempt?.requestId.orEmpty(),
                DownloadAttemptState.STAGING,
                temporary,
                storageScopeId
            )
        ) {
            throw CancellationException("This download was replaced by a newer request.")
        }
        if (temporary.exists() && !temporary.delete()) {
            throw UserFacingException(auth.localizedMessage(R.string.perror_download_stage_replace, "Unable to replace the interrupted download stage."))
        }
        requireCapturedAccountScope()
        val request = Request.Builder().url("$base/api/v1/books/${book.id}/file").get()
            .apply { auth.requestHeaders(base).forEach { (name, value) -> header(name, value) } }
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw HttpRequestException(response.code, "download the Komga book")
                val body = response.body ?: throw UserFacingException(auth.localizedMessage(R.string.perror_komga_empty_file, "Komga returned an empty book file."))
                val total = body.contentLength().takeIf { it > 0L }
                if (total != null) {
                    if (downloadIsIndividuallyOverCap(
                            total,
                            attemptOrigin,
                            storagePolicy
                        )
                    ) {
                        throw LocalBookStorageLimitException(
                            "This file is larger than the maximum local-book storage limit."
                        )
                    }
                }
                val (capacity, initialAllowance) = runBlocking {
                    AutomaticDownloadStorage.prepareTransfer(
                        context = context,
                        serverUrl = serverUrl,
                        profileId = profileId,
                        policy = storagePolicy,
                        declaredBytes = total,
                        existingFinalBytes = existingRecord
                            ?.let { LocalDownloadAccess(context).length(it.localPath) }
                            ?.coerceAtLeast(0L)
                            ?: 0L,
                        protectedFileIds = setOf(fileId),
                        allowRemoval = true,
                        reservationId = storageReservationId,
                        stagedPath = temporary.absolutePath,
                        origin = attemptOrigin
                    )
                }
                if (!capacity.allowed ||
                    (total != null && initialAllowance.maximumAdditionalBytes != Long.MAX_VALUE &&
                        total > initialAllowance.maximumAdditionalBytes)
                ) {
                    throw LocalBookStorageLimitException(
                        capacity.message ?: "This download would exceed the configured storage limits."
                    )
                }
                var copied = 0L
                var allowance = initialAllowance
                onProgress(0f.takeIf { total != null })
                body.byteStream().use { input ->
                    FileOutputStream(temporary).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            requireCapturedAccountScope()
                            val count = input.read(buffer)
                            if (count < 0) break
                            var permitted = allowance.permitsCurrentPolicyAndReserve(
                                    context,
                                    temporary,
                                    copied,
                                    count,
                                    attemptOrigin
                                )
                            if (!permitted && total == null) {
                                val (extendedCapacity, extendedAllowance) = runBlocking {
                                    AutomaticDownloadStorage.extendUnknownTransfer(
                                        context = context,
                                        serverUrl = serverUrl,
                                        profileId = profileId,
                                        policy = storagePolicy,
                                        bytesWritten = copied,
                                        protectedFileIds = setOf(fileId),
                                        reservationId = storageReservationId,
                                        stagedPath = temporary.absolutePath
                                    )
                                }
                                if (extendedCapacity.allowed) allowance = extendedAllowance
                                permitted = extendedCapacity.allowed &&
                                    allowance.permitsCurrentPolicyAndReserve(
                                        context,
                                        temporary,
                                        copied,
                                        count,
                                        attemptOrigin
                                    )
                            }
                            if (!permitted) {
                                throw LocalBookStorageLimitException(
                                    "The download stopped before crossing the configured local-book storage limits."
                                )
                            }
                            output.write(buffer, 0, count)
                            copied += count
                            onProgress(komgaDownloadProgress(copied, total))
                        }
                    }
                }
                if (!temporary.isFile || temporary.length() <= 0L) {
                    throw UserFacingException(auth.localizedMessage(R.string.perror_komga_empty_file, "Komga returned an empty book file."))
                }
                if (!downloadedFilePassesIntegrity(book, temporary)) {
                    val suffix = if (hadUsableExisting) " The previous local copy was kept." else ""
                    throw UserFacingException(auth.localizedMessage(if (hadUsableExisting) R.string.perror_komga_download_integrity_kept else R.string.perror_komga_download_integrity, "The downloaded Komga file did not pass integrity checks.$suffix"))
                }
                onProgress(1f)
            }
            val completedRecord = DownloadRecord(
                serverUrl = serverUrl,
                profileId = profileId,
                storageScopeId = storageScopeId,
                fileId = fileId,
                bookId = book.id,
                libraryId = book.libraryId,
                title = book.title,
                filename = book.filename ?: target.name,
                localPath = target.absolutePath,
                mediaKind = book.mediaKind,
                mimeType = book.format,
                sourceUpdatedAtMillis = book.updatedAtMillis,
                downloadedAtMillis = existingRecord?.downloadedAtMillis
                    ?: System.currentTimeMillis(),
                lastAccessedAtMillis = listOfNotNull(
                    existingRecord?.lastAccessedAtMillis,
                    book.lastReadAtMillis
                ).maxOrNull(),
                lastKnownCompleted = when {
                    book.isRead -> true
                    book.readStatus != null -> book.readStatus.isCompletedStatus()
                    else -> existingRecord?.lastKnownCompleted
                },
                sizeBytes = temporary.length().coerceAtLeast(0L),
                origin = existingRecord?.origin
                    ?: scheduledAttempt?.origin
                    ?: DownloadOrigin.MANUAL,
                status = DownloadRecordStatus.COMPLETE
            )
            existingRecord
                ?.takeUnless { localDownloadLocatorsMatch(it.localPath, target.absolutePath) }
                ?.let { PortableDownloadCleanup.defer(context, it) }
            requireCapturedAccountScope()
            val committed = try {
                verifyAndCommitDownloadWhenReaderInactive(
                    context = context,
                    downloadStore = downloadStore,
                    serverUrl = serverUrl,
                    storageScopeId = storageScopeId,
                    fileId = fileId,
                    requestId = scheduledAttempt?.requestId.orEmpty(),
                    stagedFile = temporary,
                    targetFile = target,
                    record = completedRecord
                )
            } catch (deferred: DownloadReplacementDeferredException) {
                retainStagedForRetry = true
                throw deferred
            }
            if (!committed) {
                throw CancellationException("This download was replaced by a newer request.")
            }
        } finally {
            runBlocking {
                AutomaticDownloadStorage.releaseTransfer(context, storageReservationId)
            }
            if (!retainStagedForRetry && temporary.exists()) temporary.delete()
        }
        PortableDownloadCleanup.schedule(context)
        DownloadedBookFile(target.absolutePath, target)
    }
}

internal fun komgaDownloadProgress(copiedBytes: Long, totalBytes: Long?): Float? =
    totalBytes?.takeIf { it > 0L }?.let { total ->
        (copiedBytes.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    }
