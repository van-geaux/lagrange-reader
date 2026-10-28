package com.vangeaux.lagrange

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomaticDownloadAdmissionTest {
    @Test
    fun `policy disabled after planning cannot publish queue work`() = runBlocking {
        val expected = policy()
        var current = expected
        val reachedAdmission = CompletableDeferred<Unit>()
        val resumeAdmission = CompletableDeferred<Unit>()
        var persistCalls = 0
        val task = async {
            admitAutomaticDownloadRequests(
                expectedPolicy = expected,
                requests = listOf(request()),
                beforeAtomicAdmission = {
                    reachedAdmission.complete(Unit)
                    resumeAdmission.await()
                },
                readPolicy = { current },
                currentOwner = { expected.ownerKey() },
                persist = {
                    persistCalls += 1
                    it.map { request -> request.first }
                },
                rollback = {}
            )
        }

        reachedAdmission.await()
        current = expected.copy(enabled = false, generation = expected.generation + 1L)
        resumeAdmission.complete(Unit)

        assertTrue(task.await().isEmpty())
        assertEquals(0, persistCalls)
    }

    @Test
    fun `account switch during durable admission rolls back accepted requests`() = runBlocking {
        val expected = policy()
        var liveOwner = expected.ownerKey()
        val rolledBack = mutableListOf<String>()

        val accepted = admitAutomaticDownloadRequests(
            expectedPolicy = expected,
            requests = listOf(request()),
            readPolicy = { expected },
            currentOwner = { liveOwner },
            persist = { requests ->
                liveOwner = liveOwner.copy(storageScopeId = "account-b")
                requests.map { request -> request.first }
            },
            rollback = { rolledBack += it.requestId }
        )

        assertTrue(accepted.isEmpty())
        assertEquals(listOf("request"), rolledBack)
    }

    @Test
    fun `current policy and owner admit the durable request`() = runBlocking {
        val expected = policy()
        var rollbackCalls = 0

        val accepted = admitAutomaticDownloadRequests(
            expectedPolicy = expected,
            requests = listOf(request()),
            readPolicy = { expected },
            currentOwner = { expected.ownerKey() },
            persist = { requests -> requests.map { request -> request.first } },
            rollback = { rollbackCalls += 1 }
        )

        assertEquals(listOf("request"), accepted.map { it.requestId })
        assertEquals(0, rollbackCalls)
    }

    private fun policy() = AutomaticDownloadPolicy(
        profileId = "profile-a",
        serverUrl = "https://books.example.test",
        storageScopeId = "account-a",
        enabled = true,
        generation = 7L
    )

    private fun request(): Pair<DownloadQueueEntry, DownloadAttempt> {
        val entry = DownloadQueueEntry(
            serverUrl = "https://books.example.test",
            requestId = "request",
            profileId = "profile-a",
            storageScopeId = "account-a",
            fileId = "file",
            bookId = "book",
            libraryId = "library",
            title = "Book",
            mediaKind = MediaKind.EPUB,
            origin = DownloadOrigin.AUTOMATIC,
            policyGeneration = 7L,
            cellularConsentGranted = false,
            sequence = 1L
        )
        val attempt = DownloadAttempt(
            serverUrl = entry.serverUrl,
            requestId = entry.requestId,
            profileId = entry.profileId,
            storageScopeId = entry.storageScopeId,
            fileId = entry.fileId,
            bookId = entry.bookId,
            libraryId = entry.libraryId,
            title = entry.title,
            targetPath = "build/downloads/book.epub",
            mediaKind = entry.mediaKind,
            origin = entry.origin,
            policyGeneration = entry.policyGeneration
        )
        return entry to attempt
    }
}
