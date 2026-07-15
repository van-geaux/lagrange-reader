package com.bookorbit.android

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryCatalogDatabaseTest {
    private lateinit var database: LibraryCatalogDatabase
    private lateinit var dao: LibraryCatalogDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            LibraryCatalogDatabase::class.java
        ).build()
        dao = database.catalogDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun reconciliation_updates_changed_rows_and_removes_deleted_books_atomically() = runBlocking {
        dao.reconcileLibrary(
            metadata = metadata(total = 2, refreshedAtMillis = 1L),
            changedBooks = listOf(book("a", 0, "Alpha"), book("b", 1, "Beta")),
            removedBookIds = emptyList(),
            jumpBuckets = listOf(bucket("A", 0, 0), bucket("B", 1, 1)),
            replaceJumpBuckets = true
        )

        dao.reconcileLibrary(
            metadata = metadata(total = 2, refreshedAtMillis = 2L),
            changedBooks = listOf(book("b", 0, "Beta updated"), book("c", 1, "Charlie")),
            removedBookIds = listOf("a"),
            jumpBuckets = listOf(bucket("B", 0, 0), bucket("C", 1, 1)),
            replaceJumpBuckets = true
        )

        assertEquals(listOf("b", "c"), dao.readBooks(SERVER, LIBRARY).map { it.bookId })
        assertEquals("Beta updated", dao.readBooks(SERVER, LIBRARY).first().title)
        assertEquals(2L, dao.readMetadata(SERVER, LIBRARY)?.refreshedAtMillis)
        assertEquals(listOf("B", "C"), dao.readJumpBuckets(SERVER, LIBRARY).map { it.bucketKey })
    }

    @Test
    fun reset_reading_state_clears_cached_progress_without_removing_the_book() = runBlocking {
        dao.insertBooks(
            listOf(
                book("a", 0, "Alpha").copy(
                    progressLabel = "42%",
                    progressPercent = 42f,
                    progressPositionMs = 10_000L,
                    progressPageIndex = 4,
                    isRead = true,
                    lastReadAtMillis = 100L,
                    readerPageIndex = 2,
                    readerPageCount = 8
                )
            )
        )

        dao.resetBookReadingState(SERVER, "a")

        val reset = dao.readBooks(SERVER, LIBRARY).single()
        assertNull(reset.progressLabel)
        assertNull(reset.progressPercent)
        assertNull(reset.progressPositionMs)
        assertNull(reset.progressPageIndex)
        assertFalse(reset.isRead)
        assertNull(reset.lastReadAtMillis)
        assertNull(reset.readerPageIndex)
        assertNull(reset.readerPageCount)
    }

    @Test
    fun mark_read_updates_cached_status_without_discarding_progress() = runBlocking {
        dao.insertBooks(
            listOf(
                book("a", 0, "Alpha").copy(
                    progressLabel = "42%",
                    progressPercent = 42f,
                    progressPositionMs = 10_000L,
                    progressPageIndex = 4
                )
            )
        )

        dao.markBookAsRead(SERVER, "a", markedAtMillis = 500L)

        val marked = dao.readBooks(SERVER, LIBRARY).single()
        assertEquals(true, marked.isRead)
        assertEquals(500L, marked.lastReadAtMillis)
        assertEquals("42%", marked.progressLabel)
        assertEquals(42f, marked.progressPercent)
        assertEquals(10_000L, marked.progressPositionMs)
        assertEquals(4, marked.progressPageIndex)
    }

    private fun metadata(total: Int, refreshedAtMillis: Long) = LibraryCatalogMetadataEntity(
        serverUrl = SERVER,
        libraryId = LIBRARY,
        total = total,
        seriesTotal = null,
        pageSize = 50,
        refreshedAtMillis = refreshedAtMillis
    )

    private fun bucket(key: String, index: Int, order: Int) = LibraryCatalogJumpBucketEntity(
        serverUrl = SERVER,
        libraryId = LIBRARY,
        bucketKey = key,
        label = key,
        catalogIndex = index,
        displayOrder = order
    )

    private fun book(id: String, position: Int, title: String) = LibraryCatalogBookEntity(
        serverUrl = SERVER,
        libraryId = LIBRARY,
        bookId = id,
        catalogPosition = position,
        fileId = null,
        title = title,
        author = null,
        format = null,
        mediaKind = MediaKind.UNKNOWN.name,
        streamUrl = null,
        downloadUrl = null,
        coverUrl = null,
        localPath = null,
        progressLabel = null,
        progressPercent = null,
        progressPositionMs = null,
        progressPageIndex = null,
        seriesId = null,
        seriesName = null,
        seriesIndex = null,
        isRead = false,
        addedAtMillis = null,
        updatedAtMillis = null,
        lastReadAtMillis = null,
        readerPageIndex = null,
        readerPageCount = null
    )

    private companion object {
        const val SERVER = "https://example.test"
        const val LIBRARY = "library-1"
    }
}
