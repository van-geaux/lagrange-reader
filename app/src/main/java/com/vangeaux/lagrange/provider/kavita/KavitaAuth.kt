package com.vangeaux.lagrange.provider.kavita

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.vangeaux.lagrange.ServerCheckResult
import com.vangeaux.lagrange.ServerProfileStore
import com.vangeaux.lagrange.SessionState
import com.vangeaux.lagrange.UserFacingException
import com.vangeaux.lagrange.normalizeServerUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import java.net.URLEncoder

internal const val KAVITA_PLUGIN_NAME = "Lagrange"

internal fun kavitaAuthenticateEndpoint(baseUrl: String, apiKey: String): String =
    "$baseUrl/api/Plugin/authenticate?apiKey=${URLEncoder.encode(apiKey, Charsets.UTF_8.name())}" +
        "&pluginName=${URLEncoder.encode(KAVITA_PLUGIN_NAME, Charsets.UTF_8.name())}"

internal fun kavitaAuthResponseToken(payload: String): String? =
    runCatching { JSONObject(payload).optString("token").takeIf { it.isNotBlank() } }.getOrNull()

internal fun kavitaSessionHeaders(token: String?): Map<String, String> =
    token?.takeIf { it.isNotBlank() }?.let { mapOf("Authorization" to "Bearer $it") } ?: emptyMap()

internal interface KavitaAuthModule {
    suspend fun checkServer(serverUrl: String): ServerCheckResult
    suspend fun authenticate(serverUrl: String, apiKey: String)
    suspend fun sessionState(serverUrl: String): SessionState
    suspend fun clearSession()
    suspend fun restoreCurrentProfileSession(): Boolean
    fun requestHeaders(): Map<String, String>
}

internal class KavitaAuthModuleImpl(context: Context) : KavitaAuthModule {
    private val appContext = context.applicationContext
    private val client = OkHttpClient()
    private val credentialStore = KavitaCredentialStore(appContext)
    private var serverUrl: String? = null
    private var token: String? = activeProfileId()?.let(credentialStore::read)

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

    override suspend fun authenticate(serverUrl: String, apiKey: String): Unit = withContext(Dispatchers.IO) {
        val base = normalizeServerUrl(serverUrl)
            ?: throw UserFacingException("Enter a valid Kavita server URL.")
        if (apiKey.isBlank()) throw UserFacingException("Enter a Kavita Auth Key.")
        val request = Request.Builder()
            .url(kavitaAuthenticateEndpoint(base, apiKey))
            .header("x-api-key", apiKey)
            .header("Accept", "application/json")
            .post(ByteArray(0).toRequestBody(null))
            .build()
        client.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (response.code == 401 || response.code == 403) {
                throw UserFacingException("Kavita did not accept this Auth Key.")
            }
            if (!response.isSuccessful) {
                throw UserFacingException("Kavita authentication failed with HTTP ${response.code}.")
            }
            val authenticatedToken = kavitaAuthResponseToken(payload)
                ?: throw UserFacingException("Kavita returned no authentication token.")
            this@KavitaAuthModuleImpl.serverUrl = base
            token = authenticatedToken
            activeProfileId()?.let { credentialStore.write(it, authenticatedToken) }
        }
    }

    override suspend fun sessionState(serverUrl: String): SessionState = withContext(Dispatchers.IO) {
        val base = normalizeServerUrl(serverUrl) ?: return@withContext SessionState.Unavailable
        val headers = requestHeaders()
        if (headers.isEmpty()) return@withContext SessionState.Unauthenticated
        runCatching {
            val request = Request.Builder()
                .url("$base/api/Library/libraries")
                .apply { headers.forEach { (name, value) -> header(name, value) } }
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

    override fun requestHeaders(): Map<String, String> = kavitaSessionHeaders(token)

    override suspend fun clearSession() {
        serverUrl = null
        token = null
        activeProfileId()?.let(credentialStore::clear)
    }

    override suspend fun restoreCurrentProfileSession(): Boolean {
        token = activeProfileId()?.let(credentialStore::read)
        return !token.isNullOrBlank()
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
