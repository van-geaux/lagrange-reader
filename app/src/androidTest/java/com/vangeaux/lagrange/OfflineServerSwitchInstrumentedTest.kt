package com.vangeaux.lagrange

import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Choosing a configured server while it cannot be reached (offline, or DNS that only works on the
 * home network) must not destroy the current server's state, and a saved profile must still open
 * its downloaded books.
 */
@RunWith(AndroidJUnit4::class)
class OfflineServerSwitchInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    // .invalid never resolves (RFC 6761), like a home-only host name seen from outside.
    private val serverUrl = "https://lagrange-offline-${System.nanoTime()}.invalid"
    private val repository = BookOrbitRepository(context)
    private val profiles = ServerProfileStore(context)
    private val downloads = DownloadStore(context)
    private val book = File(context.filesDir, "offline-switch-${System.nanoTime()}.epub")
    private lateinit var storedServerUrl: String

    @Before
    fun setUp() = runBlocking<Unit> {
        repository.clearServer()
        repository.setServerUrl(serverUrl)
        storedServerUrl = requireNotNull(repository.getServerUrl())
        book.writeBytes("not a real epub".toByteArray())
        downloads.save(
            DownloadRecord(
                serverUrl = storedServerUrl,
                fileId = "file-1",
                bookId = "book-1",
                title = "Downloaded Offline Book",
                localPath = book.absolutePath,
                mediaKind = MediaKind.EPUB
            )
        )
        profiles.upsert(ServerProfile(id = serverProfileId(serverUrl), serverUrl = serverUrl))
    }

    @After
    fun tearDown() = runBlocking<Unit> {
        downloads.delete(storedServerUrl, "file-1")
        profiles.remove(serverProfileId(serverUrl))
        repository.clearServer()
        book.delete()
    }

    private fun coordinator() = AppCoordinator(
        repository = repository,
        dispatcher = Dispatchers.Main,
        serverProfileStore = profiles
    )

    /**
     * Waits until [accept] holds for 3 s in a row: startup shows a cached Browser before its session
     * check finishes, so a single sighting does not prove the final screen.
     */
    private fun awaitScreen(coordinator: AppCoordinator, message: String, accept: (AppScreen) -> Boolean): AppScreen {
        var stableSince = 0L
        repeat(120) {
            val screen = coordinator.screen.value
            if (accept(screen)) {
                if (stableSince == 0L) stableSince = SystemClock.uptimeMillis()
                if (SystemClock.uptimeMillis() - stableSince >= 3_000) return screen
            } else {
                stableSince = 0L
            }
            SystemClock.sleep(250)
        }
        throw AssertionError("$message; last screen ${coordinator.screen.value}")
    }

    @Test
    fun choosingTheUnreachableCurrentServerKeepsItAndItsOfflineLibrary() = runBlocking<Unit> {
        val coordinator = coordinator()
        coordinator.changeServer(serverUrl)

        val screen = awaitScreen(coordinator, "the offline library did not stay on screen") {
            it is AppScreen.Browser
        }
        assertEquals("the current server must stay configured", storedServerUrl, repository.getServerUrl())
        assertTrue("expected the offline library, got $screen", screen is AppScreen.Browser)
        val browser = (screen as AppScreen.Browser).browserState
        assertTrue(browser.homeBooks.any { it.id == "book-1" })
    }

    @Test
    fun aSavedServerUnreachableAfterItWasClearedIsRestoredWithItsDownloads() = runBlocking<Unit> {
        // The state the old code left behind: no server configured, profile and downloads kept.
        repository.clearServer()
        val coordinator = coordinator()
        coordinator.switchToServerProfile(serverProfileId(serverUrl))

        val screen = awaitScreen(coordinator, "saved server was not restored offline") {
            it is AppScreen.Browser
        }
        assertEquals(storedServerUrl, repository.getServerUrl())
        assertTrue("expected the offline library, got $screen", screen is AppScreen.Browser)
        val browser = (screen as AppScreen.Browser).browserState
        assertTrue(browser.homeBooks.any { it.id == "book-1" && it.localPath == book.absolutePath })
    }

    @Test
    fun anotherProviderAtTheSavedAddressIsNotRestoredOffline() = runBlocking<Unit> {
        repository.clearServer()
        val coordinator = coordinator()
        // The saved profile is BookOrbit; the user picks the same address as a Komga server.
        coordinator.changeServer(serverUrl, providerId = PROVIDER_KOMGA)

        val screen = awaitScreen(coordinator, "the switch did not settle on server setup") {
            it is AppScreen.ServerSetup
        }
        assertTrue("another provider must not open this profile's offline library: $screen", screen !is AppScreen.Browser)
        assertEquals(
            "the saved profile keeps its provider",
            PROVIDER_BOOKORBIT,
            profiles.readAll().single { it.id == serverProfileId(serverUrl) }.providerId
        )
    }
}
