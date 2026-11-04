package com.vangeaux.lagrange

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BrowserSnapshotStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `collection catalog and pages survive reopening and stay profile scoped`() = runBlocking {
        val directory = temporaryFolder.newFolder()
        val serverUrl = "https://books.example"
        val store = BrowserSnapshotStore(directory, "profile-1")
        val collection = BookCollection(
            id = "favorites",
            name = "Favorites",
            bookCount = 1,
            description = "Saved for later"
        )
        val page = LibraryBooksPage(
            items = listOf(
                BookSummary(
                    libraryId = "library",
                    id = "book-1",
                    fileId = "file-1",
                    title = "Offline Book",
                    author = "Author",
                    isRead = true,
                    availableFormats = listOf("EPUB"),
                    downloadedFormats = listOf("EPUB"),
                    genres = listOf("Fantasy")
                )
            ),
            total = 1,
            page = 0,
            size = 100,
            isComplete = true
        )

        store.saveCollections(serverUrl, listOf(collection))
        store.saveCollectionBooksPage(serverUrl, collection.id, 0, page)

        val reopened = BrowserSnapshotStore(directory, "profile-1")
        assertEquals(listOf(collection), reopened.readCollections(serverUrl))
        val restoredPage = reopened.readCollectionBooksPage(serverUrl, collection.id, 0)
        assertEquals(page, restoredPage)
        assertEquals(listOf("EPUB"), restoredPage?.items?.single()?.availableFormats)
        assertEquals(listOf("EPUB"), restoredPage?.items?.single()?.downloadedFormats)
        assertEquals(listOf("Fantasy"), restoredPage?.items?.single()?.genres)

        val otherProfile = BrowserSnapshotStore(directory, "profile-2")
        assertNull(otherProfile.readCollections(serverUrl))
        assertNull(otherProfile.readCollectionBooksPage(serverUrl, collection.id, 0))

        reopened.saveCollections(serverUrl, emptyList())
        assertNotNull(reopened.readCollections(serverUrl))
        assertEquals(emptyList<BookCollection>(), reopened.readCollections(serverUrl))
    }
}
