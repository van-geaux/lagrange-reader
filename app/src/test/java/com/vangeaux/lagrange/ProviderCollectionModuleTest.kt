package com.vangeaux.lagrange

import com.vangeaux.lagrange.provider.kavita.KavitaAuthModule
import com.vangeaux.lagrange.provider.kavita.KavitaBookCatalogModuleImpl
import com.vangeaux.lagrange.provider.kavita.KavitaCollectionCatalogModuleImpl
import com.vangeaux.lagrange.provider.komga.KomgaAuthModule
import com.vangeaux.lagrange.provider.komga.KomgaBookCatalogModule
import com.vangeaux.lagrange.provider.komga.KomgaCollectionModuleImpl
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderCollectionModuleTest {
    @Test
    fun `Komga exposes collections and read lists and loads their books from separate endpoints`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(jsonResponse("""{"content":[{"id":"collection-1","name":"Favorites","booksCount":2}],"last":true}"""))
            server.enqueue(jsonResponse("""{"content":[{"id":"read-list-1","name":"Reading","booksCount":1}],"last":true}"""))
            server.enqueue(jsonResponse("""{"content":[{"id":"book-1","metadata":{"title":"Read List Book"}}],"last":true}"""))
            server.enqueue(jsonResponse("""{"content":[{"id":"series-1"}],"last":true}"""))

            val catalog = FakeKomgaBookCatalog()
            val module = KomgaCollectionModuleImpl(
                auth = FakeKomgaCollectionAuth(server.url("/").toString().trimEnd('/')),
                catalog = catalog,
                client = OkHttpClient()
            )

            val collections = module.loadCollections()
            assertEquals(listOf("collection-1", "readlist:read-list-1"), collections.map { it.id })
            assertEquals("Favorites", collections[0].name)
            assertEquals(2, collections[0].bookCount)
            assertEquals("Reading", collections[1].name)
            assertEquals(1, collections[1].bookCount)

            val readListBooks = module.loadBooksPage("readlist:read-list-1", 0)
            assertEquals(listOf("book-1"), readListBooks.items.map { it.id })
            assertEquals("Read List Book", readListBooks.items.single().title)
            assertEquals(1, readListBooks.total)

            val collectionBooks = module.loadBooksPage("collection-1", 0)
            assertEquals(listOf("series-book"), collectionBooks.items.map { it.id })
            assertEquals(listOf("series-1"), catalog.loadedSeriesIds)

            assertEquals("/api/v1/collections?page=0&size=100&sort=name,asc", server.takeRequest().path)
            assertEquals("/api/v1/readlists?page=0&size=100&sort=name,asc", server.takeRequest().path)
            assertEquals("/api/v1/readlists/read-list-1/books?page=0&size=100", server.takeRequest().path)
            assertEquals(
                "/api/v1/collections/collection-1/series?page=0&size=100&sort=metadata.title,asc",
                server.takeRequest().path
            )
        }
    }

    @Test
    fun `Kavita collections and Want To Read resolve series into book pages`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(jsonResponse("""[{"id":4,"title":"Favorites","summary":"Picked series"}]"""))
            server.enqueue(jsonResponse("""[{"id":12}]"""))
            server.enqueue(kavitaSeriesResponse("12", "Collection Chapter"))
            server.enqueue(jsonResponse("""{"items":[{"id":12}],"totalCount":1}"""))
            server.enqueue(kavitaSeriesResponse("12", "Want To Read Chapter"))

            val auth = FakeKavitaAuth(server.url("/").toString().trimEnd('/'))
            val client = OkHttpClient()
            val catalog = KavitaBookCatalogModuleImpl(auth, client)
            val module = KavitaCollectionCatalogModuleImpl(auth, catalog, client)

            val collections = module.loadCollections()
            assertEquals(listOf("want-to-read", "4"), collections.map { it.id })
            assertEquals("Picked series", collections.last().description)

            val collectionPage = module.loadBooksPage("4", 0)
            assertEquals(listOf("chapter:101"), collectionPage.items.map { it.id })
            assertEquals("Collection Chapter", collectionPage.items.single().title)

            val wantToReadPage = module.loadBooksPage("want-to-read", 0)
            assertEquals(listOf("chapter:101"), wantToReadPage.items.map { it.id })
            assertEquals("Want To Read Chapter", wantToReadPage.items.single().title)

            val collectionRequest = server.takeRequest()
            assertEquals("/api/Collection", collectionRequest.path)
            assertEquals("/api/Series/series-by-collection", server.takeRequest().requestUrl?.encodedPath)
            val collectionSeriesRequest = server.takeRequest()
            assertEquals("/api/Series/series-detail", collectionSeriesRequest.requestUrl?.encodedPath)
            assertEquals("seriesId=12", collectionSeriesRequest.requestUrl?.query)
            val wantToReadRequest = server.takeRequest()
            assertEquals("POST", wantToReadRequest.method)
            assertEquals("/api/want-to-read/v2", wantToReadRequest.requestUrl?.encodedPath)
            assertEquals("1", wantToReadRequest.requestUrl?.queryParameter("pageNumber"))
            assertEquals("{}", wantToReadRequest.body.readUtf8())
            val wantToReadSeriesRequest = server.takeRequest()
            assertEquals("/api/Series/series-detail", wantToReadSeriesRequest.requestUrl?.encodedPath)
            assertEquals("seriesId=12", wantToReadSeriesRequest.requestUrl?.query)
            assertEquals("Bearer test-token", wantToReadSeriesRequest.getHeader("Authorization"))
        }
    }

    private fun jsonResponse(body: String) = MockResponse()
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private fun kavitaSeriesResponse(seriesId: String, chapterTitle: String) = jsonResponse(
        """
        {
          "id": $seriesId,
          "libraryId": 2,
          "name": "Test Series",
          "chapters": [
            {"id": 101, "title": "$chapterTitle", "files": [{"id": 501, "filePath": "chapter.epub"}]}
          ]
        }
        """.trimIndent()
    )

    private class FakeKomgaCollectionAuth(
        private val serverUrl: String
    ) : KomgaAuthModule {
        override fun credentialOrigin(): String = serverUrl
        override fun requestHeaders(serverUrl: String): Map<String, String> = mapOf("Authorization" to "Basic test")
        override fun localizedMessage(messageRes: Int, fallback: String, vararg formatArgs: Any): String = fallback
        override suspend fun checkServer(serverUrl: String) = ServerCheckResult.Reachable
        override suspend fun login(serverUrl: String, username: String, password: String) = Unit
        override suspend fun sessionState(serverUrl: String) = SessionState.Unauthenticated
        override suspend fun clearSession() = Unit
    }

    private class FakeKomgaBookCatalog : KomgaBookCatalogModule {
        val loadedSeriesIds = mutableListOf<String>()

        override suspend fun loadBooks(serverUrl: String, libraryId: String, page: Int) =
            LibraryBooksPage(page = page)

        override suspend fun searchBooks(serverUrl: String, query: String) = emptyList<BookSummary>()

        override suspend fun loadSeriesBooks(serverUrl: String, seriesId: String): List<BookSummary> {
            loadedSeriesIds += seriesId
            return listOf(BookSummary("library", "series-book", "series-file", "Series Book"))
        }
    }

    private class FakeKavitaAuth(
        private val baseUrl: String
    ) : KavitaAuthModule {
        override fun serverUrl(): String = baseUrl
        override suspend fun checkServer(serverUrl: String) = ServerCheckResult.Reachable
        override suspend fun authenticate(serverUrl: String, username: String, password: String) = Unit
        override suspend fun sessionState(serverUrl: String) = SessionState.Unauthenticated
        override suspend fun clearSession() = Unit
        override suspend fun saveCurrentProfileSession() = Unit
        override suspend fun restoreCurrentProfileSession() = false
        override fun requestHeaders(): Map<String, String> = mapOf("Authorization" to "Bearer test-token")
        override fun localizedMessage(messageRes: Int, fallback: String, vararg formatArgs: Any): String = fallback
    }
}
