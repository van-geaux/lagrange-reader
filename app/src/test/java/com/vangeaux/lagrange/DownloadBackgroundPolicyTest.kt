package com.vangeaux.lagrange

import java.util.UUID
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadBackgroundPolicyTest {
    @Test
    fun `background policy allows wifi regardless of ask consent`() {
        assertTrue(
            backgroundDownloadMayStart(
                policy = CellularDownloadPolicy.ASK_FOR_CONFIRMATION,
                isCellularOrMetered = false,
                cellularConsentGranted = false
            )
        )
    }

    @Test
    fun `background policy requires consent for ask on metered networks`() {
        assertFalse(
            backgroundDownloadMayStart(
                policy = CellularDownloadPolicy.ASK_FOR_CONFIRMATION,
                isCellularOrMetered = true,
                cellularConsentGranted = false
            )
        )
        assertTrue(
            backgroundDownloadMayStart(
                policy = CellularDownloadPolicy.ASK_FOR_CONFIRMATION,
                isCellularOrMetered = true,
                cellularConsentGranted = true
            )
        )
    }

    @Test
    fun `background policy always and never remain explicit on metered networks`() {
        assertTrue(
            backgroundDownloadMayStart(
                policy = CellularDownloadPolicy.ALWAYS,
                isCellularOrMetered = true,
                cellularConsentGranted = false
            )
        )
        assertFalse(
            backgroundDownloadMayStart(
                policy = CellularDownloadPolicy.NEVER,
                isCellularOrMetered = true,
                cellularConsentGranted = true
            )
        )
    }

    @Test
    fun `unique work name separates servers accounts and files`() {
        assertNotEquals(
            downloadUniqueWorkName("https://one.example", "scope-a", "file-1"),
            downloadUniqueWorkName("https://two.example", "scope-a", "file-1")
        )
        assertNotEquals(
            downloadUniqueWorkName("https://one.example", "scope-a", "file-1"),
            downloadUniqueWorkName("https://one.example", "scope-a", "file-2")
        )
        assertNotEquals(
            downloadUniqueWorkName("https://one.example", "scope-a", "file-1"),
            downloadUniqueWorkName("https://one.example", "scope-b", "file-1")
        )
        assertTrue(
            downloadUniqueWorkName("https://one.example", "scope-a", "file-1")
                .contains("bookorbit-download")
        )
    }

    @Test
    fun `request tags distinguish replacement ownership`() {
        val first = downloadRequestTag("request-1")
        val second = downloadRequestTag("request-2")

        assertNotEquals(first, second)
        assertTrue(downloadWorkTagsMatchRequest(setOf(first), "request-1"))
        assertFalse(downloadWorkTagsMatchRequest(setOf(second), "request-1"))
        assertTrue(downloadWorkTagsMatchRequest(emptySet(), ""))
    }

    @Test
    fun `active work owns only its exact durable queue request`() {
        val queued = mapOf("file-1" to "request-new")

        assertTrue(
            downloadWorkTagsOwnQueuedRequest(
                setOf(downloadFileTag("file-1"), downloadRequestTag("request-new")),
                queued
            )
        )
        assertFalse(
            downloadWorkTagsOwnQueuedRequest(
                setOf(downloadFileTag("file-1"), downloadRequestTag("request-old")),
                queued
            )
        )
        assertFalse(
            downloadWorkTagsOwnQueuedRequest(
                setOf(downloadFileTag("file-2"), downloadRequestTag("request-new")),
                queued
            )
        )
    }

    @Test
    fun `legacy blank queue owner matches only untagged work`() {
        val queued = mapOf("legacy-file" to "")

        assertTrue(
            downloadWorkTagsOwnQueuedRequest(
                setOf(downloadFileTag("legacy-file")),
                queued
            )
        )
        assertFalse(
            downloadWorkTagsOwnQueuedRequest(
                setOf(downloadFileTag("legacy-file"), downloadRequestTag("new-request")),
                queued
            )
        )
    }

    @Test
    fun `callbacks for replaced same-file work have distinct ownership keys`() {
        val old = WorkManagerDownloadScheduler.DownloadCallbackKey(
            "https://example.test",
            "account-scope",
            "same-file",
            "old-request"
        )
        val replacement = WorkManagerDownloadScheduler.DownloadCallbackKey(
            "https://example.test",
            "account-scope",
            "same-file",
            "new-request"
        )

        assertNotEquals(old, replacement)
    }

    @Test
    fun `policy generation tags preserve newer work during delayed cleanup`() {
        val old = setOf(downloadPolicyGenerationTag(4))
        val replacement = setOf(downloadPolicyGenerationTag(5))

        assertEquals(4L, downloadPolicyGenerationFromTags(old))
        assertEquals(5L, downloadPolicyGenerationFromTags(replacement))
        assertEquals(null, downloadPolicyGenerationFromTags(emptySet()))
        assertTrue(downloadWorkMayBeCancelledThroughGeneration(old, 4))
        assertFalse(downloadWorkMayBeCancelledThroughGeneration(replacement, 4))
        assertTrue(downloadWorkMayBeCancelledThroughGeneration(emptySet(), 4))
    }

    @Test
    fun `automatic completion always rechecks active generation capacity`() {
        val current = AutomaticDownloadPolicy(
            profileId = "profile-1",
            serverUrl = "https://example.test",
            enabled = true,
            automaticRemovalEnabled = false,
            generation = 7
        )

        assertTrue(
            shouldEnforcePostDownloadCapacity(DownloadOrigin.AUTOMATIC, 7, current)
        )
        assertFalse(
            shouldEnforcePostDownloadCapacity(DownloadOrigin.AUTOMATIC, 6, current)
        )
        assertFalse(
            shouldEnforcePostDownloadCapacity(DownloadOrigin.MANUAL, null, current)
        )
        assertTrue(
            shouldEnforcePostDownloadCapacity(
                DownloadOrigin.MANUAL,
                null,
                current.copy(automaticRemovalEnabled = true)
            )
        )
    }

    @Test
    fun `same-file successor replaces its retiring stale work`() {
        assertEquals(
            androidx.work.ExistingWorkPolicy.REPLACE,
            downloadExistingWorkPolicy(
                DownloadOrigin.AUTOMATIC,
                replacingRetiringOwner = true
            )
        )
        assertEquals(
            androidx.work.ExistingWorkPolicy.KEEP,
            downloadExistingWorkPolicy(
                DownloadOrigin.AUTOMATIC,
                replacingRetiringOwner = false
            )
        )
    }

    @Test
    fun `download queue sequence remains monotonic within the same millisecond`() {
        val first = nextDownloadQueueSequence(nowMillis = 100L, previous = 0L)
        val second = nextDownloadQueueSequence(nowMillis = 100L, previous = first)
        val later = nextDownloadQueueSequence(nowMillis = 101L, previous = second)

        assertEquals(100_000L, first)
        assertEquals(100_001L, second)
        assertEquals(101_000L, later)
    }

    @Test
    fun `progress throttler emits bounded changes and terminal completion`() {
        val throttler = DownloadProgressThrottler(minIntervalMillis = 500L)

        assertEquals(true, throttler.shouldEmit(progress = 0.10f, nowMillis = 0L))
        assertEquals(false, throttler.shouldEmit(progress = 0.11f, nowMillis = 100L))
        assertEquals(true, throttler.shouldEmit(progress = 0.12f, nowMillis = 500L))
        assertEquals(false, throttler.shouldEmit(progress = 0.12f, nowMillis = 1_000L))
        assertEquals(true, throttler.shouldEmit(progress = 1f, nowMillis = 1_100L))
    }

    @Test
    fun `observer registry deduplicates and retires work ids`() {
        val registry = DownloadObserverRegistry()
        val workId = UUID.randomUUID()

        assertEquals(true, registry.register(workId))
        assertEquals(false, registry.register(workId))
        registry.retire(workId)
        assertEquals(true, registry.register(workId))
    }

    @Test
    fun `notification identity is stable and per file`() {
        assertEquals(downloadNotificationId("file-1"), downloadNotificationId("file-1"))
        assertNotEquals(downloadNotificationId("file-1"), downloadNotificationId("file-2"))
    }

    @Test
    fun `notification content policy preserves title and progress presentation`() {
        assertEquals("Downloading My Book", downloadNotificationTitle("My Book"))
        assertEquals(DownloadNotificationProgress.Indeterminate, downloadNotificationProgress(null))
        assertEquals(
            DownloadNotificationProgress.Determinate(37),
            downloadNotificationProgress(0.375f)
        )
        assertEquals(
            DownloadNotificationProgress.Determinate(100),
            downloadNotificationProgress(2f)
        )
    }

    @Test
    fun `notification progress clamps to a visible percentage`() {
        assertEquals(0, (downloadNotificationProgress(-1f) as DownloadNotificationProgress.Determinate).percent)
        assertEquals(42, (downloadNotificationProgress(0.425f) as DownloadNotificationProgress.Determinate).percent)
        assertEquals(100, (downloadNotificationProgress(2f) as DownloadNotificationProgress.Determinate).percent)
    }

    @Test
    fun `cancel action uses the existing per-file unique work identity`() {
        val serverUrl = "https://example.test"
        val storageScopeId = "account-scope"
        val fileId = "file-1"
        assertEquals(
            "bookorbit-download:$serverUrl:$storageScopeId:$fileId",
            downloadUniqueWorkName(serverUrl, storageScopeId, fileId)
        )
        assertEquals("com.vangeaux.lagrange.CANCEL_DOWNLOAD", DOWNLOAD_NOTIFICATION_CANCEL_ACTION)
    }

    @Test
    fun `server failures that can recover are retried`() {
        assertTrue(isRetryableDownloadHttpCode(408))
        assertTrue(isRetryableDownloadHttpCode(429))
        assertTrue(isRetryableDownloadHttpCode(500))
        assertTrue(isRetryableDownloadHttpCode(503))
        assertFalse(isRetryableDownloadHttpCode(400))
        assertFalse(isRetryableDownloadHttpCode(403))
    }
    @Test
    fun `persisted attempt restores active download identity`() {
        val book = downloadAttemptBookSummary(
            DownloadAttempt(
                serverUrl = "https://example.test",
                fileId = "file-413921",
                bookId = "book-413921",
                title = "Original title",
                targetPath = "/tmp/original.epub",
                mediaKind = MediaKind.EPUB,
                mimeType = "epub",
                sourceUpdatedAtMillis = 42L
            )
        )

        assertEquals("file-413921", book.fileId)
        assertEquals("book-413921", book.id)
        assertEquals("Original title", book.title)
        assertEquals(MediaKind.EPUB, book.mediaKind)
        assertEquals(42L, book.updatedAtMillis)
    }
}
