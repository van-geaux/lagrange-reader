package com.vangeaux.lagrange.provider.grimmory

import com.vangeaux.lagrange.AuthenticationRequiredException
import com.vangeaux.lagrange.HomeSection
import com.vangeaux.lagrange.BookReadStatus
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GrimmoryHomeShelfModuleTest {
    @Test
    fun `recently added series page requests native descending pagination and maps provider cards`() = runBlocking {
        val requests = mutableListOf<Pair<String, Map<String, String>>>()
        val module = GrimmoryHomeShelfModule(
            serverUrlProvider = { "https://books.example" },
            nativeJson = { path, query ->
                requests += path to query
                JSONObject()
                    .put("content", JSONArray().put(JSONObject()
                        .put("seriesName", "Saga 2")
                        .put("bookCount", 3)
                        .put("booksRead", 2)
                        .put("authors", JSONArray().put("A. Author"))
                        .put("latestAddedOn", "2026-11-01T12:00:00Z")
                        .put("coverBooks", JSONArray().put(JSONObject().put("bookId", 301)))
                    ))
                    .put("page", 3).put("size", 20).put("totalElements", 61).put("totalPages", 4)
            },
            nativeJsonArray = { _, _ -> error("Series shelf must use the native paginated endpoint") }
        )

        val page = module.loadRecentlyAddedSeriesPage(3)

        assertEquals(61, page.total)
        assertEquals(3, page.page)
        assertEquals(20, page.size)
        assertEquals("Saga 2", page.items.single().id)
        assertEquals(listOf("A. Author"), page.items.single().authors)
        assertEquals(3, page.items.single().bookCount)
        assertEquals(2, page.items.single().readCount)
        assertEquals("https://books.example/api/v1/media/book/301/cover", page.items.single().coverUrl)
        assertTrue(page.items.single().lastAddedAtMillis != null)
        assertEquals(
            listOf("/api/v1/app/series" to mapOf(
                "page" to "3", "size" to "20", "sort" to "recentlyAdded", "dir" to "desc"
            )),
            requests
        )
    }

    @Test
    fun `series detail loads provider books in series-number order`() = runBlocking {
        val requests = mutableListOf<Pair<String, Map<String, String>>>()
        val module = GrimmoryHomeShelfModule(
            serverUrlProvider = { "https://books.example" },
            nativeJson = { path, query ->
                requests += path to query
                appBookPage(
                    appBook("read", status = "READ", series = "Saga & More", number = 1.0),
                    appBook("unread", status = "UNREAD", series = "Saga & More", number = 2.0)
                )
            },
            nativeJsonArray = { _, _ -> error("Unexpected array request") }
        )

        val detail = module.loadSeriesDetail("Saga & More")

        assertEquals("Saga & More", detail?.id)
        assertEquals(2, detail?.bookCount)
        assertEquals(1, detail?.readCount)
        assertEquals(listOf("read", "unread"), detail?.books?.map { it.id })
        assertEquals(
            listOf("/api/v1/app/series/Saga%20%26%20More/books" to mapOf(
                "page" to "0", "size" to "50", "sort" to "seriesNumber", "dir" to "asc"
            )),
            requests
        )
    }

    @Test
    fun `home keeps multiple same-format in-progress books and includes continuation listening`() = runBlocking {
        val requests = mutableListOf<Pair<String, Map<String, String>>>()
        val module = GrimmoryHomeShelfModule(
            serverUrlProvider = { "https://books.example" },
            nativeJson = { path, query ->
                requests += path to query
                when (path) {
                    "/api/v1/app/books" -> when (query["status"]) {
                        "READ" -> appBookPage(
                            appBook("recent-read", status = "READ", series = "Saga", number = 1.0)
                        )
                        else -> error("Unexpected book query: $query")
                    }
                    "/api/v1/app/series" -> JSONObject()
                        .put("content", JSONArray().put(JSONObject()
                            .put("seriesName", "Saga 1")
                            .put("bookCount", 4)
                            .put("booksRead", 1)
                            .put("latestAddedOn", "2026-11-01T12:00:00Z")
                            .put("coverBooks", JSONArray().put(JSONObject().put("bookId", 301)))
                        ))
                        .put("page", 0).put("size", 20).put("totalElements", 1).put("totalPages", 1)
                    "/api/v1/app/series/Saga/books" -> appBookPage(
                        appBook("read", status = "READ", series = "Saga", number = 1.0),
                        appBook("current", status = "READING", series = "Saga", number = 2.0),
                        appBook("next", status = "UNREAD", series = "Saga", number = 3.0)
                    )
                    else -> error("Unexpected endpoint: $path")
                }
            },
            nativeJsonArray = { path, query ->
                requests += path to query
                when (path) {
                    "/api/v1/app/books/continue-reading" -> JSONArray()
                        .put(appBook("epub-one", status = "READING", fileType = "EPUB", lastReadTime = "2026-11-20T10:00:00Z"))
                        .put(appBook("epub-two", status = "READING", fileType = "EPUB", lastReadTime = "2026-11-21T10:00:00Z"))
                    "/api/v1/app/books/continue-listening" -> JSONArray()
                        .put(appBook("audio-one", status = "READING", fileType = "AUDIOBOOK", lastReadTime = "2026-11-22T10:00:00Z"))
                    "/api/v1/app/books/recently-added" -> JSONArray().put(appBook("recent-added"))
                    else -> error("Unexpected array endpoint: $path, $query")
                }
            },
            logShelfFailure = { _, _ -> }
        )

        val shelves = module.loadHomeShelves()

        assertTrue(shelves.isServerProvided)
        assertEquals(
            setOf(HomeSection.WANT_TO_READ, HomeSection.RECENTLY_RELEASED_BOOKS),
            shelves.hiddenSections
        )
        assertEquals(listOf("audio-one", "epub-two", "epub-one"), shelves.booksBySection[HomeSection.CURRENTLY_READING]?.map { it.id })
        assertTrue(shelves.booksBySection[HomeSection.ON_DECK].orEmpty().isEmpty())
        assertEquals(listOf("recent-read"), shelves.booksBySection[HomeSection.RECENTLY_READ]?.map { it.id })
        assertEquals(listOf("recent-added"), shelves.booksBySection[HomeSection.RECENTLY_ADDED_BOOKS]?.map { it.id })
        assertEquals(listOf("Saga 1"), shelves.seriesBySection[HomeSection.RECENTLY_ADDED_SERIES]?.map { it.seriesName })
        assertEquals("Saga 1", shelves.seriesBySection[HomeSection.RECENTLY_ADDED_SERIES]?.single()?.seriesId)
        assertEquals("https://books.example/api/v1/media/book/301/cover", shelves.seriesBySection[HomeSection.RECENTLY_ADDED_SERIES]?.single()?.coverUrl)
        assertEquals(
            listOf(BookReadStatus.READING, BookReadStatus.READING, BookReadStatus.READING),
            shelves.booksBySection[HomeSection.CURRENTLY_READING]?.map { it.readStatus }
        )

        assertTrue(requests.contains(
            "/api/v1/app/series" to mapOf(
                "page" to "0", "size" to "20", "sort" to "recentlyAdded", "dir" to "desc"
            )
        ))
        assertTrue(requests.contains("/api/v1/app/books/continue-reading" to mapOf("limit" to "20")))
        assertTrue(requests.contains("/api/v1/app/books/continue-listening" to mapOf("limit" to "20")))
        assertTrue(requests.contains(
            "/api/v1/app/books" to mapOf(
                "page" to "0", "size" to "50", "status" to "READ",
                "sort" to "lastReadTime", "dir" to "desc"
            )
        ))
        assertTrue(requests.contains(
            "/api/v1/app/series/Saga/books" to mapOf(
                "page" to "0", "size" to "50", "sort" to "seriesNumber", "dir" to "asc"
            )
        ))
    }

    @Test
    fun `home falls back to paginated in-progress books when continuation routes are unavailable`() = runBlocking {
        val requests = mutableListOf<Pair<String, Map<String, String>>>()
        val module = GrimmoryHomeShelfModule(
            serverUrlProvider = { "https://books.example" },
            nativeJson = { path, query ->
                requests += path to query
                when (path) {
                    "/api/v1/app/books" -> when (query["status"]) {
                        "READ" -> appBookPage()
                        null -> appBookPage(
                            appBook("epub-one", status = "READING", fileType = "EPUB", readProgress = 25f),
                            appBook("epub-two", status = "UNREAD", fileType = "EPUB", readProgress = 45f)
                        )
                        else -> error("Unexpected book query: $query")
                    }
                    "/api/v1/app/series" -> JSONObject()
                        .put("content", JSONArray())
                        .put("page", 0).put("size", 20).put("totalElements", 0).put("totalPages", 0)
                    else -> error("Unexpected endpoint: $path")
                }
            },
            nativeJsonArray = { path, query ->
                requests += path to query
                when (path) {
                    "/api/v1/app/books/continue-reading", "/api/v1/app/books/continue-listening" ->
                        throw GrimmoryHttpException(404)
                    "/api/v1/app/books/recently-added" -> JSONArray()
                    else -> error("Unexpected array endpoint: $path")
                }
            },
            logShelfFailure = { _, _ -> }
        )

        val shelves = module.loadHomeShelves()

        assertEquals(listOf("epub-one", "epub-two"), shelves.booksBySection[HomeSection.CURRENTLY_READING]?.map { it.id })
        assertTrue(requests.contains(
            "/api/v1/app/books" to mapOf(
                "page" to "0", "size" to "50",
                "sort" to "lastReadTime", "dir" to "desc"
            )
        ))
    }

    @Test
    fun `home falls back to paginated in-progress catalog when successful continuation routes are empty`() = runBlocking {
        val requests = mutableListOf<Pair<String, Map<String, String>>>()
        val module = GrimmoryHomeShelfModule(
            serverUrlProvider = { "https://books.example" },
            nativeJson = { path, query ->
                requests += path to query
                when (path) {
                    "/api/v1/app/books" -> when (query["status"]) {
                        "READ" -> appBookPage()
                        null -> appBookPage(
                            appBook("epub-one", status = "READING", fileType = "EPUB", readProgress = 30f, lastReadTime = "2026-11-20T10:00:00Z"),
                            appBook("cbx-two", status = "UNREAD", fileType = "CBX", readProgress = 40f, lastReadTime = "2026-11-21T10:00:00Z")
                        )
                        else -> error("Unexpected book query: $query")
                    }
                    "/api/v1/app/series" -> JSONObject()
                        .put("content", JSONArray())
                        .put("page", 0).put("size", 20).put("totalElements", 0).put("totalPages", 0)
                    else -> error("Unexpected endpoint: $path")
                }
            },
            nativeJsonArray = { path, query ->
                requests += path to query
                when (path) {
                    "/api/v1/app/books/continue-reading", "/api/v1/app/books/continue-listening",
                    "/api/v1/app/books/recently-added" -> JSONArray()
                    else -> error("Unexpected array endpoint: $path")
                }
            },
            logShelfFailure = { _, _ -> }
        )

        val shelves = module.loadHomeShelves()

        assertEquals(listOf("cbx-two", "epub-one"), shelves.booksBySection[HomeSection.CURRENTLY_READING]?.map { it.id })
        assertTrue(requests.contains(
            "/api/v1/app/books" to mapOf(
                "page" to "0", "size" to "50", "sort" to "lastReadTime", "dir" to "desc"
            )
        ))
    }

    @Test
    fun `home includes unfinished progress from all catalog pages regardless of reading status`() = runBlocking {
        val catalogRequests = mutableListOf<Map<String, String>>()
        val module = GrimmoryHomeShelfModule(
            serverUrlProvider = { "https://books.example" },
            nativeJson = { path, query ->
                when (path) {
                    "/api/v1/app/books" -> when (query["status"]) {
                        "READ" -> appBookPage()
                        null -> {
                            catalogRequests += query
                            when (query["page"]) {
                                "0" -> appBookPage(
                                    appBook("unread-progress", status = "UNREAD", readProgress = 33.4f, lastReadTime = "2026-11-22T10:00:00Z"),
                                    appBook("reading-progress", status = "READING", readProgress = 5f, lastReadTime = "2026-11-21T10:00:00Z"),
                                    appBook("tiny-progress", status = "UNREAD", readProgress = 0.0005f, lastReadTime = "2026-11-20T10:00:00Z"),
                                    appBook("zero-progress", status = "UNREAD", readProgress = 0f, lastReadTime = "2026-11-19T10:00:00Z"),
                                    appBook("missing-progress", status = "UNREAD", lastReadTime = "2026-11-18T10:00:00Z")
                                ).put("totalElements", 51).put("totalPages", 2)
                                "1" -> appBookPage(
                                    appBook("read-with-incomplete-progress", status = "READ", readProgress = 80f, lastReadTime = "2026-11-17T10:00:00Z"),
                                    appBook("completed-progress", status = "READ", readProgress = 99.5f, lastReadTime = "2026-11-16T10:00:00Z")
                                ).put("number", 1).put("totalElements", 51).put("totalPages", 2)
                                else -> error("Unexpected catalog page: $query")
                            }
                        }
                        else -> error("Unexpected catalog status: $query")
                    }
                    "/api/v1/app/series" -> JSONObject()
                        .put("content", JSONArray())
                        .put("page", 0).put("size", 20).put("totalElements", 0).put("totalPages", 0)
                    else -> error("Unexpected endpoint: $path, $query")
                }
            },
            nativeJsonArray = { path, _ ->
                when (path) {
                    "/api/v1/app/books/continue-reading", "/api/v1/app/books/continue-listening",
                    "/api/v1/app/books/recently-added" -> JSONArray()
                    else -> error("Unexpected array endpoint: $path")
                }
            },
            logShelfFailure = { _, _ -> }
        )

        val shelves = module.loadHomeShelves()

        assertEquals(
            listOf(
                "unread-progress",
                "reading-progress",
                "tiny-progress",
                "read-with-incomplete-progress"
            ),
            shelves.booksBySection[HomeSection.CURRENTLY_READING]?.map { it.id }
        )
        assertEquals(
            listOf(
                mapOf("page" to "0", "size" to "50", "sort" to "lastReadTime", "dir" to "desc"),
                mapOf("page" to "1", "size" to "50", "sort" to "lastReadTime", "dir" to "desc")
            ),
            catalogRequests
        )
    }

    @Test
    fun `home omits later unread series volume from both shelves while earlier volume is active`() = runBlocking {
        val module = GrimmoryHomeShelfModule(
            serverUrlProvider = { "https://books.example" },
            nativeJson = { path, query ->
                when (path) {
                    "/api/v1/app/books" -> when (query["status"]) {
                        "READ" -> appBookPage(
                            appBook("volume-one", status = "READ", series = "Series", number = 1.0)
                        )
                        null -> appBookPage(
                            appBook("volume-two", status = "READING", series = "Series", number = 2.0,
                                lastReadTime = "2026-11-23T08:00:00Z", readProgress = 78.2387f),
                            appBook("volume-three", status = "UNREAD", series = "Series", number = 3.0,
                                lastReadTime = "2026-11-23T08:01:00Z", readProgress = 1.9055f)
                        )
                        else -> error("Unexpected book query: $query")
                    }
                    "/api/v1/app/series" -> JSONObject()
                        .put("content", JSONArray())
                        .put("page", 0).put("size", 20).put("totalElements", 0).put("totalPages", 0)
                    "/api/v1/app/series/Series/books" -> appBookPage(
                        appBook("volume-one", status = "READ", series = "Series", number = 1.0),
                        appBook("volume-two", status = "READING", series = "Series", number = 2.0,
                            lastReadTime = "2026-11-23T08:00:00Z", readProgress = 78.2387f),
                        appBook("volume-three", status = "UNREAD", series = "Series", number = 3.0,
                            lastReadTime = "2026-11-23T08:01:00Z", readProgress = 1.9055f)
                    )
                    else -> error("Unexpected endpoint: $path, $query")
                }
            },
            nativeJsonArray = { path, _ ->
                when (path) {
                    "/api/v1/app/books/continue-reading", "/api/v1/app/books/continue-listening",
                    "/api/v1/app/books/recently-added" -> JSONArray()
                    else -> error("Unexpected array endpoint: $path")
                }
            },
            logShelfFailure = { _, _ -> }
        )

        val shelves = module.loadHomeShelves()

        assertEquals(
            listOf("volume-two"),
            shelves.booksBySection[HomeSection.CURRENTLY_READING]?.map { it.id }
        )
        assertTrue(shelves.booksBySection[HomeSection.ON_DECK].orEmpty().isEmpty())
    }

    @Test
    fun `currently reading see all filters every page by progress before returning the requested page`() = runBlocking {
        val requests = mutableListOf<Pair<String, Map<String, String>>>()
        val firstPageBooks = (0 until 50).map { index ->
            appBook("unread-$index", status = "UNREAD", readProgress = 20f)
        }
        val module = GrimmoryHomeShelfModule(
            serverUrlProvider = { "https://books.example" },
            nativeJson = { path, query ->
                requests += path to query
                when (query["page"]) {
                    "0" -> appBookPage(*firstPageBooks.toTypedArray())
                        .put("totalElements", 51).put("totalPages", 2)
                    "1" -> appBookPage(appBook("current-51", status = "READING", readProgress = 45f))
                        .put("number", 1).put("totalElements", 51).put("totalPages", 2)
                    else -> error("Unexpected catalog page: $query")
                }
            },
            nativeJsonArray = { _, _ -> error("Currently Reading See all should use paged app books") }
        )

        val page = module.loadRecentBooksPage("7", HomeSection.CURRENTLY_READING, page = 1)

        assertEquals(listOf("current-51"), page.items.map { it.id })
        assertEquals(1, page.page)
        assertTrue(page.isComplete)
        assertEquals(
            listOf(
                "/api/v1/app/books" to mapOf(
                    "page" to "0", "size" to "50", "libraryId" to "7",
                    "sort" to "lastReadTime", "dir" to "desc"
                ),
                "/api/v1/app/books" to mapOf(
                    "page" to "1", "size" to "50", "libraryId" to "7",
                    "sort" to "lastReadTime", "dir" to "desc"
                )
            ),
            requests
        )
    }

    @Test
    fun `recent catalog shelf applies enriched formats to provider summaries`() = runBlocking {
        var enrichCalls = 0
        val module = GrimmoryHomeShelfModule(
            serverUrlProvider = { "https://books.example/lab" },
            nativeJson = { path, _ ->
                assertEquals("/api/v1/app/books", path)
                appBookPage(appBook("42"))
            },
            nativeJsonArray = { _, _ -> error("Unexpected array request") },
            enrichBooks = { books, base ->
                assertEquals("https://books.example/lab", base)
                enrichCalls++
                books.map { it.copy(availableFormats = listOf("EPUB", "AUDIO")) }
            }
        )

        val page = module.loadRecentBooksPage("7", HomeSection.RECENTLY_ADDED_BOOKS, 0)

        assertEquals(1, enrichCalls)
        assertEquals(listOf("EPUB", "AUDIO"), page.items.single().availableFormats)
    }

    @Test
    fun `failed on deck request does not erase valid home shelves`() = runBlocking {
        val module = GrimmoryHomeShelfModule(
            serverUrlProvider = { "https://books.example" },
            nativeJson = { path, query ->
                when (path) {
                    "/api/v1/app/books" -> when (query["status"]) {
                        "READING,RE_READING" -> appBookPage(appBook("current", status = "READING"))
                        "READ" -> appBookPage(
                            appBook("recent-read", status = "READ", series = "Broken", number = 1.0)
                        )
                        else -> error("Unexpected book query: $query")
                    }
                    "/api/v1/app/series/Broken/books" -> throw GrimmoryHttpException(404)
                    else -> error("Unexpected endpoint: $path")
                }
            },
            nativeJsonArray = { path, _ ->
                when (path) {
                    "/api/v1/app/books/continue-reading" -> JSONArray().put(appBook("current", status = "READING"))
                    "/api/v1/app/books/continue-listening" -> JSONArray()
                    "/api/v1/app/books/recently-added" -> JSONArray().put(appBook("recent-added"))
                    else -> error("Unexpected array endpoint: $path")
                }
            },
            logShelfFailure = { _, _ -> }
        )

        val shelves = module.loadHomeShelves()

        assertEquals(listOf("current"), shelves.booksBySection[HomeSection.CURRENTLY_READING]?.map { it.id })
        assertEquals(listOf("recent-read"), shelves.booksBySection[HomeSection.RECENTLY_READ]?.map { it.id })
        assertEquals(listOf("recent-added"), shelves.booksBySection[HomeSection.RECENTLY_ADDED_BOOKS]?.map { it.id })
        assertTrue(shelves.booksBySection[HomeSection.ON_DECK].orEmpty().isEmpty())
    }

    @Test
    fun `authentication failure from a home shelf is propagated for session recovery`() = runBlocking {
        val module = GrimmoryHomeShelfModule(
            serverUrlProvider = { "https://books.example" },
            nativeJson = { _, _ -> throw AuthenticationRequiredException() },
            nativeJsonArray = { _, _ -> JSONArray() },
            logShelfFailure = { _, _ -> }
        )

        var propagated = false
        try {
            module.loadHomeShelves()
        } catch (_: AuthenticationRequiredException) {
            propagated = true
        }

        assertTrue(propagated)
    }

    @Test
    fun `failed series detail is skipped without discarding other on deck candidates`() = runBlocking {
        val seriesBooksRequests = mutableListOf<String>()
        val module = GrimmoryHomeShelfModule(
            serverUrlProvider = { "https://books.example" },
            nativeJson = { path, query ->
                when (path) {
                    "/api/v1/app/books" -> {
                        assertEquals("READ", query["status"])
                        assertEquals("7", query["libraryId"])
                        appBookPage(
                            appBook("broken-read", status = "READ", series = "Broken", number = 1.0),
                            appBook("good-read", status = "READ", series = "Good", number = 1.0)
                        )
                    }
                    "/api/v1/app/series/Broken/books" -> {
                        assertEquals("7", query["libraryId"])
                        seriesBooksRequests += path
                        throw GrimmoryHttpException(404)
                    }
                    "/api/v1/app/series/Good/books" -> {
                        assertEquals("7", query["libraryId"])
                        seriesBooksRequests += path
                        appBookPage(
                            appBook("read", status = "READ", series = "Good", number = 1.0),
                            appBook("next", status = "UNREAD", series = "Good", number = 2.0)
                        )
                    }
                    else -> error("Unexpected endpoint: $path ($query)")
                }
            },
            nativeJsonArray = { _, _ -> error("Unexpected array endpoint") },
            logShelfFailure = { _, _ -> }
        )

        val page = module.loadRecentBooksPage("7", HomeSection.ON_DECK, page = 0)

        assertEquals(listOf("next"), page.items.map { it.id })
        assertEquals(listOf(
            "/api/v1/app/series/Broken/books",
            "/api/v1/app/series/Good/books"
        ), seriesBooksRequests)
    }

    @Test
    fun `recently added see all uses paged app book catalog`() = runBlocking {
        val requests = mutableListOf<Pair<String, Map<String, String>>>()
        val module = GrimmoryHomeShelfModule(
            serverUrlProvider = { "https://books.example" },
            nativeJson = { path, query ->
                requests += path to query
                appBookPage(appBook("book-51"))
                    .put("number", 1)
                    .put("size", 50)
                    .put("totalElements", 51)
                    .put("totalPages", 2)
            },
            nativeJsonArray = { _, _ -> error("Recently added See all should use paged books") }
        )

        val page = module.loadRecentBooksPage("", HomeSection.RECENTLY_ADDED_BOOKS, page = 1)

        assertEquals(listOf("book-51"), page.items.map { it.id })
        assertEquals(1, page.page)
        assertTrue(page.isComplete)
        assertEquals(
            listOf(
                "/api/v1/app/books" to mapOf(
                    "page" to "1", "size" to "50", "sort" to "addedOn", "dir" to "desc"
                )
            ),
            requests
        )
    }

    @Test
    fun `untouched next series book is projected to on deck when status is missing`() = runBlocking {
        val module = GrimmoryHomeShelfModule(
            serverUrlProvider = { "https://books.example" },
            nativeJson = { path, query ->
                when (path) {
                    "/api/v1/app/books" -> when (query["status"]) {
                        "READING,RE_READING" -> appBookPage()
                        "READ" -> appBookPage(
                            appBook("read", status = "READ", series = "Saga", number = 1.0)
                        )
                        else -> error("Unexpected book query: $query")
                    }
                    "/api/v1/app/series" -> JSONObject()
                        .put("content", JSONArray().put(JSONObject()
                            .put("seriesName", "Saga 1")
                            .put("bookCount", 4)
                            .put("booksRead", 1)
                            .put("latestAddedOn", "2026-11-01T12:00:00Z")
                            .put("coverBooks", JSONArray().put(JSONObject().put("bookId", 301)))
                        ))
                        .put("page", 0).put("size", 20).put("totalElements", 1).put("totalPages", 1)
                    "/api/v1/app/series/Saga/books" -> appBookPage(
                        appBook("read", status = "READ", series = "Saga", number = 1.0),
                        appBook("untouched", status = null, series = "Saga", number = 2.0)
                    )
                    else -> error("Unexpected endpoint: $path")
                }
            },
            nativeJsonArray = { _, _ -> JSONArray() },
            logShelfFailure = { _, _ -> }
        )

        val shelves = module.loadHomeShelves()

        assertEquals(listOf("untouched"), shelves.booksBySection[HomeSection.ON_DECK]?.map { it.id })
    }

    private fun appBookPage(vararg books: JSONObject): JSONObject = JSONObject()
        .put("content", JSONArray(books.toList()))
        .put("number", 0)
        .put("size", 50)
        .put("totalElements", books.size)
        .put("totalPages", 1)

    private fun appBook(
        id: String,
        status: String? = "UNREAD",
        series: String? = null,
        number: Double? = null,
        fileType: String? = null,
        lastReadTime: String? = null,
        readProgress: Float? = null
    ): JSONObject = JSONObject().apply {
        put("id", id)
        put("libraryId", "7")
        put("title", id)
        status?.let { put("readStatus", it) }
        fileType?.let {
            put("primaryFileType", it)
            put("primaryFileName", "$id.${if (it == "AUDIOBOOK") "m4b" else it.lowercase()}")
        }
        lastReadTime?.let { put("lastReadTime", it) }
        readProgress?.let { put("readProgress", it) }
        if (series != null) put("seriesName", series)
        if (number != null) put("seriesNumber", number)
    }
}
