package com.vangeaux.lagrange.provider.grimmory

import com.vangeaux.lagrange.BookSummary
import com.vangeaux.lagrange.BookFileOption
import com.vangeaux.lagrange.EpubReaderScope
import com.vangeaux.lagrange.MediaKind
import com.vangeaux.lagrange.PROVIDER_GRIMMORY
import com.vangeaux.lagrange.ReaderLaunchMode
import com.vangeaux.lagrange.UserFacingException
import com.vangeaux.lagrange.UserFacingReason
import com.vangeaux.lagrange.comicPageUrl
import com.vangeaux.lagrange.core.AuthenticatedMediaModule
import com.vangeaux.lagrange.core.ActiveReaderSession
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class GrimmoryReaderModuleTest {
    @Test
    fun `online comic state uses native one-based pages and does not download the book`() = runBlocking {
        val cacheDirectory = Files.createTempDirectory("grimmory-comic-reader").toFile()
        MockWebServer().use { server ->
            server.start()
            val scope = readerScope(server.url("/mount/").toString())
            var requestedBookId: String? = null
            val module = readerModule(
                server,
                scope,
                cacheDirectory,
                comicPageNumbersProvider = { bookId ->
                    requestedBookId = bookId
                    listOf(1, 2, 3)
                }
            )
            val book = comicBook().copy(progressPageIndex = 1, progressPercent = 50f)

            val state = module.buildReaderState(book, localOnly = false)

            assertEquals("42", requestedBookId)
            assertEquals(null, state.localFile)
            assertEquals("${server.url("/mount/").toString().trimEnd('/')}/api/v1/media/book/42/cbx/pages/{page1}", state.comicPagesUrl)
            assertEquals(3, state.comicPageCount)
            assertFalse(state.book.isDownloaded)
            assertEquals(1, state.pageIndex)
            assertEquals("${server.url("/mount/").toString().trimEnd('/')}/api/v1/media/book/42/cbx/pages/1", comicPageUrl(state.comicPagesUrl!!, 0))
            assertEquals("${server.url("/mount/").toString().trimEnd('/')}/api/v1/media/book/42/cbx/pages/3", comicPageUrl(state.comicPagesUrl!!, 2))
        }
    }

    @Test
    fun `offline missing comic has local reader unavailable reason`() = runBlocking {
        val cacheDirectory = Files.createTempDirectory("grimmory-comic-offline").toFile()
        MockWebServer().use { server ->
            server.start()
            val scope = readerScope(server.url("/").toString())
            val module = readerModule(server, scope, cacheDirectory)

            val error = runCatching { module.buildReaderState(comicBook(), localOnly = true) }.exceptionOrNull()

            assertTrue(error is UserFacingException)
            assertEquals(UserFacingReason.LOCAL_READER_UNAVAILABLE, (error as UserFacingException).reason)
        }
    }

    @Test
    fun `restoring an online comic reader rebuilds its remote page source`() = runBlocking {
        val cacheDirectory = Files.createTempDirectory("grimmory-comic-restore").toFile()
        MockWebServer().use { server ->
            server.start()
            val scope = readerScope(server.url("/mount/").toString())
            val module = readerModule(
                server,
                scope,
                cacheDirectory,
                comicPageNumbersProvider = { listOf(1, 2) }
            )
            val session = ActiveReaderSession(
                serverUrl = scope.serverUrl,
                book = comicBook(),
                launchMode = ReaderLaunchMode.PREVIEW,
                profileId = scope.profileId,
                providerId = PROVIDER_GRIMMORY,
                accountScope = scope.accountScope,
                storageScopeId = "scope-${scope.profileId}"
            )

            val state = module.restoreActiveReaderState(session, localOnly = false)

            assertEquals(ReaderLaunchMode.PREVIEW, state?.launchMode)
            assertEquals(2, state?.comicPageCount)
            assertEquals(
                "${server.url("/mount/").toString().trimEnd('/')}/api/v1/media/book/42/cbx/pages/{page1}",
                state?.comicPagesUrl
            )
        }
    }

    @Test
    fun `online reader preparation materializes the native EPUB and keeps it out of download identity`() = runBlocking {
        val cacheDirectory = Files.createTempDirectory("grimmory-reader-module").toFile()
        val bytes = epubBytes()
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(Buffer().write(bytes)))
            server.start()
            val scope = readerScope(server.url("/mount/").toString())
            val module = readerModule(server, scope, cacheDirectory)
            val book = epubBook()

            val state = module.buildReaderState(book, localOnly = false)

            assertSame(book, state.book)
            assertNotNull(state.localFile)
            assertTrue(state.localFile!!.isFile)
            assertEquals(bytes.size.toLong(), state.localFile!!.length())
            assertEquals(null, state.streamUrl)
            assertFalse(state.book.isDownloaded)
            assertTrue(state.localFile!!.canonicalPath.startsWith(cacheDirectory.canonicalPath))
            val request = server.takeRequest()
            assertEquals("/mount/api/v1/books/42/content?bookType=EPUB", request.path)
            assertEquals("Bearer reader-token", request.getHeader("Authorization"))
        }
    }

    @Test
    fun `offline reader preparation reuses cached online EPUB without another request`() = runBlocking {
        val cacheDirectory = Files.createTempDirectory("grimmory-reader-module-offline").toFile()
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(Buffer().write(epubBytes())))
            server.start()
            val scope = readerScope(server.url("/").toString())
            val module = readerModule(server, scope, cacheDirectory)
            val book = epubBook()

            val online = module.buildReaderState(book, localOnly = false)
            val offline = module.buildReaderState(book, localOnly = true)

            assertEquals(online.localFile?.canonicalPath, offline.localFile?.canonicalPath)
            assertEquals(1, server.requestCount)
            assertFalse(offline.book.isDownloaded)
        }
    }

    @Test
    fun `online PDF reader state uses restored Grimmory page before presentation`() = runBlocking {
        val cacheDirectory = Files.createTempDirectory("grimmory-reader-progress").toFile()
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody("""{"pdfProgress":{"page":7,"percentage":28.5},"readProgress":28.5}""")
            )
            server.start()
            val scope = readerScope(server.url("/mount/").toString())
            val auth = object : AuthenticatedMediaModule {
                override suspend fun requestHeaders(url: String) = mapOf("Authorization" to "Bearer test")
                override suspend fun recoverAuthentication() = false
            }
            val progressModule = GrimmoryProgressModule(
                client = OkHttpClient(),
                auth = auth,
                serverUrlProvider = { server.url("/mount/").toString().trimEnd('/') }
            )
            val module = readerModule(server, scope, cacheDirectory, progressModule)
            val book = BookSummary(
                libraryId = "7",
                id = "42",
                fileId = "99",
                title = "A PDF",
                format = "PDF",
                mediaKind = MediaKind.PDF,
                progressPageIndex = 2,
                progressPercent = 4f
            )

            val restored = module.loadReaderProgress(book, listOf(BookFileOption(book)))
            val state = module.buildReaderState(restored, localOnly = false)

            assertEquals(6, state.pageIndex)
            assertEquals(28.5f, state.progressPercent!!, 0f)
            assertTrue(state.serverProgressAuthoritative)
            assertEquals("/mount/api/v1/app/books/42/progress", server.takeRequest().path)
        }
    }

    @Test
    fun `online audiobook reader state restores absolute timeline position and selected track`() = runBlocking {
        val cacheDirectory = Files.createTempDirectory("grimmory-reader-audio-progress").toFile()
        MockWebServer().use { server ->
            server.start()
            val scope = readerScope(server.url("/mount/").toString())
            val module = readerModule(server, scope, cacheDirectory)
            val book = BookSummary(
                libraryId = "7",
                id = "42",
                fileId = "99",
                title = "A folder audiobook",
                format = "MP3",
                mediaKind = MediaKind.AUDIO,
                streamUrl = "https://books.example/api/v1/audiobooks/42/stream?trackIndex=1",
                progressPositionMs = 2_500L,
                audioProgressPosition = com.vangeaux.lagrange.AudioProgressPosition(
                    absolutePositionMs = 12_500L,
                    trackIndex = 1,
                    trackPositionMs = 2_500L
                ),
                serverProgressPercent = 41f
            )

            val state = module.buildReaderState(book, localOnly = false)

            assertEquals(12_500L, state.lastKnownPosition)
            assertEquals(1, state.book.audioProgressPosition?.trackIndex)
            assertEquals(2_500L, state.book.audioProgressPosition?.trackPositionMs)
            assertTrue(state.serverProgressAuthoritative)
        }
    }

    @Test
    fun `selected alternate M4B stream remains the reader source`() = runBlocking {
        val cacheDirectory = Files.createTempDirectory("grimmory-reader-m4b").toFile()
        MockWebServer().use { server ->
            server.start()
            val scope = readerScope(server.url("/mount/").toString())
            val module = readerModule(server, scope, cacheDirectory)
            val selectedFileUrl = "${server.url("/mount/").toString().trimEnd('/')}/api/v1/audiobooks/42/stream?bookType=AUDIOBOOK"
            val book = BookSummary(
                libraryId = "7",
                id = "42",
                fileId = "101",
                title = "A Story",
                format = "M4B",
                mediaKind = MediaKind.AUDIO,
                streamUrl = selectedFileUrl
            )

            val state = module.buildReaderState(book, localOnly = false)

            assertEquals("101", state.book.fileId)
            assertEquals("M4B", state.book.format)
            assertEquals(selectedFileUrl, state.streamUrl)
            assertEquals(selectedFileUrl, state.book.streamUrl)
        }
    }

    @Test
    fun `EPUB reader state forwards restored locator and server authority`() {
        val localFile = Files.createTempFile("grimmory-restored", ".epub").toFile()
        val locator = """{"href":"OPS/chapter.xhtml","type":"application/xhtml+xml","locations":{"fragments":["epubcfi(/6/2!)"]}}"""
        val book = epubBook().copy(
            readerLocatorJson = locator,
            serverProgressPercent = 42.5f,
            progressPercent = 42.5f
        )

        val state = grimmoryEpubReaderState(book, localFile)

        assertEquals(locator, state.initialLocatorJson)
        assertEquals(42.5f, state.progressPercent!!, 0f)
        assertTrue(state.serverProgressAuthoritative)
    }

    private fun readerModule(
        server: MockWebServer,
        scope: EpubReaderScope,
        cacheDirectory: java.io.File,
        progressModule: GrimmoryProgressModule? = null,
        comicPageNumbersProvider: suspend (String) -> List<Int> = { emptyList() }
    ) = GrimmoryReaderModule(
        serverUrlProvider = { server.url("/mount/").toString().trimEnd('/') },
        scopeResolver = { scope.copy(serverUrl = it) },
        storageScopeIdProvider = { "scope-${it.profileId}" },
        restoreLocalReader = { null },
        contentCache = GrimmoryEpubReaderContentCache(
            cacheDirectory,
            GrimmoryEpubReaderContentTransport(OkHttpClient(), object : AuthenticatedMediaModule {
                override suspend fun requestHeaders(url: String) = mapOf("Authorization" to "Bearer reader-token")
                override suspend fun recoverAuthentication() = false
            })
        ),
        progressModule = progressModule,
        comicPageNumbersProvider = comicPageNumbersProvider
    )

    private fun readerScope(serverUrl: String) = EpubReaderScope(
        serverUrl = serverUrl.trimEnd('/'),
        profileId = "profile-a",
        providerId = PROVIDER_GRIMMORY,
        accountScope = "account-a"
    )

    private fun epubBook() = BookSummary(
        libraryId = "7",
        id = "42",
        fileId = "99",
        title = "A Story",
        format = "EPUB",
        mediaKind = MediaKind.EPUB
    )

    private fun comicBook() = BookSummary(
        libraryId = "7",
        id = "42",
        fileId = "99",
        title = "A comic",
        format = "CBZ",
        mediaKind = MediaKind.COMIC
    )

    private fun epubBytes(): ByteArray = ByteArrayOutputStream().use { output ->
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("META-INF/container.xml"))
            zip.write("<container/>".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("OPS/chapter.xhtml"))
            zip.write("<html><body>content</body></html>".toByteArray())
            zip.closeEntry()
        }
        output.toByteArray()
    }
}
