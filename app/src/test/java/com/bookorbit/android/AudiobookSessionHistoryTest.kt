package com.bookorbit.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudiobookSessionHistoryTest {
    @Test
    fun `history keeps newest bounded events per book and file`() {
        val events = (0 until 22).map { index ->
            AudiobookSessionEvent(
                serverUrl = "https://books.example.test",
                bookId = "book-1",
                fileId = "file-1",
                title = "Book",
                type = if (index % 2 == 0) AudiobookSessionEventType.PLAY else AudiobookSessionEventType.PAUSE,
                occurredAtMillis = index.toLong(),
                positionMs = index * 1_000L
            )
        } + AudiobookSessionEvent(
            serverUrl = "https://books.example.test",
            bookId = "book-1",
            fileId = "file-2",
            title = "Book",
            type = AudiobookSessionEventType.PAUSE,
            occurredAtMillis = 100L,
            positionMs = 5_000L
        )

        val retained = trimAudiobookSessionHistory(events, maxPerBook = 20)

        assertEquals(21, retained.size)
        assertEquals(100L, retained.first { it.fileId == "file-2" }.occurredAtMillis)
        assertTrue(retained.filter { it.fileId == "file-1" }.all { it.occurredAtMillis >= 2L })
        assertEquals(
            (2L..21L).toList().reversed(),
            retained.filter { it.fileId == "file-1" }.map { it.occurredAtMillis }
        )
    }

    @Test
    fun `history identity includes server book and file`() {
        val event = AudiobookSessionEvent(
            serverUrl = "https://books.example.test",
            bookId = "book-1",
            fileId = "file-1",
            title = "Book",
            type = AudiobookSessionEventType.PLAY,
            occurredAtMillis = 1L,
            positionMs = 0L
        )

        assertEquals(
            AudiobookSessionKey("https://books.example.test", "book-1", "file-1"),
            event.key
        )
    }
}
