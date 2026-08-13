package com.vangeaux.lagrange

import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnotationMutationQueueStoreTest {
    @Test
    fun `stores distinct mutations for different annotations`() = runBlocking {
        val store = AnnotationMutationQueueStore(Files.createTempDirectory("annotation-queue").toFile())

        store.enqueue(create("local:a", updatedAtMillis = 10L))
        store.enqueue(create("local:b", updatedAtMillis = 20L))

        assertEquals(listOf("local:a", "local:b"), store.readAll().map { it.annotationId })
    }

    @Test
    fun `update after a pending create collapses into one create entry`() = runBlocking {
        val store = AnnotationMutationQueueStore(Files.createTempDirectory("annotation-collapse-update").toFile())

        store.enqueue(create("local:a", text = "original", updatedAtMillis = 10L))
        store.enqueue(update("local:a", note = "remember this", updatedAtMillis = 20L))

        val pending = store.readAll()
        assertEquals(1, pending.size)
        assertEquals(AnnotationMutationOp.CREATE, pending.single().op)
        assertEquals("original", pending.single().text)
        assertEquals("remember this", pending.single().note)
        assertTrue(pending.single().noteSpecified)
        assertEquals(20L, pending.single().updatedAtMillis)
    }

    @Test
    fun `delete of a pending create cancels the create entirely`() = runBlocking {
        val store = AnnotationMutationQueueStore(Files.createTempDirectory("annotation-collapse-delete").toFile())

        store.enqueue(create("local:a", updatedAtMillis = 10L))
        store.enqueue(delete("local:a", updatedAtMillis = 20L))

        assertTrue(store.readAll().isEmpty())
    }

    @Test
    fun `delete of an already synced annotation replaces a queued update`() = runBlocking {
        val store = AnnotationMutationQueueStore(Files.createTempDirectory("annotation-collapse-update-delete").toFile())

        store.enqueue(update("server-42", updatedAtMillis = 10L))
        store.enqueue(delete("server-42", updatedAtMillis = 20L))

        val pending = store.readAll()
        assertEquals(1, pending.size)
        assertEquals(AnnotationMutationOp.DELETE, pending.single().op)
    }

    @Test
    fun `acknowledging an id removes only that entry`() = runBlocking {
        val filesDir = Files.createTempDirectory("annotation-ack").toFile()
        val syncingStore = AnnotationMutationQueueStore(filesDir)
        val editorStore = AnnotationMutationQueueStore(filesDir)

        syncingStore.enqueue(create("local:a", updatedAtMillis = 10L))
        editorStore.enqueue(create("local:b", updatedAtMillis = 20L))
        syncingStore.acknowledge(setOf("local:a"))

        assertEquals(listOf("local:b"), editorStore.readAll().map { it.annotationId })
    }

    @Test
    fun `counts and clears only the requested server`() = runBlocking {
        val store = AnnotationMutationQueueStore(Files.createTempDirectory("annotation-server").toFile())
        store.enqueue(create("local:a", updatedAtMillis = 10L))
        store.enqueue(create("local:b", serverUrl = "https://other.example", updatedAtMillis = 20L))

        assertEquals(1, store.countFor("https://example.test"))
        assertEquals(1, store.countFor("https://other.example"))

        store.clearServer("https://example.test")

        assertEquals(listOf("local:b"), store.readAll().map { it.annotationId })
    }

    @Test
    fun `mapping store survives a new instance`() = runBlocking {
        val filesDir = Files.createTempDirectory("annotation-mapping").toFile()
        AnnotationLocalIdMappingStore(filesDir).remember("local:a", "server-1")

        assertEquals("server-1", AnnotationLocalIdMappingStore(filesDir).resolve("local:a"))
    }

    @Test
    fun `pending update can explicitly clear a note`() = runBlocking {
        val store = AnnotationMutationQueueStore(Files.createTempDirectory("annotation-clear-note").toFile())
        store.enqueue(create("local:a", updatedAtMillis = 10L).copy(note = "old", noteSpecified = true))
        store.enqueue(update("local:a", note = null, updatedAtMillis = 20L))

        val pending = store.readAll().single()
        assertTrue(pending.noteSpecified)
        assertEquals(null, pending.note)
    }

    @Test
    fun `the mutation queue only understands create, update and delete - restore and purge are online-only`() {
        // Issue #56 adds restore/purge routes that are not queued through this store: they are
        // implemented as direct online-only calls in BookOrbitRepository instead of inventing a
        // new queued op here. This test pins the op set so that assumption stays visible.
        assertEquals(
            setOf("CREATE", "UPDATE", "DELETE"),
            AnnotationMutationOp.entries.map { it.name }.toSet()
        )
    }

    @Test
    fun `restore and purge routes are annotation-scoped, not book-scoped like update and delete`() {
        assertEquals("/api/v1/annotations/annotation-1/restore", annotationRestorePath("annotation-1"))
        assertEquals("/api/v1/annotations/annotation-1", annotationPurgePath("annotation-1"))
    }

    private fun create(
        annotationId: String,
        serverUrl: String = "https://example.test",
        text: String = "hello",
        updatedAtMillis: Long
    ) = AnnotationMutationPayload(
        annotationId = annotationId,
        serverUrl = serverUrl,
        bookId = "book-1",
        op = AnnotationMutationOp.CREATE,
        cfi = "epubcfi(/6/2)",
        text = text,
        color = "yellow",
        style = "highlight",
        updatedAtMillis = updatedAtMillis
    )

    private fun update(
        annotationId: String,
        serverUrl: String = "https://example.test",
        note: String? = null,
        updatedAtMillis: Long
    ) = AnnotationMutationPayload(
        annotationId = annotationId,
        serverUrl = serverUrl,
        bookId = "book-1",
        op = AnnotationMutationOp.UPDATE,
        note = note,
        noteSpecified = true,
        color = "yellow",
        style = "highlight",
        updatedAtMillis = updatedAtMillis
    )

    private fun delete(
        annotationId: String,
        serverUrl: String = "https://example.test",
        updatedAtMillis: Long
    ) = AnnotationMutationPayload(
        annotationId = annotationId,
        serverUrl = serverUrl,
        bookId = "book-1",
        op = AnnotationMutationOp.DELETE,
        updatedAtMillis = updatedAtMillis
    )
}
