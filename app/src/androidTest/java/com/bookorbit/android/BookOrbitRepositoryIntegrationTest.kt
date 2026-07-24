package com.bookorbit.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkManager
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import okio.Buffer
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookOrbitRepositoryIntegrationTest {
    private lateinit var context: Context
    private lateinit var server: MockWebServer
    private lateinit var repository: BookOrbitRepository

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        WorkManager.getInstance(context)
            .cancelUniqueWork(PROGRESS_SYNC_WORK_NAME)
            .result
            .get(5, TimeUnit.SECONDS)
        ProgressQueueStore(context).clear()
        LastSyncedProgressStore(context).clear()
        clearDownloads()
        File(context.cacheDir, "reader-cache").deleteRecursively()
        repository = BookOrbitRepository(context)
        repository.clearServer()
        server = MockWebServer().apply { start() }
        repository.setServerUrl(server.url("/").toString().trimEnd('/'))
    }

    @After
    fun tearDown() = runBlocking {
        WorkManager.getInstance(context)
            .cancelUniqueWork(PROGRESS_SYNC_WORK_NAME)
            .result
            .get(5, TimeUnit.SECONDS)
        ProgressQueueStore(context).clear()
        LastSyncedProgressStore(context).clear()
        clearDownloads()
        File(context.cacheDir, "reader-cache").deleteRecursively()
        repository.clearServer()
        server.shutdown()
    }

    @Test
    fun loginTokenBootstrapsAuthenticatedSessionCheck() = runBlocking {
        server.enqueue(jsonResponse("""{"accessToken":"integration-token"}"""))
        server.enqueue(jsonResponse("""{"id":"reader-1","username":"reader"}"""))

        repository.login("reader", "secret")
        val session = repository.getSessionState()

        assertEquals(SessionState.Authenticated, session)
        val loginRequest = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("POST", loginRequest.method)
        assertEquals("/api/v1/auth/login", loginRequest.path)
        val credentials = JSONObject(loginRequest.body.readUtf8())
        assertEquals("reader", credentials.getString("username"))
        assertEquals("secret", credentials.getString("password"))

        val sessionRequest = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("GET", sessionRequest.method)
        assertEquals("/api/v1/auth/me", sessionRequest.path)
        assertEquals("Bearer integration-token", sessionRequest.getHeader("Authorization"))
    }

    @Test
    fun setBookUserRatingWritesNumericRatingAndUsesAuthoritativeDetail() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(jsonResponse("""{"id":"123","title":"Rated Book","rating":4}"""))
        val rated = repository.setBookUserRating(BookSummary(libraryId = "library-1", id = "123", fileId = null, title = "Rated Book"), 4)
        assertEquals(4, rated.userRating)
        val post = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("POST", post.method)
        assertEquals("/api/v1/books/bulk-set-rating", post.path)
        val body = JSONObject(post.body.readUtf8())
        assertEquals(123, body.getJSONArray("bookIds").getInt(0))
        assertEquals(4, body.getInt("rating"))
        val get = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("GET", get.method)
        assertEquals("/api/v1/books/123", get.path)
    }

    @Test
    fun clearBookUserRatingWritesJsonNullAndUsesAuthoritativeDetail() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(jsonResponse("""{"id":"123","title":"Rated Book","rating":null}"""))

        val rated = repository.setBookUserRating(
            BookSummary(libraryId = "library-1", id = "123", fileId = null, title = "Rated Book"),
            null
        )

        assertNull(rated.userRating)
        val post = server.takeRequest(5, TimeUnit.SECONDS)!!
        val body = JSONObject(post.body.readUtf8())
        assertTrue(body.isNull("rating"))
        val get = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("GET", get.method)
        assertEquals("/api/v1/books/123", get.path)
    }

    @Test
    fun libraryAndBookLoadingUseTheLiveHttpContract() = runBlocking {
        server.enqueue(
            jsonResponse(
                """
                {"items":[{"id":"library-1","name":"Main Library","description":"Integration catalog"}]}
                """.trimIndent()
            )
        )
        server.enqueue(
            jsonResponse(
                """
                {
                  "items":[{
                    "id":"book-1",
                    "title":"Integration Book",
                    "author":"Test Author",
                    "files":[{"id":"file-1","format":"epub","name":"integration.epub"}],
                    "readingProgress":{"percentage":37.5,"pageNumber":5}
                  }],
                  "total":1,
                  "seriesTotal":0,
                  "page":0,
                  "size":100
                }
                """.trimIndent()
            )
        )

        val libraries = repository.loadLibraries()
        val page = repository.loadBooksPage("library-1", 0)

        assertEquals(listOf(LibrarySummary("library-1", "Main Library", "Integration catalog")), libraries)
        assertEquals(1, page.total)
        assertEquals(100, page.size)
        val book = page.items.single()
        assertEquals("book-1", book.id)
        assertEquals("file-1", book.fileId)
        assertEquals("Integration Book", book.title)
        assertEquals(MediaKind.EPUB, book.mediaKind)
        assertEquals(37.5f, book.progressPercent)

        val libraryRequest = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("GET", libraryRequest.method)
        assertEquals("/api/v1/libraries", libraryRequest.path)
        val booksRequest = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("POST", booksRequest.method)
        assertEquals("/api/v1/libraries/library-1/books", booksRequest.path)
        val query = JSONObject(booksRequest.body.readUtf8())
        assertEquals(0, query.getJSONObject("pagination").getInt("page"))
        assertEquals(100, query.getJSONObject("pagination").getInt("size"))
    }

    @Test
    fun genreFilteredBooksUseTheOfficialRelationRuleContract() = runBlocking {
        server.enqueue(
            jsonResponse(
                """
                {
                  "items":[{
                    "id":"book-comedy",
                    "libraryId":"library-1",
                    "title":"Comedy Book",
                    "files":[{"id":"file-comedy","format":"epub"}]
                  }],
                  "total":1,
                  "page":0,
                  "size":100
                }
                """.trimIndent()
            )
        )

        val page = repository.loadBooksPage(
            libraryId = "library-1",
            page = 0,
            filter = BookBrowseFilter(genre = "Comedy")
        )

        assertEquals(listOf("book-comedy"), page.items.map { it.id })
        val request = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("/api/v1/libraries/library-1/books", request.path)
        val rule = JSONObject(request.body.readUtf8())
            .getJSONObject("filter")
            .getJSONArray("rules")
            .getJSONObject(0)
        assertEquals("genre", rule.getString("field"))
        assertEquals("includesAny", rule.getString("operator"))
        assertEquals("Comedy", rule.getJSONArray("value").getString(0))
    }

    @Test
    fun queuedProgressStaysLocalUntilExplicitReplayThenAcknowledgesBothWrites() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path == "/api/v1/books/files/file-1/progress" && request.method == "POST" -> {
                    jsonResponse("{}")
                }
                request.path == "/api/v1/books/book-1/status" && request.method == "PATCH" -> {
                    jsonResponse("{}")
                }
                else -> MockResponse().setResponseCode(404)
            }
        }
        val book = BookSummary(
            libraryId = "library-1",
            id = "book-1",
            fileId = "file-1",
            title = "Queued Book",
            mediaKind = MediaKind.EPUB
        )

        repository.queueProgress(book, position = 12_000L, pageIndex = 4, progressPercent = 37.5f)
        WorkManager.getInstance(context)
            .cancelUniqueWork(PROGRESS_SYNC_WORK_NAME)
            .result
            .get(5, TimeUnit.SECONDS)

        assertEquals(1, repository.pendingProgressCount())
        assertEquals(0, server.requestCount)

        assertEquals(SyncAttemptResult.Success, repository.syncPendingProgress())
        assertEquals(0, repository.pendingProgressCount())

        val progressRequest = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("/api/v1/books/files/file-1/progress", progressRequest.path)
        val progress = JSONObject(progressRequest.body.readUtf8())
        assertEquals(37.5, progress.getDouble("percentage"), 0.0)
        assertEquals(5, progress.getInt("pageNumber"))
        assertEquals(12.0, progress.getDouble("positionSeconds"), 0.0)

        val statusRequest = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("/api/v1/books/book-1/status", statusRequest.path)
        assertEquals("reading", JSONObject(statusRequest.body.readUtf8()).getString("status"))
    }

    @Test
    fun staleQueuedFileIdRemapsToTheBooksCurrentPrimaryFile() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path == "/api/v1/books/files/file-stale/progress" -> {
                    MockResponse().setResponseCode(404)
                }
                request.path == "/api/v1/books/book-remapped" && request.method == "GET" -> {
                    jsonResponse(
                        """
                            {
                              "id":"book-remapped",
                              "title":"Remapped EPUB",
                              "files":[{"id":"file-current","format":"epub","role":"primary"}]
                            }
                        """.trimIndent()
                    )
                }
                request.path == "/api/v1/books/files/file-current/progress" -> jsonResponse("{}")
                request.path == "/api/v1/books/book-remapped/status" -> jsonResponse("{}")
                else -> MockResponse().setResponseCode(404)
            }
        }
        val book = BookSummary(
            libraryId = "light-novels",
            id = "book-remapped",
            fileId = "file-stale",
            title = "Remapped EPUB",
            mediaKind = MediaKind.EPUB
        )

        repository.queueProgress(book, position = 8_000L, pageIndex = 2, progressPercent = 20f)
        WorkManager.getInstance(context)
            .cancelUniqueWork(PROGRESS_SYNC_WORK_NAME)
            .result
            .get(5, TimeUnit.SECONDS)

        assertEquals(SyncAttemptResult.Success, repository.syncPendingProgress())
        assertEquals(0, repository.pendingProgressCount())
        assertEquals("/api/v1/books/files/file-stale/progress", server.takeRequest(5, TimeUnit.SECONDS)!!.path)
        assertEquals("/api/v1/books/book-remapped", server.takeRequest(5, TimeUnit.SECONDS)!!.path)
        assertEquals("/api/v1/books/files/file-current/progress", server.takeRequest(5, TimeUnit.SECONDS)!!.path)
        assertEquals("/api/v1/books/book-remapped/status", server.takeRequest(5, TimeUnit.SECONDS)!!.path)
    }

    @Test
    fun failedLocalUpdateKeepsOldFileAndValidRetryReplacesIt() = runBlocking {
        var downloadRequestCount = 0
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path == "/api/v1/books/files/file-update/download" -> {
                    downloadRequestCount += 1
                    MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/pdf")
                        .setBody(
                            when (downloadRequestCount) {
                                1 -> "%PDF-old"
                                2 -> "not-a-pdf"
                                else -> "%PDF-new"
                            }
                        )
                }
                request.path == "/api/v1/books/book-update" -> MockResponse().setResponseCode(404)
                else -> MockResponse().setResponseCode(404)
            }
        }
        val original = BookSummary(
            libraryId = "library-1",
            id = "book-update",
            fileId = "file-update",
            title = "Versioned Book",
            format = "pdf",
            mediaKind = MediaKind.PDF,
            updatedAtMillis = 100L
        )

        val localFile = repository.downloadBook(original)
        assertEquals("%PDF-old", localFile.readText())

        val updated = original.copy(updatedAtMillis = 200L, localPath = localFile.absolutePath)
        val failedUpdate = runCatching { repository.downloadBook(updated) }
        assertTrue(failedUpdate.exceptionOrNull() is UserFacingException)
        assertEquals("%PDF-old", localFile.readText())

        repository.downloadBook(updated)
        assertEquals("%PDF-new", localFile.readText())
        val record = DownloadStore(context).find(repository.getServerUrl().orEmpty(), "file-update")!!
        assertEquals(200L, record.sourceUpdatedAtMillis)
        assertEquals(3, downloadRequestCount)
    }

    @Test
    fun nonLocalEpubPreviewDownloadsAValidatedTemporaryReaderCopy() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/epub+zip")
                .setBody(Buffer().write(testEpubBytes()))
        )
        val book = BookSummary(
            libraryId = "library-1",
            id = "preview-epub",
            fileId = "preview-epub-file",
            title = "Remote Preview",
            format = "epub",
            mediaKind = MediaKind.EPUB
        )

        val state = repository.buildReaderState(book, localOnly = false)

        assertTrue(state.localFile?.exists() == true)
        assertTrue(ReaderFileValidator.isReadable(MediaKind.EPUB, state.localFile!!))
        val request = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("GET", request.method)
        assertEquals("/api/v1/books/files/preview-epub-file/download", request.path)
    }

    @Test
    fun nonLocalAudiobookPreviewDownloadsAnAuthenticatedTemporaryReaderCopy() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "audio/mpeg")
                .setBody("test-audio-payload")
        )
        val book = BookSummary(
            libraryId = "library-1",
            id = "preview-audio",
            fileId = "preview-audio-file",
            title = "Remote Audiobook Preview",
            format = "mp3",
            mediaKind = MediaKind.AUDIO
        )

        val state = repository.buildReaderState(book, localOnly = false)

        assertTrue(state.localFile?.exists() == true)
        assertTrue(ReaderFileValidator.isReadable(MediaKind.AUDIO, state.localFile!!))
        val request = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("GET", request.method)
        assertEquals("/api/v1/books/files/preview-audio-file/download", request.path)
    }

    @Test
    fun interruptedAudiobookPreviewDoesNotReuseAPartialReaderCopy() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "audio/mpeg")
                .setBody("partial")
                .setHeader("Content-Length", "100")
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "audio/mpeg")
                .setBody("complete-audio-payload")
        )
        val book = BookSummary(
            libraryId = "library-1",
            id = "preview-audio-retry",
            fileId = "preview-audio-retry-file",
            title = "Retry Audiobook Preview",
            format = "mp3",
            mediaKind = MediaKind.AUDIO
        )

        assertTrue(runCatching { repository.buildReaderState(book, localOnly = false) }.isFailure)
        val state = repository.buildReaderState(book, localOnly = false)

        assertEquals("complete-audio-payload", state.localFile?.readText())
        assertEquals(
            "/api/v1/books/files/preview-audio-retry-file/download",
            server.takeRequest(5, TimeUnit.SECONDS)?.path
        )
        assertEquals(
            "/api/v1/books/files/preview-audio-retry-file/download",
            server.takeRequest(5, TimeUnit.SECONDS)?.path
        )
    }

    private fun jsonResponse(body: String): MockResponse = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private suspend fun clearDownloads() {
        val store = DownloadStore(context)
        store.readAll().forEach { record -> File(record.localPath).delete() }
        store.clear()
    }

    private fun testEpubBytes(): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            fun entry(path: String, body: String) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(body.toByteArray())
                zip.closeEntry()
            }
            entry("mimetype", "application/epub+zip")
            entry(
                "META-INF/container.xml",
                """<?xml version="1.0"?><container><rootfiles><rootfile full-path="OEBPS/package.opf"/></rootfiles></container>"""
            )
            entry(
                "OEBPS/package.opf",
                """<package><manifest><item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="chapter"/></spine></package>"""
            )
            entry("OEBPS/chapter.xhtml", "<html><body><p>Preview chapter</p></body></html>")
        }
        return output.toByteArray()
    }

    private companion object {
        const val PROGRESS_SYNC_WORK_NAME = "bookorbit-progress-sync"
    }
}