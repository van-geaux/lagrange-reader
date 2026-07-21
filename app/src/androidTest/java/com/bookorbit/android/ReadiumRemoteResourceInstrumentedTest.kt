package com.bookorbit.android

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.DelicateReadiumApi
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.asset.ResourceAsset
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.mediatype.MediaType

@RunWith(AndroidJUnit4::class)
@OptIn(DelicateReadiumApi::class)
class ReadiumRemoteResourceInstrumentedTest {
    @Test
    fun rangeProbeRejectsServerThatIgnoresTheRangeHeader() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Accept-Ranges", "bytes")
                .setHeader("Content-Type", "audio/mp4")
                .setBody(Buffer().write(ByteArray(1_024)))
        )
        server.start()
        try {
            val url = requireNotNull(AbsoluteUrl(server.url("/audio.m4b").toString()))
            val result = probeRemoteByteRangeSupport(url, DefaultHttpClient())

            assertEquals(RemoteByteRangeSupport.UNSUPPORTED, result)
            assertEquals("bytes=0-0", server.takeRequest().getHeader("Range"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun rangeProbeRenewsAuthenticationOnceBeforeRetrying() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Accept-Ranges", "bytes")
                .setHeader("Content-Range", "bytes 0-0/128")
                .setHeader("Content-Length", 1)
                .setHeader("Content-Type", "audio/mp4")
                .setBody(Buffer().writeByte(0))
        )
        server.start()
        try {
            var token = "stale"
            var recoveryCount = 0
            val client = AuthenticatedReadiumHttpClient(
                delegate = DefaultHttpClient(),
                headersProvider = { mapOf("Authorization" to "Bearer " + token) },
                recoverAuthentication = {
                    recoveryCount += 1
                    token = "current"
                    true
                }
            )
            val url = requireNotNull(AbsoluteUrl(server.url("/audio.m4b").toString()))

            assertEquals(
                RemoteByteRangeSupport.SUPPORTED,
                probeRemoteByteRangeSupport(url, client)
            )
            assertEquals(1, recoveryCount)
            val first = server.takeRequest()
            val second = server.takeRequest()
            assertEquals("Bearer stale", first.getHeader("Authorization"))
            assertEquals("Bearer current", second.getHeader("Authorization"))
            assertEquals("bytes=0-0", second.getHeader("Range"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun authenticatedReadiumResourceReadsOnlyTheRequestedByteRange() = runBlocking {
        val content = ByteArray(128) { it.toByte() }
        val server = MockWebServer()
        server.dispatcher = rangeDispatcher(content)
        server.start()
        try {
            val application = ApplicationProvider.getApplicationContext<BookOrbitApplication>()
            val url = requireNotNull(
                AbsoluteUrl(server.url("/api/v1/books/files/42/serve").toString())
            )
            val client = AuthenticatedReadiumHttpClient(
                delegate = DefaultHttpClient(),
                headersProvider = {
                    mapOf(
                        "Authorization" to "Bearer test-token",
                        "Cookie" to "session=test-session"
                    )
                },
                recoverAuthentication = { false }
            )
            val asset = AssetRetriever(application.contentResolver, client)
                .retrieve(url, MediaType.MP4)
                .getOrNull() as? ResourceAsset
                ?: error("Readium did not create a remote resource asset.")

            val bytes = asset.resource.read(32L..47L).getOrNull()
                ?: error("Readium could not read the requested remote range.")

            assertArrayEquals(content.copyOfRange(32, 48), bytes)
            val requests = buildList {
                repeat(server.requestCount) {
                    add(server.takeRequest())
                }
            }
            val rangedRequest = requests.last { it.getHeader("Range") == "bytes=32-47" }
            assertEquals("Bearer test-token", rangedRequest.getHeader("Authorization"))
            assertEquals("session=test-session", rangedRequest.getHeader("Cookie"))
            assertTrue(requests.none { it.getHeader("Range") == null && it.method == "GET" })
            asset.close()
        } finally {
            server.shutdown()
        }
    }

    private fun rangeDispatcher(content: ByteArray): Dispatcher = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            if (request.method == "HEAD") {
                return MockResponse()
                    .setResponseCode(200)
                    .setHeader("Accept-Ranges", "bytes")
                    .setHeader("Content-Length", content.size)
                    .setHeader("Content-Type", "audio/mp4")
            }
            val match = RANGE.matchEntire(request.getHeader("Range").orEmpty())
                ?: return MockResponse()
                    .setResponseCode(200)
                    .setHeader("Accept-Ranges", "bytes")
                    .setHeader("Content-Type", "audio/mp4")
                    .setBody(Buffer().write(content))
            val start = match.groupValues[1].toInt()
            val end = match.groupValues[2].toIntOrNull() ?: content.lastIndex
            val slice = content.copyOfRange(start, end + 1)
            return MockResponse()
                .setResponseCode(206)
                .setHeader("Accept-Ranges", "bytes")
                .setHeader("Content-Range", "bytes " + start + "-" + end + "/" + content.size)
                .setHeader("Content-Length", slice.size)
                .setHeader("Content-Type", "audio/mp4")
                .setBody(Buffer().write(slice))
        }
    }

    private companion object {
        val RANGE = Regex("bytes=([0-9]+)-([0-9]*)")
    }
}
