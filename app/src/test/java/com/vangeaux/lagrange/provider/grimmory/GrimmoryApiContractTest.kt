package com.vangeaux.lagrange.provider.grimmory

import com.vangeaux.lagrange.core.AuthenticatedMediaModule
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import kotlinx.coroutines.runBlocking

class GrimmoryApiContractTest {
    @Test
    fun bearerHeaderUsesNativeJwtAuthentication() {
        assertEquals("Bearer access.jwt", grimmoryBearerAuthorizationHeader("access.jwt"))
    }

    @Test
    fun loginPayloadEscapesUsernameAndPasswordAsJson() {
        val payload = org.json.JSONObject(grimmoryLoginPayload("reader", "p\"w\\d"))
        assertEquals("reader", payload.optString("username"))
        assertEquals("p\"w\\d", payload.optString("password"))
    }

    @Test
    fun tokenResponseMapsNativeAccessAndRefreshFields() {
        assertEquals(
            GrimmoryTokenSet("access", "refresh", 1_810_000L, false),
            grimmoryTokenSet("{\"accessToken\":\"access\",\"refreshToken\":\"refresh\",\"expires\":1800,\"isDefaultPassword\":false}", nowMillis = 10_000L)
        )
    }


    @Test
    fun nativeRefreshEndpointUsesThePrimaryApiRoot() {
        assertEquals(
            "https://books.example/api/v1/auth/refresh",
            grimmoryApiUrl("https://books.example", "api/v1/auth/refresh")
        )
    }

    @Test
    fun nativeLibraryEndpointRetainsReverseProxyPrefix() {
        assertEquals(
            "https://books.example/lab/api/v1/libraries",
            grimmoryApiUrl("https://books.example/lab/", "/api/v1/libraries")
        )
    }

    @Test
    fun bookCoverUsesNativeAppCoverRoute() {
        assertEquals("/api/v1/media/book/42/cover", grimmoryBookCoverPath("42"))
    }

    @Test
    fun `CBX page contracts use native endpoints and one-based contiguous page numbers`() {
        assertEquals("/api/v1/cbx/42/pages", grimmoryCbxPageListPath("42"))
        assertEquals(
            "/api/v1/media/book/42/cbx/pages/{page1}",
            grimmoryCbxPageImageTemplatePath("42")
        )
        assertEquals(
            listOf(1, 2, 3),
            parseGrimmoryCbxPageNumbers(org.json.JSONArray("[1,2,3]"))
        )
        assertEquals(emptyList<Int>(), parseGrimmoryCbxPageNumbers(org.json.JSONArray("[1,3]")))
        assertEquals(emptyList<Int>(), parseGrimmoryCbxPageNumbers(org.json.JSONArray("[1,null,3]")))
        assertEquals(emptyList<Int>(), parseGrimmoryCbxPageNumbers(org.json.JSONArray("[1,1.5,2]")))
        assertEquals(emptyList<Int>(), parseGrimmoryCbxPageNumbers(org.json.JSONArray("[]")))
    }

    @Test
    fun nativeCatalogFilterUsesGrimmoryQueryFieldsAndEnums() {
        val query = grimmoryBookCatalogQuery(
            "7",
            2,
            com.vangeaux.lagrange.BookBrowseFilter(
                title = "  Moon  ", author = "Reader", series = "Saga", genre = "Fantasy",
                readStatus = com.vangeaux.lagrange.BookReadFilter.IN_PROGRESS,
                format = com.vangeaux.lagrange.BookFormatFilter.COMIC,
                sort = com.vangeaux.lagrange.BookSortOption.SERIES,
                direction = com.vangeaux.lagrange.SortDirection.DESCENDING
            )
        )

        assertEquals(
            mapOf(
                "page" to "2", "size" to "50", "libraryId" to "7", "search" to "Moon",
                "authors" to "Reader", "series" to "Saga", "category" to "Fantasy",
                "status" to "READING,RE_READING", "fileType" to "CBX",
                "sort" to "seriesName", "dir" to "desc"
            ),
            query
        )
    }

    @Test
    fun catalogFilterUsesCommaDelimitedValuesOnTheFinalHttpRequest() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("{}"))
            server.start()
            val auth = object : AuthenticatedMediaModule {
                override suspend fun requestHeaders(url: String): Map<String, String> = emptyMap()
                override suspend fun recoverAuthentication(): Boolean = false
            }
            val api = GrimmoryNativeApiClient(
                client = OkHttpClient(),
                serverUrlProvider = { server.url("/lab/").toString() },
                auth = auth
            )
            api.json(
                "/api/v1/app/books",
                grimmoryBookCatalogQuery(
                    "", 0,
                    com.vangeaux.lagrange.BookBrowseFilter(
                        readStatus = com.vangeaux.lagrange.BookReadFilter.IN_PROGRESS,
                        format = com.vangeaux.lagrange.BookFormatFilter.COMIC
                    )
                )
            )

            val url = server.takeRequest().requestUrl!!
            assertEquals("READING,RE_READING", url.queryParameter("status"))
            assertEquals("CBX", url.queryParameter("fileType"))
            assertFalse(url.encodedQuery.orEmpty().contains("%5B"))
        }
    }

    @Test
    fun catalogRejectsSortOptionsUnsupportedByNativeEndpoint() {
        val error = runCatching {
            grimmoryBookCatalogQuery(
                "", 0,
                com.vangeaux.lagrange.BookBrowseFilter(sort = com.vangeaux.lagrange.BookSortOption.AUTHOR)
            )
        }.exceptionOrNull()

        assertEquals(com.vangeaux.lagrange.UserFacingReason.CAPABILITY_UNAVAILABLE,
            (error as com.vangeaux.lagrange.UserFacingException).reason)
    }

    @Test
    fun nativeReadStatusMapsOnlySupportedGrimmoryEnumValues() {
        val expected = mapOf(
            "UNREAD" to com.vangeaux.lagrange.BookReadStatus.UNREAD,
            "READING" to com.vangeaux.lagrange.BookReadStatus.READING,
            "RE_READING" to com.vangeaux.lagrange.BookReadStatus.REREADING,
            "READ" to com.vangeaux.lagrange.BookReadStatus.READ,
            "ABANDONED" to com.vangeaux.lagrange.BookReadStatus.ABANDONED
        )

        expected.forEach { (wireStatus, status) ->
            val book = parseGrimmoryAppBookSummary(
                org.json.JSONObject().put("id", "42").put("readStatus", wireStatus),
                "https://books.example"
            )
            assertEquals(status, book?.readStatus)
        }
        val untouched = parseGrimmoryAppBookSummary(
            org.json.JSONObject().put("id", "44"),
            "https://books.example"
        )
        assertEquals(com.vangeaux.lagrange.BookReadStatus.UNREAD, untouched?.readStatus)

        val unset = parseGrimmoryAppBookSummary(
            org.json.JSONObject().put("id", "45").put("readStatus", "UNSET"),
            "https://books.example"
        )
        assertEquals(com.vangeaux.lagrange.BookReadStatus.UNREAD, unset?.readStatus)

        val unmapped = parseGrimmoryAppBookSummary(
            org.json.JSONObject().put("id", "43").put("readStatus", "PAUSED"),
            "https://books.example"
        )
        assertEquals(null, unmapped?.readStatus)
    }

    @Test
    fun nativeCatalogTypesUsePhysicalFileExtensionForReaderRouting() {
        val comic = parseGrimmoryAppBookSummary(
            org.json.JSONObject("""{"id":42,"libraryId":7,"primaryFileType":"CBX","primaryFileName":"story.cbr"}"""),
            "https://books.example"
        )
        val audiobook = parseGrimmoryAppBookSummary(
            org.json.JSONObject("""{"id":43,"libraryId":7,"primaryFileType":"AUDIOBOOK","primaryFileName":"story.m4b"}"""),
            "https://books.example"
        )

        assertEquals(com.vangeaux.lagrange.MediaKind.COMIC, comic?.mediaKind)
        assertEquals("CBR", comic?.format)
        assertEquals(listOf("CBR"), comic?.availableFormats)
        assertEquals(com.vangeaux.lagrange.MediaKind.AUDIO, audiobook?.mediaKind)
        assertEquals("M4B", audiobook?.format)
        assertEquals(listOf("AUDIO"), audiobook?.availableFormats)
    }

    @Test
    fun `detail alternate file keeps its own format and media kind`() = runBlocking {
        val base = "https://books.example"
        val book = parseGrimmoryAppBookSummary(
            org.json.JSONObject("""{"id":42,"libraryId":7,"title":"A Story","primaryFileId":99,"primaryFileType":"EPUB","primaryFileName":"story.epub"}"""),
            base
        )!!
        val detail = loadGrimmoryBookDetail(book, base) { path ->
            assertEquals("/api/v1/app/books/42", path)
            org.json.JSONObject(
                """{"id":42,"libraryId":7,"title":"A Story","primaryFileId":99,"primaryFileType":"EPUB","primaryFileName":"story.epub","files":[{"id":99,"fileName":"story.epub","bookType":"EPUB","extension":"epub","primary":true},{"id":101,"fileName":"story.m4b","bookType":"AUDIOBOOK","extension":"m4b","primary":false}]}"""
            )
        }!!

        assertEquals(listOf("99", "101"), detail.availableFiles.map { it.fileId })
        assertEquals(listOf("EPUB", "M4B"), detail.availableFiles.map { it.book.format })
        assertEquals(
            listOf(com.vangeaux.lagrange.MediaKind.EPUB, com.vangeaux.lagrange.MediaKind.AUDIO),
            detail.availableFiles.map { it.book.mediaKind }
        )
        assertEquals(
            "https://books.example/api/v1/audiobooks/42/stream?bookType=AUDIOBOOK",
            detail.availableFiles[1].book.streamUrl
        )
        assertEquals(
            "https://books.example/api/v1/books/42/files/101/download",
            detail.availableFiles[1].book.downloadUrl
        )
        assertEquals(listOf("EPUB", "AUDIO"), detail.book.availableFormats)
    }

    @Test
    fun `selected alternate audiobook requests matching info and exposes selected stream`() = runBlocking {
        val base = "https://books.example/lab"
        val summary = parseGrimmoryAppBookSummary(
            org.json.JSONObject("""{"id":42,"libraryId":7,"title":"A Story","primaryFileId":99,"primaryFileType":"EPUB","primaryFileName":"story.epub"}"""),
            base
        )!!
        val selected = summary.copy(
            fileId = "101",
            filename = "story.m4b",
            format = "M4B",
            mediaKind = com.vangeaux.lagrange.MediaKind.AUDIO,
            downloadUrl = grimmoryDownloadEndpoint(base, "42", "101")
        )
        val requestedPaths = mutableListOf<String>()
        val detail = loadGrimmoryBookDetail(selected, base) { path ->
            requestedPaths += path
            when (path) {
                "/api/v1/app/books/42" -> org.json.JSONObject(
                    """{"id":42,"libraryId":7,"title":"A Story","primaryFileId":99,"primaryFileType":"EPUB","primaryFileName":"story.epub","files":[{"id":99,"fileName":"story.epub","bookType":"EPUB","extension":"epub","primary":true},{"id":101,"fileName":"story.m4b","bookType":"AUDIOBOOK","extension":"m4b","primary":false}]}"""
                )
                "/api/v1/audiobooks/42/info?bookType=AUDIOBOOK" -> org.json.JSONObject(
                    """{"bookId":42,"bookFileId":101,"durationMs":30000,"folderBased":false}"""
                )
                else -> error("Unexpected Grimmory route: $path")
            }
        }!!

        assertEquals(
            listOf("/api/v1/app/books/42", "/api/v1/audiobooks/42/info?bookType=AUDIOBOOK"),
            requestedPaths
        )
        assertEquals(com.vangeaux.lagrange.MediaKind.AUDIO, detail.book.mediaKind)
        assertEquals("101", detail.book.fileId)
        assertEquals("M4B", detail.book.format)
        assertEquals("https://books.example/lab/api/v1/audiobooks/42/stream?bookType=AUDIOBOOK", detail.book.streamUrl)
        assertEquals(30L, detail.durationSeconds)
    }

    @Test
    fun `alternate audiobook routes use native book type selector`() {
        assertEquals(
            "/api/v1/audiobooks/42/info?bookType=AUDIOBOOK",
            grimmoryAudiobookInfoPath("42", "AUDIOBOOK")
        )
        assertEquals(
            "/api/v1/audiobooks/42/stream?bookType=AUDIOBOOK",
            grimmoryAudiobookStreamPath("42", bookType = "AUDIOBOOK")
        )
    }

    @Test
    fun nativeAppBookPageMapsContentAndPaginationMetadata() {
        val page = parseGrimmoryAppBookPage(
            """{"content":[{"id":42,"libraryId":7,"title":"A Story","primaryFileType":"EPUB"}],"totalElements":21,"number":1,"size":5,"totalPages":5}""",
            "https://books.example/lab"
        )

        assertEquals(1, page.page)
        assertEquals(5, page.size)
        assertEquals(21, page.total)
        assertEquals("42", page.items.single().id)
        assertEquals(false, page.isComplete)
    }

    @Test
    fun nativeAppBookSummaryMapsCatalogFieldsAndUsesNativeContentUrls() {
        val item = org.json.JSONObject(
            """{"id":42,"libraryId":7,"title":"A Story","authors":["A. Reader"],"primaryFileId":99,"primaryFileType":"EPUB","primaryFileName":"story.epub","thumbnailUrl":"/api/books/42/cover","readStatus":"IN_PROGRESS","readProgress":0.25,"pageCount":300,"categories":["Fantasy"]}"""
        )

        val book = parseGrimmoryAppBookSummary(item, "https://books.example/lab")

        assertEquals("42", book?.id)
        assertEquals("7", book?.libraryId)
        assertEquals("A. Reader", book?.author)
        assertEquals("99", book?.fileId)
        assertEquals("EPUB", book?.format)
        assertEquals("https://books.example/lab/api/v1/media/book/42/cover", book?.coverUrl)
        assertEquals(25f, book?.progressPercent)
        assertEquals(listOf("Fantasy"), book?.genres)
    }

    @Test
    fun folderAudiobookMetadataProjectsOrderedTracksWithOneLogicalFileId() {
        val book = com.vangeaux.lagrange.BookSummary(
            libraryId = "7",
            id = "42",
            fileId = "99",
            title = "A Story",
            format = "M4B",
            mediaKind = com.vangeaux.lagrange.MediaKind.AUDIO
        )
        val payload = org.json.JSONObject(
            """{"bookId":42,"bookFileId":99,"durationMs":30000,"folderBased":true,"tracks":[{"index":0,"fileName":"01.mp3","title":"First","durationMs":10000,"fileSizeBytes":100,"cumulativeStartMs":0},{"index":1,"fileName":"02.mp3","title":"Second","durationMs":20000,"fileSizeBytes":200,"cumulativeStartMs":10000}]}"""
        )

        val tracks = parseGrimmoryAudiobookTracks(book, payload, "https://books.example/lab")

        assertEquals(listOf("99", "99"), tracks.map { it.fileId })
        assertEquals(listOf(0, 1), tracks.map { it.audioTrackIndex })
        assertEquals(listOf("01.mp3", "02.mp3"), tracks.map { it.book.filename })
        assertEquals(listOf("First", "Second"), tracks.map { it.filename })
        assertEquals(listOf(10_000L, 20_000L), tracks.map { it.durationMs })
        assertEquals(listOf(0L, 10_000L), tracks.map { it.audioCumulativeStartMs })
        assertEquals(
            listOf(
                "https://books.example/lab/api/v1/audiobooks/42/stream?trackIndex=0",
                "https://books.example/lab/api/v1/audiobooks/42/stream?trackIndex=1"
            ),
            tracks.map { it.book.streamUrl }
        )
        assertEquals(2, tracks.map { it.playbackItemId }.distinct().size)
    }

    @Test
    fun `book detail requests native audiobook info and projects folder tracks`() = runBlocking {
        val requestedPaths = mutableListOf<String>()
        val book = com.vangeaux.lagrange.BookSummary(
            libraryId = "7", id = "42", fileId = "99", title = "A Story",
            format = "M4B", mediaKind = com.vangeaux.lagrange.MediaKind.AUDIO
        )

        val detail = loadGrimmoryBookDetail(book, "https://books.example/lab") { path ->
            requestedPaths += path
            when (path) {
                "/api/v1/app/books/42" -> org.json.JSONObject(
                    """{"id":42,"libraryId":7,"title":"A Story","primaryFileId":99,"primaryFileType":"AUDIOBOOK","primaryFileName":"folder.m4b","files":[{"id":99,"fileName":"folder.m4b","bookType":"AUDIOBOOK","fileSizeKb":1,"isPrimary":true}]}"""
                )
                "/api/v1/audiobooks/42/info" -> org.json.JSONObject(
                    """{"bookId":42,"bookFileId":99,"durationMs":30000,"folderBased":true,"tracks":[{"index":0,"fileName":"01.mp3","title":"First","durationMs":10000,"fileSizeBytes":100,"cumulativeStartMs":0},{"index":1,"fileName":"02.mp3","title":"Second","durationMs":20000,"fileSizeBytes":200,"cumulativeStartMs":10000}]}"""
                )
                else -> error("Unexpected Grimmory route: $path")
            }
        }

        assertEquals(listOf("/api/v1/app/books/42", "/api/v1/audiobooks/42/info"), requestedPaths)
        assertEquals(listOf("99", "99"), detail?.availableFiles?.map { it.fileId })
        assertEquals(listOf(0, 1), detail?.availableFiles?.map { it.audioTrackIndex })
        assertEquals("99", detail?.book?.fileId)
        val expectedChapters = listOf(
            com.vangeaux.lagrange.AudiobookChapter("First", 0L),
            com.vangeaux.lagrange.AudiobookChapter("Second", 10_000L)
        )
        assertEquals(expectedChapters, detail?.audioChapters)
        assertEquals(expectedChapters, detail?.book?.audioChapters)
    }

    @Test
    fun `single file audiobook detail uses native audiobook stream route`() = runBlocking {
        val book = com.vangeaux.lagrange.BookSummary(
            libraryId = "7", id = "42", fileId = "99", title = "A Story",
            format = "M4B", mediaKind = com.vangeaux.lagrange.MediaKind.AUDIO
        )
        val detail = loadGrimmoryBookDetail(book, "https://books.example/lab") { path ->
            when (path) {
                "/api/v1/app/books/42" -> org.json.JSONObject(
                    """{"id":42,"libraryId":7,"title":"A Story","primaryFileId":99,"primaryFileType":"AUDIOBOOK","primaryFileName":"story.m4b","files":[{"id":99,"fileName":"story.m4b","bookType":"AUDIOBOOK","fileSizeKb":128,"isPrimary":true}]}"""
                )
                "/api/v1/audiobooks/42/info" -> org.json.JSONObject(
                    """{"bookId":42,"bookFileId":99,"durationMs":30000,"folderBased":false}"""
                )
                else -> error("Unexpected Grimmory route: $path")
            }
        }

        assertEquals(
            "https://books.example/lab/api/v1/audiobooks/42/stream",
            detail?.book?.streamUrl
        )
        assertEquals(
            "https://books.example/lab/api/v1/audiobooks/42/stream",
            detail?.availableFiles?.single()?.book?.streamUrl
        )
    }

    @Test
    fun `single file audiobook detail projects native chapter metadata`() = runBlocking {
        val book = com.vangeaux.lagrange.BookSummary(
            libraryId = "7", id = "42", fileId = "99", title = "A Story",
            format = "M4B", mediaKind = com.vangeaux.lagrange.MediaKind.AUDIO
        )
        val detail = loadGrimmoryBookDetail(book, "https://books.example/lab") { path ->
            when (path) {
                "/api/v1/app/books/42" -> org.json.JSONObject(
                    """{"id":42,"libraryId":7,"title":"A Story","primaryFileId":99,"primaryFileType":"AUDIOBOOK","primaryFileName":"story.m4b","files":[{"id":99,"fileName":"story.m4b","bookType":"AUDIOBOOK","fileSizeKb":128,"isPrimary":true}]}"""
                )
                "/api/v1/audiobooks/42/info" -> org.json.JSONObject(
                    """{"bookId":42,"bookFileId":99,"durationMs":30000,"folderBased":false,"chapters":[{"index":0,"title":"Opening","startTimeMs":0,"endTimeMs":12000,"durationMs":12000},{"index":1,"title":"Chapter One","startTimeMs":12000,"endTimeMs":30000,"durationMs":18000}]}"""
                )
                else -> error("Unexpected Grimmory route: $path")
            }
        }
        val expected = listOf(
            com.vangeaux.lagrange.AudiobookChapter("Opening", 0L),
            com.vangeaux.lagrange.AudiobookChapter("Chapter One", 12_000L)
        )

        assertEquals(expected, detail?.audioChapters)
        assertEquals(expected, detail?.book?.audioChapters)
    }

    @Test
    fun activeReaderRestoreUsesNativeContentRouteInsteadOfPersistedProviderUrl() {
        val session = com.vangeaux.lagrange.core.ActiveReaderSession(
            serverUrl = "https://books.example/lab",
            book = com.vangeaux.lagrange.BookSummary(
                libraryId = "7",
                id = "42",
                fileId = "99",
                title = "A Story",
                mediaKind = com.vangeaux.lagrange.MediaKind.EPUB,
                streamUrl = "https://books.example/lab/api/books/42/file",
                progressPositionMs = 1_234L,
                progressPageIndex = 4,
                readerPageIndex = 5,
                progressPercent = 43f,
                readerLocatorJson = "{\"cfi\":\"epubcfi(/6/4)\"}"
            ),
            launchMode = com.vangeaux.lagrange.ReaderLaunchMode.NORMAL,
            profileId = "profile-a",
            providerId = "grimmory",
            accountScope = "account-a",
            storageScopeId = "storage-a"
        )

        val state = grimmoryRestoredReaderState(session, "https://books.example/lab", null)

        assertEquals("https://books.example/lab/api/v1/books/42/content", state.streamUrl)
        assertEquals("https://books.example/lab/api/v1/books/42/content", state.book.streamUrl)
        assertEquals(1_234L, state.lastKnownPosition)
        assertEquals(4, state.pageIndex)
        assertEquals(5, state.readerPageIndex)
        assertEquals(43f, state.progressPercent)
        assertEquals("{\"cfi\":\"epubcfi(/6/4)\"}", state.initialLocatorJson)
    }
}