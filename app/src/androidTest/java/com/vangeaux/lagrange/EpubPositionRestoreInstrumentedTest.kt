package com.vangeaux.lagrange

import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.Locator

/**
 * Background read-aloud keeps saving the reading position after the reader has stopped and Android
 * has captured its saved instance state. When Android later rebuilds the reader from that state
 * (process death, or the activity being reclaimed), the newer stored position must win.
 */
@RunWith(AndroidJUnit4::class)
class EpubPositionRestoreInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val epub = File(context.cacheDir, "position-restore-${System.nanoTime()}.epub")
    private val readerKey = "position-restore-${System.nanoTime()}"

    @Before
    fun writeFixture() = writeThreeChapterEpub(epub)

    @After
    fun cleanUp() {
        epub.delete()
        ReadiumEpubTtsPositionStore(context).remove(readerKey)
    }

    private fun launch(): ActivityScenario<ReadiumEpubReaderActivity> = ActivityScenario.launch(
        ReadiumEpubReaderActivity.createIntent(
            context = context,
            file = epub,
            title = "Position restore",
            readerKey = readerKey,
            launchMode = ReaderLaunchMode.NORMAL,
            initialChapter = 0,
            initialPage = 0,
            initialPageCount = 1,
            initialPercent = null
        )
    )

    /** Waits for a stored position in the accepted resource, settled with its book position. */
    private fun awaitStoredHref(message: String, accept: (String) -> Boolean): Locator {
        var last: Locator? = null
        repeat(80) {
            last = ReadiumEpubLocatorStore(context).read(readerKey)
            if (last != null && accept(last!!.href.toString()) && last!!.locations.totalProgression != null) {
                return last!!
            }
            SystemClock.sleep(250)
        }
        throw AssertionError("$message; stored ${last?.href}")
    }

    /** What EpubTtsPlaybackService writes for each utterance while the reader is in the background. */
    private fun simulateBackgroundReadAloudReaching(chapterHref: String): Locator {
        val locator = requireNotNull(
            Locator.fromJSON(
                JSONObject(
                    """{"href":"$chapterHref","type":"application/xhtml+xml",
                       "locations":{"progression":0.5,"totalProgression":0.83}}"""
                )
            )
        )
        ReadiumEpubTtsPositionStore(context).save(readerKey, locator)
        ReadiumEpubLocatorStore(context).save(readerKey, locator)
        return locator
    }

    @Test
    fun restoredReaderPrefersPositionSavedByBackgroundReadAloud() {
        launch().use { scenario ->
            val opened = awaitStoredHref("reader did not open on chapter 1") { it.endsWith("chapter-1.xhtml") }

            // Leave the reader: Android captures the saved instance state at chapter 1.
            scenario.moveToState(Lifecycle.State.CREATED)
            val later = simulateBackgroundReadAloudReaching(
                opened.href.toString().replace("chapter-1.xhtml", "chapter-3.xhtml")
            )

            SystemClock.sleep(2000)
            // Android rebuilds the reader from the saved state captured before read-aloud moved on.
            scenario.recreate()
            scenario.moveToState(Lifecycle.State.RESUMED)

            val restored = awaitStoredHref("reader jumped back to the stale saved-state position") {
                it.endsWith("chapter-3.xhtml")
            }
            SystemClock.sleep(1500)
            val settled = requireNotNull(ReadiumEpubLocatorStore(context).read(readerKey))
            assertEquals(later.href, restored.href)
            assertTrue(
                "reader must stay on the newer position, not overwrite it with the old one: ${settled.href}",
                settled.href.toString().endsWith("chapter-3.xhtml")
            )
        }
    }

    @Test
    fun returningToTheForegroundDoesNotOverwriteThePositionSavedByBackgroundReadAloud() {
        launch().use { scenario ->
            val opened = awaitStoredHref("reader did not open on chapter 1") { it.endsWith("chapter-1.xhtml") }

            scenario.moveToState(Lifecycle.State.CREATED)
            val later = simulateBackgroundReadAloudReaching(
                opened.href.toString().replace("chapter-1.xhtml", "chapter-3.xhtml")
            )
            // Unlock the phone / switch back: the same reader instance starts again.
            scenario.moveToState(Lifecycle.State.RESUMED)
            SystemClock.sleep(2000)

            val stored = requireNotNull(ReadiumEpubLocatorStore(context).read(readerKey))
            assertEquals(
                "the replayed visual position must not replace the newer read-aloud position",
                later.href,
                stored.href
            )
        }
    }

    @Test
    fun scopedResumeCleanupPreservesAnotherProfile() = runBlocking {
        val serverUrl = "https://scope-cleanup.example/base"
        val firstScope = EpubReaderScope(
            serverUrl = serverUrl,
            profileId = "profile-a",
            providerId = "bookorbit",
            accountScope = "account-a"
        )
        val secondScope = firstScope.copy(profileId = "profile-b", accountScope = "account-b")
        val fingerprint = epubPublicationFingerprint(epub)
        val first = epubReaderPositionIdentity(firstScope, "book", "file", epub, fingerprint)
            .copy(updatedAtMillis = 10L)
        val second = epubReaderPositionIdentity(secondScope, "book", "file", epub, fingerprint)
            .copy(updatedAtMillis = 20L)
        val firstKey = epubReaderPersistenceKey(
            firstScope.profileId,
            firstScope.providerId,
            firstScope.accountScope,
            firstScope.serverUrl,
            first.bookId,
            first.fileId,
            fingerprint
        )
        val secondKey = epubReaderPersistenceKey(
            secondScope.profileId,
            secondScope.providerId,
            secondScope.accountScope,
            secondScope.serverUrl,
            second.bookId,
            second.fileId,
            fingerprint
        )
        val locator = requireNotNull(
            Locator.fromJSON(
                JSONObject(
                    """{"href":"chapter.xhtml","type":"application/xhtml+xml","locations":{"progression":0.5}}"""
                )
            )
        )
        val positions = EpubReaderPositionStore(context)
        val locators = ReadiumEpubLocatorStore(context)
        val ttsPositions = ReadiumEpubTtsPositionStore(context)

        try {
            assertTrue(positions.importIfAbsent(first))
            assertTrue(positions.importIfAbsent(second))
            locators.save(firstKey, locator)
            locators.save(secondKey, locator)
            ttsPositions.save(firstKey, locator)
            ttsPositions.save(secondKey, locator)

            clearEpubResumeStateForScope(context, firstScope)

            assertNull(positions.readExact(first))
            assertNotNull(positions.readExact(second))
            assertNull(locators.read(firstKey))
            assertNotNull(locators.read(secondKey))
            assertNull(ttsPositions.read(firstKey))
            assertNotNull(ttsPositions.read(secondKey))
        } finally {
            clearEpubResumeStateForScope(context, firstScope)
            clearEpubResumeStateForScope(context, secondScope)
        }
    }

    @Test
    fun passiveWakeReflowKeepsCanonicalProgressUntilARealPageMoveArrives() = runBlocking {
        val scope = EpubReaderScope(
            serverUrl = "https://wake-recovery.example",
            profileId = "wake-profile-${System.nanoTime()}",
            providerId = "bookorbit",
            accountScope = "wake-account"
        )
        val bookId = "wake-book-${System.nanoTime()}"
        val fileId = "wake-file"
        val fingerprint = epubPublicationFingerprint(epub)
        val initialLocatorJson =
            """{"href":"OEBPS/Text/chapter-1.xhtml","type":"application/xhtml+xml","locations":{"progression":0.1690164842185969,"position":14,"totalProgression":0.04887218045112782}}"""
        val identity = epubReaderPositionIdentity(
            scope = scope,
            bookId = bookId,
            fileId = fileId,
            file = epub,
            publicationFingerprint = fingerprint
        )
        val seeded = identity.copy(
            chapterIndex = 0,
            pageIndex = 12,
            pageCount = 71,
            updatedAtMillis = 1_793_337_624_167L,
            locatorJson = initialLocatorJson,
            progressPercent = 4.511278f,
            overallPositionNumber = 13,
            overallPositionCount = 266,
            renderedPageLayoutKey = "seeded-layout",
            renderedPageCounts = listOf(71, 0, 0)
        )
        val store = EpubReaderPositionStore(context)
        assertTrue(store.importIfAbsent(seeded))
        val intent = ReadiumEpubReaderActivity.createIntent(
            context = context,
            file = epub,
            fileId = fileId,
            bookId = bookId,
            title = "Passive wake recovery",
            readerKey = readerKey,
            publicationFingerprint = fingerprint,
            persistenceScope = scope,
            launchMode = ReaderLaunchMode.NORMAL,
            initialChapter = 0,
            initialPage = 0,
            initialPageCount = 1,
            initialPercent = null
        )

        try {
            ActivityScenario.launch<ReadiumEpubReaderActivity>(intent).use { scenario ->
                var ready = false
                for (attempt in 0 until 80) {
                    scenario.onActivity { activity -> ready = activity.isPublicationReadyForTest() }
                    if (ready) break
                    SystemClock.sleep(250)
                }
                assertTrue("reader publication did not become ready", ready)

                scenario.moveToState(Lifecycle.State.CREATED)
                scenario.moveToState(Lifecycle.State.RESUMED)
                val baseline = requireNotNull(store.readExact(identity))
                assertCanonicalProgressEquals(seeded, baseline)
                val passiveLocator = requireNotNull(
                    Locator.fromJSON(
                        JSONObject(initialLocatorJson).apply {
                            getJSONObject("locations").put("progression", 0.1791071724117178)
                        }
                    )
                )
                scenario.onActivity { activity ->
                    assertTrue(
                        activity.applyEpubPaginationForTest(
                            pageIndex = 12,
                            pageCount = 67,
                            locator = passiveLocator
                        )
                    )
                    val displayed = activity.currentProgressStatusForTest()
                    assertEquals(67, displayed.chapterPageCount)
                    assertEquals(4.511278f, displayed.completionPercent)
                    val result = activity.progressResultForTest()
                    assertEquals(12, result.pageIndex)
                    assertEquals(71, result.pageCount)
                    assertEquals(4.511278f, result.percent)
                    assertEquals(initialLocatorJson, result.locatorJson)
                }
                SystemClock.sleep(500)
                val afterPassive = requireNotNull(store.readExact(identity))
                assertCanonicalProgressEquals(baseline, afterPassive)
                assertEquals(baseline.updatedAtMillis, afterPassive.updatedAtMillis)
                assertEquals(baseline.writerSequence, afterPassive.writerSequence)

                val movedLocator = requireNotNull(
                    Locator.fromJSON(
                        JSONObject(initialLocatorJson).apply {
                            getJSONObject("locations").put("progression", 0.22)
                        }
                    )
                )
                scenario.onActivity { activity ->
                    assertTrue(
                        activity.applyEpubPaginationForTest(
                            pageIndex = 13,
                            pageCount = 67,
                            locator = movedLocator,
                            authoritativePageMove = true
                        )
                    )
                }
                scenario.onActivity { activity -> runBlocking { activity.flushPositionForTest() } }
                val afterMove = requireNotNull(store.readExact(identity))
                assertEquals(13, afterMove.pageIndex)
                assertEquals(67, afterMove.pageCount)
                assertTrue(afterMove.updatedAtMillis > baseline.updatedAtMillis)
                assertTrue(afterMove.writerSequence > baseline.writerSequence)
                assertEquals(0.22, afterMove.toEpubCanonicalPosition()?.resourceProgression ?: -1.0, 0.000001)
                scenario.onActivity { activity ->
                    val result = activity.progressResultForTest()
                    assertEquals(13, result.pageIndex)
                    assertEquals(67, result.pageCount)
                }

                val delayedPassiveLocator = requireNotNull(
                    Locator.fromJSON(
                        JSONObject(initialLocatorJson).apply {
                            getJSONObject("locations").put("progression", 0.225)
                        }
                    )
                )
                scenario.onActivity { activity ->
                    assertTrue(
                        activity.applyEpubPaginationForTest(
                            pageIndex = 13,
                            pageCount = 65,
                            locator = delayedPassiveLocator
                        )
                    )
                }
                SystemClock.sleep(500)
                val afterDelayedPassive = requireNotNull(store.readExact(identity))
                assertCanonicalProgressEquals(afterMove, afterDelayedPassive)
                assertEquals(afterMove.updatedAtMillis, afterDelayedPassive.updatedAtMillis)
                assertEquals(afterMove.writerSequence, afterDelayedPassive.writerSequence)

                lateinit var activity: ReadiumEpubReaderActivity
                scenario.onActivity { activity = it }
                withContext(Dispatchers.Main) {
                    activity.evaluateReaderJavascript(
                        "document.getElementById('internal-jump').click(); 'clicked';"
                    )
                }
                var linkedPosition: EpubReaderPosition? = null
                for (attempt in 0 until 40) {
                    linkedPosition = store.readExact(identity)
                    if (linkedPosition?.locatorJson?.contains("chapter-2.xhtml") == true) break
                    SystemClock.sleep(250)
                }
                assertTrue(
                    "internal EPUB link was not persisted through the navigator-owned jump",
                    linkedPosition?.locatorJson?.contains("chapter-2.xhtml") == true
                )
            }
        } finally {
            store.removeForBook(scope, bookId)
        }
    }

    @Test
    fun aRepaginatedReReportOfTheOldPageDoesNotReplaceNewerReadAloudProgress() {
        launch().use { scenario ->
            val opened = awaitStoredHref("reader did not open on chapter 1") { it.endsWith("chapter-1.xhtml") }

            scenario.moveToState(Lifecycle.State.CREATED)
            val later = simulateBackgroundReadAloudReaching(
                opened.href.toString().replace("chapter-1.xhtml", "chapter-3.xhtml")
            )
            // Hold the catch-up navigation so the replayed report deterministically comes first.
            scenario.onActivity { it.holdForegroundSyncNavigationForTest = true }
            scenario.moveToState(Lifecycle.State.RESUMED)
            // Readium re-paginates and reports the page the reader showed before, with slightly
            // different progression, fragment and position details.
            val stale = requireNotNull(
                Locator.fromJSON(
                    JSONObject(opened.toJSON().toString()).apply {
                        put("href", "${opened.href}#p2")
                        getJSONObject("locations").apply {
                            put("progression", (opened.locations.progression ?: 0.0) + 0.002)
                            put("position", (opened.locations.position ?: 1) + 1)
                        }
                    }
                )
            )
            scenario.onActivity { activity ->
                assertTrue("sync must still be pending", activity.isForegroundSyncPendingForTest())
                activity.applyEpubPaginationForTest(
                    pageIndex = activity.currentPageForTest(),
                    pageCount = activity.currentPageCountForTest(),
                    locator = stale
                )
                assertEquals(
                    "the replayed old page must not be recorded",
                    later.href.toString(),
                    requireNotNull(ReadiumEpubLocatorStore(context).read(readerKey)).href.toString()
                )
                activity.releaseForegroundSyncNavigationForTest()
            }
            SystemClock.sleep(2000)

            val stored = requireNotNull(ReadiumEpubLocatorStore(context).read(readerKey))
            assertEquals(
                "a stale visual re-report must not replace the newer read-aloud position",
                later.href.removeFragment().toString(),
                stored.href.removeFragment().toString()
            )
            // The reader itself caught up: the navigator now shows the newer position.
            scenario.onActivity { activity -> assertTrue(!activity.isForegroundSyncPendingForTest()) }
        }
    }

    @Test
    fun stoppingAgainWhileCatchingUpLeavesTheBackgroundPositionAlone() {
        launch().use { scenario ->
            val opened = awaitStoredHref("reader did not open on chapter 1") { it.endsWith("chapter-1.xhtml") }
            scenario.moveToState(Lifecycle.State.CREATED)
            simulateBackgroundReadAloudReaching(opened.href.toString().replace("chapter-1.xhtml", "chapter-2.xhtml"))
            scenario.onActivity { it.holdForegroundSyncNavigationForTest = true }
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { assertTrue(it.isForegroundSyncPendingForTest()) }

            // Back to the background before catching up; read-aloud carries on meanwhile.
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.onActivity { assertTrue("stopping ends the catch-up", !it.isForegroundSyncPendingForTest()) }
            val later = simulateBackgroundReadAloudReaching(
                opened.href.toString().replace("chapter-1.xhtml", "chapter-3.xhtml")
            )
            // Past the catch-up window: nothing may record the stale visual page while stopped.
            SystemClock.sleep(FOREGROUND_SYNC_TIMEOUT_MILLIS + 1_000)
            assertEquals(
                later.href.toString(),
                requireNotNull(ReadiumEpubLocatorStore(context).read(readerKey)).href.toString()
            )
        }
    }

    @Test
    fun aRealMoveWhileCatchingUpIsStillRecorded() {
        launch().use { scenario ->
            val opened = awaitStoredHref("reader did not open on chapter 1") { it.endsWith("chapter-1.xhtml") }
            scenario.moveToState(Lifecycle.State.CREATED)
            simulateBackgroundReadAloudReaching(opened.href.toString().replace("chapter-1.xhtml", "chapter-3.xhtml"))
            scenario.onActivity { it.holdForegroundSyncNavigationForTest = true }
            scenario.moveToState(Lifecycle.State.RESUMED)

            val elsewhere = requireNotNull(
                Locator.fromJSON(
                    JSONObject(opened.toJSON().toString()).put(
                        "href",
                        opened.href.toString().replace("chapter-1.xhtml", "chapter-2.xhtml")
                    )
                )
            )
            scenario.onActivity { activity ->
                assertTrue("sync must still be pending", activity.isForegroundSyncPendingForTest())
                activity.applyEpubPaginationForTest(
                    pageIndex = 0,
                    pageCount = activity.currentPageCountForTest().coerceAtLeast(1),
                    locator = elsewhere,
                    authoritativePageMove = true
                )
                assertTrue("a real move ends the catch-up", !activity.isForegroundSyncPendingForTest())
            }
            assertEquals(
                elsewhere.href.toString(),
                requireNotNull(ReadiumEpubLocatorStore(context).read(readerKey)).href.toString()
            )
        }
    }

    @Test
    fun onlyReReportsOfTheStalePageAreIgnoredWhileCatchingUp() {
        val stalePage = locatorJson("""{"href":"a.xhtml","type":"application/xhtml+xml","locations":{"progression":0.20}}""")
        val target = locatorJson("""{"href":"a.xhtml","type":"application/xhtml+xml","locations":{"progression":0.60}}""")
        val reReport = locatorJson("""{"href":"a.xhtml#p2","type":"application/xhtml+xml","locations":{"progression":0.21,"position":5}}""")
        val arrived = locatorJson("""{"href":"a.xhtml","type":"application/xhtml+xml","locations":{"progression":0.58}}""")
        val otherChapter = locatorJson("""{"href":"b.xhtml","type":"application/xhtml+xml","locations":{"progression":0.6}}""")
        val laterChapter = locatorJson("""{"href":"c.xhtml","type":"application/xhtml+xml","locations":{"progression":0.5}}""")

        assertTrue("re-report of the stale page", isStaleForegroundReReport(stalePage, target, reReport))
        assertTrue("arrival", !isStaleForegroundReReport(stalePage, target, arrived))
        assertTrue("a chapter the user went to", !isStaleForegroundReReport(stalePage, target, otherChapter))
        // Read-aloud moved to another resource: the old resource is the stale one.
        assertTrue(isStaleForegroundReReport(stalePage, laterChapter, reReport))
        assertTrue(!isStaleForegroundReReport(stalePage, laterChapter, laterChapter))
        assertTrue("nothing recorded yet", !isStaleForegroundReReport(null, target, reReport))
    }

    @Test
    fun anOldReadAloudPositionDoesNotPullTheReaderBackWhenItIsRebuilt() {
        val chapter1 = "OEBPS/Text/chapter-1.xhtml"
        val chapter3 = "OEBPS/Text/chapter-3.xhtml"
        fun locator(href: String, progression: Double, total: Double) = requireNotNull(
            Locator.fromJSON(
                JSONObject(
                    """{"href":"$href","type":"application/xhtml+xml",
                       "locations":{"progression":$progression,"totalProgression":$total}}"""
                )
            )
        )
        // Read-aloud was used early in the book and paused; the reader then read on to chapter 3.
        ReadiumEpubTtsPositionStore(context).save(readerKey, locator(chapter1, 0.4, 0.13))
        ReadiumEpubLocatorStore(context).save(readerKey, locator(chapter3, 0.5, 0.83))

        val written = java.util.Collections.synchronizedList(mutableListOf<String>())
        val positions = context.getSharedPreferences("readium_epub_locations", android.content.Context.MODE_PRIVATE)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == readerKey) prefs.getString(key, null)?.let(written::add)
        }
        launch().use { scenario ->
            awaitStoredHref("reader did not open on chapter 3") { it.endsWith("chapter-3.xhtml") }
            positions.registerOnSharedPreferenceChangeListener(listener)
            // Android rebuilds the reader (rotation, fold, or reopening after process death).
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.recreate()
            scenario.moveToState(Lifecycle.State.RESUMED)
            // Record every position written while the rebuilt reader settles: a jump back to the old
            // read-aloud position can be overwritten again moments later, so a final check alone
            // would miss it.
            SystemClock.sleep(4000)
            scenario.onActivity { }
            assertTrue(
                "reader jumped to the old read-aloud position; positions written: $written",
                written.none { it.contains("chapter-1.xhtml") }
            )
            val stored = requireNotNull(ReadiumEpubLocatorStore(context).read(readerKey))
            assertTrue(stored.href.toString().endsWith("chapter-3.xhtml"))
        }
        positions.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private fun locatorJson(json: String): Locator = requireNotNull(Locator.fromJSON(JSONObject(json)))

    private fun assertCanonicalProgressEquals(
        expected: EpubReaderPosition,
        actual: EpubReaderPosition
    ) {
        assertEquals(expected.chapterIndex, actual.chapterIndex)
        assertEquals(expected.pageIndex, actual.pageIndex)
        assertEquals(expected.pageCount, actual.pageCount)
        assertEquals(expected.locatorJson, actual.locatorJson)
        assertEquals(expected.progressPercent, actual.progressPercent)
        assertEquals(expected.overallPositionNumber, actual.overallPositionNumber)
        assertEquals(expected.overallPositionCount, actual.overallPositionCount)
        assertEquals(expected.renderedPageLayoutKey, actual.renderedPageLayoutKey)
        assertEquals(expected.renderedPageCounts, actual.renderedPageCounts)
    }

    @Test
    fun visualPositionsAreRecordedOnlyWhenTheReaderMoves() {
        val start = locatorJson("""{"href":"a.xhtml","type":"application/xhtml+xml","locations":{"progression":0.25}}""")
        val enriched = locatorJson(
            """{"href":"a.xhtml#p3","type":"application/xhtml+xml","locations":{"progression":0.25,"position":4,"totalProgression":0.1}}"""
        )
        val later = locatorJson("""{"href":"a.xhtml","type":"application/xhtml+xml","locations":{"progression":0.5}}""")
        val otherChapter = locatorJson("""{"href":"b.xhtml","type":"application/xhtml+xml","locations":{"progression":0.25}}""")
        val noProgression = locatorJson("""{"href":"a.xhtml","type":"application/xhtml+xml","locations":{}}""")

        assertTrue(shouldRecordVisualLocator(null, start))
        assertTrue(!shouldRecordVisualLocator(start, start))
        assertTrue("same place re-reported with book positions is not a move", !shouldRecordVisualLocator(start, enriched))
        assertTrue(shouldRecordVisualLocator(start, later))
        assertTrue(shouldRecordVisualLocator(start, otherChapter))
        assertTrue(shouldRecordVisualLocator(start, noProgression))
        assertTrue(!shouldRecordVisualLocator(noProgression, noProgression))
    }

    @Test
    fun savedStateRestoreUsesTheStoredPositionAndFallsBackToSavedState() {
        val saved = locatorJson("""{"href":"a.xhtml","type":"application/xhtml+xml","locations":{"progression":0.1}}""")
        val stored = locatorJson("""{"href":"c.xhtml","type":"application/xhtml+xml","locations":{"progression":0.5}}""")

        assertEquals(stored, epubRestoredLocator(savedState = saved, stored = stored))
        assertEquals(saved, epubRestoredLocator(savedState = saved, stored = null))
        // A fresh launch has no saved state and resolves its position from the intent and store.
        assertEquals(null, epubRestoredLocator(savedState = null, stored = stored))
    }

    @Test
    fun configurationRecreationStillRestoresTheCurrentPosition() {
        launch().use { scenario ->
            val opened = awaitStoredHref("reader did not open") { it.endsWith("chapter-1.xhtml") }
            scenario.recreate()
            val restored = awaitStoredHref("recreation lost the position") { it.endsWith("chapter-1.xhtml") }
            assertEquals(opened.href, restored.href)
        }
    }

    private fun writeThreeChapterEpub(target: File) {
        ZipOutputStream(target.outputStream().buffered()).use { zip ->
            val mimetype = "application/epub+zip".toByteArray()
            zip.putNextEntry(
                ZipEntry("mimetype").apply {
                    method = ZipEntry.STORED
                    size = mimetype.size.toLong()
                    compressedSize = mimetype.size.toLong()
                    crc = CRC32().apply { update(mimetype) }.value
                }
            )
            zip.write(mimetype)
            zip.closeEntry()
            fun deflated(name: String, text: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.trimIndent().toByteArray())
                zip.closeEntry()
            }
            deflated(
                "META-INF/container.xml",
                """
                <?xml version="1.0"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles>
                    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                  </rootfiles>
                </container>
                """
            )
            val chapters = (1..3)
            deflated(
                "OEBPS/content.opf",
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="book-id">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:identifier id="book-id">position-restore-fixture</dc:identifier>
                    <dc:title>Position Restore Fixture</dc:title>
                    <dc:language>en</dc:language>
                    <meta property="dcterms:modified">2026-10-10T00:00:00Z</meta>
                  </metadata>
                  <manifest>
                    ${chapters.joinToString("") { """<item id="c$it" href="Text/chapter-$it.xhtml" media-type="application/xhtml+xml"/>""" }}
                  </manifest>
                  <spine>${chapters.joinToString("") { """<itemref idref="c$it"/>""" }}</spine>
                </package>
                """
            )
            chapters.forEach { index ->
                deflated(
                    "OEBPS/Text/chapter-$index.xhtml",
                    """
                    <html xmlns="http://www.w3.org/1999/xhtml">
                      <head><title>Chapter $index</title></head>
                      <body><h1>Chapter $index</h1>
                        ${if (index == 1) """<a id="internal-jump" href="chapter-2.xhtml">Jump to chapter 2</a>""" else ""}
                        <p>${"Chapter $index reading content. ".repeat(300)}</p>
                      </body>
                    </html>
                    """
                )
            }
        }
    }
}
