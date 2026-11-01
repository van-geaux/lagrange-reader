package com.vangeaux.lagrange

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vangeaux.lagrange.provider.kavita.KavitaRepository
import kotlinx.coroutines.runBlocking
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

@RunWith(AndroidJUnit4::class)
class KavitaRepositorySessionCleanupInstrumentedTest {
    // Use the test APK's private storage so these destructive cleanup tests cannot touch the
    // installed app's profiles, downloads, or login state on a physical device.
    private val context get() = InstrumentationRegistry.getInstrumentation().context
    private val profileId = "profile-kavita-cleanup-${System.nanoTime()}"
    private val serverUrl = "https://kavita-cleanup-${System.nanoTime()}.example"
    private lateinit var readerKey: String
    private val bookId = "cleanup-book"
    private val fileId = "cleanup-file"
    private lateinit var repository: KavitaRepository
    private lateinit var scope: EpubReaderScope

    @Before
    fun setUp() = runBlocking<Unit> {
        ServerProfileStore(context).upsert(
            ServerProfile(
                id = profileId,
                serverUrl = serverUrl,
                providerId = PROVIDER_KAVITA
            )
        )
        repository = KavitaRepository(context)
        repository.setServerUrl(serverUrl)
        repository.setSelectedLibraryId("library-1")
        val accountScope = AuthenticatedAccountScopeStore(context).writeAuthoritative(
            profileId = profileId,
            providerId = PROVIDER_KAVITA,
            principal = "id:cleanup-user"
        )
        scope = EpubReaderScope(serverUrl, profileId, PROVIDER_KAVITA, accountScope)
        readerKey = epubReaderPersistenceKey(
            profileId = profileId,
            providerId = PROVIDER_KAVITA,
            accountScope = accountScope,
            serverUrl = serverUrl,
            bookId = bookId,
            fileId = fileId,
            publicationFingerprint = "cleanup-fingerprint"
        )
        seedAccountOwnedState()
    }

    @After
    fun tearDown() = runBlocking<Unit> {
        BrowserSnapshotStore(context).clear()
        EpubReaderPositionStore(context).clear()
        ReadiumEpubLocatorStore(context).clear()
        ReadiumEpubTtsPositionStore(context).clear()
        EpubBookLanguageStore(context, profileId).clearProfile()
        AuthenticatedAccountScopeStore(context).clear(profileId)
        context.getSharedPreferences("kavita_connection", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        ServerProfileStore(context).remove(profileId)
    }

    @Test
    fun clearSessionPurgesAccountOwnedStateButKeepsServerConfiguration() = runBlocking<Unit> {
        repository.clearSession()

        assertEquals(serverUrl, repository.getServerUrl())
        assertAccountOwnedStateCleared()
        assertNull(EpubBookLanguageStore(context, profileId).read(readerKey))
    }

    @Test
    fun clearServerPreservesScopedResumeForProfileSwitch() = runBlocking<Unit> {
        repository.clearServer()

        assertNull(repository.getServerUrl())
        assertNull(repository.getSelectedLibraryId())
        assertNull(BrowserSnapshotStore(context).read(serverUrl))
        assertTrue(EpubReaderPositionStore(context).readAll(scope).isNotEmpty())
        assertNotNull(ReadiumEpubLocatorStore(context).read(readerKey))
        assertNotNull(ReadiumEpubTtsPositionStore(context).read(readerKey))
        assertNull(AuthenticatedAccountScopeStore(context).read(profileId))
        assertEquals("de", EpubBookLanguageStore(context, profileId).read(readerKey))
    }

    private suspend fun seedAccountOwnedState() {
        BrowserSnapshotStore(context).saveLibraries(
            serverUrl = serverUrl,
            selectedLibraryId = "library-1",
            libraries = listOf(LibrarySummary("library-1", "Private library"))
        )
        assertTrue(EpubReaderPositionStore(context).importIfAbsent(
            EpubReaderPosition(
                serverUrl = serverUrl,
                bookId = bookId,
                fileId = fileId,
                chapterIndex = 1,
                pageIndex = 2,
                pageCount = 3,
                updatedAtMillis = 123L,
                profileId = profileId,
                providerId = PROVIDER_KAVITA,
                accountScope = scope.accountScope,
                publicationFingerprint = "cleanup-fingerprint"
            )
        ))
        val locator = requireNotNull(
            Locator.fromJSON(
                JSONObject(
                    """{"href":"chapter.xhtml","type":"application/xhtml+xml","locations":{"progression":0.5}}"""
                )
            )
        )
        ReadiumEpubLocatorStore(context).save(readerKey, locator)
        ReadiumEpubTtsPositionStore(context).save(readerKey, locator)
        EpubBookLanguageStore(context, profileId).save(readerKey, "de")

        assertTrue(EpubReaderPositionStore(context).readAll(scope).isNotEmpty())
        assertEquals("library-1", repository.getSelectedLibraryId())
        assertEquals("de", EpubBookLanguageStore(context, profileId).read(readerKey))
    }

    private suspend fun assertAccountOwnedStateCleared() {
        assertNull(repository.getSelectedLibraryId())
        assertNull(BrowserSnapshotStore(context).read(serverUrl))
        assertTrue(EpubReaderPositionStore(context).readAll(scope).isEmpty())
        assertNull(ReadiumEpubLocatorStore(context).read(readerKey))
        assertNull(ReadiumEpubTtsPositionStore(context).read(readerKey))
        assertNull(AuthenticatedAccountScopeStore(context).read(profileId))
    }
}
