package com.vangeaux.lagrange.provider.grimmory

import com.vangeaux.lagrange.AuthenticationRequiredException
import com.vangeaux.lagrange.UserFacingException
import com.vangeaux.lagrange.normalizeServerUrl
import com.vangeaux.lagrange.core.AuthenticatedMediaModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

internal class GrimmoryNativeApiClient(
    private val client: OkHttpClient,
    private val serverUrlProvider: suspend () -> String?,
    private val auth: AuthenticatedMediaModule
) {
    suspend fun json(path: String, query: Map<String, String> = emptyMap()): JSONObject =
        JSONObject(request(path, query).toString(Charsets.UTF_8))

    suspend fun jsonArray(path: String, query: Map<String, String> = emptyMap()): JSONArray =
        JSONArray(request(path, query).toString(Charsets.UTF_8))

    suspend fun bytes(path: String, query: Map<String, String> = emptyMap()): ByteArray =
        request(path, query)

    suspend fun imageBytes(path: String, query: Map<String, String> = emptyMap()): ByteArray =
        request(path, query, accept = "image/*")

    suspend fun putJson(path: String, payload: String) {
        request(path, method = "PUT", payload = payload)
    }

    private suspend fun request(
        path: String,
        query: Map<String, String> = emptyMap(),
        accept: String = "application/json",
        method: String = "GET",
        payload: String? = null
    ): ByteArray = withContext(Dispatchers.IO) {
        val base = normalizeServerUrl(serverUrlProvider().orEmpty())
            ?: throw UserFacingException("Enter a valid Grimmory server URL.")
        val target = grimmoryApiUrl(base, path).toHttpUrlOrNull()?.newBuilder()
            ?: throw UserFacingException("Grimmory returned an invalid API URL.")
        query.forEach { (key, value) -> target.addQueryParameter(key, value) }
        val url = target.build().toString()

        suspend fun buildRequest(): Request {
            val builder = Request.Builder().url(url).header("Accept", accept)
            auth.requestHeaders(url).forEach { (name, value) -> builder.header(name, value) }
            if (payload != null) {
                builder.header("Content-Type", "application/json")
                builder.method(method, payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
            } else {
                builder.method(method, if (method == "GET") null else ByteArray(0).toRequestBody(null))
            }
            return builder.build()
        }

        var response = client.newCall(buildRequest()).execute()
        if (response.code == 401 && auth.recoverAuthentication()) {
            response.close()
            response = client.newCall(buildRequest()).execute()
        }
        response.use {
            val body = it.body?.bytes() ?: ByteArray(0)
            if (!it.isSuccessful) {
                if (it.code == 401 || it.code == 403) throw AuthenticationRequiredException()
                throw GrimmoryHttpException(it.code)
            }
            body
        }
    }
}
