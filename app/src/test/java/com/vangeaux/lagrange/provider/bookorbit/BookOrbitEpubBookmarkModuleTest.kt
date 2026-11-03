package com.vangeaux.lagrange.provider.bookorbit

import com.vangeaux.lagrange.EpubBookmarkPosition
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookOrbitEpubBookmarkModuleTest {
    @Test
    fun `bookmark CRUD routes and provider payloads stay in BookOrbit module`() = runBlocking {
        val transport = RecordingBookmarkTransport(
            responses = ArrayDeque(
                listOf(
                    """{"data":[{"id":7,"bookId":12,"cfi":"epubcfi(/6/2)","title":"Existing"}]}""",
                    """{"id":8,"bookId":12,"cfi":"epubcfi(/6/4)","title":"New"}""",
                    "",
                    ""
                )
            )
        )
        val module = BookOrbitEpubBookmarkModule(transport)

        val listed = module.loadBookmarks("book one", "file one")
        val created = module.createBookmark(
            "book one",
            "42",
            EpubBookmarkPosition("epubcfi(/6/4)", "OPS/chapter.xhtml", null, emptyList()),
            "New"
        )
        module.updateBookmark("book one", "8", "Renamed", null)
        module.deleteBookmark("book one", "8")

        assertTrue(module.isAvailable)
        assertTrue(module.supportsEditing)
        assertEquals("Existing", listed.single().title)
        assertEquals("New", created.title)
        assertEquals(4, transport.calls.size)
        assertEquals(Call("/api/v1/books/book%20one/bookmarks?readerFileId=file%20one", "GET", null), transport.calls[0])
        assertEquals(Call("/api/v1/books/book%20one/bookmarks", "POST", transport.calls[1].body), transport.calls[1])
        assertEquals("epubcfi(/6/4)", org.json.JSONObject(transport.calls[1].body!!).getString("cfi"))
        assertEquals(42, org.json.JSONObject(transport.calls[1].body).getInt("bookFileId"))
        assertEquals("New", org.json.JSONObject(transport.calls[1].body).getString("title"))
        assertEquals(Call("/api/v1/books/book%20one/bookmarks/8", "PATCH", transport.calls[2].body), transport.calls[2])
        assertEquals("Renamed", org.json.JSONObject(transport.calls[2].body!!).getString("title"))
        assertEquals(org.json.JSONObject.NULL, org.json.JSONObject(transport.calls[2].body).get("note"))
        assertEquals(Call("/api/v1/books/book%20one/bookmarks/8", "DELETE", null), transport.calls[3])
    }

    @Test
    fun `module without authenticated transport is unavailable`() = runBlocking {
        val module = BookOrbitEpubBookmarkModule(null)

        assertFalse(module.isAvailable)
        try {
            module.loadBookmarks("12", "42")
            throw AssertionError("Expected unavailable operation to fail")
        } catch (error: com.vangeaux.lagrange.UserFacingException) {
            assertEquals("BookOrbit bookmarks are unavailable.", error.message)
        }
    }

    private data class Call(val path: String, val method: String, val body: String?)

    private class RecordingBookmarkTransport(responses: ArrayDeque<String>) : BookOrbitAuthenticatedJsonRequestExecutor {
        private val responses = responses.toMutableList()
        val calls = mutableListOf<Call>()

        override suspend fun executeAuthenticatedJsonRequest(path: String, method: String, jsonBody: String?): String {
            calls += Call(path, method, jsonBody)
            return responses.removeAt(0)
        }
    }
}
