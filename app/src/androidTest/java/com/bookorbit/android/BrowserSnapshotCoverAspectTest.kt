package com.bookorbit.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserSnapshotCoverAspectTest {
    @Test
    fun library_and_book_cover_aspects_survive_snapshot_round_trip() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = BrowserSnapshotStore(context)
        store.clear()
        val library = LibrarySummary(
            id = "square-library",
            name = "Audiobooks",
            coverAspectRatio = CoverAspectRatio.SQUARE
        )
        val book = BookSummary(
            libraryId = library.id,
            id = "book-1",
            fileId = "file-1",
            title = "Square Book",
            coverAspectRatio = CoverAspectRatio.SQUARE
        )

        store.saveLibraries("https://example.test", library.id, listOf(library))
        store.saveBooks("https://example.test", library.id, library.id, listOf(book))

        val restored = store.read("https://example.test")
        assertEquals(CoverAspectRatio.SQUARE, restored?.libraries?.single()?.coverAspectRatio)
        assertEquals(
            CoverAspectRatio.SQUARE,
            restored?.booksByLibraryId?.get(library.id)?.single()?.coverAspectRatio
        )
        store.clear()
    }
}
