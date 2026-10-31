package com.vangeaux.lagrange

import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubReaderPositionStoreTest {
    @Test
    fun `save and read preserve exact chapter page position`() = runBlocking {
        val store = EpubReaderPositionStore(Files.createTempDirectory("epub-reader-position").toFile())
        val position = EpubReaderPosition(
            serverUrl = "https://example.test",
            bookId = "book-1",
            fileId = "file-1",
            chapterIndex = 3,
            pageIndex = 7,
            pageCount = 12,
            updatedAtMillis = 42L,
            locatorJson = "{\"href\":\"chapter-4.xhtml\"}",
            progressPercent = 42.5f,
            overallPositionNumber = 18,
            overallPositionCount = 40,
            renderedPageLayoutKey = "rendered-pages-v1:test",
            renderedPageCounts = listOf(1, 8),
            profileId = "profile-a",
            providerId = "bookorbit",
            accountScope = "account-a",
            publicationFingerprint = "revision-a"
        )

        assertTrue(store.importIfAbsent(position))

        assertEquals(position, store.readExact(position))
    }

    @Test
    fun `exact resume records are isolated by profile provider and publication revision`() = runBlocking {
        val store = EpubReaderPositionStore(Files.createTempDirectory("epub-reader-position-scope").toFile())
        val first = EpubReaderPosition(
            serverUrl = "https://example.test/base",
            bookId = "book-1",
            fileId = "file-1",
            chapterIndex = 0,
            pageIndex = 0,
            pageCount = 1,
            updatedAtMillis = 1L,
            locatorJson = "{\"href\":\"one.xhtml\"}",
            profileId = "profile-a",
            providerId = "bookorbit",
            accountScope = "account-a",
            publicationFingerprint = "100:1"
        )
        assertTrue(store.importIfAbsent(first))

        assertEquals(first, store.readExact(first))
        assertNull(store.readExact(first.copy(profileId = "profile-b")))
        assertNull(store.readExact(first.copy(providerId = "komga")))
        assertNull(store.readExact(first.copy(accountScope = "account-b")))
        assertNull(store.readExact(first.copy(publicationFingerprint = "100:2")))
        assertEquals(listOf(first), store.readAll(scope()))
        assertEquals(emptyList<EpubReaderPosition>(), store.readAll(scope().copy(accountScope = "account-b")))
    }

    @Test
    fun `readAll returns only the requested server newest first`() = runBlocking {
        val store = EpubReaderPositionStore(Files.createTempDirectory("epub-reader-position-all").toFile())
        val older = scopedPosition(updatedAtMillis = 10L).copy(
            serverUrl = "https://one.example",
            pageCount = 2
        )
        val newer = older.copy(bookId = "book-2", fileId = "file-2", updatedAtMillis = 20L)
        assertTrue(store.importIfAbsent(newer))
        assertTrue(store.importIfAbsent(older))
        assertTrue(store.importIfAbsent(older.copy(serverUrl = "https://two.example", updatedAtMillis = 30L)))

        assertEquals(
            listOf(newer, older),
            store.readAll(scope(serverUrl = "https://one.example/"))
        )
    }

    @Test
    fun `removeForBook clears exact local resume across files`() = runBlocking {
        val store = EpubReaderPositionStore(Files.createTempDirectory("epub-reader-position-remove").toFile())
        val target = scopedPosition(updatedAtMillis = 1L).copy(chapterIndex = 2, pageIndex = 3, pageCount = 8)
        val other = target.copy(bookId = "book-2", fileId = "file-2")
        val otherAccount = target.copy(accountScope = "account-b")
        assertTrue(store.importIfAbsent(target))
        assertTrue(store.importIfAbsent(other))
        assertTrue(store.importIfAbsent(otherAccount))

        store.removeForBook(scope(), target.bookId)

        assertNull(store.readExact(target))
        assertEquals(other, store.readExact(other))
        assertEquals(otherAccount, store.readExact(otherAccount))
    }

    @Test
    fun `new writer generation rejects delayed writes from an older reader`() = runBlocking {
        val store = EpubReaderPositionStore(Files.createTempDirectory("epub-reader-owner").toFile())
        val identity = scopedPosition(updatedAtMillis = 0L)
        val oldLease = store.claimWriter(identity, "old-reader")
        assertTrue(
            store.saveOwned(
                oldLease,
                sequence = 1L,
                position = identity.copy(pageIndex = 1, updatedAtMillis = 10L)
            )
        )

        val newLease = store.claimWriter(identity, "new-reader")
        assertTrue(
            store.saveOwned(
                newLease,
                sequence = 1L,
                position = identity.copy(pageIndex = 2, updatedAtMillis = 20L)
            )
        )
        assertFalse(
            store.saveOwned(
                oldLease,
                sequence = 2L,
                position = identity.copy(pageIndex = 3, updatedAtMillis = 30L)
            )
        )

        assertEquals(2, store.readExact(identity)?.pageIndex)
    }

    @Test
    fun `writer claim without a first save is not restorable after process death`() = runBlocking {
        val filesDir = Files.createTempDirectory("epub-reader-uncommitted-claim").toFile()
        val identity = scopedPosition(updatedAtMillis = 0L)
        EpubReaderPositionStore(filesDir).claimWriter(identity, "reader-before-death")

        val restartedStore = EpubReaderPositionStore(filesDir)
        assertNull(restartedStore.readExact(identity))
        assertTrue(restartedStore.readAll(scope()).isEmpty())

        val replacementLease = restartedStore.claimWriter(identity, "reader-after-death")
        assertTrue(
            restartedStore.saveOwned(
                replacementLease,
                sequence = 1L,
                position = identity.copy(
                    chapterIndex = 2,
                    pageIndex = 4,
                    pageCount = 8,
                    updatedAtMillis = 100L
                )
            )
        )
        assertEquals(2, restartedStore.readExact(identity)?.chapterIndex)
        assertEquals(4, restartedStore.readExact(identity)?.pageIndex)
        assertEquals(listOf(4), restartedStore.readAll(scope()).map { it.pageIndex })
    }

    @Test
    fun `rotation atomically commits bounded handoff and rejects an unflushed old activity`() = runBlocking {
        val store = EpubReaderPositionStore(Files.createTempDirectory("epub-reader-rotation").toFile())
        val identity = scopedPosition(updatedAtMillis = 0L)
        val beforeRotation = store.claimWriter(identity, "reader-session")
        val handoff = identity.copy(
            pageIndex = 4,
            updatedAtMillis = 10L,
            locatorJson = "{\"href\":\"chapter.xhtml\"}"
        )
        val afterRotation = store.claimWriter(
            identity,
            "rotated-activity",
            handoff,
            expectedResetEpoch = beforeRotation.resetEpoch
        )

        assertEquals(beforeRotation.generation + 1L, afterRotation.generation)
        assertEquals(0L, afterRotation.sequence)
        assertEquals(4, store.readExact(identity)?.pageIndex)
        assertFalse(
            store.saveOwned(
                beforeRotation,
                sequence = 1L,
                position = identity.copy(pageIndex = 9, updatedAtMillis = 19L)
            )
        )
        assertTrue(
            store.saveOwned(
                afterRotation,
                sequence = 1L,
                position = identity.copy(pageIndex = 2, updatedAtMillis = 20L)
            )
        )
        assertEquals(2, store.readExact(identity)?.pageIndex)
    }

    @Test
    fun `rotation handoff rejects an oversized locator snapshot`() = runBlocking {
        val store = EpubReaderPositionStore(Files.createTempDirectory("epub-reader-handoff-cap").toFile())
        val identity = scopedPosition(updatedAtMillis = 0L)

        assertTrue(
            runCatching {
                store.claimWriter(
                    identity = identity,
                    sessionId = "reader-session",
                    handoff = identity.copy(
                        updatedAtMillis = 1L,
                        locatorJson = "x".repeat(MAX_SAVED_READER_LOCATOR_BYTES + 1)
                    ),
                    expectedResetEpoch = 0L
                )
            }.isFailure
        )
        assertNull(store.readExact(identity))
    }

    @Test
    fun `clearing a book invalidates an outstanding writer lease`() = runBlocking {
        val store = EpubReaderPositionStore(Files.createTempDirectory("epub-reader-reset").toFile())
        val identity = scopedPosition(updatedAtMillis = 0L)
        val lease = store.claimWriter(identity, "reader-session")

        store.removeForBook(scope(), identity.bookId)

        assertFalse(
            store.saveOwned(
                lease,
                sequence = 1L,
                position = identity.copy(pageIndex = 3, updatedAtMillis = 30L)
            )
        )
        assertNull(store.readExact(identity))
    }

    @Test
    fun `reset then new writer rejects old owned and direct saves`() = runBlocking {
        val store = EpubReaderPositionStore(Files.createTempDirectory("epub-reader-reset-new").toFile())
        val identity = scopedPosition(updatedAtMillis = 0L)
        val old = store.claimWriter(identity, "old-reader")
        store.removeForBook(scope(), identity.bookId)
        val current = store.claimWriter(identity, "new-reader")

        assertFalse(store.saveOwned(old, 1L, identity.copy(pageIndex = 7, updatedAtMillis = 10L)))
        assertFalse(store.importIfAbsent(identity.copy(pageIndex = 8, updatedAtMillis = 20L)))
        assertNull(store.readExact(identity))
        assertTrue(store.saveOwned(current, 1L, identity.copy(pageIndex = 2, updatedAtMillis = 30L)))
        assertEquals(2, store.readExact(identity)?.pageIndex)
    }

    @Test
    fun `reset tombstone rejects a delayed rotation Bundle handoff`() = runBlocking {
        val store = EpubReaderPositionStore(Files.createTempDirectory("epub-reader-reset-bundle").toFile())
        val identity = scopedPosition(updatedAtMillis = 0L)
        val beforeReset = store.claimWriter(identity, "old-activity")
        val staleBundle = identity.copy(
            pageIndex = 7,
            updatedAtMillis = 50L,
            locatorJson = "{\"href\":\"stale.xhtml\"}"
        )

        store.removeForBook(scope(), identity.bookId)

        assertTrue(
            runCatching {
                store.claimWriter(
                    identity = identity,
                    sessionId = "delayed-recreation",
                    handoff = staleBundle,
                    expectedResetEpoch = beforeReset.resetEpoch
                )
            }.exceptionOrNull() is StaleEpubReaderResetException
        )
        assertNull(store.readExact(identity))

        val fresh = store.claimWriter(identity, "fresh-open")
        assertEquals(beforeReset.resetEpoch + 1L, fresh.resetEpoch)
        assertEquals(0, fresh.position.pageIndex)
        assertTrue(
            store.saveOwned(
                fresh,
                sequence = 1L,
                position = identity.copy(pageIndex = 3, updatedAtMillis = 200L)
            )
        )
        assertEquals(3, store.readExact(identity)?.pageIndex)
    }

    @Test
    fun `reset tombstone rejects delayed exact imports`() = runBlocking {
        val store = EpubReaderPositionStore(Files.createTempDirectory("epub-reader-reset-import").toFile())
        val identity = scopedPosition(updatedAtMillis = 0L)

        store.removeForBook(scope(), identity.bookId)

        assertFalse(
            store.importIfAbsent(identity.copy(pageIndex = 9, updatedAtMillis = 100L))
        )
        assertNull(store.readExact(identity))
    }

    @Test
    fun `writer lease cannot be reused for another publication`() = runBlocking {
        val store = EpubReaderPositionStore(Files.createTempDirectory("epub-reader-lease-scope").toFile())
        val identity = scopedPosition(updatedAtMillis = 0L)
        val other = identity.copy(bookId = "book-2")
        val lease = store.claimWriter(identity, "reader-session")
        store.claimWriter(other, "reader-session")

        assertFalse(store.saveOwned(lease, 1L, other.copy(updatedAtMillis = 10L)))
        assertNull(store.readExact(other))
    }

    @Test
    fun `exact keys do not collide when book and file ids contain delimiters`() = runBlocking {
        val store = EpubReaderPositionStore(Files.createTempDirectory("epub-reader-key-fields").toFile())
        val first = scopedPosition(1L).copy(bookId = "x|y", fileId = "z", pageIndex = 1)
        val second = scopedPosition(2L).copy(bookId = "x", fileId = "y|z", pageIndex = 2)

        assertTrue(store.importIfAbsent(first))
        assertTrue(store.importIfAbsent(second))

        assertEquals(1, store.readExact(first)?.pageIndex)
        assertEquals(2, store.readExact(second)?.pageIndex)
    }

    @Test
    fun `newer owned snapshot without a bounded locator clears stale locator`() = runBlocking {
        val store = EpubReaderPositionStore(Files.createTempDirectory("epub-reader-locator-clear").toFile())
        val identity = scopedPosition(0L)
        val lease = store.claimWriter(identity, "reader-session")
        assertTrue(
            store.saveOwned(
                lease,
                1L,
                identity.copy(
                    pageIndex = 1,
                    updatedAtMillis = 10L,
                    locatorJson = "{\"href\":\"old.xhtml\"}"
                )
            )
        )
        assertTrue(
            store.saveOwned(
                lease,
                2L,
                identity.copy(pageIndex = 2, updatedAtMillis = 20L, locatorJson = null)
            )
        )

        assertNull(store.readExact(identity)?.locatorJson)
        assertEquals(2, store.readExact(identity)?.pageIndex)
    }

    @Test
    fun `close flush waits for the newest owned snapshot`() = runBlocking {
        val store = EpubReaderPositionStore(Files.createTempDirectory("epub-reader-close").toFile())
        val identity = scopedPosition(updatedAtMillis = 0L)
        val lease = store.claimWriter(identity, "reader-session")

        coroutineScope {
            val pending = async {
                store.saveOwned(lease, 1L, identity.copy(pageIndex = 6, updatedAtMillis = 10L))
            }
            assertTrue(awaitEpubPositionCloseFlush { pending.await() })
        }

        assertEquals(6, store.readExact(identity)?.pageIndex)
    }

    @Test
    fun `rapid position updates coalesce while close flush persists the newest snapshot`() = runBlocking {
        val firstWriteStarted = CompletableDeferred<Unit>()
        val releaseFirstWrite = CompletableDeferred<Unit>()
        val writes = mutableListOf<Pair<Long, Int>>()
        val writer = LatestEpubPositionWriter<Int>(this) { sequence, snapshot ->
            writes += sequence to snapshot
            if (sequence == 1L) {
                firstWriteStarted.complete(Unit)
                releaseFirstWrite.await()
            }
            true
        }

        writer.submit(1L, 1)
        firstWriteStarted.await()
        (2..100).forEach { value -> writer.submit(value.toLong(), value) }
        writer.submit(50L, -1)
        releaseFirstWrite.complete(Unit)

        assertTrue(awaitEpubPositionCloseFlush { writer.flush() })
        assertEquals(listOf(1L to 1, 100L to 100), writes)
    }

    @Test
    fun `submit blocked at idle boundary starts a successor writer and flushes it`() = runBlocking {
        val idleLockHeld = CountDownLatch(1)
        val releaseIdleLock = CountDownLatch(1)
        val blockFirstIdleBoundary = AtomicBoolean(true)
        val writes = mutableListOf<Pair<Long, Int>>()
        val writerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        var submitter: Thread? = null
        val writer = LatestEpubPositionWriter<Int>(
            scope = writerScope,
            onIdleLockedForTest = {
                if (blockFirstIdleBoundary.compareAndSet(true, false)) {
                    idleLockHeld.countDown()
                    check(releaseIdleLock.await(5L, TimeUnit.SECONDS))
                }
            }
        ) { sequence, snapshot ->
            synchronized(writes) { writes += sequence to snapshot }
            true
        }

        try {
            writer.submit(1L, 1)
            assertTrue(idleLockHeld.await(5L, TimeUnit.SECONDS))
            submitter = Thread({ writer.submit(2L, 2) }, "epub-position-idle-submit").also { it.start() }
            val blockedDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5L)
            while (submitter.state != Thread.State.BLOCKED && System.nanoTime() < blockedDeadline) {
                Thread.sleep(1L)
            }
            assertEquals(Thread.State.BLOCKED, submitter.state)
            releaseIdleLock.countDown()
            submitter.join(5_000L)
            assertFalse(submitter.isAlive)

            assertTrue(awaitEpubPositionCloseFlush { writer.flush() })
            assertEquals(listOf(1L to 1, 2L to 2), synchronized(writes) { writes.toList() })
        } finally {
            releaseIdleLock.countDown()
            submitter?.join(5_000L)
            writerScope.cancel()
        }
    }

    @Test
    fun `scoped cleanup attempts every backing store and preserves first failure`() = runBlocking {
        val attempted = mutableListOf<String>()
        val failure = runCatching {
            runAllEpubResumeCleanups(
                listOf(
                    {
                        attempted += "positions"
                        throw IllegalStateException("positions")
                    },
                    { attempted += "locators" },
                    {
                        attempted += "tts"
                        throw IllegalArgumentException("tts")
                    }
                )
            )
        }.exceptionOrNull()

        assertEquals(listOf("positions", "locators", "tts"), attempted)
        assertEquals("positions", failure?.message)
    }

    private fun scopedPosition(updatedAtMillis: Long): EpubReaderPosition = EpubReaderPosition(
        serverUrl = "https://example.test/base",
        bookId = "book-1",
        fileId = "file-1",
        chapterIndex = 0,
        pageIndex = 0,
        pageCount = 1,
        updatedAtMillis = updatedAtMillis,
        profileId = "profile-a",
        providerId = "bookorbit",
        accountScope = "account-a",
        publicationFingerprint = "100:1"
    )

    private fun scope(serverUrl: String = "https://example.test/base") = EpubReaderScope(
        serverUrl = serverUrl,
        profileId = "profile-a",
        providerId = "bookorbit",
        accountScope = "account-a"
    )
}
