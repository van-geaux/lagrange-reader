package com.vangeaux.lagrange.provider.opds

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OpdsCatalogModuleTest {
    @Test
    fun followsServerNextLinkAndMapsNavigationAcquisitionsAndMetadata() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(atomFeed("""
                <link rel="next" href="/proxy/catalog?page=2"/>
                <entry><id>urn:book:one</id><title>First</title><author><name>A. Author</name></author>
                  <summary>A summary.</summary><dc:publisher>Test Press</dc:publisher>
                  <link rel="http://opds-spec.org/image" href="/covers/one.jpg"/>
                  <link rel="http://opds-spec.org/acquisition" type="application/epub+zip" length="1234" href="/acquire/one.epub"/>
                  <link rel="http://opds-spec.org/acquisition/open-access" type="application/pdf" href="/acquire/one.pdf"/>
                </entry>
                <entry><id>nav:fiction</id><title>Fiction</title><link rel="subsection" type="application/atom+xml;profile=opds-catalog" href="/proxy/fiction"/></entry>
            """))
            server.enqueue(atomFeed("""
                <entry><id>urn:book:two</id><title>Second</title>
                  <link rel="http://opds-spec.org/acquisition" type="application/pdf" href="/acquire/two.pdf"/>
                </entry>
            """))
            val rootUrl = server.url("/proxy/catalog?view=main").toString()
            val module = OpdsCatalogModule({ rootUrl })

            val libraries = module.loadLibraries()
            val first = module.loadBooksPage(libraries.single().id, 0)
            val navigation = first.navigationEntries.single()
            val second = module.loadBooksPage(libraries.single().id, 1)
            val detail = module.loadBookDetail(first.items.single())

            assertEquals("/proxy/catalog?view=main", server.takeRequest().path)
            assertEquals("/proxy/catalog?page=2", server.takeRequest().path)
            assertEquals("First", first.items.single().title)
            assertEquals("A. Author", first.items.single().author)
            assertEquals("/covers/one.jpg", java.net.URI(first.items.single().coverUrl).path)
            assertEquals("Fiction", navigation.title)
            assertEquals("/proxy/fiction", java.net.URI(navigation.targetId).path)
            assertEquals("Second", second.items.single().title)
            assertTrue(second.isComplete)
            assertEquals(2, detail?.availableFiles?.size)
            assertEquals("A summary.", detail?.synopsis)
            assertEquals("Test Press", detail?.publisher)
        }
    }

    @Test
    fun legacyCwaProfileKeepsAcquisitionIdentityAndIgnoresAudiobooks() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(atomFeed("""
                <entry><id>book-42</id><title>Existing Title</title>
                  <link rel="subsection" title="All books" href="/proxy/opds/books/all"/>
                </entry>
            """))
            server.enqueue(atomFeed("""
                <entry><id>book-42</id><title>Existing Title</title>
                  <link rel="http://opds-spec.org/acquisition" type="application/epub+zip" href="/proxy/get/42.epub"/>
                  <link rel="http://opds-spec.org/acquisition" type="audio/mpeg" href="/proxy/get/42.mp3"/>
                </entry>
            """))
            val feedUrl = server.url("/proxy/opds").toString()
            val module = OpdsCatalogModule({ feedUrl }, legacyCwa = true)

            val library = module.loadLibraries().single()
            val books = module.loadBooksPage(library.id, 0)
            val detail = module.loadBookDetail(books.items.single())
            val acquisition = server.takeRequest().path
            val page = server.takeRequest().path

            assertEquals("/proxy/opds", acquisition)
            assertEquals("/proxy/opds/books/all", page)
            assertEquals("book-42:${server.url("/proxy/get/42.epub")}", books.items.single().fileId)
            assertEquals("Existing Title.epub", books.items.single().filename)
            assertEquals(library.id, books.items.single().libraryId)
            assertEquals(1, detail?.availableFiles?.size)
        }
    }

    @Test
    fun rejectsCrossOriginNavigationAndAcquisitionLinks() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(atomFeed("""
                <entry><id>urn:book:one</id><title>First</title>
                  <link rel="http://opds-spec.org/acquisition" href="https://attacker.invalid/book.epub"/>
                  <link rel="subsection" href="https://attacker.invalid/feed"/>
                </entry>
            """))
            val rootUrl = server.url("/catalog").toString()
            val module = OpdsCatalogModule({ rootUrl })
            val libraryId = module.loadLibraries().single().id
            val page = module.loadBooksPage(libraryId, 0)

            assertTrue(page.items.isEmpty())
            assertTrue(page.navigationEntries.isEmpty())
            assertEquals("/catalog", server.takeRequest().path)
        }
    }

    @Test
    fun rejectsCrossOriginRedirectWithoutFollowingIt() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "https://attacker.invalid/next"))
            val rootUrl = server.url("/catalog").toString()
            val client = OpdsFeedClient()

            val error = assertThrows(java.io.IOException::class.java) {
                runBlocking { client.fetchFeed(rootUrl, rootUrl) }
            }

            assertTrue(error.message.orEmpty().contains("outside the configured origin"))
            assertEquals(1, server.requestCount)
            assertEquals("/catalog", server.takeRequest().path)
        }
    }

    @Test
    fun sendsConfiguredLegacyBasicAuthorizationOnlyToTrustedOrigin() {
        MockWebServer().use { server ->
            server.enqueue(atomFeed("""<entry><id>book</id><title>Book</title></entry>"""))
            val feedUrl = server.url("/proxy/opds").toString()
            val client = OpdsFeedClient(authorization = { legacyCwaBasicAuthorization("reader", "secret") })

            runBlocking { client.fetchFeed(feedUrl, feedUrl) }

            assertEquals("Basic cmVhZGVyOnNlY3JldA==", server.takeRequest().getHeader("Authorization"))
        }
    }

    @Test
    fun reportsAuthenticationRequiredInsteadOfTreatingUnauthorizedFeedAsEmpty() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(401))
            val rootUrl = server.url("/catalog").toString()
            val client = OpdsFeedClient()

            val error = assertThrows(OpdsHttpStatusException::class.java) {
                runBlocking { client.fetchFeed(rootUrl, rootUrl) }
            }

            assertEquals(401, error.statusCode)
            assertTrue(error.message.orEmpty().contains("requires authentication"))
            assertEquals(1, server.requestCount)
            assertEquals("/catalog", server.takeRequest().path)
        }
    }

    @Test
    fun distinguishesForbiddenFeedAccessFromAuthenticationFailure() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(403))
            val feedUrl = server.url("/catalog").toString()
            val client = OpdsFeedClient()

            val error = assertThrows(OpdsHttpStatusException::class.java) {
                runBlocking { client.fetchFeed(feedUrl, feedUrl) }
            }

            assertEquals(403, error.statusCode)
            assertTrue(error.message.orEmpty().contains("forbidden"))
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun genericBasicCredentialsAreSentToFeedImagesAndAcquisitions() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(atomFeed("<entry><id>root</id><title>Catalog</title></entry>"))
            server.enqueue(MockResponse().setBody("cover bytes"))
            server.enqueue(MockResponse().setBody("book bytes"))
            val feedUrl = server.url("/api/v1/opds").toString()
            val imageUrl = server.url("/api/v1/opds/book/cover").toString()
            val acquisitionUrl = server.url("/api/v1/opds/book/download").toString()
            val client = OpdsFeedClient {
                opdsBasicAuthorization("opds-reader", "test-password")
            }

            client.fetchFeed(feedUrl, feedUrl)
            assertEquals("cover bytes", client.fetchImage(imageUrl, feedUrl)?.toString(Charsets.UTF_8))
            client.streamBlocking(acquisitionUrl, feedUrl, consume = { input, _ -> input.copyTo(java.io.ByteArrayOutputStream()) })

            val expected = "Basic b3Bkcy1yZWFkZXI6dGVzdC1wYXNzd29yZA=="
            repeat(3) { assertEquals(expected, server.takeRequest().getHeader("Authorization")) }
        }
    }

    @Test
    fun streamsAcquisitionBodyAndPreservesConfiguredFeedPrefix() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("book bytes"))
            val rootUrl = server.url("/proxy/catalog").toString()
            val acquisitionUrl = server.url("/proxy/acquire/book.epub").toString()
            val client = OpdsFeedClient()
            val received = java.io.ByteArrayOutputStream()

            client.streamBlocking(acquisitionUrl, rootUrl, consume = { input, length ->
                assertEquals(10L, length)
                input.copyTo(received)
            })

            assertEquals("book bytes", received.toString(Charsets.UTF_8.name()))
            assertEquals("/proxy/acquire/book.epub", server.takeRequest().path)
        }
    }

    private fun atomFeed(entries: String) = MockResponse()
        .setHeader("Content-Type", "application/atom+xml; charset=utf-8")
        .setBody("""
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom" xmlns:dc="http://purl.org/dc/elements/1.1/">
              <id>urn:test:feed</id><title>Test catalog</title><updated>2026-01-01T00:00:00Z</updated>
              $entries
            </feed>
        """.trimIndent())
}
