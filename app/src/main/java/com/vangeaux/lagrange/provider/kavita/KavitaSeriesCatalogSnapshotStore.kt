package com.vangeaux.lagrange.provider.kavita

import android.content.Context
import com.vangeaux.lagrange.SeriesSummary
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal class KavitaSeriesCatalogSnapshotStore(context: Context) {
    private val file = File(context.applicationContext.filesDir, "kavita_series_catalog_snapshot.json")

    @Synchronized
    fun read(libraryId: String?, page: Int): KavitaSeriesSummaryPage? {
        if (!file.isFile) return null
        val root = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return null
        val key = key(libraryId, page)
        val value = root.optJSONObject("pages")?.optJSONObject(key) ?: return null
        val items = value.optJSONArray("items").toSeriesSummaries()
        return KavitaSeriesSummaryPage(
            items = items,
            total = value.optInt("total", -1).takeIf { it >= 0 },
            pageSize = value.optInt("size", 0)
        )
    }

    @Synchronized
    fun write(libraryId: String?, page: Int, value: KavitaSeriesSummaryPage) {
        val root = runCatching { if (file.isFile) JSONObject(file.readText()) else JSONObject() }
            .getOrDefault(JSONObject())
        val pages = root.optJSONObject("pages") ?: JSONObject().also { root.put("pages", it) }
        pages.put(key(libraryId, page), JSONObject().apply {
            put("total", value.total ?: JSONObject.NULL)
            put("size", value.pageSize)
            put("items", JSONArray().apply { value.items.forEach { put(it.toJson()) } })
        })
        file.writeText(root.toString())
    }

    private fun key(libraryId: String?, page: Int) = "${libraryId ?: "all"}:$page"

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
