package com.vangeaux.lagrange

import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
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

    @Test
    fun `legacy cache entries larger than the per image limit are removed`() {
        runBlocking {
            val filesDir = Files.createTempDirectory("cover-cache-entry-cap-test").toFile()
            val permissive = CoverCacheStore(filesDir, maxEntryBytes = 8L)
            permissive.save("server", "book", "cover", ByteArray(8) { 1 })

            val bounded = CoverCacheStore(filesDir, maxEntryBytes = 4L)

            assertNull(bounded.read("server", "book", "cover"))
            assertFalse(bounded.contains("server", "book", "cover"))
            assertTrue(File(filesDir, "cover_cache").listFiles().isNullOrEmpty())
            filesDir.deleteRecursively()
        }
    }

    @Test
    fun `new cache entries larger than the per image limit are not saved`() {
        runBlocking {
            val filesDir = Files.createTempDirectory("cover-cache-save-cap-test").toFile()
            val store = CoverCacheStore(filesDir, maxEntryBytes = 4L)

            store.save("server", "book", "cover", ByteArray(5) { 1 })

            assertFalse(store.contains("server", "book", "cover"))
            filesDir.deleteRecursively()
        }
    }

    @Test
    fun `directory mutation lock serializes different keys across save and clear`() {
        runBlocking {
            val filesDir = Files.createTempDirectory("cover-cache-clear-race-test").toFile()
            val firstSaveReachedCommit = CountDownLatch(1)
            val releaseFirstSave = CountDownLatch(1)
            val secondSaveReachedCommit = CountDownLatch(1)
            val clearStarted = CountDownLatch(1)
            val clockCalls = AtomicInteger(0)
            val store = CoverCacheStore(
                filesDir,
                clock = {
                    val call = clockCalls.incrementAndGet()
                    when (call) {
                        1 -> {
                            firstSaveReachedCommit.countDown()
                            assertTrue(releaseFirstSave.await(5, TimeUnit.SECONDS))
                        }
                        2 -> secondSaveReachedCommit.countDown()
                    }
                    call.toLong()
                }
            )

            val firstSave = async(Dispatchers.IO) {
                store.save("server", "book-a", "cover-a", byteArrayOf(1, 2, 3))
            }
            assertTrue(firstSaveReachedCommit.await(5, TimeUnit.SECONDS))
            val clear = async(Dispatchers.IO) {
                clearStarted.countDown()
                store.clear()
            }
            assertTrue(clearStarted.await(5, TimeUnit.SECONDS))
            val secondSave = async(Dispatchers.IO) {
                store.save("server", "book-b", "cover-b", byteArrayOf(4, 5, 6))
            }

            assertFalse(secondSaveReachedCommit.await(250, TimeUnit.MILLISECONDS))
            releaseFirstSave.countDown()
            firstSave.await()
            clear.await()
            secondSave.await()

            filesDir.deleteRecursively()
        }
    }

    @Test
    fun `prune cannot race a read that is updating lru state`() {
        runBlocking {
            val filesDir = Files.createTempDirectory("cover-cache-prune-read-race-test").toFile()
            val readReachedClock = CountDownLatch(1)
            val releaseRead = CountDownLatch(1)
            val newSaveReachedClock = CountDownLatch(1)
            val clockCalls = AtomicInteger(0)
            val store = CoverCacheStore(
                filesDir,
                maxBytes = 6L,
                clock = {
                    val call = clockCalls.incrementAndGet()
                    when (call) {
                        3 -> {
                            readReachedClock.countDown()
                            assertTrue(releaseRead.await(5, TimeUnit.SECONDS))
                        }
                        4 -> newSaveReachedClock.countDown()
                    }
                    call.toLong()
                }
            )
            store.save("server", "old", "old-cover", byteArrayOf(1, 2, 3))
            store.save("server", "reading", "reading-cover", byteArrayOf(4, 5, 6))

            val read = async(Dispatchers.IO) {
                store.read("server", "reading", "reading-cover")
            }
            assertTrue(readReachedClock.await(5, TimeUnit.SECONDS))
            val save = async(Dispatchers.IO) {
                store.save("server", "new", "new-cover", byteArrayOf(7, 8, 9))
            }

            assertFalse(newSaveReachedClock.await(250, TimeUnit.MILLISECONDS))
            releaseRead.countDown()
            assertArrayEquals(byteArrayOf(4, 5, 6), read.await())
            save.await()

            assertNull(store.read("server", "old", "old-cover"))
            assertArrayEquals(
                byteArrayOf(4, 5, 6),
                store.read("server", "reading", "reading-cover")
            )
            assertArrayEquals(byteArrayOf(7, 8, 9), store.read("server", "new", "new-cover"))
            filesDir.deleteRecursively()
        }
    }
}
