package com.vangeaux.lagrange

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vangeaux.lagrange.provider.kavita.KavitaHomeSnapshotStore
import com.vangeaux.lagrange.provider.kavita.KavitaSeriesCatalogSnapshotStore
import com.vangeaux.lagrange.provider.kavita.KavitaSeriesSummaryPage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KavitaHomeSnapshotStoreTest {
    @Test
    fun home_shelves_round_trip_and_reject_a_different_server() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = KavitaHomeSnapshotStore(context)
        store.clear()
        val currentBook = BookSummary(
            libraryId = "library-1",
            id = "chapter:1",
            fileId = "file-1",
            title = "Current book",
            format = "epub",
            mediaKind = MediaKind.EPUB,
            coverUrl = "https://one.example/cover",
            progressPercent = 42f,
            seriesId = "series-1",
            seriesName = "Series one",
            readStatus = BookReadStatus.READING
        )
        val seriesCard = BookSummary(
            libraryId = "library-1",
            id = "series:1",
            fileId = null,
            title = "Series one",
            seriesId = "1",
            seriesName = "Series one",
            coverUrl = "https://one.example/series-cover"
        )
        val shelves = HomeShelfData(
            booksBySection = mapOf(HomeSection.CURRENTLY_READING to listOf(currentBook)),
            seriesBySection = mapOf(HomeSection.RECENTLY_ADDED_SERIES to listOf(seriesCard)),
            hiddenSections = setOf(HomeSection.RECENTLY_ADDED_BOOKS),
            isServerProvided = true,
            isAggregatedAcrossLibraries = true,
            deferInitialCatalogLoad = true
        )

        store.write("https://one.example", shelves)

        assertEquals(shelves, store.read("https://one.example"))
        assertNull(store.read("https://two.example"))
        store.clear()
    }

    @Test
    fun series_snapshot_is_scoped_to_the_server() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = KavitaSeriesCatalogSnapshotStore(context)
        store.clear()
        val page = KavitaSeriesSummaryPage(
            items = listOf(SeriesSummary(id = "series-1", name = "Series one", bookCount = 3)),
            total = 3,
            pageSize = 20
        )

        store.write("https://one.example", "library-1", 0, page)

        assertEquals(page, store.read("https://one.example", "library-1", 0))
        assertNull(store.read("https://two.example", "library-1", 0))
        store.clear()
    }
}
