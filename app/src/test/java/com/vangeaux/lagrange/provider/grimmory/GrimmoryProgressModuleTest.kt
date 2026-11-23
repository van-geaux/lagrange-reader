package com.vangeaux.lagrange.provider.grimmory

import com.vangeaux.lagrange.BookFileOption
import com.vangeaux.lagrange.BookReadStatus
import com.vangeaux.lagrange.BookSummary
import com.vangeaux.lagrange.AudioProgressPosition
import com.vangeaux.lagrange.MediaKind
import com.vangeaux.lagrange.core.AuthenticatedMediaModule
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class GrimmoryProgressModuleTest {
    @Test
    fun `load progress requests native endpoint and restores EPUB locator`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """{"readProgress":42.5,"readStatus":"READING","lastReadTime":"2026-11-17T12:30:00Z","epubProgress":{"cfi":"epubcfi(/6/2[chapter]!/4/2/2:8)","href":"OPS/chapter.xhtml","percentage":42.5}}"""
                )
            )
            server.start()
            val auth = TestAuth()
            val module = GrimmoryProgressModule(
                client = OkHttpClient(),
                auth = auth,
                serverUrlProvider = { server.url("/library/").toString().trimEnd('/') }
            )
            val book = BookSummary(
                libraryId = "7",
                id = "42",
                fileId = "99",
                title = "A Story",
                format = "EPUB",
                mediaKind = MediaKind.EPUB
            )

            val restored = module.loadReaderProgress(book, listOf(BookFileOption(book)))

            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/library/api/v1/app/books/42/progress", request.path)
            assertTrue(request.getHeader("Authorization").orEmpty().startsWith("Bearer "))
            assertEquals(1, auth.headerRequests)
            assertEquals(42.5f, restored.serverProgressPercent!!, 0f)
            assertEquals(42.5f, restored.progressPercent!!, 0f)
            assertEquals(BookReadStatus.READING, restored.readStatus)
            assertEquals(
                Instant.parse("2026-11-17T12:30:00Z").toEpochMilli(),
                restored.lastReadAtMillis ?: -1L
            )
            val locator = JSONObject(restored.readerLocatorJson!!)
            assertEquals("OPS/chapter.xhtml", locator.optString("href"))
            assertEquals(
                "epubcfi(/6/2[chapter]!/4/2/2:8)",
                locator.optJSONObject("locations")?.optJSONArray("fragments")?.optString(0)
            )
        }
    }

    @Test
    fun `load progress converts Grimmory one-based PDF first page to reader page zero`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """{"readProgress":17.25,"readStatus":"READING","pdfProgress":{"page":1,"percentage":17.25}}"""
                )
            )
            server.start()
            val module = GrimmoryProgressModule(
                client = OkHttpClient(),
                auth = TestAuth(),
                serverUrlProvider = { server.url("/library/").toString().trimEnd('/') }
            )
            val book = BookSummary(
                libraryId = "7",
                id = "42",
                fileId = "99",
                title = "A PDF",
                format = "PDF",
                mediaKind = MediaKind.PDF
            )

            val restored = module.loadReaderProgress(book, listOf(BookFileOption(book)))

            assertEquals(0, restored.progressPageIndex)
            assertTrue(restored.serverProgressPercent == 17.25f)
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun `load progress maps Grimmory rereading enum to the shared status`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """{"readProgress":17.25,"readStatus":"RE_READING","pdfProgress":{"page":1,"percentage":17.25}}"""
                )
            )
            server.start()
            val module = GrimmoryProgressModule(
                client = OkHttpClient(),
                auth = TestAuth(),
                serverUrlProvider = { server.url("/").toString().trimEnd('/') }
            )
            val book = BookSummary(
                libraryId = "7",
                id = "42",
                fileId = "99",
                title = "A PDF",
                format = "PDF",
                mediaKind = MediaKind.PDF
            )

            val restored = module.loadReaderProgress(book, listOf(BookFileOption(book)))

            assertEquals(BookReadStatus.REREADING, restored.readStatus)
        }
    }

    @Test
    fun `load progress converts Grimmory one-based CBX page to reader page index`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody("""{"readProgress":12,"cbxProgress":{"page":7,"percentage":12}}""")
            )
            server.start()
            val module = GrimmoryProgressModule(
                client = OkHttpClient(),
                auth = TestAuth(),
                serverUrlProvider = { server.url("/library/").toString().trimEnd('/') }
            )
            val book = BookSummary(
                libraryId = "7",
                id = "42",
                fileId = "99",
                title = "A CBX",
                format = "CBZ",
                mediaKind = MediaKind.COMIC
            )

            val restored = module.loadReaderProgress(book, listOf(BookFileOption(book)))

            assertEquals(6, restored.progressPageIndex)
            assertEquals(12f, restored.serverProgressPercent!!, 0f)
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun `load audiobook progress restores selected track and both positions`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """{"readProgress":42.5,"readStatus":"READING","audiobookProgress":{"positionMs":2500,"trackIndex":1,"percentage":42.5}}"""
                )
            )
            server.start()
            val module = GrimmoryProgressModule(
                client = OkHttpClient(),
                auth = TestAuth(),
                serverUrlProvider = { server.url("/library/").toString().trimEnd('/') }
            )
            val book = BookSummary(
                libraryId = "7",
                id = "42",
                fileId = "99",
                title = "A folder audiobook",
                format = "MP3",
                mediaKind = MediaKind.AUDIO
            )
            val tracks = listOf(
                BookFileOption(book, filename = "First", durationMs = 10_000L).copy(
                    playbackItemId = "grimmory:42:99:0", audioTrackIndex = 0,
                    audioCumulativeStartMs = 0L
                ),
                BookFileOption(book, filename = "Second", durationMs = 20_000L).copy(
                    playbackItemId = "grimmory:42:99:1", audioTrackIndex = 1,
                    audioCumulativeStartMs = 10_000L
                )
            )

            val restored = module.loadReaderProgress(book, tracks)

            assertEquals(1, server.requestCount)
            assertEquals("/library/api/v1/app/books/42/progress", server.takeRequest().path)
            assertEquals(AudioProgressPosition(12_500L, 1, 2_500L), restored.audioProgressPosition)
            assertEquals(12_500L, restored.progressPositionMs)
            assertEquals(42.5f, restored.serverProgressPercent!!, 0f)
        }
    }

    @Test
    fun `load audiobook progress when alternate audio formats are available`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """{"readProgress":17.5,"audiobookProgress":{"positionMs":2500,"trackIndex":0,"trackPositionMs":2500,"percentage":17.5}}"""
                )
            )
            server.start()
            val module = GrimmoryProgressModule(
                client = OkHttpClient(),
                auth = TestAuth(),
                serverUrlProvider = { server.url("/library/").toString().trimEnd('/') }
            )
            val book = BookSummary(
                libraryId = "7",
                id = "42",
                fileId = "99",
                title = "An audiobook with alternate formats",
                format = "MP3",
                mediaKind = MediaKind.AUDIO
            )
            val files = listOf(
                BookFileOption(book, filename = "Narration.mp3").copy(
                    playbackItemId = "grimmory:42:99:0",
                    audioTrackIndex = 0,
                    audioCumulativeStartMs = 0L
                ),
                BookFileOption(
                    book.copy(fileId = "100", format = "M4B"),
                    filename = "Narration.m4b"
                )
            )

            val restored = module.loadReaderProgress(book, files)

            assertEquals(1, server.requestCount)
            assertEquals("/library/api/v1/app/books/42/progress", server.takeRequest().path)
            assertEquals(AudioProgressPosition(2_500L, 0, 2_500L), restored.audioProgressPosition)
            assertEquals(2_500L, restored.progressPositionMs)
        }
    }

    @Test
    fun `does not hydrate PDF when multiple files make response identity ambiguous`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"pdfProgress":{"page":3,"percentage":20}}"""))
            server.start()
            val module = GrimmoryProgressModule(
                client = OkHttpClient(),
                auth = TestAuth(),
                serverUrlProvider = { server.url("/library/").toString().trimEnd('/') }
            )
            val book = BookSummary(
                libraryId = "7",
                id = "42",
                fileId = "99",
                title = "A PDF",
                format = "PDF",
                mediaKind = MediaKind.PDF,
                progressPageIndex = 5,
                progressPercent = 40f
            )

            val restored = module.loadReaderProgress(
                book,
                listOf(BookFileOption(book), BookFileOption(book.copy(fileId = "100")))
            )

            assertEquals(book, restored)
            assertEquals(0, server.requestCount)
        }
    }

    @Test
    fun `queue EPUB progress sends native file progress payload and accepts empty success`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(204))
            server.start()
            val auth = TestAuth()
            val module = GrimmoryProgressModule(
                client = OkHttpClient(),
                auth = auth,
                serverUrlProvider = { server.url("/library/").toString().trimEnd('/') }
            )
            val cfi = "epubcfi(/6/2[chapter]!/4/2/2:8)"
            val href = "OPS/chapter.xhtml"
            val book = BookSummary(
                libraryId = "7",
                id = "42",
                fileId = "99",
                title = "A Story",
                format = "EPUB",
                mediaKind = MediaKind.EPUB,
                readerLocatorJson = """{"href":"$href","type":"application/xhtml+xml","locations":{"fragments":["$cfi"]}}"""
            )

            module.queueProgress(book, position = 0L, pageIndex = 0, progressPercent = 65.5f)

            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals("/library/api/v1/app/books/42/progress", request.path)
            assertTrue(request.getHeader("Authorization").orEmpty().startsWith("Bearer "))
            assertEquals(1, auth.headerRequests)
            val body = JSONObject(request.body.readUtf8())
            val fileProgress = body.optJSONObject("fileProgress")
            assertEquals(99L, fileProgress?.optLong("bookFileId", -1L))
            assertEquals(cfi, fileProgress?.optString("positionData"))
            assertEquals(href, fileProgress?.optString("positionHref"))
            assertEquals(65.5, fileProgress?.optDouble("progressPercent", -1.0) ?: -1.0, 0.001)
        }
    }

    @Test
    fun `queue PDF progress writes one-based page number`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(204))
            server.start()
            val module = GrimmoryProgressModule(
                client = OkHttpClient(),
                auth = TestAuth(),
                serverUrlProvider = { server.url("/library/").toString().trimEnd('/') }
            )
            val book = BookSummary(
                libraryId = "7",
                id = "42",
                fileId = "99",
                title = "A PDF",
                format = "PDF",
                mediaKind = MediaKind.PDF
            )

            module.queueProgress(book, position = 0L, pageIndex = 0, progressPercent = 17.25f)

            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            val fileProgress = JSONObject(request.body.readUtf8()).optJSONObject("fileProgress")
            assertEquals(99L, fileProgress?.optLong("bookFileId", -1L))
            assertEquals("1", fileProgress?.optString("positionData"))
            assertEquals(17.25, fileProgress?.optDouble("progressPercent", -1.0) ?: -1.0, 0.001)
        }
    }

    @Test
    fun `queue CBX progress writes one-based page number`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(204))
            server.start()
            val module = GrimmoryProgressModule(
                client = OkHttpClient(),
                auth = TestAuth(),
                serverUrlProvider = { server.url("/library/").toString().trimEnd('/') }
            )
            val book = BookSummary(
                libraryId = "7",
                id = "42",
                fileId = "99",
                title = "A CBX",
                format = "CBZ",
                mediaKind = MediaKind.COMIC
            )

            module.queueProgress(book, position = 0L, pageIndex = 6, progressPercent = 12f)

            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            val fileProgress = JSONObject(request.body.readUtf8()).optJSONObject("fileProgress")
            assertEquals(99L, fileProgress?.optLong("bookFileId", -1L))
            assertEquals("7", fileProgress?.optString("positionData"))
            assertEquals(12.0, fileProgress?.optDouble("progressPercent", -1.0) ?: -1.0, 0.001)
        }
    }

    @Test
    fun `queue audiobook progress writes track-local position and track identity`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(204))
            server.start()
            val module = GrimmoryProgressModule(
                client = OkHttpClient(),
                auth = TestAuth(),
                serverUrlProvider = { server.url("/library/").toString().trimEnd('/') }
            )
            val book = BookSummary(
                libraryId = "7",
                id = "42",
                fileId = "99",
                title = "A folder audiobook",
                format = "MP3",
                mediaKind = MediaKind.AUDIO,
                audioProgressPosition = AudioProgressPosition(12_500L, 1, 2_500L)
            )

            module.queueProgress(book, position = 12_500L, pageIndex = 1, progressPercent = 42.5f)

            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals("/library/api/v1/app/books/42/progress", request.path)
            val body = JSONObject(request.body.readUtf8())
            val fileProgress = body.optJSONObject("fileProgress")
            assertEquals(99L, fileProgress?.optLong("bookFileId", -1L))
            assertEquals("2500", fileProgress?.optString("positionData"))
            assertEquals("1", fileProgress?.optString("positionHref"))
            assertEquals(42.5, fileProgress?.optDouble("progressPercent", -1.0) ?: -1.0, 0.001)
            val audio = body.optJSONObject("audiobookProgress")
            assertEquals(2_500L, audio?.optLong("positionMs", -1L))
            assertEquals(1, audio?.optInt("trackIndex", -1))
            assertEquals(2_500L, audio?.optLong("trackPositionMs", -1L))
            assertEquals(42.5, audio?.optDouble("percentage", -1.0) ?: -1.0, 0.001)
        }
    }

    private class TestAuth : AuthenticatedMediaModule {
        var headerRequests = 0

        override suspend fun requestHeaders(url: String): Map<String, String> {
            headerRequests += 1
            return mapOf("Authorization" to "Bearer synthetic-test-token")
        }

        override suspend fun recoverAuthentication(): Boolean = false
    }
}
