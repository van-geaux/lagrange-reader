package com.vangeaux.lagrange

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomaticDownloadsTest {
    @Test
    fun `policy defaults are conservative`() {
        val policy = AutomaticDownloadPolicy("profile", "https://example.test")

        assertFalse(policy.enabled)
        assertFalse(policy.allReadableCopies)
        assertTrue(policy.unmeteredOnly)
        assertTrue(policy.chargingRequired)
        assertFalse(policy.automaticRemovalEnabled)
        assertEquals(AUTOMATIC_DOWNLOAD_DEFAULT_RESERVE_BYTES, policy.reserveBytes)
        assertEquals(AUTOMATIC_DOWNLOAD_DEFAULT_MAXIMUM_BYTES, policy.maximumBytes)
    }

    @Test
    fun `case sensitive authenticated principals remain distinct`() {
        assertFalse(
            authenticatedAccountScope("bookorbit", "User-A") ==
                authenticatedAccountScope("bookorbit", "user-a")
        )
    }

    @Test
    fun `reader lease blocks only its exact replacement target or scoped file`() {
        val pathLease = LocalBookReaderLease(
            ownerId = "tts",
            serverUrl = "",
            storageScopeId = null,
            fileId = "file-1",
            localPath = "build/reader/book.epub"
        )
        assertTrue(
            localReaderLeaseBlocksReplacement(
                pathLease,
                "https://example.test",
                "scope-a",
                "file-1",
                "build/reader/./book.epub"
            )
        )

        val scopedLease = pathLease.copy(
            serverUrl = "https://example.test/",
            storageScopeId = "scope-a",
            localPath = null
        )
        assertTrue(
            localReaderLeaseBlocksReplacement(
                scopedLease,
                "https://example.test",
                "scope-a",
                "file-1",
                "build/reader/other.epub"
            )
        )
        assertFalse(
            localReaderLeaseBlocksReplacement(
                scopedLease,
                "https://example.test",
                "scope-b",
                "file-1",
                "build/reader/other.epub"
            )
        )
    }

    @Test
    fun `reader release wakes an old verified replacement regardless of retry age`() {
        val attempt = DownloadAttempt(
            serverUrl = "https://example.test",
            requestId = "request-1",
            profileId = "profile-1",
            storageScopeId = "scope-a",
            fileId = "file-1",
            bookId = "book-1",
            libraryId = "library-1",
            title = "Book",
            targetPath = "build/reader/book.epub",
            mediaKind = MediaKind.EPUB,
            startedAtMillis = 1L,
            state = DownloadAttemptState.VERIFIED
        )
        val lease = LocalBookReaderLease(
            ownerId = "tts",
            serverUrl = "",
            storageScopeId = null,
            fileId = "file-1",
            localPath = "build/reader/./book.epub"
        )

        assertEquals(
            setOf(
                VerifiedReplacementWakeTarget(
                    "https://example.test",
                    "scope-a",
                    "file-1"
                )
            ),
            verifiedReplacementWakeTargets(listOf(attempt), listOf(lease))
        )
        assertTrue(
            verifiedReplacementWakeTargets(
                listOf(attempt.copy(state = DownloadAttemptState.STAGING)),
                listOf(lease)
            ).isEmpty()
        )
    }

    @Test
    fun `storage admission enforces both maximum and reserve`() {
        val byMaximum = automaticStorageAdmission(
            currentDownloadedBytes = 9_000,
            availableBytes = 50_000,
            incomingFinalBytes = 2_000,
            incomingPeakBytes = 2_000,
            maximumBytes = 10_000,
            reserveBytes = 1_000
        )
        assertEquals(1_000, byMaximum.requiredReclaimBytes)
        assertEquals(1_000, byMaximum.maximumExceededByBytes)

        val byReserve = automaticStorageAdmission(
            currentDownloadedBytes = 1_000,
            availableBytes = 2_500,
            incomingFinalBytes = 500,
            incomingPeakBytes = 2_000,
            maximumBytes = 10_000,
            reserveBytes = 1_000
        )
        assertEquals(500, byReserve.requiredReclaimBytes)
        assertEquals(500, byReserve.reserveShortfallBytes)

        val disabled = automaticStorageAdmission(
            currentDownloadedBytes = Long.MAX_VALUE / 2,
            availableBytes = 1,
            incomingFinalBytes = 10_000,
            incomingPeakBytes = 10_000,
            maximumBytes = 0,
            reserveBytes = 0
        )
        assertEquals(0, disabled.requiredReclaimBytes)
    }

    @Test
    fun `eviction prefers completed then least recently accessed whole books`() {
        val completedOld = candidate("complete-old", true, 10, 100, 200)
        val completedRecent = candidate("complete-new", true, 30, 100, 300)
        val incompleteOld = candidate("incomplete", false, 1, 100, 100)

        val selected = selectAutomaticEvictions(
            listOf(incompleteOld, completedRecent, completedOld),
            requiredBytes = 250
        )

        assertEquals(
            listOf("complete-old", "complete-new", "incomplete"),
            selected.map { it.bookId }
        )
    }

    @Test
    fun `impossible reclaim and individually oversized files do not start eviction`() {
        val plan = listOf(candidate("only-old-book", true, 1, 200, 1))
        assertFalse(evictionPlanCanSatisfy(plan, requiredBytes = 500))
        assertTrue(evictionPlanCanSatisfy(plan, requiredBytes = 200))

        val capped = AutomaticDownloadPolicy(
            profileId = "profile",
            serverUrl = "https://example.test",
            enabled = true,
            maximumBytes = 1_000,
            automaticRemovalEnabled = true
        )
        assertTrue(downloadIsIndividuallyOverCap(1_001, DownloadOrigin.AUTOMATIC, capped))
        assertFalse(downloadIsIndividuallyOverCap(1_001, DownloadOrigin.MANUAL, capped))
        assertFalse(
            downloadIsIndividuallyOverCap(
                1_001,
                DownloadOrigin.MANUAL,
                capped.copy(enabled = false, automaticRemovalEnabled = false)
            )
        )
        assertTrue(automaticStorageLimitsApply(DownloadOrigin.AUTOMATIC))
        assertFalse(automaticStorageLimitsApply(DownloadOrigin.MANUAL))
    }

    @Test
    fun `eviction stops when enablement removal or generation changes`() {
        val expected = LocalBookStoragePolicy(
            automaticRemovalEnabled = true,
            generation = 9
        )

        assertTrue(evictionPolicyIsStillCurrent(expected, expected))
        assertFalse(
            evictionPolicyIsStillCurrent(
                expected,
                expected.copy(automaticRemovalEnabled = false)
            )
        )
        assertFalse(evictionPolicyIsStillCurrent(expected, expected.copy(generation = 10)))
    }

    @Test
    fun `managed staging bytes count against maximum and reserve`() {
        val policy = LocalBookStoragePolicy(
            maximumBytes = 1_000,
            reserveBytes = 200,
            generation = 3
        )

        val allowance = downloadStorageAllowance(
            usage = StorageUsage(downloadedBytes = 750, availableBytes = 350),
            policy = policy
        )

        assertEquals(150L, allowance.maximumAdditionalBytes)
        assertTrue(allowance.permits(100, 50))
        assertFalse(allowance.permits(100, 51))
        assertEquals(3L, allowance.storageGeneration)
    }

    @Test
    fun `concurrent transfer reservations share the remaining headroom`() {
        val policy = LocalBookStoragePolicy(maximumBytes = 1_000, reserveBytes = 100)
        val usage = StorageUsage(downloadedBytes = 600, availableBytes = 1_000)

        val first = reservedDownloadStorageAllowance(
            usage = usage,
            policy = policy,
            otherReservedBytes = 0,
            requestedBytes = 250
        )
        val second = reservedDownloadStorageAllowance(
            usage = usage,
            policy = policy,
            otherReservedBytes = first.maximumAdditionalBytes,
            requestedBytes = null
        )

        assertEquals(250L, first.maximumAdditionalBytes)
        assertEquals(150L, second.maximumAdditionalBytes)
        assertFalse(second.permits(150, 1))
    }

    @Test
    fun `unknown size candidates remain eligible for bounded streaming`() {
        val epub = book("epub", MediaKind.EPUB, "epub")
        val detail = BookDetailInfo(
            book = epub,
            availableFiles = listOf(BookFileOption(epub, sizeBytes = null))
        )

        val option = automaticDownloadOptions(detail, epub, allReadableCopies = false).single()
        assertNull(option.sizeBytes)
        assertEquals(null, AutomaticDownloadCandidate(option.book, option.sizeBytes).expectedSizeBytes)
    }

    @Test
    fun `unknown transfer accepts a partial final window near the maximum`() {
        val allowance = reservedDownloadStorageAllowance(
            usage = StorageUsage(downloadedBytes = 16L, availableBytes = 100L),
            policy = LocalBookStoragePolicy(maximumBytes = 20L, reserveBytes = 0L),
            otherReservedBytes = 0L,
            requestedBytes = UNKNOWN_DOWNLOAD_RESERVATION_WINDOW_BYTES
        )

        assertEquals(4L, allowance.maximumAdditionalBytes)
        assertTrue(allowance.permits(bytesWritten = 0L, nextChunkBytes = 4))
        assertFalse(allowance.permits(bytesWritten = 0L, nextChunkBytes = 5))
    }

    @Test
    fun `unknown transfer can start when maximum is smaller than a reservation window`() {
        val maximum = 20L * 1024L * 1024L
        val allowance = reservedDownloadStorageAllowance(
            usage = StorageUsage(downloadedBytes = 0L, availableBytes = Long.MAX_VALUE),
            policy = LocalBookStoragePolicy(maximumBytes = maximum, reserveBytes = 0L),
            otherReservedBytes = 0L,
            requestedBytes = UNKNOWN_DOWNLOAD_RESERVATION_WINDOW_BYTES
        )

        assertEquals(maximum, allowance.maximumAdditionalBytes)
        assertTrue(allowance.permits(0L, 18 * 1024 * 1024))
    }

    @Test
    fun `aggregate scan repeats partial pages and completes only after the last library`() {
        val partial = automaticScanAdvance(
            currentPage = 3,
            currentLibraryIndex = 0,
            libraryCount = 2,
            pageFullyExamined = false,
            lastPage = false
        )
        assertEquals(3, partial.nextPage)
        assertEquals(0, partial.nextLibraryIndex)
        assertFalse(partial.cycleComplete)

        val firstLibraryDone = automaticScanAdvance(3, 0, 2, true, true)
        assertEquals(0, firstLibraryDone.nextPage)
        assertEquals(1, firstLibraryDone.nextLibraryIndex)
        assertFalse(firstLibraryDone.cycleComplete)

        val fullCycleDone = automaticScanAdvance(1, 1, 2, true, true)
        assertEquals(0, fullCycleDone.nextPage)
        assertEquals(0, fullCycleDone.nextLibraryIndex)
        assertTrue(fullCycleDone.cycleComplete)
    }

    @Test
    fun `scan cursor advances only across items whose queue writes persisted`() {
        val candidateIdsByItem = listOf(
            emptySet(),
            setOf("file-a"),
            setOf("file-b", "file-c"),
            emptySet()
        )

        assertEquals(
            2,
            automaticDurablyExaminedItemCount(candidateIdsByItem, setOf("file-a", "file-b"))
        )
        assertEquals(7, automaticScanNextItemIndex(5, 2, false, 10))
        assertEquals(0, automaticScanNextItemIndex(5, 2, true, 10))
    }

    @Test
    fun `late queue attempt or reader activity protects an eviction group`() {
        val group = setOf("part-1", "part-2")

        assertTrue(downloadFileGroupIsProtected(group, setOf("part-1"), emptySet(), null))
        assertTrue(downloadFileGroupIsProtected(group, emptySet(), setOf("part-2"), null))
        assertTrue(downloadFileGroupIsProtected(group, emptySet(), emptySet(), "part-1"))
        assertFalse(downloadFileGroupIsProtected(group, emptySet(), emptySet(), null))
    }

    @Test
    fun `multipart records are one logical clear and eviction group`() {
        fun part(fileId: String) = DownloadRecord(
            serverUrl = "https://example.test",
            fileId = fileId,
            bookId = "audiobook-1",
            title = "Audiobook",
            localPath = "/managed/$fileId.mp3",
            mediaKind = MediaKind.AUDIO
        )
        val grouped = groupDownloadedRecordsByBook(listOf(part("part-1"), part("part-2")))
        val fileIds = grouped.getValue("audiobook-1").mapTo(mutableSetOf()) { it.fileId }

        assertEquals(setOf("part-1", "part-2"), fileIds)
        assertTrue(
            downloadFileGroupIsProtected(
                fileIds,
                emptySet(),
                emptySet(),
                activeFileId = "part-1"
            )
        )
    }

    @Test
    fun `preferred mode chooses one readable copy while all copies includes alternates`() {
        val epub = book("epub", MediaKind.EPUB, "epub")
        val pdf = book("pdf", MediaKind.PDF, "pdf")
        val unknown = book("unknown", MediaKind.UNKNOWN, "bin")
        val detail = BookDetailInfo(
            book = epub,
            availableFiles = listOf(
                BookFileOption(epub, sizeBytes = 10L),
                BookFileOption(pdf, sizeBytes = 20L),
                BookFileOption(unknown, sizeBytes = 30L)
            )
        )

        assertEquals(
            listOf("epub"),
            automaticDownloadOptions(detail, epub, allReadableCopies = false)
                .mapNotNull { it.fileId }
        )
        assertEquals(
            listOf("epub", "pdf"),
            automaticDownloadOptions(detail, epub, allReadableCopies = true)
                .mapNotNull { it.fileId }
        )
    }

    @Test
    fun `preferred audiobook mode keeps all downloadable parts`() {
        val first = book("audio-1", MediaKind.AUDIO, "mp3")
        val second = book("audio-2", MediaKind.AUDIO, "mp3")
        val detail = BookDetailInfo(
            book = first,
            availableFiles = listOf(
                BookFileOption(first, filename = "01.mp3", sizeBytes = 10L),
                BookFileOption(second, filename = "02.mp3", sizeBytes = 20L)
            )
        )

        assertEquals(
            listOf("audio-1", "audio-2"),
            automaticDownloadOptions(detail, first, allReadableCopies = false)
                .mapNotNull { it.fileId }
        )
    }

    @Test
    fun `stale cleanup compares queue entries with the current policy generation`() {
        val current = AutomaticDownloadPolicy(
            profileId = "profile",
            serverUrl = "https://example.test",
            enabled = true,
            generation = 5
        )
        val currentEntry = queueEntry(profileId = "profile", generation = 5)
        val oldEntry = queueEntry(profileId = "profile", generation = 4)

        assertFalse(automaticQueueEntryIsStale(currentEntry, current))
        assertTrue(automaticQueueEntryIsStale(oldEntry, current))
        assertFalse(
            automaticQueueEntryIsStale(
                oldEntry.copy(storageScopeId = "account-a"),
                current.copy(storageScopeId = "account-b")
            )
        )
    }

    private fun candidate(
        id: String,
        completed: Boolean,
        accessed: Long,
        size: Long,
        downloaded: Long
    ) = AutomaticEvictionCandidate(
        bookId = id,
        fileIds = setOf("$id-file"),
        sizeBytes = size,
        completed = completed,
        lastAccessedAtMillis = accessed,
        downloadedAtMillis = downloaded
    )

    private fun book(fileId: String, kind: MediaKind, format: String) = BookSummary(
        libraryId = "library-1",
        id = "book-1",
        fileId = fileId,
        title = "Book",
        filename = "book.$format",
        format = format,
        mediaKind = kind
    )

    private fun queueEntry(profileId: String, generation: Long) = DownloadQueueEntry(
        serverUrl = "https://example.test",
        profileId = profileId,
        fileId = "file",
        bookId = "book",
        libraryId = "library",
        title = "Book",
        mediaKind = MediaKind.EPUB,
        origin = DownloadOrigin.AUTOMATIC,
        requiresUnmeteredNetwork = true,
        requiresCharging = true,
        policyGeneration = generation,
        cellularConsentGranted = false,
        sequence = 1
    )
}
