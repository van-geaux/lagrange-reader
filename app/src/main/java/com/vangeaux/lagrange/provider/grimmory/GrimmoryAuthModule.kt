package com.vangeaux.lagrange.provider.grimmory

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.vangeaux.lagrange.AuthenticatedAccountScopeStore
import com.vangeaux.lagrange.AuthenticationRequiredException
import com.vangeaux.lagrange.DownloadStore
import com.vangeaux.lagrange.PROVIDER_GRIMMORY
import com.vangeaux.lagrange.ServerCheckResult
import com.vangeaux.lagrange.ServerProfileStore
import com.vangeaux.lagrange.SessionState
import com.vangeaux.lagrange.UserFacingException
import com.vangeaux.lagrange.normalizeServerUrl
import com.vangeaux.lagrange.sameHttpOrigin
import com.vangeaux.lagrange.core.LoginModule
import com.vangeaux.lagrange.core.ProviderSessionModule
import com.vangeaux.lagrange.core.AuthenticatedMediaModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.net.ssl.SSLException

internal class GrimmoryAuthModuleImpl(
    context: Context,
    private val client: OkHttpClient = OkHttpClient()
) : LoginModule, ProviderSessionModule, AuthenticatedMediaModule {
    private val appContext = context.applicationContext
    private val profiles = ServerProfileStore(appContext)
    private val accountScopes = AuthenticatedAccountScopeStore(appContext)
    private val auth = GrimmoryAuthClient(client, EncryptedGrimmoryTokenStore(appContext), profileId = { profiles.activeId() })
    private val identity = GrimmoryIdentityClient(
        client = client,
        auth = auth,
        profileId = { profiles.activeId() },
        persistAccount = { profileId, principal ->
            accountScopes.writeAuthoritative(
                profileId = profileId,
                providerId = PROVIDER_GRIMMORY,
                principal = principal.canonical,
                equivalentPrincipals = principal.provenAliases
            )
            DownloadStore(appContext).quarantineLegacyUnscopedDownloads()
        }
    )

    override suspend fun login(username: String, password: String) {
        val base = serverUrl() ?: throw UserFacingException("Enter a valid Grimmory server URL.")
        auth.login(base, username, password)
        val identityResult = try {
            identity.fetchAndPersist(base)
        } catch (error: kotlinx.coroutines.CancellationException) {
            clearSession()
            throw error
        } catch (_: Exception) {
            clearSession()
            throw UserFacingException("Grimmory could not confirm the signed-in account.")
        }
        if (identityResult.statusCode !in 200..299 || identityResult.principal == null) {
            clearSession()
            throw UserFacingException("Grimmory could not confirm the signed-in account.")
        }
    }

    override suspend fun clearSession() {
        auth.clearSession()
        profiles.activeId()?.let(accountScopes::deactivate)
    }

    override suspend fun getSessionState(): SessionState = withContext(Dispatchers.IO) {
        val base = serverUrl() ?: return@withContext SessionState.Unavailable
        var result = runCatching { identity.fetchAndPersist(base) }.getOrElse {
            return@withContext if (it is AuthenticationRequiredException) SessionState.Unauthenticated else SessionState.Unavailable
        }
        if (result.statusCode == 401 && auth.recoverAuthentication(base)) {
            result = runCatching { identity.fetchAndPersist(base) }.getOrElse {
                return@withContext if (it is AuthenticationRequiredException) SessionState.Unauthenticated else SessionState.Unavailable
            }
        }
        when {
            result.statusCode in 200..299 && result.principal != null -> SessionState.Authenticated
            result.statusCode == 401 || result.statusCode == 403 -> SessionState.Unauthenticated
            else -> SessionState.Unavailable
        }
    }

    override suspend fun requestHeaders(url: String): Map<String, String> {
        val base = serverUrl() ?: return emptyMap()
        if (!sameHttpOrigin(url, base)) return emptyMap()
        return auth.requestHeaders(base)
    }

    override suspend fun recoverAuthentication(): Boolean {
        val base = serverUrl() ?: return false
        return auth.recoverAuthentication(base)
    }

    override suspend fun saveCurrentProfileSession(): Boolean = auth.saveCurrentProfileSession()

    override suspend fun restoreCurrentProfileSession(): Boolean {
        if (!auth.restoreCurrentProfileSession()) return false
        val profileId = profiles.activeId() ?: return false
        if (accountScopes.read(profileId) != null) return true
        return getSessionState() == SessionState.Authenticated
    }

    suspend fun checkServer(serverUrl: String): ServerCheckResult = withContext(Dispatchers.IO) {
        val base = normalizeServerUrl(serverUrl) ?: return@withContext ServerCheckResult.MalformedUrl
        try {
            val request = Request.Builder().url(grimmoryApiUrl(base, "/api/v1/libraries")).get().build()
            client.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful || response.code == 401 || response.code == 403 -> ServerCheckResult.Reachable
                    response.isRedirect -> ServerCheckResult.Redirected
                    else -> ServerCheckResult.HttpFailure
                }
            }
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: UnknownHostException) {
            ServerCheckResult.UnreachableHost
        } catch (error: SocketTimeoutException) {
            ServerCheckResult.Timeout
        } catch (error: SSLException) {
            ServerCheckResult.TlsFailure
        } catch (error: IOException) {
            ServerCheckResult.NetworkFailure
        }
    }

    private suspend fun serverUrl(): String? = normalizeServerUrl(profiles.active()?.serverUrl.orEmpty())

}

private class EncryptedGrimmoryTokenStore(context: Context) : GrimmoryTokenStore {
    private val preferences = context.getSharedPreferences("grimmory_auth", Context.MODE_PRIVATE)

    override fun read(profileId: String): GrimmoryTokenSet? = runCatching {
        val encoded = preferences.getString(key(profileId), null) ?: return null
        val packed = Base64.decode(encoded, Base64.DEFAULT)
        require(packed.size > GCM_IV_LENGTH)
        val iv = packed.copyOfRange(0, GCM_IV_LENGTH)
        val encrypted = packed.copyOfRange(GCM_IV_LENGTH, packed.size)
        val clear = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_LENGTH, iv))
        }.doFinal(encrypted).toString(Charsets.UTF_8)
        grimmoryTokenSet(clear)
    }.getOrNull()

    override fun write(profileId: String, tokenSet: GrimmoryTokenSet) {
        val json = org.json.JSONObject()
            .put("accessToken", tokenSet.accessToken)
            .put("refreshToken", tokenSet.refreshToken)
            .put("expiresAtMillis", tokenSet.expiresAtMillis)
            .put("isDefaultPassword", tokenSet.isDefaultPassword)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        val packed = cipher.iv + cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
        check(preferences.edit().putString(key(profileId), Base64.encodeToString(packed, Base64.NO_WRAP)).commit()) {
            "Grimmory session could not be persisted."
        }
    }

    override fun clear(profileId: String) {
        preferences.edit().remove(key(profileId)).commit()
    }

    private fun key(profileId: String): String =
        "token_${Base64.encodeToString(profileId.toByteArray(Charsets.UTF_8), Base64.NO_WRAP or Base64.URL_SAFE)}"

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!store.containsAlias(KEY_ALIAS)) {
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).apply {
                init(KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build())
            }.generateKey()
        }
        return (store.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
    }

    private companion object {
        const val KEY_ALIAS = "lagrange_grimmory_auth"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_LENGTH = 12
        const val GCM_TAG_LENGTH = 128
    }
}
