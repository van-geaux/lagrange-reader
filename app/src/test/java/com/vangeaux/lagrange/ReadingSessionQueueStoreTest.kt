package com.vangeaux.lagrange

import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingSessionQueueStoreTest {
    @Test
    fun `stores distinct sessions for the same file`() = runBlocking {
        val store = ReadingSessionQueueStore(Files.createTempDirectory("reading-session-queue").toFile())

        store.enqueue(payload("session-1", updatedAtMillis = 10L))
        store.enqueue(payload("session-2", updatedAtMillis = 20L))

        assertEquals(listOf("session-1", "session-2"), store.readAll().map { it.sessionId })
    }

    @Test
    fun `acknowledging an older snapshot preserves a newer session`() = runBlocking {
        val filesDir = Files.createTempDirectory("reading-session-ack").toFile()
        val syncingStore = ReadingSessionQueueStore(filesDir)
        val readerStore = ReadingSessionQueueStore(filesDir)

        syncingStore.enqueue(payload("in-flight", updatedAtMillis = 10L))
        readerStore.enqueue(payload("newer", updatedAtMillis = 20L))
        syncingStore.acknowledge(setOf("in-flight"))

        assertEquals(listOf("newer"), readerStore.readAll().map { it.sessionId })
    }

    @Test
    fun `counts and clears only the requested server`() = runBlocking {
        val store = ReadingSessionQueueStore(Files.createTempDirectory("reading-session-server").toFile())
        store.enqueue(payload("one", updatedAtMillis = 10L))
        store.enqueue(payload("two", serverUrl = "https://other.example", updatedAtMillis = 20L))

        assertEquals(1, store.countFor("https://example.test"))
        assertEquals(1, store.countFor("https://other.example"))

        store.clearServer("https://example.test")

        assertEquals(listOf("two"), store.readAll().map { it.sessionId })
    }

    private fun payload(
        sessionId: String,
        serverUrl: String = "https://example.test",
        updatedAtMillis: Long
    ) = ReadingSessionPayload(
        sessionId = sessionId,
        serverUrl = serverUrl,
        fileId = "file-1",
        startedAtMillis = updatedAtMillis,
        endedAtMillis = updatedAtMillis + 10_000L,
        durationSeconds = 10L,
        progressDelta = 1.0,
        endProgress = 11.0,
        updatedAtMillis = updatedAtMillis
    )
}
