package com.vangeaux.lagrange.provider.kavita

import com.vangeaux.lagrange.*
import com.vangeaux.lagrange.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

internal class KavitaLibraryModuleImpl(private val auth: KavitaAuthModule) : LibraryModule {
    private val client = OkHttpClient()

    override suspend fun loadLibraries(): List<LibrarySummary> = withContext(Dispatchers.IO) {
        val base = auth.serverUrl() ?: throw UserFacingException(auth.localizedMessage(R.string.perror_kavita_server_url, "Enter a valid Kavita server URL."))
        val request = Request.Builder()
            .url("$base/api/Library/libraries")
            .apply { auth.requestHeaders().forEach { (name, value) -> header(name, value) } }
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw UserFacingException(auth.localizedMessage(R.string.perror_kavita_libraries_http, "Unable to load Kavita libraries (HTTP ${response.code}).", response.code))
            }
            val array = runCatching { JSONArray(payload) }.getOrElse {
                JSONObject(payload).optJSONArray("libraries") ?: JSONArray()
            }
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optInt("id", -1).takeIf { it >= 0 } ?: continue
                    add(LibrarySummary(id.toString(), item.optString("name", id.toString())))
                }
            }
        }
    }
}

internal fun KavitaAuthModule.serverUrl(): String? =
    (this as? KavitaAuthModuleImpl)?.currentServerUrl()
