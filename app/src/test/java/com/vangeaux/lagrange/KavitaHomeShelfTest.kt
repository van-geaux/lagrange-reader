package com.vangeaux.lagrange

import com.vangeaux.lagrange.provider.kavita.KavitaAuthModule
import com.vangeaux.lagrange.provider.kavita.KavitaHomeShelfModuleImpl
import com.vangeaux.lagrange.provider.kavita.kavitaDashboardItems
import com.vangeaux.lagrange.provider.kavita.kavitaSeriesPageItems
import com.vangeaux.lagrange.provider.kavita.kavitaSeriesCard
import com.vangeaux.lagrange.provider.kavita.runKavitaShelfRequest
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class KavitaHomeShelfTest {
    @Test
    fun dashboardParserAcceptsPagedItemsEnvelope() {
        val items = kavitaDashboardItems(
            """
            {"items":[{"id":42,"name":"A Series"}],"totalCount":1}
            """
        )

        assertEquals(1, items.size)
        assertEquals(42, items.single().optInt("id"))
    }

    @Test
    fun dashboardParserAcceptsRawArray() {
        val items = kavitaDashboardItems(
            """
            [{"seriesId":42,"count":3}]
            """
        )

        assertEquals(1, items.size)
        assertEquals(42, items.single().optInt("seriesId"))
    }

    @Test
    fun catalogParserAcceptsPagedItemsEnvelope() {
        val items = kavitaSeriesPageItems("{\"items\":[{\"id\":42}]}")

        assertEquals(1, items.length())
        assertEquals(42, items.optJSONObject(0)?.optInt("id"))
    }

    @Test
    fun recentlyUpdatedSeriesUsesSeriesIdInsteadOfGroupedItemId() {
        val card = kavitaSeriesCard(
            JSONObject(
                """
                {
                  "id": 0,
                  "seriesId": 42,
                  "seriesName": "Actual Series",
                  "libraryId": 7
                }
                """.trimIndent()
            ),
            "https://kavita.example"
        )

        assertEquals("42", card?.seriesId)
        assertEquals("Actual Series", card?.title)
        assertEquals(
            "https://kavita.example/api/Image/series-cover?seriesId=42",
            card?.coverUrl
        )
    }

    @Test
    fun seriesCardFallsBackToIdForSeriesDtoPayloads() {
        val card = kavitaSeriesCard(
            JSONObject("{\"id\":42,\"name\":\"Actual Series\"}"),
            "https://kavita.example"
        )

        assertEquals("42", card?.seriesId)
        assertEquals("Actual Series", card?.title)
    }

    @Test
    fun homeShelfFailureDoesNotDiscardSuccessfulSiblingShelves() = runBlocking {
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when (request.requestUrl?.encodedPath) {
                    "/api/series/on-deck" -> jsonResponse(
                        """[{"seriesId":11,"seriesName":"On Deck","libraryId":1}]"""
                    )
                    "/api/series/recently-updated-series" -> jsonResponse(
                        """[{"seriesId":12,"seriesName":"Updated","libraryId":1}]"""
                    )
                    "/api/series/recently-added-v2" -> MockResponse().setResponseCode(503)
                    "/api/Series/series-by-ids" -> jsonResponse(
                        """[{"id":0,"seriesId":12,"seriesName":"Updated","libraryId":1}]"""
                    )
                    "/api/Series/series-detail" -> jsonResponse(
                        """{"id":11,"libraryId":1,"name":"On Deck","chapters":[{"id":101,"title":"Current Chapter","pages":10,"pagesRead":4,"files":[{"id":1001,"filePath":"chapter.epub"}]}]}"""
                    )
                    else -> MockResponse().setResponseCode(404)
                }
            }

            val home = KavitaHomeShelfModuleImpl(FakeKavitaHomeAuth(server.url("/").toString().trimEnd('/')))
                .loadHomeShelves()

            assertEquals(
                listOf("Current Chapter"),
                home.booksBySection[HomeSection.CURRENTLY_READING]?.map { it.title }
            )
            assertEquals(
                listOf("Updated"),
                home.seriesBySection[HomeSection.RECENTLY_UPDATED_SERIES]?.map { it.title }
            )
            assertEquals(emptyList<BookSummary>(), home.seriesBySection[HomeSection.RECENTLY_ADDED_SERIES])

            val requestPaths = List(5) { server.takeRequest().requestUrl?.encodedPath }.toSet()
            assertEquals(
                setOf(
                    "/api/series/on-deck",
                    "/api/series/recently-updated-series",
                    "/api/series/recently-added-v2",
                    "/api/Series/series-by-ids",
                    "/api/Series/series-detail"
                ),
                requestPaths
            )
        }
    }

    @Test
    fun failedHomeShelfFallsBackWithoutFailingOtherShelves() = runBlocking {
        val successful = runKavitaShelfRequest(emptyList<String>()) { listOf("recent") }
        val failed = runKavitaShelfRequest(emptyList<String>()) { throw IllegalStateException("offline") }

        assertEquals(listOf("recent"), successful)
        assertEquals(emptyList<String>(), failed)
    }

    private fun jsonResponse(body: String) = MockResponse()
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private class FakeKavitaHomeAuth(private val baseUrl: String) : KavitaAuthModule {
        override fun serverUrl(): String = baseUrl
        override suspend fun checkServer(serverUrl: String) = ServerCheckResult.Reachable
        override suspend fun authenticate(serverUrl: String, username: String, password: String) = Unit
        override suspend fun sessionState(serverUrl: String) = SessionState.Unauthenticated
        override suspend fun clearSession() = Unit
        override suspend fun saveCurrentProfileSession() = Unit
        override suspend fun restoreCurrentProfileSession() = false
        override fun requestHeaders(): Map<String, String> = emptyMap()
        override fun localizedMessage(messageRes: Int, fallback: String, vararg formatArgs: Any): String = fallback
    }
}
