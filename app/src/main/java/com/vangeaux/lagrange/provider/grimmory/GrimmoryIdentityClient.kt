package com.vangeaux.lagrange.provider.grimmory

import com.vangeaux.lagrange.AuthenticatedAccountPrincipal
import com.vangeaux.lagrange.authenticatedAccountPrincipal
import com.vangeaux.lagrange.normalizeServerUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

internal data class GrimmoryIdentityResult(
    val statusCode: Int,
    val principal: AuthenticatedAccountPrincipal? = null
)

internal fun grimmoryAuthenticatedPrincipal(payload: String): AuthenticatedAccountPrincipal? =
    authenticatedAccountPrincipal(payload, stableFields = listOf("id"))

internal class GrimmoryIdentityClient(
    private val client: OkHttpClient,
    private val auth: GrimmoryAuthClient,
    private val profileId: () -> String?,
    private val persistAccount: suspend (String, AuthenticatedAccountPrincipal) -> Unit
) {
    suspend fun fetchAndPersist(serverUrl: String): GrimmoryIdentityResult = withContext(Dispatchers.IO) {
        val profile = profileId()?.takeIf(String::isNotBlank)
            ?: return@withContext GrimmoryIdentityResult(statusCode = 401)
        val base = normalizeServerUrl(serverUrl)
            ?: return@withContext GrimmoryIdentityResult(statusCode = 400)
        val request = Request.Builder()
            .url(grimmoryApiUrl(base, "/api/v1/users/me"))
            .apply { auth.requestHeaders(base).forEach { (name, value) -> header(name, value) } }
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val principal = if (response.isSuccessful) {
                grimmoryAuthenticatedPrincipal(response.body?.string().orEmpty())
            } else null
            if (principal != null && profileId() == profile) {
                persistAccount(profile, principal)
                GrimmoryIdentityResult(response.code, principal)
            } else {
                GrimmoryIdentityResult(response.code)
            }
        }
    }
}
