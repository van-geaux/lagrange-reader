package com.bookorbit.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
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

    private fun jsonResponse(body: String): MockResponse = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private companion object {
        const val PROGRESS_SYNC_WORK_NAME = "bookorbit-progress-sync"
    }
}
