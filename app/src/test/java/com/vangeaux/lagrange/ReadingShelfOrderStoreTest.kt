package com.vangeaux.lagrange

import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingShelfOrderStoreTest {
    @Test
    fun `order persists across instances and account scopes`() = runBlocking {
        val directory = Files.createTempDirectory("reading-shelf-order").toFile()
        val first = ReadingActivityKey("library", "z")
        val second = ReadingActivityKey("library", "a")
        val store = ReadingShelfOrderStore(directory)
        try {
            store.write("account-1", listOf(first, second))
            store.write("account-2", listOf(second, first))
            val reopened = ReadingShelfOrderStore(directory)
            assertEquals(listOf(first, second), reopened.read("account-1"))
            assertEquals(listOf(second, first), reopened.read("account-2"))
            reopened.clearScope("account-1")
            assertEquals(emptyList<ReadingActivityKey>(), store.read("account-1"))
            assertEquals(listOf(second, first), store.read("account-2"))
        } finally {
            Files.deleteIfExists(directory.toPath().resolve("reading_shelf_order.json"))
            Files.delete(directory.toPath())
        }
    }
}
