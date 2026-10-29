package com.vangeaux.lagrange.provider.kavita

import android.content.Context
import com.vangeaux.lagrange.BookReadStatus
import com.vangeaux.lagrange.BookSummary
import com.vangeaux.lagrange.CoverAspectRatio
import com.vangeaux.lagrange.HomeSection
import com.vangeaux.lagrange.HomeShelfData
import com.vangeaux.lagrange.MediaKind
import com.vangeaux.lagrange.ServerProfileStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Base64

internal class KavitaHomeSnapshotStore(context: Context) {
    private val appContext = context.applicationContext
    private val mutex = Mutex()

    suspend fun read(serverUrl: String): HomeShelfData? = mutex.withLock {
        val root = readRoot() ?: return@withLock null
        if (root.optString("serverUrl") != serverUrl) return@withLock null
        root.toHomeShelfData()
    }

    suspend fun write(serverUrl: String, shelves: HomeShelfData) = mutex.withLock {
        val root = JSONObject().apply {
            put("serverUrl", serverUrl)
            put("booksBySection", shelves.booksBySection.toJson())
            put("seriesBySection", shelves.seriesBySection.toJson())
            put("hiddenSections", JSONArray(shelves.hiddenSections.map(HomeSection::name)))
            put("isServerProvided", shelves.isServerProvided)
            put("isAggregatedAcrossLibraries", shelves.isAggregatedAcrossLibraries)
            put("deferInitialCatalogLoad", shelves.deferInitialCatalogLoad)
        }
        val file = snapshotFile()
        file.parentFile?.mkdirs()
        file.writeText(root.toString())
    }

    suspend fun clear() = mutex.withLock {
        snapshotFile().takeIf(File::exists)?.delete()
    }

    private fun readRoot(): JSONObject? = snapshotFile()
        .takeIf(File::isFile)
        ?.let { file -> runCatching { JSONObject(file.readText()) }.getOrNull() }

    private fun snapshotFile(): File {
        val profileId = ServerProfileStore(appContext).activeId()
        return if (profileId == null || !profileId.startsWith("profile-")) {
            File(appContext.filesDir, "kavita_home_snapshot.json")
        } else {
            val safeId = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(profileId.toByteArray(Charsets.UTF_8))
            File(appContext.filesDir, "kavita_home_snapshot_$safeId.json")
        }
    }

    private fun Map<HomeSection, List<BookSummary>>.toJson(): JSONObject = JSONObject().apply {
        forEach { (section, books) ->
            put(section.name, JSONArray().apply { books.forEach { put(it.toJson()) } })
        }
    }

    private fun JSONObject.toHomeShelfData(): HomeShelfData = HomeShelfData(
        booksBySection = optJSONObject("booksBySection").toBooksBySection(),
        seriesBySection = optJSONObject("seriesBySection").toBooksBySection(),
        hiddenSections = optJSONArray("hiddenSections").toHomeSections(),
        isServerProvided = optBoolean("isServerProvided"),
        isAggregatedAcrossLibraries = optBoolean("isAggregatedAcrossLibraries"),
        deferInitialCatalogLoad = optBoolean("deferInitialCatalogLoad")
    )

    private fun JSONObject?.toBooksBySection(): Map<HomeSection, List<BookSummary>> {
        val source = this ?: return emptyMap()
        return buildMap {
            source.keys().forEach { name ->
                val section = HomeSection.entries.firstOrNull { it.name == name } ?: return@forEach
                put(section, source.optJSONArray(name).toBooks())
            }
        }
    }

    private fun JSONArray?.toHomeSections(): Set<HomeSection> {
        val source = this ?: return emptySet()
        return buildSet {
            for (index in 0 until source.length()) {
                HomeSection.entries.firstOrNull { it.name == source.optString(index) }?.let(::add)
            }
        }
    }

    private fun JSONArray?.toBooks(): List<BookSummary> {
        val source = this ?: return emptyList()
        return buildList {
            for (index in 0 until source.length()) {
                source.optJSONObject(index)?.toBook()?.let(::add)
            }
        }
    }

    private fun BookSummary.toJson(): JSONObject = JSONObject().apply {
        put("libraryId", libraryId)
        put("id", id)
        put("fileId", fileId)
        put("title", title)
        put("filename", filename)
        put("author", author)
        put("format", format)
        put("mediaKind", mediaKind.name)
        put("coverUrl", coverUrl)
        put("coverAspectRatio", coverAspectRatio.wireValue)
        put("localPath", localPath)
        put("progressLabel", progressLabel)
        put("progressPercent", progressPercent)
        put("progressPageIndex", progressPageIndex)
        put("seriesId", seriesId)
        put("seriesName", seriesName)
        put("seriesIndex", seriesIndex)
        put("readStatus", readStatus?.wireValue)
        put("isRead", isRead)
        put("addedAtMillis", addedAtMillis)
        put("updatedAtMillis", updatedAtMillis)
        put("lastReadAtMillis", lastReadAtMillis)
        put("availableFormats", JSONArray(availableFormats))
        put("downloadedFormats", JSONArray(downloadedFormats))
        put("genres", JSONArray(genres))
    }

    private fun JSONObject.toBook(): BookSummary = BookSummary(
        libraryId = optString("libraryId"),
        id = optString("id"),
        fileId = optString("fileId").takeIf(String::isNotBlank),
        title = optString("title"),
        filename = optString("filename").takeIf(String::isNotBlank),
        author = optString("author").takeIf(String::isNotBlank),
        format = optString("format").takeIf(String::isNotBlank),
        mediaKind = runCatching { MediaKind.valueOf(optString("mediaKind")) }.getOrDefault(MediaKind.UNKNOWN),
        coverUrl = optString("coverUrl").takeIf(String::isNotBlank),
        coverAspectRatio = CoverAspectRatio.fromWireValue(optString("coverAspectRatio")),
        localPath = optString("localPath").takeIf(String::isNotBlank),
        progressLabel = optString("progressLabel").takeIf(String::isNotBlank),
        progressPercent = optDoubleOrNull("progressPercent")?.toFloat(),
        progressPageIndex = optIntOrNull("progressPageIndex"),
        seriesId = optString("seriesId").takeIf(String::isNotBlank),
        seriesName = optString("seriesName").takeIf(String::isNotBlank),
        seriesIndex = optDoubleOrNull("seriesIndex"),
        readStatus = BookReadStatus.fromWireValue(optString("readStatus")),
        isRead = optBoolean("isRead"),
        addedAtMillis = optLongOrNull("addedAtMillis"),
        updatedAtMillis = optLongOrNull("updatedAtMillis"),
        lastReadAtMillis = optLongOrNull("lastReadAtMillis"),
        availableFormats = optStringList("availableFormats"),
        downloadedFormats = optStringList("downloadedFormats"),
        genres = optStringList("genres")
    )

    private fun JSONObject.optStringList(name: String): List<String> =
        (optJSONArray(name) ?: JSONArray()).let { array ->
            (0 until array.length()).map { array.optString(it) }.filter(String::isNotBlank)
        }

    private fun JSONObject.optDoubleOrNull(name: String): Double? =
        if (!has(name) || isNull(name)) null else optDouble(name)

    private fun JSONObject.optIntOrNull(name: String): Int? =
        if (!has(name) || isNull(name)) null else optInt(name)

    private fun JSONObject.optLongOrNull(name: String): Long? =
        if (!has(name) || isNull(name)) null else optLong(name)
}
