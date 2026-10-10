package com.vangeaux.lagrange

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadStoreTest {
    @Test
    fun `readAll prunes records whose files are missing`() = runBlocking {
        val filesDir = Files.createTempDirectory("download-store-test").toFile()
        val store = DownloadStore(filesDir)
        val existingFile = File(filesDir, "downloads/existing.epub").apply {
            parentFile?.mkdirs()
            writeText("ok")
        }
        File(filesDir, "downloads.json").writeText(
            """
            [
              {
                "serverUrl":"https://example.test",
                "storageScopeId":"test-default-scope",
                "fileId":"keep",
                "bookId":"book-1",
                "title":"Keep",
                "localPath":"${existingFile.absolutePath.replace("\\", "\\\\")}",
                "mediaKind":"EPUB",
                "mimeType":"application/epub+zip",
                "downloadedAtMillis":1
              },
              {
                "serverUrl":"https://example.test",
                "storageScopeId":"test-default-scope",
                "fileId":"drop",
                "bookId":"book-2",
                "title":"Drop",
                "localPath":"${File(filesDir, "downloads/missing.epub").absolutePath.replace("\\", "\\\\")}",
                "mediaKind":"EPUB",
                "mimeType":"application/epub+zip",
                "downloadedAtMillis":2
              }
            ]
            """.trimIndent()
        )

        val records = store.readAll("https://example.test")

        assertEquals(listOf("keep"), records.map { it.fileId })
        assertNotNull(store.find("https://example.test", "keep"))
        assertNull(store.find("https://example.test", "drop"))
    }

    @Test
    fun `downloadTarget keeps the more specific extension from format hints`() {
        val filesDir = Files.createTempDirectory("download-store-target").toFile()
        val store = DownloadStore(filesDir)

        val target = store.downloadTarget(
            serverUrl = "https://example.test",
            fileId = "123",
            title = "Example Audio",
            mediaKind = MediaKind.AUDIO,
            formatHint = "audio/x-m4b"
        )

        assertTrue(target.name.startsWith("Example_Audio-"))
        assertTrue(target.name.endsWith(".m4b"))
    }

    @Test
    fun `downloadTarget preserves all BookOrbit comic archive extensions`() {
        val filesDir = Files.createTempDirectory("download-store-comic-target").toFile()
        val store = DownloadStore(filesDir)

        assertTrue(
            store.downloadTarget("https://example.test", "1", "Issue", MediaKind.COMIC, "cbz")
                .name.endsWith(".cbz")
        )
        assertTrue(
            store.downloadTarget("https://example.test", "2", "Issue", MediaKind.COMIC, "cbr")
                .name.endsWith(".cbr")
        )
        assertTrue(
            store.downloadTarget("https://example.test", "3", "Issue", MediaKind.COMIC, "cb7")
                .name.endsWith(".cb7")
        )
    }

    @Test
    fun `same server downloads are isolated by authenticated account scope`() = runBlocking {
        val filesDir = Files.createTempDirectory("download-store-account-scope").toFile()
        var accountScope = "account-a"
        val store = DownloadStore(filesDir) { accountScope }
        val server = "https://example.test"
        val targetA = store.downloadTarget(server, "shared", "Book", MediaKind.EPUB, "epub")
            .apply {
                parentFile?.mkdirs()
                writeText("account a")
            }
        store.save(
            DownloadRecord(
                serverUrl = server,
                profileId = "profile-1",
                fileId = "shared",
                bookId = "book-a",
                title = "Book",
                localPath = targetA.absolutePath,
                mediaKind = MediaKind.EPUB
            )
        )

        accountScope = "account-b"
        val targetB = store.downloadTarget(server, "shared", "Book", MediaKind.EPUB, "epub")
        assertNull(store.find(server, "shared"))
        assertFalse(targetA.parentFile == targetB.parentFile)
        targetB.parentFile?.mkdirs()
        targetB.writeText("account b")
        store.save(
            DownloadRecord(
                serverUrl = server,
                profileId = "profile-1",
                fileId = "shared",
                bookId = "book-b",
                title = "Book",
                localPath = targetB.absolutePath,
                mediaKind = MediaKind.EPUB
            )
        )
        assertEquals("book-b", store.find(server, "shared")?.bookId)

        accountScope = "account-a"
        assertEquals("book-a", store.find(server, "shared")?.bookId)
        assertEquals(2, store.readAll().count { it.fileId == "shared" })
        assertEquals(listOf("book-a"), store.readAll(storageScopeId = "account-a").map { it.bookId })
        assertEquals(listOf("book-b"), store.readAll(storageScopeId = "account-b").map { it.bookId })
    }

    @Test
    fun `same server queue ownership is isolated by authenticated account scope`() = runBlocking {
        val filesDir = Files.createTempDirectory("download-store-queue-account-scope").toFile()
        val store = DownloadStore(filesDir) { "account-a" }
        val base = DownloadQueueEntry(
            serverUrl = "https://example.test",
            requestId = "request-a",
            profileId = "profile-1",
            storageScopeId = "account-a",
            fileId = "shared",
            bookId = "book-a",
            libraryId = "library",
            title = "Account A",
            mediaKind = MediaKind.EPUB,
            cellularConsentGranted = false,
            sequence = 1L
        )
        val other = base.copy(
            requestId = "request-b",
            storageScopeId = "account-b",
            bookId = "book-b",
            title = "Account B",
            sequence = 2L
        )
        store.enqueueDownloads(listOf(base, other))

        assertEquals("request-a", store.readDownloadQueue(base.serverUrl, "account-a").single().requestId)
        assertEquals("request-b", store.readDownloadQueue(base.serverUrl, "account-b").single().requestId)
        assertFalse(
            store.cancelDownloadRequestIfOwned(
                base.serverUrl,
                base.fileId,
                base.requestId,
                storageScopeId = "account-b"
            )
        )
        assertTrue(
            store.cancelDownloadRequestIfOwned(
                base.serverUrl,
                base.fileId,
                base.requestId,
                storageScopeId = "account-a"
            )
        )
        assertTrue(store.readDownloadQueue(base.serverUrl, "account-a").isEmpty())
        assertEquals("request-b", store.readDownloadQueue(base.serverUrl, "account-b").single().requestId)
    }

    @Test
    fun `runtime mutations reject an unresolved authenticated account scope`() = runBlocking {
        val filesDir = Files.createTempDirectory("download-store-unresolved-scope").toFile()
        val store = DownloadStore(filesDir) { "unresolved-account" }
        val entry = DownloadQueueEntry(
            serverUrl = "https://example.test",
            requestId = "request",
            profileId = "profile",
            fileId = "file",
            bookId = "book",
            libraryId = "library",
            title = "Book",
            mediaKind = MediaKind.EPUB,
            cellularConsentGranted = false,
            sequence = 1L
        )

        val failure = runCatching { store.enqueueDownload(entry) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertEquals(emptyList<DownloadRecord>(), store.readAll(entry.serverUrl))
        assertNull(store.find(entry.serverUrl, entry.fileId))
        assertEquals(emptyList<DownloadAttempt>(), store.readAttempts(entry.serverUrl))
        assertNull(store.findAttempt(entry.serverUrl, entry.fileId))
        assertEquals(emptyList<DownloadQueueEntry>(), store.readDownloadQueue(entry.serverUrl))
        assertTrue(store.readDownloadQueue().isEmpty())
    }

    @Test
    fun `unresolved account reads neither expose nor rewrite another account state`() = runBlocking {
        val filesDir = Files.createTempDirectory("download-store-null-scope").toFile()
        val server = "https://example.test"
        val foreignFile = File(filesDir, "downloads/missing-foreign.epub")
        val ownerStore = DownloadStore(filesDir) { "account-a" }
        ownerStore.save(
            DownloadRecord(
                serverUrl = server,
                storageScopeId = "account-a",
                fileId = "foreign-file",
                bookId = "foreign-book",
                title = "Foreign",
                localPath = foreignFile.absolutePath,
                mediaKind = MediaKind.EPUB
            )
        )
        ownerStore.enqueueDownload(
            DownloadQueueEntry(
                serverUrl = server,
                requestId = "foreign-request",
                profileId = "foreign-profile",
                storageScopeId = "account-a",
                fileId = "queued-file",
                bookId = "queued-book",
                libraryId = "foreign-library",
                title = "Queued",
                mediaKind = MediaKind.EPUB,
                cellularConsentGranted = false,
                sequence = 1L
            )
        )
        val recordsFile = File(filesDir, "downloads.json")
        val queueFile = File(filesDir, "download-queue.json")
        val recordsBefore = recordsFile.readText()
        val queueBefore = queueFile.readText()
        val unresolved = DownloadStore(filesDir) { null }

        assertTrue(unresolved.readAll(server).isEmpty())
        assertNull(unresolved.find(server, "foreign-file"))
        assertTrue(unresolved.readAttempts(server).isEmpty())
        assertTrue(unresolved.readDownloadQueue(server).isEmpty())
        assertNull(unresolved.nextQueuedDownload(server))
        assertEquals(recordsBefore, recordsFile.readText())
        assertEquals(queueBefore, queueFile.readText())

        listOf<suspend () -> Unit>(
            { unresolved.delete(server, "foreign-file") },
            { unresolved.removeRecord(server, "foreign-file") },
            { unresolved.markAccessed(server, "foreign-file") },
            { unresolved.updateBookReadingState(server, "foreign-book", completed = true) },
            { unresolved.removeAttempt(server, "queued-file") },
            { unresolved.removeQueuedDownload(server, "queued-file") },
            { unresolved.clearDownloadQueue(server) }
        ).forEach { mutation ->
            assertTrue(runCatching { mutation() }.exceptionOrNull() is IllegalArgumentException)
        }
        assertFalse(foreignFile.exists())
        assertEquals(recordsBefore, recordsFile.readText())
        assertEquals(queueBefore, queueFile.readText())
    }

    @Test
    fun `legacy unscoped downloads are quarantined and remain reclaimable`() = runBlocking {
        val filesDir = Files.createTempDirectory("download-store-legacy-quarantine").toFile()
        val legacyFile = File(filesDir, "downloads/legacy.epub").apply {
            parentFile?.mkdirs()
            writeText("legacy bytes")
        }
        File(filesDir, "downloads.json").writeText(
            """[{"serverUrl":"https://example.test","profileId":"profile-1","fileId":"legacy","bookId":"book-1","title":"Legacy","localPath":"${legacyFile.absolutePath.replace("\\", "\\\\")}","mediaKind":"EPUB","downloadedAtMillis":1}]"""
        )
        val store = DownloadStore(filesDir) { "account-scope" }

        assertEquals(1, store.quarantineLegacyUnscopedDownloads())
        assertNull(store.find("https://example.test", "legacy"))
        val quarantined = store.readAllForMaintenance("https://example.test").single()
        assertTrue(quarantined.storageScopeId.startsWith("legacy-quarantine:"))
        assertTrue(quarantined.localPath.contains("quarantine"))
        assertTrue(File(quarantined.localPath).isFile)
        assertFalse(legacyFile.exists())
    }

    @Test
    fun `legacy unscoped attempt and queue are retired without a completed record`() = runBlocking {
        val filesDir = Files.createTempDirectory("download-store-legacy-transfer-only").toFile()
        val stage = File(filesDir, "downloads/.legacy.part").apply {
            parentFile?.mkdirs()
            writeText("partial")
        }
        val escapedStage = stage.absolutePath.replace("\\", "\\\\")
        val escapedTarget = File(filesDir, "downloads/legacy.epub")
            .absolutePath.replace("\\", "\\\\")
        File(filesDir, "download-attempts.json").writeText(
            """[{"serverUrl":"https://example.test","requestId":"legacy-request","profileId":"profile","fileId":"legacy-file","bookId":"legacy-book","title":"Legacy","targetPath":"$escapedTarget","mediaKind":"EPUB","stagedPath":"$escapedStage"}]"""
        )
        File(filesDir, "download-queue.json").writeText(
            """[{"serverUrl":"https://example.test","requestId":"legacy-request","profileId":"profile","fileId":"legacy-file","bookId":"legacy-book","title":"Legacy","mediaKind":"EPUB","sequence":1}]"""
        )
        val store = DownloadStore(filesDir) { "account-scope" }

        assertEquals(2, store.quarantineLegacyUnscopedDownloads())
        assertFalse(stage.exists())
        assertTrue(store.readAttempts().isEmpty())
        assertTrue(store.readDownloadQueue().isEmpty())
    }

    @Test
    fun `downloadTarget isolates identical file ids from different servers`() {
        val filesDir = Files.createTempDirectory("download-store-server-target").toFile()
        val store = DownloadStore(filesDir)

        val first = store.downloadTarget(
            "https://one.example",
            "same-file",
            "Same title",
            MediaKind.EPUB,
            "epub"
        )
        val second = store.downloadTarget(
            "https://two.example",
            "same-file",
            "Same title",
            MediaKind.EPUB,
            "epub"
        )

        assertTrue(first.parentFile != second.parentFile)
        assertTrue(first.canonicalPath.startsWith(File(filesDir, "downloads").canonicalPath))
        assertTrue(second.canonicalPath.startsWith(File(filesDir, "downloads").canonicalPath))
    }

    @Test
    fun `downloadTarget contains hostile file ids inside the hashed server directory`() {
        val filesDir = Files.createTempDirectory("download-store-hostile-target").toFile()
        val store = DownloadStore(filesDir)

        val target = store.downloadTarget(
            "https://example.test",
            "../../../../shared_prefs/session.xml",
            "Hostile title",
            MediaKind.EPUB,
            "epub"
        )
        val downloadsRoot = File(filesDir, "downloads").canonicalFile

        assertEquals(downloadsRoot, target.parentFile?.parentFile)
        assertTrue(target.canonicalPath.startsWith(downloadsRoot.path + File.separator))
        assertFalse(target.name.contains("session.xml"))
        assertFalse(target.name.contains(".."))
    }

    @Test
    fun `recorded legacy path is never reused as a server-owned write target`() {
        val filesDir = Files.createTempDirectory("download-store-legacy-target").toFile()
        val store = DownloadStore(filesDir)
        val legacy = File(filesDir, "downloads/Shared-file.epub").apply {
            parentFile?.mkdirs()
            writeText("ambiguous legacy bytes")
        }

        val target = store.ownedTargetOrIsolated(
            serverUrl = "https://one.test",
            fileId = "shared-file",
            title = "Shared",
            mediaKind = MediaKind.EPUB,
            formatHint = "epub",
            recordedPath = legacy.absolutePath
        )

        assertFalse(target.canonicalFile == legacy.canonicalFile)
        assertTrue(target.canonicalFile.parentFile?.parentFile == File(filesDir, "downloads").canonicalFile)
        assertTrue(legacy.exists())
    }

    @Test
    fun `server target hashing normalizes host but preserves case-sensitive paths`() {
        val filesDir = Files.createTempDirectory("download-store-server-path-case").toFile()
        val store = DownloadStore(filesDir)

        val upperHost = store.downloadTarget(
            "https://EXAMPLE.test/Komga",
            "same-file",
            "Same title",
            MediaKind.EPUB,
            "epub"
        )
        val lowerHost = store.downloadTarget(
            "https://example.test/Komga/",
            "same-file",
            "Same title",
            MediaKind.EPUB,
            "epub"
        )
        val differentPathCase = store.downloadTarget(
            "https://example.test/komga",
            "same-file",
            "Same title",
            MediaKind.EPUB,
            "epub"
        )

        assertEquals(upperHost.parentFile, lowerHost.parentFile)
        assertTrue(upperHost.parentFile != differentPathCase.parentFile)
    }

    @Test
    fun `delete refuses unmanaged paths and retains their records`() = runBlocking {
        val filesDir = Files.createTempDirectory("download-store-unmanaged-delete").toFile()
        val outsideFile = Files.createTempFile("download-store-outside", ".epub").toFile()
            .apply { writeText("keep") }
        val store = DownloadStore(filesDir)
        store.save(
            DownloadRecord(
                serverUrl = "https://example.test",
                fileId = "outside",
                bookId = "book-outside",
                title = "Outside",
                localPath = outsideFile.absolutePath,
                mediaKind = MediaKind.EPUB
            )
        )

        assertEquals(false, store.delete("https://example.test", "outside"))
        assertTrue(outsideFile.isFile)
        assertNotNull(store.find("https://example.test", "outside"))
    }

    @Test
    fun `download metadata survives a store round trip`() = runBlocking {
        val filesDir = Files.createTempDirectory("download-store-policy-metadata").toFile()
        val localFile = File(filesDir, "downloads/metadata.epub").apply {
            parentFile?.mkdirs()
            writeText("metadata")
        }
        val store = DownloadStore(filesDir)
        store.save(
            DownloadRecord(
                serverUrl = "https://example.test",
                profileId = "profile-1",
                fileId = "metadata-file",
                bookId = "metadata-book",
                libraryId = "library-1",
                title = "Metadata",
                localPath = localFile.absolutePath,
                mediaKind = MediaKind.EPUB,
                downloadedAtMillis = 10L,
                lastAccessedAtMillis = 20L,
                lastKnownCompleted = true,
                sizeBytes = localFile.length(),
                origin = DownloadOrigin.AUTOMATIC
            )
        )

        val restored = DownloadStore(filesDir)
            .find("https://example.test", "metadata-file")
        assertEquals("profile-1", restored?.profileId)
        assertEquals("library-1", restored?.libraryId)
        assertEquals(20L, restored?.lastAccessedAtMillis)
        assertEquals(true, restored?.lastKnownCompleted)
        assertEquals(localFile.length(), restored?.sizeBytes)
        assertEquals(DownloadOrigin.AUTOMATIC, restored?.origin)
    }

    @Test
    fun `readAll and find are scoped by server url`() = runBlocking {
        val filesDir = Files.createTempDirectory("download-store-server-scope").toFile()
        val store = DownloadStore(filesDir)
        val firstFile = File(filesDir, "downloads/first.epub").apply {
            parentFile?.mkdirs()
            writeText("one")
        }
        val secondFile = File(filesDir, "downloads/second.epub").apply {
            parentFile?.mkdirs()
            writeText("two")
        }

        store.save(
            DownloadRecord(
                serverUrl = "https://one.example",
                fileId = "shared-file",
                bookId = "book-1",
                title = "Book One",
                localPath = firstFile.absolutePath,
                mediaKind = MediaKind.EPUB
            )
        )
        store.save(
            DownloadRecord(
                serverUrl = "https://two.example",
                fileId = "shared-file",
                bookId = "book-2",
                title = "Book Two",
                localPath = secondFile.absolutePath,
                mediaKind = MediaKind.EPUB
            )
        )

        assertEquals(listOf("https://one.example"), store.readAll("https://one.example").map { it.serverUrl })
        assertEquals(listOf("https://two.example"), store.readAll("https://two.example").map { it.serverUrl })
        assertEquals(firstFile.absolutePath, store.find("https://one.example", "shared-file")?.localPath)
        assertEquals(secondFile.absolutePath, store.find("https://two.example", "shared-file")?.localPath)
    }

    @Test
    fun `source catalog version survives download store round trip`() = runBlocking {
        val filesDir = Files.createTempDirectory("download-store-source-version").toFile()
        val store = DownloadStore(filesDir)
        val localFile = File(filesDir, "downloads/versioned.epub").apply {
            parentFile?.mkdirs()
            writeText("versioned")
        }

        store.save(
            DownloadRecord(
                serverUrl = "https://example.test",
                fileId = "file-versioned",
                bookId = "book-versioned",
                title = "Versioned",
                localPath = localFile.absolutePath,
                mediaKind = MediaKind.EPUB,
                sourceUpdatedAtMillis = 1234L
            )
        )

        assertEquals(1234L, store.find("https://example.test", "file-versioned")?.sourceUpdatedAtMillis)
    }

    @Test
    fun `interrupted record survives reopen without a completed file`() = runBlocking {
        val filesDir = Files.createTempDirectory("download-store-interrupted").toFile()
        val store = DownloadStore(filesDir)
        val target = File(filesDir, "downloads/interrupted.epub")

        store.save(
            DownloadRecord(
                serverUrl = "https://example.test",
                fileId = "file-interrupted",
                bookId = "book-interrupted",
                title = "Interrupted",
                localPath = target.absolutePath,
                mediaKind = MediaKind.EPUB,
                status = DownloadRecordStatus.INTERRUPTED
            )
        )

        val reopened = DownloadStore(filesDir)
        val restored = reopened.find("https://example.test", "file-interrupted")
        assertEquals(DownloadRecordStatus.INTERRUPTED, restored?.status)
        assertEquals(target.absolutePath, restored?.localPath)

        assertEquals(true, reopened.removeRecord("https://example.test", "file-interrupted"))
        assertEquals(null, reopened.find("https://example.test", "file-interrupted"))
    }

    @Test
    fun `download attempt preserves existing local copy separately from completed record`() = runBlocking {
        val filesDir = Files.createTempDirectory("download-store-attempt").toFile()
        val store = DownloadStore(filesDir)
        val localFile = File(filesDir, "downloads/existing.epub").apply {
            parentFile?.mkdirs()
            writeText("old copy")
        }

        store.save(
            DownloadRecord(
                serverUrl = "https://example.test",
                fileId = "file-update",
                bookId = "book-update",
                title = "Book update",
                filename = "Chapter 01.mp3",
                localPath = localFile.absolutePath,
                mediaKind = MediaKind.EPUB
            )
        )
        store.saveAttempt(
            DownloadAttempt(
                serverUrl = "https://example.test",
                fileId = "file-update",
                bookId = "book-update",
                title = "Book update",
                targetPath = localFile.absolutePath,
                existingLocalPath = localFile.absolutePath,
                mediaKind = MediaKind.EPUB
            )
        )

        assertEquals(localFile.absolutePath, store.find("https://example.test", "file-update")?.localPath)
        assertEquals("Chapter 01.mp3", store.find("https://example.test", "file-update")?.filename)
        assertEquals(localFile.absolutePath, store.readAttempts("https://example.test").single().existingLocalPath)
        assertEquals(true, store.removeAttempt("https://example.test", "file-update"))
        assertEquals(emptyList<DownloadAttempt>(), store.readAttempts("https://example.test"))
    }

    @Test
    fun `queued download attempt preserves title metadata before worker starts`() = runBlocking {
        val filesDir = Files.createTempDirectory("download-store-queued").toFile()
        val store = DownloadStore(filesDir)
        val target = store.downloadTarget(
            "https://example.test",
            "queued-file",
            "Queued title",
            MediaKind.EPUB,
            "epub"
        )

        store.saveAttempt(
            DownloadAttempt(
                serverUrl = "https://example.test",
                fileId = "queued-file",
                bookId = "queued-book",
                title = "Queued title",
                filename = "Chapter 02.mp3",
                targetPath = target.absolutePath,
                mediaKind = MediaKind.EPUB,
                mimeType = "epub",
                sourceUpdatedAtMillis = 42L
            )
        )

        val restored = DownloadStore(filesDir).readAttempts("https://example.test").single()

        assertEquals("Queued title", restored.title)
        assertEquals("Chapter 02.mp3", restored.filename)
        assertEquals("queued-book", restored.bookId)
        assertEquals(target.absolutePath, restored.targetPath)
        assertEquals(42L, restored.sourceUpdatedAtMillis)
    }

    @Test
    fun `concurrent attempt saves from separate store instances sharing a directory do not lose data`() = runBlocking {
        val filesDir = Files.createTempDirectory("download-store-concurrent").toFile()
        val iterations = 40

        coroutineScope {
            repeat(iterations) { index ->
                launch(Dispatchers.IO) {
                    DownloadStore(filesDir).saveAttempt(
                        DownloadAttempt(
                            serverUrl = "https://example.test",
                            fileId = "file-a-$index",
                            bookId = "book-a-$index",
                            title = "Title A $index",
                            targetPath = "/tmp/a-$index.epub",
                            mediaKind = MediaKind.EPUB
                        )
                    )
                }
                launch(Dispatchers.IO) {
                    DownloadStore(filesDir).saveAttempt(
                        DownloadAttempt(
                            serverUrl = "https://example.test",
                            fileId = "file-b-$index",
                            bookId = "book-b-$index",
                            title = "Title B $index",
                            targetPath = "/tmp/b-$index.epub",
                            mediaKind = MediaKind.EPUB
                        )
                    )
                }
            }
        }

        val restored = DownloadStore(filesDir).readAttempts("https://example.test")
        assertEquals(iterations * 2, restored.size)
    }

    @Test
    fun `durable download queue preserves authoritative sequence and deduplicates files`() = runBlocking {
        val filesDir = Files.createTempDirectory("download-store-queue").toFile()
        val store = DownloadStore(filesDir)
        val later = DownloadQueueEntry(
            serverUrl = "https://example.test",
            fileId = "file-2",
            bookId = "book-1",
            libraryId = "library-1",
            title = "Multipart",
            filename = "Chapter 02.mp3",
            mediaKind = MediaKind.AUDIO,
            mimeType = "audio/mpeg",
            sourceUpdatedAtMillis = 20L,
            cellularConsentGranted = true,
            sequence = 2L
        )
        val first = later.copy(
            fileId = "file-1",
            filename = "Chapter 01.mp3",
            sourceUpdatedAtMillis = 10L,
            sequence = 1L
        )

        store.enqueueDownloads(
            listOf(later, first, first.copy(title = "Duplicate ignored"))
        )

        val restoredStore = DownloadStore(filesDir)
        assertEquals(
            listOf("file-1", "file-2"),
            restoredStore.readDownloadQueue("https://example.test").map { it.fileId }
        )
        assertEquals("file-1", restoredStore.nextQueuedDownload("https://example.test")?.fileId)
        assertEquals(true, restoredStore.removeQueuedDownload("https://example.test", "file-1"))
        assertEquals("file-2", restoredStore.nextQueuedDownload("https://example.test")?.fileId)
    }

    @Test
    fun `manual queue entries take priority over older automatic entries`() = runBlocking {
        val filesDir = Files.createTempDirectory("download-store-priority").toFile()
        val store = DownloadStore(filesDir)
        val automatic = DownloadQueueEntry(
            serverUrl = "https://example.test",
            requestId = "automatic-request",
            profileId = "profile-1",
            fileId = "automatic",
            bookId = "book-auto",
            libraryId = "library-1",
            title = "Automatic",
            mediaKind = MediaKind.EPUB,
            origin = DownloadOrigin.AUTOMATIC,
            cellularConsentGranted = false,
            sequence = 1L
        )
        val manual = automatic.copy(
            requestId = "manual-request",
            fileId = "manual",
            bookId = "book-manual",
            title = "Manual",
            origin = DownloadOrigin.MANUAL,
            sequence = 2L
        )

        store.enqueueDownloads(listOf(automatic, manual))

        assertEquals("manual", store.nextQueuedDownload("https://example.test")?.fileId)
    }

    @Test
    fun `manual request upgrades the same automatically queued file`() = runBlocking {
        val filesDir = Files.createTempDirectory("download-store-upgrade").toFile()
        val store = DownloadStore(filesDir)
        val automatic = DownloadQueueEntry(
            serverUrl = "https://example.test",
            requestId = "automatic-request",
            profileId = "profile-1",
            fileId = "same-file",
            bookId = "same-book",
            libraryId = "library-1",
            title = "Automatic",
            mediaKind = MediaKind.EPUB,
            origin = DownloadOrigin.AUTOMATIC,
            requiresUnmeteredNetwork = true,
            requiresCharging = true,
            cellularConsentGranted = false,
            sequence = 1L
        )
        val manual = automatic.copy(
            requestId = "manual-request",
            title = "Manual",
            origin = DownloadOrigin.MANUAL,
            requiresUnmeteredNetwork = false,
            requiresCharging = false,
            cellularConsentGranted = true,
            sequence = 2L
        )

        store.enqueueDownload(automatic)
        store.enqueueDownload(manual)

        val restored = store.readDownloadQueue("https://example.test").single()
        assertEquals(DownloadOrigin.MANUAL, restored.origin)
        assertEquals(false, restored.requiresUnmeteredNetwork)
        assertEquals(false, restored.requiresCharging)
        assertEquals("Manual", restored.title)
        assertEquals("manual-request", restored.requestId)
    }

    @Test
    fun `cancelled old worker cannot remove a promoted manual request`() = runBlocking {
        val filesDir = Files.createTempDirectory("download-store-owned-cleanup").toFile()
        val store = DownloadStore(filesDir)
        val automatic = DownloadQueueEntry(
            serverUrl = "https://example.test",
            requestId = "automatic-request",
            profileId = "profile-1",
            fileId = "same-file",
            bookId = "same-book",
            libraryId = "library-1",
            title = "Automatic",
            mediaKind = MediaKind.EPUB,
            origin = DownloadOrigin.AUTOMATIC,
            policyGeneration = 3,
            cellularConsentGranted = false,
            sequence = 1L
        )
        val manual = automatic.copy(
            requestId = "manual-request",
            title = "Manual",
            origin = DownloadOrigin.MANUAL,
            policyGeneration = null,
            cellularConsentGranted = true,
            sequence = 2L
        )
        store.enqueueDownload(automatic)
        store.saveAttempt(
            DownloadAttempt(
                serverUrl = automatic.serverUrl,
                requestId = automatic.requestId,
                fileId = automatic.fileId,
                bookId = automatic.bookId,
                title = automatic.title,
                targetPath = File(filesDir, "automatic.epub").absolutePath,
                mediaKind = MediaKind.EPUB,
                origin = DownloadOrigin.AUTOMATIC,
                policyGeneration = 3
            )
        )
        store.enqueueDownload(manual)
        store.saveAttempt(
            DownloadAttempt(
                serverUrl = manual.serverUrl,
                requestId = manual.requestId,
                fileId = manual.fileId,
                bookId = manual.bookId,
                title = manual.title,
                targetPath = File(filesDir, "manual.epub").absolutePath,
                mediaKind = MediaKind.EPUB,
                origin = DownloadOrigin.MANUAL
            )
        )

        assertFalse(
            store.removeQueuedDownloadIfOwned(
                automatic.serverUrl,
                automatic.fileId,
                automatic.requestId
            )
        )
        assertFalse(
            store.removeAttemptIfOwned(
                automatic.serverUrl,
                automatic.fileId,
                automatic.requestId
            )
        )
        assertEquals("manual-request", store.readDownloadQueue().single().requestId)
        assertEquals("manual-request", store.readAttempts().single().requestId)
    }

    @Test
    fun `delayed captured cancellation cannot retire an immediate replacement`() = runBlocking {
        val filesDir = Files.createTempDirectory("download-store-cancel-restart").toFile()
        val store = DownloadStore(filesDir)
        fun request(requestId: String) = DownloadQueueEntry(
            serverUrl = "https://example.test",
            requestId = requestId,
            profileId = "profile-1",
            fileId = "same-file",
            bookId = "same-book",
            libraryId = "library-1",
            title = requestId,
            mediaKind = MediaKind.EPUB,
            origin = DownloadOrigin.MANUAL,
            cellularConsentGranted = true,
            sequence = if (requestId == "old") 1L else 2L
        ).let { entry ->
            entry to DownloadAttempt(
                serverUrl = entry.serverUrl,
                requestId = entry.requestId,
                profileId = entry.profileId,
                fileId = entry.fileId,
                bookId = entry.bookId,
                libraryId = entry.libraryId,
                title = entry.title,
                targetPath = File(filesDir, "$requestId.epub").absolutePath,
                mediaKind = entry.mediaKind,
                origin = entry.origin
            )
        }
        store.enqueueDownloadsWithAttempts(listOf(request("old")))
        store.enqueueDownloadsWithAttempts(listOf(request("replacement")))

        assertFalse(
            store.cancelDownloadRequestIfOwned(
                "https://example.test",
                "same-file",
                "old"
            )
        )
        assertEquals("replacement", store.readDownloadQueue().single().requestId)
        assertEquals("replacement", store.readAttempts().single().requestId)
    }

    @Test
    fun `cancelled stale work removes only its exact orphaned attempt`() = runBlocking {
        val filesDir = Files.createTempDirectory("download-store-stale-attempt").toFile()
        val store = DownloadStore(filesDir)
        val staleAttempt = DownloadAttempt(
            serverUrl = "https://example.test",
            requestId = "stale-request",
            profileId = "profile-1",
            fileId = "stale-file",
            bookId = "stale-book",
            libraryId = "library-1",
            title = "Stale",
            targetPath = File(filesDir, "stale.epub").absolutePath,
            mediaKind = MediaKind.EPUB,
            origin = DownloadOrigin.AUTOMATIC,
            policyGeneration = 3
        )
        store.saveAttempt(staleAttempt)

        assertFalse(
            store.removeAttemptIfOwned(
                staleAttempt.serverUrl,
                staleAttempt.fileId,
                "different-request"
            )
        )
        assertEquals("stale-request", store.readAttempts().single().requestId)

        assertTrue(
            store.removeAttemptIfOwned(
                staleAttempt.serverUrl,
                staleAttempt.fileId,
                staleAttempt.requestId
            )
        )
        assertTrue(store.readAttempts().isEmpty())
    }

    @Test
    fun `generation cleanup removes orphaned automatic attempts but preserves future and manual work`() =
        runBlocking {
            val filesDir = Files.createTempDirectory("download-store-orphan-attempts").toFile()
            val store = DownloadStore(filesDir)
            fun attempt(
                fileId: String,
                requestId: String,
                origin: DownloadOrigin,
                generation: Long?
            ) = DownloadAttempt(
                serverUrl = "https://example.test",
                requestId = requestId,
                profileId = "profile-1",
                fileId = fileId,
                bookId = "book-$fileId",
                libraryId = "library-1",
                title = fileId,
                targetPath = File(filesDir, "$fileId.epub").absolutePath,
                mediaKind = MediaKind.EPUB,
                origin = origin,
                policyGeneration = generation
            )
            store.saveAttempt(attempt("old", "old-request", DownloadOrigin.AUTOMATIC, 3))
            store.saveAttempt(attempt("future", "future-request", DownloadOrigin.AUTOMATIC, 5))
            store.saveAttempt(attempt("manual", "manual-request", DownloadOrigin.MANUAL, null))

            val removed = store.removeAutomaticAttemptsThroughGeneration(
                "https://example.test",
                "profile-1",
                4
            )

            assertEquals(listOf("old-request"), removed.map { it.requestId })
            assertEquals(
                setOf("future-request", "manual-request"),
                store.readAttempts().mapTo(mutableSetOf()) { it.requestId }
            )
        }

    @Test
    fun `generation cleanup preserves future automatic requests`() = runBlocking {
        val filesDir = Files.createTempDirectory("download-store-generation-cleanup").toFile()
        val store = DownloadStore(filesDir)
        fun entry(request: String, generation: Long, file: String) = DownloadQueueEntry(
            serverUrl = "https://example.test",
            requestId = request,
            profileId = "profile-1",
            fileId = file,
            bookId = "book-$file",
            libraryId = "library-1",
            title = file,
            mediaKind = MediaKind.EPUB,
            origin = DownloadOrigin.AUTOMATIC,
            policyGeneration = generation,
            cellularConsentGranted = false,
            sequence = generation
        )
        store.enqueueDownloads(
            listOf(entry("old", 4, "old-file"), entry("future", 6, "future-file"))
        )

        val removed = store.removeAutomaticQueueThroughGeneration(
            "https://example.test",
            "profile-1",
            4
        )

        assertEquals(listOf("old"), removed.map { it.requestId })
        assertEquals(listOf("future"), store.readDownloadQueue().map { it.requestId })
    }

    @Test
    fun `queue winner and attempt ownership change atomically during manual promotion`() = runBlocking {
        val filesDir = Files.createTempDirectory("download-store-atomic-promotion").toFile()
        val store = DownloadStore(filesDir)
        fun request(requestId: String, origin: DownloadOrigin) = DownloadQueueEntry(
            serverUrl = "https://example.test",
            requestId = requestId,
            profileId = "profile-1",
            fileId = "same-file",
            bookId = "same-book",
            libraryId = "library-1",
            title = requestId,
            mediaKind = MediaKind.EPUB,
            origin = origin,
            policyGeneration = 4.takeIf { origin == DownloadOrigin.AUTOMATIC }?.toLong(),
            cellularConsentGranted = origin == DownloadOrigin.MANUAL,
            sequence = if (origin == DownloadOrigin.MANUAL) 2L else 1L
        ).let { entry ->
            entry to DownloadAttempt(
                serverUrl = entry.serverUrl,
                requestId = entry.requestId,
                profileId = entry.profileId,
                fileId = entry.fileId,
                bookId = entry.bookId,
                libraryId = entry.libraryId,
                title = entry.title,
                targetPath = File(filesDir, "$requestId.epub").absolutePath,
                mediaKind = entry.mediaKind,
                origin = entry.origin,
                policyGeneration = entry.policyGeneration
            )
        }

        store.enqueueDownloadsWithAttempts(listOf(request("automatic", DownloadOrigin.AUTOMATIC)), 5)
        store.enqueueDownloadsWithAttempts(listOf(request("manual", DownloadOrigin.MANUAL)), 5)
        val rejected = store.enqueueDownloadsWithAttempts(
            listOf(request("stale-automatic", DownloadOrigin.AUTOMATIC)),
            5
        )

        assertTrue(rejected.isEmpty())
        assertEquals("manual", store.readDownloadQueue().single().requestId)
        assertEquals("manual", store.readAttempts().single().requestId)
        assertEquals(DownloadOrigin.MANUAL, store.readAttempts().single().origin)
    }

    @Test
    fun `queue claim repairs a torn durable attempt from the queue winner`() = runBlocking {
        val filesDir = Files.createTempDirectory("download-store-torn-pair").toFile()
        val store = DownloadStore(filesDir)
        val automaticEntry = DownloadQueueEntry(
            serverUrl = "https://example.test",
            requestId = "automatic-request",
            profileId = "profile-1",
            fileId = "same-file",
            bookId = "same-book",
            libraryId = "library-1",
            title = "Automatic",
            mediaKind = MediaKind.EPUB,
            origin = DownloadOrigin.AUTOMATIC,
            policyGeneration = 4,
            cellularConsentGranted = false,
            sequence = 1
        )
        store.enqueueDownload(automaticEntry)
        store.saveAttempt(
            DownloadAttempt(
                serverUrl = automaticEntry.serverUrl,
                requestId = "torn-manual-attempt",
                profileId = automaticEntry.profileId,
                fileId = automaticEntry.fileId,
                bookId = automaticEntry.bookId,
                libraryId = automaticEntry.libraryId,
                title = "Manual",
                targetPath = File(filesDir, "wrong.epub").absolutePath,
                mediaKind = MediaKind.EPUB,
                origin = DownloadOrigin.MANUAL
            )
        )

        val claimed = store.claimQueuedDownload(
            automaticEntry.serverUrl,
            automaticEntry.fileId,
            automaticEntry.requestId
        )

        assertEquals("automatic-request", claimed?.requestId)
        assertEquals(DownloadOrigin.AUTOMATIC, claimed?.origin)
        assertEquals(4L, claimed?.policyGeneration)
        assertEquals("automatic-request", store.readAttempts().single().requestId)
    }

    @Test
    fun `successful old worker cannot finish a newer automatic request`() = runBlocking {
        val filesDir = Files.createTempDirectory("download-store-new-generation-owner").toFile()
        val store = DownloadStore(filesDir)
        fun request(requestId: String, generation: Long) = DownloadQueueEntry(
            serverUrl = "https://example.test",
            requestId = requestId,
            profileId = "profile-1",
            fileId = "same-file",
            bookId = "same-book",
            libraryId = "library-1",
            title = requestId,
            mediaKind = MediaKind.EPUB,
            origin = DownloadOrigin.AUTOMATIC,
            policyGeneration = generation,
            cellularConsentGranted = false,
            sequence = generation
        ).let { entry ->
            entry to DownloadAttempt(
                serverUrl = entry.serverUrl,
                requestId = entry.requestId,
                profileId = entry.profileId,
                fileId = entry.fileId,
                bookId = entry.bookId,
                libraryId = entry.libraryId,
                title = entry.title,
                targetPath = File(filesDir, "$requestId.epub").absolutePath,
                mediaKind = entry.mediaKind,
                origin = entry.origin,
                policyGeneration = generation
            )
        }
        store.enqueueDownloadsWithAttempts(listOf(request("old", 4)), 5)
        store.removeAutomaticQueueThroughGeneration("https://example.test", "profile-1", 4)
        store.enqueueDownloadsWithAttempts(listOf(request("new", 5)), 5)

        assertFalse(
            store.finishDownloadRequest(
                "https://example.test",
                "same-file",
                "old",
                successfulCompletion = true
            )
        )
        assertEquals("new", store.readDownloadQueue().single().requestId)
        assertEquals("new", store.readAttempts().single().requestId)
    }

    @Test
    fun `stale staged bytes cannot commit after attempt ownership changes`() = runBlocking {
        val filesDir = Files.createTempDirectory("download-store-owned-commit").toFile()
        val store = DownloadStore(filesDir)
        val target = store.downloadTarget(
            "https://example.test",
            "same-file",
            "Book",
            MediaKind.EPUB,
            "epub"
        )
        target.parentFile?.mkdirs()
        target.writeText("existing")
        val staged = File(target.parentFile, ".${target.name}.part").apply { writeText("stale") }
        fun attempt(requestId: String) = DownloadAttempt(
            serverUrl = "https://example.test",
            requestId = requestId,
            profileId = "profile-1",
            fileId = "same-file",
            bookId = "book-1",
            libraryId = "library-1",
            title = "Book",
            targetPath = target.absolutePath,
            mediaKind = MediaKind.EPUB
        )
        store.saveAttempt(attempt("old"))
        store.saveAttempt(attempt("replacement"))

        val committed = store.commitDownloadIfOwned(
            serverUrl = "https://example.test",
            fileId = "same-file",
            requestId = "old",
            stagedFile = staged,
            targetFile = target,
            record = DownloadRecord(
                serverUrl = "https://example.test",
                profileId = "profile-1",
                fileId = "same-file",
                bookId = "book-1",
                libraryId = "library-1",
                title = "Book",
                localPath = target.absolutePath,
                mediaKind = MediaKind.EPUB
            )
        )

        assertFalse(committed)
        assertEquals("existing", target.readText())
        assertEquals("replacement", store.readAttempts().single().requestId)
        assertTrue(store.readAll().isEmpty())
    }

    @Test
    fun `scheduler attempt cannot commit after its authoritative queue is removed`() = runBlocking {
        val filesDir = Files.createTempDirectory("download-store-revoked-commit").toFile()
        val store = DownloadStore(filesDir)
        val target = store.downloadTarget(
            "https://example.test",
            "same-file",
            "Book",
            MediaKind.EPUB,
            "epub"
        )
        target.parentFile?.mkdirs()
        val staged = File(target.parentFile, ".${target.name}.part").apply { writeText("stale") }
        val entry = DownloadQueueEntry(
            serverUrl = "https://example.test",
            requestId = "revoked",
            profileId = "profile-1",
            fileId = "same-file",
            bookId = "book-1",
            libraryId = "library-1",
            title = "Book",
            mediaKind = MediaKind.EPUB,
            origin = DownloadOrigin.AUTOMATIC,
            policyGeneration = 4,
            cellularConsentGranted = false,
            sequence = 1
        )
        val attempt = DownloadAttempt(
            serverUrl = entry.serverUrl,
            requestId = entry.requestId,
            profileId = entry.profileId,
            fileId = entry.fileId,
            bookId = entry.bookId,
            libraryId = entry.libraryId,
            title = entry.title,
            targetPath = target.absolutePath,
            mediaKind = entry.mediaKind,
            origin = entry.origin,
            policyGeneration = entry.policyGeneration
        )
        store.enqueueDownloadsWithAttempts(listOf(entry to attempt), 5)
        store.removeAutomaticQueueThroughGeneration(entry.serverUrl, entry.profileId, 4)

        val committed = store.commitDownloadIfOwned(
            entry.serverUrl,
            entry.fileId,
            entry.requestId,
            staged,
            target,
            DownloadRecord(
                serverUrl = entry.serverUrl,
                profileId = entry.profileId,
                fileId = entry.fileId,
                bookId = entry.bookId,
                libraryId = entry.libraryId,
                title = entry.title,
                localPath = target.absolutePath,
                mediaKind = entry.mediaKind,
                origin = entry.origin
            )
        )

        assertFalse(committed)
        assertFalse(target.exists())
        assertTrue(staged.exists())
    }

    @Test
    fun `atomic deletion keeps a local copy when a transfer owns the file`() = runBlocking {
        val filesDir = Files.createTempDirectory("download-store-protected-delete").toFile()
        val store = DownloadStore(filesDir)
        val target = store.downloadTarget(
            "https://example.test",
            "file-1",
            "Book",
            MediaKind.EPUB,
            "epub"
        ).apply {
            parentFile?.mkdirs()
            writeText("book")
        }
        store.save(
            DownloadRecord(
                serverUrl = "https://example.test",
                fileId = "file-1",
                bookId = "book-1",
                title = "Book",
                localPath = target.absolutePath,
                mediaKind = MediaKind.EPUB
            )
        )
        store.enqueueDownload(
            DownloadQueueEntry(
                serverUrl = "https://example.test",
                requestId = "request-1",
                fileId = "file-1",
                bookId = "book-1",
                libraryId = "library-1",
                title = "Book",
                mediaKind = MediaKind.EPUB,
                origin = DownloadOrigin.MANUAL,
                cellularConsentGranted = true,
                sequence = 1
            )
        )

        val result = store.deleteGroupIfNotQueuedOrAttempted(
            "https://example.test",
            setOf("file-1")
        )

        assertTrue(result.protectedByTransfer)
        assertTrue(target.exists())
        assertNotNull(store.find("https://example.test", "file-1"))
    }

    @Test
    fun `old limit cleanup cannot delete a manually promoted same-file request`() = runBlocking {
        val filesDir = Files.createTempDirectory("download-store-owned-limit-cleanup").toFile()
        val store = DownloadStore(filesDir)
        val target = store.downloadTarget(
            "https://example.test",
            "file-1",
            "Book",
            MediaKind.EPUB,
            "epub"
        ).apply {
            parentFile?.mkdirs()
            writeText("completed")
        }
        store.save(
            DownloadRecord(
                serverUrl = "https://example.test",
                fileId = "file-1",
                bookId = "book-1",
                title = "Book",
                localPath = target.absolutePath,
                mediaKind = MediaKind.EPUB,
                origin = DownloadOrigin.AUTOMATIC
            )
        )
        val manualEntry = DownloadQueueEntry(
            serverUrl = "https://example.test",
            requestId = "manual-replacement",
            profileId = "profile-1",
            fileId = "file-1",
            bookId = "book-1",
            libraryId = "library-1",
            title = "Book",
            mediaKind = MediaKind.EPUB,
            origin = DownloadOrigin.MANUAL,
            cellularConsentGranted = true,
            sequence = 2
        )
        val manualAttempt = DownloadAttempt(
            serverUrl = manualEntry.serverUrl,
            requestId = manualEntry.requestId,
            profileId = manualEntry.profileId,
            fileId = manualEntry.fileId,
            bookId = manualEntry.bookId,
            libraryId = manualEntry.libraryId,
            title = manualEntry.title,
            targetPath = target.absolutePath,
            mediaKind = manualEntry.mediaKind,
            origin = manualEntry.origin
        )
        store.enqueueDownloadsWithAttempts(listOf(manualEntry to manualAttempt))

        assertFalse(
            store.deleteCompletedDownloadIfOwned(
                manualEntry.serverUrl,
                manualEntry.fileId,
                "old-automatic"
            )
        )
        assertTrue(target.exists())
        assertEquals("manual-replacement", store.readDownloadQueue().single().requestId)
        assertEquals("manual-replacement", store.readAttempts().single().requestId)
    }

    @Test
    fun `verified staged request is recovered after process death`() = runBlocking {
        val filesDir = Files.createTempDirectory("download-store-verified-recovery").toFile()
        val store = DownloadStore(filesDir)
        val target = store.downloadTarget(
            "https://example.test",
            "file-1",
            "Book",
            MediaKind.EPUB,
            "epub"
        )
        target.parentFile?.mkdirs()
        val staged = File(target.parentFile, ".${target.name}.part.request-1").apply {
            writeText("verified bytes")
        }
        val entry = DownloadQueueEntry(
            serverUrl = "https://example.test",
            requestId = "request-1",
            profileId = "profile-1",
            fileId = "file-1",
            bookId = "book-1",
            libraryId = "library-1",
            title = "Book",
            mediaKind = MediaKind.EPUB,
            origin = DownloadOrigin.AUTOMATIC,
            cellularConsentGranted = false,
            sequence = 1L
        )
        val attempt = DownloadAttempt(
            serverUrl = entry.serverUrl,
            requestId = entry.requestId,
            profileId = entry.profileId,
            fileId = entry.fileId,
            bookId = entry.bookId,
            libraryId = entry.libraryId,
            title = entry.title,
            targetPath = target.absolutePath,
            mediaKind = entry.mediaKind,
            origin = entry.origin
        )
        store.enqueueDownloadsWithAttempts(listOf(entry to attempt))
        assertTrue(
            store.markAttemptStateIfOwned(
                entry.serverUrl,
                entry.fileId,
                entry.requestId,
                DownloadAttemptState.VERIFIED,
                staged
            )
        )

        val reopened = DownloadStore(filesDir)
        assertEquals(setOf("file-1"), reopened.reconcileVerifiedCommits(entry.serverUrl))
        assertEquals("verified bytes", target.readText())
        assertNotNull(reopened.find(entry.serverUrl, entry.fileId))
        assertTrue(reopened.readAttempts(entry.serverUrl).isEmpty())
        assertTrue(reopened.readDownloadQueue(entry.serverUrl).isEmpty())
    }

    @Test
    fun `verified update remains staged while active reader blocks target replacement`() = runBlocking {
        val filesDir = Files.createTempDirectory("download-store-reader-deferral").toFile()
        val store = DownloadStore(filesDir)
        val target = store.downloadTarget(
            "https://example.test",
            "file-1",
            "Book",
            MediaKind.EPUB,
            "epub"
        ).apply {
            parentFile?.mkdirs()
            writeText("open publication")
        }
        val staged = File(target.parentFile, ".${target.name}.part.request-1").apply {
            writeText("updated publication")
        }
        val entry = DownloadQueueEntry(
            serverUrl = "https://example.test",
            requestId = "request-1",
            profileId = "profile-1",
            fileId = "file-1",
            bookId = "book-1",
            libraryId = "library-1",
            title = "Book",
            mediaKind = MediaKind.EPUB,
            origin = DownloadOrigin.AUTOMATIC,
            cellularConsentGranted = false,
            sequence = 1L
        )
        val attempt = DownloadAttempt(
            serverUrl = entry.serverUrl,
            requestId = entry.requestId,
            profileId = entry.profileId,
            fileId = entry.fileId,
            bookId = entry.bookId,
            libraryId = entry.libraryId,
            title = entry.title,
            targetPath = target.absolutePath,
            existingLocalPath = target.absolutePath,
            mediaKind = entry.mediaKind,
            origin = entry.origin
        )
        store.enqueueDownloadsWithAttempts(listOf(entry to attempt))
        assertTrue(
            store.markAttemptStateIfOwned(
                entry.serverUrl,
                entry.fileId,
                entry.requestId,
                DownloadAttemptState.VERIFIED,
                staged
            )
        )

        assertTrue(
            store.reconcileVerifiedCommits(
                entry.serverUrl,
                prevalidated = store.prevalidateVerifiedCommits(entry.serverUrl),
                canReplaceTarget = { _, _ -> false }
            ).isEmpty()
        )
        assertEquals("open publication", target.readText())
        assertEquals("updated publication", staged.readText())
        assertEquals(DownloadAttemptState.VERIFIED, store.readAttempts(entry.serverUrl).single().state)

        assertEquals(
            setOf("file-1"),
            store.reconcileVerifiedCommits(
                entry.serverUrl,
                prevalidated = store.prevalidateVerifiedCommits(entry.serverUrl)
            )
        )
        assertEquals("updated publication", target.readText())
    }

    @Test
    fun `recovery cannot publish a verified stage after ownership replacement`() = runBlocking {
        val filesDir = Files.createTempDirectory("download-store-stale-recovery").toFile()
        val store = DownloadStore(filesDir)
        val target = store.downloadTarget(
            "https://example.test",
            "file-1",
            "Book",
            MediaKind.EPUB,
            "epub"
        )
        target.parentFile?.mkdirs()
        val staleStage = File(target.parentFile, ".${target.name}.part.old").apply {
            writeText("old bytes")
        }
        fun request(id: String, origin: DownloadOrigin) = DownloadQueueEntry(
            serverUrl = "https://example.test",
            requestId = id,
            profileId = "profile-1",
            fileId = "file-1",
            bookId = "book-1",
            libraryId = "library-1",
            title = "Book",
            mediaKind = MediaKind.EPUB,
            origin = origin,
            cellularConsentGranted = true,
            sequence = if (id == "old") 1L else 2L
        )
        fun attempt(entry: DownloadQueueEntry) = DownloadAttempt(
            serverUrl = entry.serverUrl,
            requestId = entry.requestId,
            profileId = entry.profileId,
            fileId = entry.fileId,
            bookId = entry.bookId,
            libraryId = entry.libraryId,
            title = entry.title,
            targetPath = target.absolutePath,
            mediaKind = entry.mediaKind,
            origin = entry.origin
        )
        val old = request("old", DownloadOrigin.AUTOMATIC)
        store.enqueueDownloadsWithAttempts(listOf(old to attempt(old)))
        store.markAttemptStateIfOwned(
            old.serverUrl,
            old.fileId,
            old.requestId,
            DownloadAttemptState.VERIFIED,
            staleStage
        )
        val replacement = request("replacement", DownloadOrigin.MANUAL)
        store.enqueueDownloadsWithAttempts(listOf(replacement to attempt(replacement)))

        assertTrue(store.reconcileVerifiedCommits(old.serverUrl).isEmpty())
        assertFalse(target.exists())
        assertEquals("replacement", store.readDownloadQueue(old.serverUrl).single().requestId)
    }

    @Test
    fun `cancelling the owning request removes only its managed stage`() = runBlocking {
        val filesDir = Files.createTempDirectory("download-store-stage-cancel").toFile()
        val store = DownloadStore(filesDir)
        val target = store.downloadTarget(
            "https://example.test",
            "file-1",
            "Book",
            MediaKind.EPUB,
            "epub"
        )
        target.parentFile?.mkdirs()
        val stage = File(target.parentFile, ".${target.name}.part.request-1").apply {
            writeText("partial")
        }
        val entry = DownloadQueueEntry(
            serverUrl = "https://example.test",
            requestId = "request-1",
            profileId = "profile-1",
            fileId = "file-1",
            bookId = "book-1",
            libraryId = "library-1",
            title = "Book",
            mediaKind = MediaKind.EPUB,
            cellularConsentGranted = true,
            sequence = 1L
        )
        val attempt = DownloadAttempt(
            serverUrl = entry.serverUrl,
            requestId = entry.requestId,
            profileId = entry.profileId,
            fileId = entry.fileId,
            bookId = entry.bookId,
            libraryId = entry.libraryId,
            title = entry.title,
            targetPath = target.absolutePath,
            mediaKind = entry.mediaKind,
            stagedPath = stage.absolutePath,
            state = DownloadAttemptState.STAGING
        )
        store.enqueueDownloadsWithAttempts(listOf(entry to attempt))

        assertTrue(store.cancelDownloadRequestIfOwned(entry.serverUrl, entry.fileId, entry.requestId))
        assertFalse(stage.exists())
        assertTrue(store.readAttempts(entry.serverUrl).isEmpty())
        assertTrue(store.readDownloadQueue(entry.serverUrl).isEmpty())
    }

    @Test
    fun `legacy generation cleanup does not remove another servers same file`() = runBlocking {
        val filesDir = Files.createTempDirectory("download-store-cross-server-cleanup").toFile()
        val store = DownloadStore(filesDir)
        fun legacyEntry(server: String) = DownloadQueueEntry(
            serverUrl = server,
            profileId = "profile-$server",
            fileId = "shared-file",
            bookId = "book-$server",
            libraryId = "library-1",
            title = server,
            mediaKind = MediaKind.EPUB,
            origin = DownloadOrigin.AUTOMATIC,
            policyGeneration = null,
            cellularConsentGranted = false,
            sequence = server.length.toLong()
        )
        store.enqueueDownloads(
            listOf(legacyEntry("https://one.test"), legacyEntry("https://two.test"))
        )

        store.removeAutomaticQueueThroughGeneration("https://one.test", "profile-https://one.test", 4)

        assertEquals(
            listOf("https://two.test"),
            store.readDownloadQueue().map { it.serverUrl }
        )
    }

    @Test
    fun `corrupt queue state is discarded instead of recreating a startup crash loop`() = runBlocking {
        val filesDir = Files.createTempDirectory("download-queue-corrupt").toFile()
        File(filesDir, "download-queue.json").writeText("not-json")

        assertEquals(emptyList<DownloadQueueEntry>(), DownloadStore(filesDir).readDownloadQueue())
    }

    @Test
    fun `durable download queue survives a fresh store instance`() = runBlocking {
        val filesDir = Files.createTempDirectory("download-store-queue-reopen").toFile()
        DownloadStore(filesDir).enqueueDownload(
            DownloadQueueEntry(
                serverUrl = "https://example.test",
                fileId = "file-1",
                bookId = "book-1",
                libraryId = "library-1",
                title = "Multipart",
                filename = "Chapter 01.mp3",
                mediaKind = MediaKind.AUDIO,
                mimeType = "audio/mpeg",
                sourceUpdatedAtMillis = 10L,
                cellularConsentGranted = true,
                sequence = 1L
            )
        )

        assertEquals("file-1", DownloadStore(filesDir).nextQueuedDownload("https://example.test")?.fileId)
    }
}
