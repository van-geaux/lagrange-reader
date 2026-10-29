package com.vangeaux.lagrange.provider.kavita

import android.content.Context
import com.vangeaux.lagrange.SeriesSummary
import com.vangeaux.lagrange.HomeSection
import com.vangeaux.lagrange.ServerProfileStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Base64

internal class KavitaSeriesCatalogSnapshotStore(context: Context) {
    private val appContext = context.applicationContext

    @Synchronized
    fun read(
        serverUrl: String,
        libraryId: String?,
        page: Int,
        section: HomeSection? = null
    ): KavitaSeriesSummaryPage? {
        val file = snapshotFile()
        if (!file.isFile) return null
        val root = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return null
        if (root.optString("serverUrl") != serverUrl) return null
        val key = key(section, libraryId, page)
        val value = root.optJSONObject("pages")?.optJSONObject(key) ?: return null
        val items = value.optJSONArray("items").toSeriesSummaries()
        return KavitaSeriesSummaryPage(
            items = items,
            total = value.optInt("total", -1).takeIf { it >= 0 },
            pageSize = value.optInt("size", 0)
        )
    }

    @Synchronized
    fun write(
        serverUrl: String,
        libraryId: String?,
        page: Int,
        value: KavitaSeriesSummaryPage,
        section: HomeSection? = null
    ) {
        val file = snapshotFile()
        val root = runCatching { if (file.isFile) JSONObject(file.readText()) else JSONObject() }
            .getOrDefault(JSONObject())
        root.put("serverUrl", serverUrl)
        val pages = root.optJSONObject("pages") ?: JSONObject().also { root.put("pages", it) }
        pages.put(key(section, libraryId, page), JSONObject().apply {
            put("total", value.total ?: JSONObject.NULL)
            put("size", value.pageSize)
            put("items", JSONArray().apply { value.items.forEach { put(it.toJson()) } })
        })
        file.parentFile?.mkdirs()
        file.writeText(root.toString())
    }

    @Synchronized
    fun clear() {
        snapshotFile().takeIf(File::exists)?.delete()
    }

    private fun snapshotFile(): File {
        val profileId = ServerProfileStore(appContext).activeId()
        return if (profileId == null || !profileId.startsWith("profile-")) {
            File(appContext.filesDir, "kavita_series_catalog_snapshot.json")
        } else {
            val safeId = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(profileId.toByteArray(Charsets.UTF_8))
            File(appContext.filesDir, "kavita_series_catalog_snapshot_$safeId.json")
        }
    }

    private fun key(section: HomeSection?, libraryId: String?, page: Int) =
        "${section?.name ?: "catalog"}:${libraryId ?: "all"}:$page"

    private fun SeriesSummary.toJson() = JSONObject().apply {
        put("id", id); put("name", name); put("bookCount", bookCount); put("readCount", readCount)
        put("coverUrl", coverUrl ?: JSONObject.NULL); put("lastAddedAtMillis", lastAddedAtMillis ?: JSONObject.NULL)
        put("availableFormats", JSONArray(availableFormats)); put("downloadedFormats", JSONArray(downloadedFormats))
        put("authors", JSONArray(authors)); put("genres", JSONArray(genres))
    }

    private fun JSONArray?.toSeriesSummaries(): List<SeriesSummary> = (this ?: JSONArray()).let { array ->
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            SeriesSummary(
                id = item.optString("id"), name = item.optString("name"),
                authors = item.optStringList("authors"), bookCount = item.optInt("bookCount"), readCount = item.optInt("readCount"),
                coverUrl = item.optString("coverUrl").takeIf(String::isNotBlank),
                lastAddedAtMillis = item.optLong("lastAddedAtMillis", Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE },
                availableFormats = item.optStringList("availableFormats"), downloadedFormats = item.optStringList("downloadedFormats"),
                genres = item.optStringList("genres")
            )
        }
    }

    private fun JSONObject.optStringList(name: String): List<String> =
        (optJSONArray(name) ?: JSONArray()).let { array -> (0 until array.length()).map { array.optString(it) }.filter(String::isNotBlank) }
}
