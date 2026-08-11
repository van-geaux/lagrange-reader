package com.vangeaux.lagrange

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverCacheStoreTest {
    @Test
    fun `cover bytes survive a new store instance and remain server scoped`() {
        runBlocking {
            val filesDir = Files.createTempDirectory("cover-cache-test").toFile()
            val first = CoverCacheStore(filesDir)
            val bytes = byteArrayOf(1, 2, 3)

            first.save("https://one.example", "book-1", "https://one.example/cover", bytes)
            assertTrue(first.contains("https://one.example", "book-1", "https://one.example/cover"))
            assertFalse(first.contains("https://one.example", "book-1", "https://one.example/other"))

            val reopened = CoverCacheStore(filesDir)
            assertArrayEquals(bytes, reopened.read("https://one.example", "book-1", "https://one.example/cover"))
            assertNull(reopened.read("https://two.example", "book-1", "https://one.example/cover"))
            assertNull(reopened.read("https://one.example", "book-2", "https://one.example/cover"))

            reopened.clear()
            assertNull(reopened.read("https://one.example", "book-1", "https://one.example/cover"))
            filesDir.deleteRecursively()
        }
    }

    @Test
    fun `saving beyond the byte cap evicts the least recently used thumbnail`() {
        runBlocking {
            val filesDir = Files.createTempDirectory("cover-cache-cap-test").toFile()
            var now = 1L
            val store = CoverCacheStore(filesDir, maxBytes = 6L, clock = { now++ })

            store.save("server", "old", "old-cover", byteArrayOf(1, 2, 3))
            store.save("server", "kept", "kept-cover", byteArrayOf(4, 5, 6))
            assertArrayEquals(byteArrayOf(1, 2, 3), store.read("server", "old", "old-cover"))
            store.save("server", "new", "new-cover", byteArrayOf(7, 8, 9))

            assertArrayEquals(byteArrayOf(1, 2, 3), store.read("server", "old", "old-cover"))
            assertNull(store.read("server", "kept", "kept-cover"))
            assertArrayEquals(byteArrayOf(7, 8, 9), store.read("server", "new", "new-cover"))
            filesDir.deleteRecursively()
        }
    }
}
