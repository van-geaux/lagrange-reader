package com.vangeaux.lagrange.provider.kavita

import android.content.Context
import android.webkit.CookieManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.vangeaux.lagrange.AuthenticatedAccountPrincipal
import com.vangeaux.lagrange.AuthenticatedAccountScopeStore
import com.vangeaux.lagrange.R
import com.vangeaux.lagrange.ServerCheckResult
import com.vangeaux.lagrange.ServerProfileStore
import com.vangeaux.lagrange.SessionState
import com.vangeaux.lagrange.UserFacingException
import com.vangeaux.lagrange.authenticatedAccountPrincipal
import com.vangeaux.lagrange.normalizeServerUrl
import com.vangeaux.lagrange.providerErrorMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.net.ssl.SSLException
import kotlin.coroutines.resume
internal fun kavitaLoginEndpoint(baseUrl: String): String =
    "$baseUrl/api/Account/login"

internal fun kavitaAuthResponseToken(payload: String): String? =
    runCatching { JSONObject(payload).optString("token").takeIf { it.isNotBlank() } }.getOrNull()

internal fun kavitaAuthResponsePrincipal(payload: String): AuthenticatedAccountPrincipal? =
    authenticatedAccountPrincipal(payload, stableFields = listOf("id"))

internal fun kavitaJwtPrincipal(token: String): AuthenticatedAccountPrincipal? = runCatching {
    val encodedPayload = token.split('.').getOrNull(1)?.takeIf(String::isNotBlank) ?: return null
    val payload = String(
        java.util.Base64.getUrlDecoder().decode(encodedPayload),
        Charsets.UTF_8
    )
    val value = JSONObject(payload)
    val id = sequenceOf(
        "nameid",
        "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/nameidentifier"
    ).mapNotNull { field -> value.optString(field).trim().takeIf(String::isNotBlank) }
        .firstOrNull()
        ?: return null
    AuthenticatedAccountPrincipal(canonical = "id:$id")
}.getOrNull()

internal fun kavitaSessionHeaders(token: String?, cookie: String? = null): Map<String, String> = buildMap {
    token?.takeIf { it.isNotBlank() }?.let { put("Authorization", "Bearer $it") }
    cookie?.takeIf { it.isNotBlank() }?.let { put("Cookie", it) }
}

internal interface KavitaAuthModule {
    suspend fun checkServer(serverUrl: String): ServerCheckResult
    suspend fun authenticate(serverUrl: String, username: String, password: String)
    suspend fun sessionState(serverUrl: String): SessionState
    suspend fun clearSession()
    suspend fun saveCurrentProfileSession()
    suspend fun restoreCurrentProfileSession(): Boolean
    fun requestHeaders(): Map<String, String>

    /** Localizes a provider error message, falling back to the original English text. */
    fun localizedMessage(messageRes: Int, fallback: String, vararg formatArgs: Any): String
}

internal class KavitaAuthModuleImpl(context: Context) : KavitaAuthModule {
    private val appContext = context.applicationContext
    private val client = OkHttpClient()
    private val credentialStore = KavitaCredentialStore(appContext)
    private val accountScopeStore = AuthenticatedAccountScopeStore(appContext)
    private var serverUrl: String? = null
    private var token: String? = activeProfileId()?.let(credentialStore::read)

    override fun localizedMessage(messageRes: Int, fallback: String, vararg formatArgs: Any): String =
        providerErrorMessage(appContext, messageRes, fallback, *formatArgs)

    override suspend fun checkServer(serverUrl: String): ServerCheckResult = withContext(Dispatchers.IO) {
        val base = normalizeServerUrl(serverUrl) ?: return@withContext ServerCheckResult.MalformedUrl
        runCatching {
            val request = Request.Builder().url("$base/api/Library/libraries").get().build()
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

    override suspend fun authenticate(serverUrl: String, username: String, password: String): Unit = withContext(Dispatchers.IO) {
        val base = normalizeServerUrl(serverUrl)
            ?: throw UserFacingException(
                localizedMessage(
                    R.string.perror_kavita_server_url,
                    "Enter a valid Kavita server URL."
                )
            )
        if (username.isBlank()) throw UserFacingException(
            localizedMessage(
                R.string.perror_kavita_username_required,
                "Enter your Kavita username."
            )
        )
        if (password.isBlank()) throw UserFacingException(
            localizedMessage(
                R.string.perror_kavita_password_required,
                "Enter your Kavita password."
            )
        )
        val request = Request.Builder()
            .url(kavitaLoginEndpoint(base))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .post(
                JSONObject()
                    .put("username", username)
                    .put("password", password)
                    .toString()
                    .toRequestBody("application/json; charset=utf-8".toMediaType())
            )
            .build()
        client.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (response.code == 401 || response.code == 403) {
                throw UserFacingException(
                    localizedMessage(
                        R.string.perror_kavita_credentials,
                        "Kavita did not accept this username or password."
                    )
                )
            }
            if (!response.isSuccessful) {
                throw UserFacingException(
                    localizedMessage(
                        R.string.perror_kavita_auth_http,
                        "Kavita authentication failed with HTTP ${response.code}.",
                        response.code
                    )
                )
            }
            val authenticatedToken = kavitaAuthResponseToken(payload)
                ?: throw UserFacingException(
                    localizedMessage(
                        R.string.perror_kavita_no_token,
                        "Kavita returned no authentication token."
                    )
                )
            val principal = kavitaAuthResponsePrincipal(payload)
                ?: throw UserFacingException(
                    localizedMessage(
                        R.string.perror_kavita_no_identity,
                        "Kavita returned no stable account identity."
                    )
                )
            this@KavitaAuthModuleImpl.serverUrl = base
            token = authenticatedToken
            activeProfileId()?.let { profileId ->
                accountScopeStore.writeAuthoritative(
                    profileId = profileId,
                    providerId = "kavita",
                    principal = principal.canonical,
                    equivalentPrincipals = principal.provenAliases
                )
                credentialStore.write(profileId, authenticatedToken)
            }
        }
    }

    override suspend fun sessionState(serverUrl: String): SessionState = withContext(Dispatchers.IO) {
        val base = normalizeServerUrl(serverUrl) ?: return@withContext SessionState.Unavailable
        this@KavitaAuthModuleImpl.serverUrl = base
        ensureAccountScopeFromToken()
        val headers = requestHeaders()
        if (headers.isEmpty()) return@withContext SessionState.Unauthenticated
        runCatching {
            val request = Request.Builder()
                .url("$base/api/Account")
                .apply { headers.forEach { (name, value) -> header(name, value) } }
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> {
                        val principal = kavitaAuthResponsePrincipal(response.body?.string().orEmpty())
                        val profileId = activeProfileId()
                        if (principal != null && profileId != null) {
                            accountScopeStore.writeAuthoritative(
                                profileId = profileId,
                                providerId = "kavita",
                                principal = principal.canonical,
                                equivalentPrincipals = principal.provenAliases
                            )
                            SessionState.Authenticated
                        } else {
                            SessionState.Unavailable
                        }
                    }
                    response.code == 401 || response.code == 403 -> SessionState.Unauthenticated
                    else -> SessionState.Unavailable
                }
            }
        }.getOrDefault(SessionState.Unavailable)
    }

    override fun requestHeaders(): Map<String, String> {
        val origin = currentServerUrl()
        return kavitaSessionHeaders(
            token = token,
            cookie = origin?.let { CookieManager.getInstance().getCookie(it) }
        )
    }

    internal fun currentServerUrl(): String? =
        serverUrl ?: ServerProfileStore(appContext).active()?.serverUrl

    override suspend fun clearSession() {
        activeProfileId()?.let(accountScopeStore::deactivate)
        serverUrl = null
        token = null
        activeProfileId()?.let(credentialStore::clear)
        withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine { continuation ->
                CookieManager.getInstance().removeAllCookies {
                    CookieManager.getInstance().flush()
                    if (continuation.isActive) continuation.resume(Unit)
                }
            }
        }
    }

    override suspend fun saveCurrentProfileSession() {
        activeProfileId()?.let { profileId ->
            token?.let {
                ensureAccountScopeFromToken()
                credentialStore.write(profileId, it)
            }
        }
    }

    override suspend fun restoreCurrentProfileSession(): Boolean {
        token = activeProfileId()?.let(credentialStore::read)
        ensureAccountScopeFromToken()
        val configuredServer = ServerProfileStore(appContext).active()?.serverUrl
        val hasCookie = configuredServer?.let { CookieManager.getInstance().getCookie(it).isNullOrBlank().not() } == true
        if (token.isNullOrBlank() && hasCookie && configuredServer != null) {
            sessionState(configuredServer)
        }
        return !token.isNullOrBlank() || hasCookie
    }

    private fun ensureAccountScopeFromToken() {
        val profileId = activeProfileId() ?: return
        if (accountScopeStore.read(profileId) != null) return
        val principal = token?.let(::kavitaJwtPrincipal) ?: return
        accountScopeStore.writeAuthoritative(
            profileId = profileId,
            providerId = "kavita",
            principal = principal.canonical
        )
    }

    private fun activeProfileId(): String? = ServerProfileStore(appContext).active()?.id
}

private class KavitaCredentialStore(context: Context) {
    private val preferences = context.getSharedPreferences("kavita_auth", Context.MODE_PRIVATE)

    fun read(profileId: String): String? = runCatching {
        val encoded = preferences.getString(profileKey(profileId), null) ?: return null
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
            preferences.edit().putString(
                profileKey(profileId),
                Base64.encodeToString(packed, Base64.NO_WRAP)
            ).apply()
        }
    }

    fun clear(profileId: String) {
        preferences.edit().remove(profileKey(profileId)).apply()
    }

    private fun profileKey(profileId: String): String = "token_$profileId"

    private fun key(): java.security.Key {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!store.containsAlias(KEY_ALIAS)) {
            val generator = javax.crypto.KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )
            generator.init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
            generator.generateKey()
        }
        return (store.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
    }

    private companion object {
        const val KEY_ALIAS = "lagrange_kavita_auth"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_LENGTH = 12
        const val GCM_TAG_LENGTH = 128
    }
}
