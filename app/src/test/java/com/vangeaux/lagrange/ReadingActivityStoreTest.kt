package com.vangeaux.lagrange

import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingActivityStoreTest {
    @Test
    fun `activity persists across store instances and is scoped to server and library`() = runBlocking {
        val directory = Files.createTempDirectory("reading-activity").toFile()
        val store = ReadingActivityStore(directory)
        val key = ReadingActivityKey("library-1", "book-1")
        val otherLibrary = ReadingActivityKey("library-2", "book-1")
        try {
            store.record("server-1", key, 100L)
            store.record("server-1", key, 90L)
            store.record("server-1", otherLibrary, 200L)
            store.record("server-2", key, 300L)

            val restored = ReadingActivityStore(directory)
            assertEquals(mapOf(key to 100L, otherLibrary to 200L), restored.read("server-1"))
            assertEquals(mapOf(key to 300L), restored.read("server-2"))

            restored.remove("server-1", key)
            assertEquals(mapOf(otherLibrary to 200L), store.read("server-1"))
            assertEquals(mapOf(key to 300L), store.read("server-2"))
            restored.clear()
            assertEquals(emptyMap<ReadingActivityKey, Long>(), store.read("server-2"))
        } finally {
            store.clear()
            Files.delete(directory.toPath())
        }
    }
}
