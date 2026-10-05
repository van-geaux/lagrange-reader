package com.vangeaux.lagrange.provider.komga

import com.vangeaux.lagrange.*
import com.vangeaux.lagrange.core.*

import android.content.Context
import android.util.Log
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64

import com.vangeaux.lagrange.*
import kotlinx.coroutines.Dispatchers
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
import java.time.Instant
import java.time.ZonedDateTime
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.net.ssl.SSLException

private const val PAGE_SIZE = 100

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
        lastReadAtMillis = progress.optString("readDate")
            .takeIf { it.isNotBlank() }
            ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
            ?: book.lastReadAtMillis
    )
}

internal fun komgaProgressionLocatorJson(payload: String): String? =
    JSONObject(payload).optJSONObject("locator")?.toString()

internal fun komgaProgressEndpoint(base: String, bookId: String, hasLocator: Boolean): String =
    if (hasLocator) "$base/api/v1/books/$bookId/progression"
    else "$base/api/v1/books/$bookId/read-progress"

private fun komgaFormat(format: String?, filename: String?): String? {
    val value = (format ?: filename?.substringAfterLast('.')).orEmpty().lowercase(Locale.US)
    return when {
        value.contains("epub") -> "epub"
        value.contains("pdf") -> "pdf"
        value.contains("cbz") -> "cbz"
        value.contains("cbr") -> "cbr"
        value.contains("cb7") || value.contains("7z") -> "cb7"
        value.contains("m4b") -> "m4b"
        value.contains("m4a") -> "m4a"
        value.contains("mp3") || value.contains("mpeg") -> "mp3"
        value.contains("flac") -> "flac"
        value.contains("opus") -> "opus"
        value.contains("ogg") -> "ogg"
        value.contains("wav") -> "wav"
        else -> filename?.substringAfterLast('.', "")?.takeIf { it.isNotBlank() }
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

private class KomgaHttpClient {
    val client = OkHttpClient()
}

private class KomgaCredentialStore(context: Context) {
    private val preferences = context.getSharedPreferences("komga_auth", Context.MODE_PRIVATE)

    fun read(profileId: String): String? = runCatching {
        val encoded = preferences.getString(profileKey(profileId), null)
            ?: preferences.getString(AUTH_KEY, null)
            ?: return null
        val packed = Base64.decode(encoded, Base64.DEFAULT)
        val iv = packed.copyOfRange(0, GCM_IV_LENGTH)
        val encrypted = packed.copyOfRange(GCM_IV_LENGTH, packed.size)
        Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(GCM_TAG_LENGTH, iv))
        }.doFinal(encrypted).toString(Charsets.UTF_8)
    }.getOrNull()

    fun write(profileId: String, value: String) {
        runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
            val packed = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            preferences.edit().putString(profileKey(profileId), Base64.encodeToString(packed, Base64.NO_WRAP)).apply()
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
    private var serverUrl: String? = null
    private var authorization: String? = ServerProfileStore(appContext).active()?.id?.let(credentialStore::read)

    override suspend fun checkServer(serverUrl: String): ServerCheckResult = withContext(Dispatchers.IO) {
        val base = normalizeServerUrl(serverUrl) ?: return@withContext ServerCheckResult.MalformedUrl
        runCatching {
            val request = Request.Builder().url("$base/api/v1/libraries").get().build()
            client.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful || response.code == 401 || response.code == 403 -> ServerCheckResult.Reachable
                    response.isRedirect -> ServerCheckResult.Redirected
                    else -> ServerCheckResult.HttpFailure
                }
            }
        }.getOrElse { error ->
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
            ?: throw UserFacingException("Enter a valid Komga server URL.")
        val request = Request.Builder()
            .url("$base/api/v2/users/me")
            .header("Authorization", Credentials.basic(username, password))
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 401 || response.code == 403) {
                throw UserFacingException("Komga did not accept this username or password.")
            }
            if (!response.isSuccessful) {
                throw UserFacingException("Komga sign-in failed with HTTP ${response.code}.")
            }
        }
        this@KomgaAuthModuleImpl.serverUrl = base
        authorization = Credentials.basic(username, password)
        activeProfileId()?.let { credentialStore.write(it, authorization!!) }
        Unit
    }

    override suspend fun sessionState(serverUrl: String): SessionState = withContext(Dispatchers.IO) {
        val base = normalizeServerUrl(serverUrl) ?: return@withContext SessionState.Unavailable
        val auth = authorization ?: return@withContext SessionState.Unauthenticated
        runCatching {
            val request = Request.Builder()
                .url("$base/api/v2/users/me")
                .header("Authorization", auth)
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> SessionState.Authenticated
                    response.code == 401 || response.code == 403 -> SessionState.Unauthenticated
                    else -> SessionState.Unavailable
                }
            }
        }.getOrDefault(SessionState.Unavailable)
    }

    override suspend fun clearSession() {
        serverUrl = null
        authorization = null
        activeProfileId()?.let(credentialStore::clear)
    }

    internal suspend fun saveCurrentProfileSession() {
        activeProfileId()?.let { profileId -> authorization?.let { credentialStore.write(profileId, it) } }
    }

    internal suspend fun restoreCurrentProfileSession(): Boolean {
        authorization = activeProfileId()?.let(credentialStore::read)
        return !authorization.isNullOrBlank()
    }

    internal fun clearRuntimeSession() {
        serverUrl = null
        authorization = null
    }

    private fun activeProfileId(): String? = ServerProfileStore(appContext).active()?.id

    internal fun authorizationHeader(): String? = authorization
}

class KomgaLibraryModuleImpl(private val auth: KomgaAuthModuleImpl) : KomgaLibraryModule {
    private val client = KomgaHttpClient().client

    override suspend fun loadLibraries(serverUrl: String): List<LibrarySummary> = withContext(Dispatchers.IO) {
        val base = normalizeServerUrl(serverUrl) ?: throw UserFacingException("Enter a valid Komga server URL.")
        val request = Request.Builder().url("$base/api/v1/libraries").get()
            .apply { auth.authorizationHeader()?.let { header("Authorization", it) } }
            .build()
        client.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw UserFacingException("Unable to load Komga libraries (HTTP ${response.code}).")
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

    override suspend fun loadBooks(serverUrl: String, libraryId: String, page: Int): LibraryBooksPage = withContext(Dispatchers.IO) {
        val base = normalizeServerUrl(serverUrl) ?: throw UserFacingException("Enter a valid Komga server URL.")
        val url = "$base/api/v1/books?library_id=$libraryId&page=$page&size=$PAGE_SIZE&sort=metadata.title,asc"
        val request = Request.Builder().url(url).get()
            .apply { auth.authorizationHeader()?.let { header("Authorization", it) } }
            .build()
        client.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw UserFacingException("Unable to load Komga books (HTTP ${response.code}).")
            val root = JSONObject(payload)
            val content = root.optJSONArray("content") ?: JSONArray()
            val items = buildList {
                for (index in 0 until content.length()) {
                    val item = content.optJSONObject(index) ?: continue
                    val metadata = item.optJSONObject("metadata") ?: JSONObject()
                    val projection = komgaBookProjection(item)
                    val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
                    val readState = projection.readState
                    add(
                        BookSummary(
                            libraryId = libraryId,
                            id = id,
                            fileId = item.optString("id").takeIf { it.isNotBlank() },
                            title = metadata.optString("title", id),
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
                            isRead = readState.isRead
                        )
                    )
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
}

class KomgaHomeShelfModuleImpl(private val auth: KomgaAuthModuleImpl) {
    private val client = KomgaHttpClient().client

    suspend fun loadHomeShelves(serverUrl: String): HomeShelfData = withContext(Dispatchers.IO) {
        val base = normalizeServerUrl(serverUrl) ?: throw UserFacingException("Enter a valid Komga server URL.")
        HomeShelfData(
            booksBySection = buildMap {
                listOf(
                    HomeSection.CURRENTLY_READING to "/api/v1/books?read_status=IN_PROGRESS&page=0&size=8&sort=readProgress.lastModified,desc",
                    HomeSection.ON_DECK to "/api/v1/books/ondeck?page=0&size=8",
                    HomeSection.RECENTLY_READ to "/api/v1/books?read_status=READ&page=0&size=8&sort=readProgress.readDate,desc",
                    HomeSection.RECENTLY_ADDED_BOOKS to "/api/v1/books/latest?page=0&size=8"
                ).forEach { (section, path) ->
                    runCatching { loadBooks(base, path) }
                        .onFailure { error -> Log.w("KomgaHome", "shelf $section failed: ${error.message}") }
                        .getOrNull()?.let { put(section, it) }
                }
                runCatching { loadRecentlyReleasedBooks(base) }
                    .onFailure { error -> Log.w("KomgaHome", "shelf ${HomeSection.RECENTLY_RELEASED_BOOKS} failed: ${error.message}") }
                    .getOrNull()?.let { put(HomeSection.RECENTLY_RELEASED_BOOKS, it) }
            },
            seriesBySection = runCatching {
                mapOf(HomeSection.RECENTLY_ADDED_SERIES to loadSeries(base, "/api/v1/series/new?page=0&size=8"))
            }.onFailure { error -> Log.w("KomgaHome", "recent series failed: ${error.message}") }
                .getOrDefault(emptyMap()),
            isServerProvided = true
        )
    }

    private fun loadBooks(base: String, path: String): List<BookSummary> {
        val request = Request.Builder().url("$base$path").get()
            .apply { auth.authorizationHeader()?.let { header("Authorization", it) } }
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw UserFacingException("Unable to load Komga Home shelf (HTTP ${response.code}).")
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
            .apply { auth.authorizationHeader()?.let { header("Authorization", it) } }
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
                throw UserFacingException("Unable to load Komga Home shelf (HTTP ${response.code}).")
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
            .apply { auth.authorizationHeader()?.let { header("Authorization", it) } }
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw UserFacingException("Unable to load Komga Home shelf (HTTP ${response.code}).")
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
            lastReadAtMillis = parseInstantMillis(item.optJSONObject("readProgress")?.optString("readDate").orEmpty())
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
            ?: throw UserFacingException("Komga server URL is unavailable for progress synchronization.")
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
            .apply { auth.authorizationHeader()?.let { header("Authorization", it) } }
            .method(if (locator != null) "PUT" else "PATCH", payload.toString().toRequestBody(json))
            .build()
        client.newCall(request).execute().use { response ->
            Log.d("KomgaProgress", "book=${book.id} endpoint=${endpoint.substringAfter("/api/v1/")} code=${response.code} page=$page completed=$completed")
            if (!response.isSuccessful) {
                Log.w("KomgaProgress", "error=${response.body?.string()?.take(400).orEmpty()}")
                throw UserFacingException("Unable to synchronize Komga reading progress (HTTP ${response.code}).")
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
            else -> throw UserFacingException("Komga supports only read and unread book status.")
        }
    }

    override suspend fun markBookAsRead(book: BookSummary) = withContext(Dispatchers.IO) {
        val base = requireServerUrl()
        val endpoint = "$base/api/v1/books/${book.id}/read-progress"
        val request = Request.Builder()
            .url(endpoint)
            .header("Content-Type", "application/json")
            .apply { auth.authorizationHeader()?.let { header("Authorization", it) } }
            .patch(JSONObject().put("completed", true).toString().toRequestBody(json))
            .build()
        execute(endpoint, request)
    }

    override suspend fun resetBookReadingState(book: BookSummary) = withContext(Dispatchers.IO) {
        val base = requireServerUrl()
        val endpoint = "$base/api/v1/books/${book.id}/read-progress"
        val request = Request.Builder()
            .url(endpoint)
            .apply { auth.authorizationHeader()?.let { header("Authorization", it) } }
            .delete()
            .build()
        execute(endpoint, request)
    }

    private suspend fun requireServerUrl(): String =
        normalizeServerUrl(serverUrlProvider())
            ?: throw UserFacingException("Komga server URL is unavailable for reading-status synchronization.")

    private fun execute(endpoint: String, request: Request) {
        client.newCall(request).execute().use { response ->
            Log.d("KomgaReadingStatus", "endpoint=${endpoint.substringAfter("/api/v1/")} code=${response.code}")
            if (!response.isSuccessful) {
                Log.w("KomgaReadingStatus", "error=${response.body?.string()?.take(400).orEmpty()}")
                throw UserFacingException("Unable to synchronize Komga reading status (HTTP ${response.code}).")
            }
        }
    }
}

class KomgaSeriesModuleImpl(
    private val auth: KomgaAuthModuleImpl,
    private val libraryModule: KomgaLibraryModuleImpl,
    private val catalogModule: KomgaBookCatalogModuleImpl
) : KomgaSeriesModule {
    override suspend fun loadSeriesCatalog(serverUrl: String, libraryId: String?, filter: SeriesCatalogFilter, page: Int): SeriesCatalogPage {
        val books = filterBooksForSeriesCatalog(loadAllBooks(serverUrl, null), filter.libraryId)
        val filtered = filterAndSortSeriesCatalog(aggregateBooksToSeriesCatalog(books).items, filter)
        val from = (page * PAGE_SIZE).coerceAtMost(filtered.size)
        val to = (from + PAGE_SIZE).coerceAtMost(filtered.size)
        return SeriesCatalogPage(filtered.subList(from, to), filtered.size, page, PAGE_SIZE)
    }

    override suspend fun loadSeriesDetail(serverUrl: String, seriesId: String): SeriesDetailInfo? {
        val books = loadAllBooks(serverUrl, null).filter { book ->
            book.seriesId == seriesId ||
                (book.seriesId.isNullOrBlank() && "name:${book.seriesName}" == seriesId)
        }.sortedWith(compareBy<BookSummary> { it.seriesIndex ?: Double.MAX_VALUE }.thenBy { it.title })
        if (books.isEmpty()) return null
        val name = books.firstNotNullOfOrNull { it.seriesName?.takeIf(String::isNotBlank) } ?: "Series"
        return SeriesDetailInfo(
            id = seriesId,
            name = name,
            bookCount = books.size,
            readCount = books.count { it.isRead || (it.progressPercent ?: 0f) >= 99.5f },
            authors = books.flatMap { it.author.orEmpty().split(",") }.map(String::trim)
                .filter(String::isNotBlank).distinct().sorted(),
            books = books,
            firstBook = books.firstOrNull()?.let { runCatching { KomgaBookDetailModuleImpl(auth).loadBookDetail(serverUrl, it) }.getOrNull() }
        )
    }

    private suspend fun loadAllBooks(serverUrl: String, libraryId: String?): List<BookSummary> {
        val libraries = if (libraryId == null) libraryModule.loadLibraries(serverUrl)
        else listOf(LibrarySummary(libraryId, libraryId))
        return libraries.flatMap { library ->
            buildList {
                var page = 0
                while (true) {
                    val result = catalogModule.loadBooks(serverUrl, library.id, page)
                    addAll(result.items)
                    if (result.isComplete || result.items.isEmpty()) break
                    page++
                }
            }
        }
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
        val base = normalizeServerUrl(serverUrl) ?: throw UserFacingException("Enter a valid Komga server URL.")
        val request = Request.Builder().url("$base/api/v1/books/${book.id}").get()
            .apply { auth.authorizationHeader()?.let { header("Authorization", it) } }
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw UserFacingException("Unable to load Komga book details (HTTP ${response.code}).")
            val root = JSONObject(response.body?.string().orEmpty())
            val metadata = root.optJSONObject("metadata") ?: JSONObject()
            val media = root.optJSONObject("media") ?: JSONObject()
            val primaryFile = media.optJSONArray("files")?.optJSONObject(0)
            val filename = primaryFile?.optString("fileName")?.takeIf { it.isNotBlank() }
                ?: media.optString("fileName").takeIf { it.isNotBlank() }
                ?: root.optString("name").takeIf { it.isNotBlank() }
            val format = primaryFile?.optString("mediaType")?.takeIf { it.isNotBlank() }
                ?: media.optString("mediaType").takeIf { it.isNotBlank() }
                ?: filename?.substringAfterLast('.', "")
            val mediaKind = komgaMediaKind(format, filename)
            val progressionLocatorJson = if (mediaKind == MediaKind.EPUB) {
                runCatching {
                    val progressionRequest = Request.Builder()
                        .url("$base/api/v1/books/${book.id}/progression")
                        .header("Accept", "application/vnd.readium.progression+json")
                        .apply { auth.authorizationHeader()?.let { header("Authorization", it) } }
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
            val readProgress = komgaReadState(root.optJSONObject("readProgress"))
            val readProgressDate = root.optJSONObject("readProgress")?.optString("readDate")
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
                lastReadAtMillis = readProgressDate
                    ?.takeIf { it.isNotBlank() }
                    ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
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
    private val client = KomgaHttpClient().client

    override suspend fun loadBookCover(serverUrl: String, book: BookSummary): ByteArray? = withContext(Dispatchers.IO) {
        val base = normalizeServerUrl(serverUrl) ?: return@withContext null
        val thumbnailUrl = book.coverUrl?.takeIf { it.isNotBlank() } ?: if (book.id.startsWith("series:")) {
            "$base/api/v1/series/${book.id.removePrefix("series:")}/thumbnail"
        } else {
            "$base/api/v1/books/${book.id}/thumbnail"
        }
        val request = Request.Builder().url(thumbnailUrl).get()
            .apply { auth.authorizationHeader()?.let { header("Authorization", it) } }
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) null else response.body?.bytes()
        }
    }
    override suspend fun loadCatalogImage(url: String): ByteArray? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).get()
            .apply { auth.authorizationHeader()?.let { header("Authorization", it) } }
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) null else response.body?.bytes()
        }
    }
}

class KomgaDownloadModuleImpl(
    private val context: Context,
    private val auth: KomgaAuthModuleImpl
) : KomgaDownloadModule {
    private val client = KomgaHttpClient().client

    override suspend fun downloadBook(serverUrl: String, book: BookSummary, onProgress: (Float?) -> Unit): File = withContext(Dispatchers.IO) {
        val base = normalizeServerUrl(serverUrl) ?: throw UserFacingException("Enter a valid Komga server URL.")
        val target = DownloadStore(context).downloadTarget(
            fileId = book.fileId ?: book.id,
            title = book.title,
            mediaKind = book.mediaKind,
            formatHint = book.format
        )
        target.parentFile?.mkdirs()
        val temporary = File(target.path + ".part")
        val request = Request.Builder().url("$base/api/v1/books/${book.id}/file").get()
            .apply { auth.authorizationHeader()?.let { header("Authorization", it) } }
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw UserFacingException("Unable to download the Komga book (HTTP ${response.code}).")
            val body = response.body ?: throw UserFacingException("Komga returned an empty book file.")
            val total = body.contentLength()
            var copied = 0L
            body.byteStream().use { input ->
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        copied += count
                        onProgress(total.takeIf { it > 0 }?.let { copied.toFloat() / it })
                    }
                }
            }
        }
        if (target.exists()) target.delete()
        check(temporary.renameTo(target)) { "Unable to finalize the Komga download." }
        target
    }
}
