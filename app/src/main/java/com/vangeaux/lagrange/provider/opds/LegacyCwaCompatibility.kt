package com.vangeaux.lagrange.provider.opds

import android.content.Context
import java.util.Base64 as JavaBase64
import com.vangeaux.lagrange.BookSummary
import com.vangeaux.lagrange.normalizeServerUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject

/** Preserves the established endpoint for profiles saved by the earlier CWA provider. */
internal fun legacyCwaOpdsFeedUrl(serverUrl: String): String {
    val normalized = normalizeServerUrl(serverUrl)
        ?: throw IllegalArgumentException("Enter a valid HTTP or HTTPS server URL")
    val base = normalized.toHttpUrlOrNull()
        ?: throw IllegalArgumentException("Enter a valid HTTP or HTTPS server URL")
    val path = base.encodedPath.trimEnd('/')
    val opdsPath = if (path.endsWith("/opds", ignoreCase = true)) path else "$path/opds"
    return base.newBuilder().encodedPath(opdsPath.ifEmpty { "/opds" }).build().toString()
}

internal fun legacyCwaBasicAuthorization(username: String, password: String): String =
    opdsBasicAuthorization(username, password)

internal data class LegacyCwaLocalProgress(val position: Long, val pageIndex: Int, val percent: Float?)

/** Reads and writes the exact preference/key format used by legacy CWA profiles. */
internal fun legacyCwaProgressStorageKey(profileId: String, book: BookSummary): String {
    val identity = "$profileId\u0000${book.id}\u0000${book.fileId.orEmpty()}"
    return "progress_${JavaBase64.getUrlEncoder().encodeToString(identity.toByteArray(Charsets.UTF_8))}"
}

internal class LegacyCwaLocalProgressStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("cwa_local_reading_progress", Context.MODE_PRIVATE)

    fun read(profileId: String, book: BookSummary): LegacyCwaLocalProgress? {
        val raw = preferences.getString(legacyCwaProgressStorageKey(profileId, book), null) ?: return null
        return runCatching {
            val value = JSONObject(raw)
            LegacyCwaLocalProgress(
                position = value.optLong("position", 0L).coerceAtLeast(0L),
                pageIndex = value.optInt("pageIndex", 0).coerceAtLeast(0),
                percent = if (value.isNull("percent")) null else value.optDouble("percent").toFloat().coerceIn(0f, 100f)
            )
        }.getOrNull()
    }

    fun write(profileId: String, book: BookSummary, position: Long, pageIndex: Int, percent: Float?) {
        val value = JSONObject()
            .put("position", position.coerceAtLeast(0L))
            .put("pageIndex", pageIndex.coerceAtLeast(0))
            .put("percent", percent?.coerceIn(0f, 100f) ?: JSONObject.NULL)
        preferences.edit().putString(legacyCwaProgressStorageKey(profileId, book), value.toString()).apply()
    }
}
